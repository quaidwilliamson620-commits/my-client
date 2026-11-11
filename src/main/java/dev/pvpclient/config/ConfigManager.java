package dev.pvpclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.pvpclient.event.EventBus;
import dev.pvpclient.event.ModuleToggleEvent;
import dev.pvpclient.event.SettingChangedEvent;
import dev.pvpclient.module.Module;
import dev.pvpclient.module.ModuleRegistry;
import dev.pvpclient.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads and saves named profiles as JSON under {@code <config>/pvpclient/profiles/}.
 *
 * <ul>
 *   <li>Writes go to a temp file first and are moved into place, so a crash can't leave half a file.</li>
 *   <li>Unreadable profiles are renamed to {@code .corrupt} and defaults are used.</li>
 *   <li>Changes mark the config dirty; {@link #flushIfDirty()} saves (called on tick in phase 2 and on JVM exit).</li>
 * </ul>
 */
public final class ConfigManager {
    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final String DEFAULT_PROFILE = "default";

    private final ModuleRegistry registry;
    private final Path profilesDir;
    private final Path statePath;

    private String activeProfile = DEFAULT_PROFILE;
    private volatile boolean dirty;
    private boolean loading;

    public ConfigManager(Path rootDir, ModuleRegistry registry) {
        this.registry = registry;
        this.profilesDir = rootDir.resolve("profiles");
        this.statePath = rootDir.resolve("state.json");
    }

    public static boolean isValidProfileName(String name) {
        return name != null && PROFILE_NAME.matcher(name).matches();
    }

    public synchronized void init() {
        try {
            Files.createDirectories(profilesDir);
        } catch (IOException e) {
            Log.LOGGER.error("Could not create config directory {}", profilesDir, e);
        }

        activeProfile = readActiveProfile();
        if (Files.exists(profilePath(activeProfile))) {
            loadProfile(activeProfile);
        } else {
            saveProfile(); // first run: write defaults so the file exists
        }

        EventBus.GLOBAL.subscribe(SettingChangedEvent.class, e -> markDirty());
        EventBus.GLOBAL.subscribe(ModuleToggleEvent.class, e -> markDirty());
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushIfDirty, "pvpclient-config-flush"));
    }

    // ---- dirty tracking -------------------------------------------------------------------

    public void markDirty() {
        if (!loading) dirty = true;
    }

    public synchronized void flushIfDirty() {
        if (dirty) saveProfile();
    }

    // ---- profiles -------------------------------------------------------------------------

    public String activeProfile() { return activeProfile; }

    public synchronized List<String> listProfiles() {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(profilesDir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .filter(ConfigManager::isValidProfileName)
                    .forEach(names::add);
        } catch (IOException e) {
            Log.LOGGER.warn("Could not list profiles", e);
        }
        Collections.sort(names);
        return names;
    }

    /** Creates a new profile as a copy of the current settings. @return false if invalid or it already exists. */
    public synchronized boolean createProfile(String name) {
        if (!isValidProfileName(name) || Files.exists(profilePath(name))) return false;
        return writeProfile(name);
    }

    /** Saves the current profile, then loads {@code name}. @return false if it doesn't exist. */
    public synchronized boolean switchProfile(String name) {
        if (!isValidProfileName(name) || !Files.exists(profilePath(name))) return false;
        flushIfDirty();
        loadProfile(name);
        activeProfile = name;
        writeState();
        return true;
    }

    /** The active profile can't be deleted. */
    public synchronized boolean deleteProfile(String name) {
        if (!isValidProfileName(name) || name.equals(activeProfile)) return false;
        try {
            return Files.deleteIfExists(profilePath(name));
        } catch (IOException e) {
            Log.LOGGER.warn("Could not delete profile {}", name, e);
            return false;
        }
    }

    public synchronized void resetModule(Module module) {
        module.resetToDefaults();
        markDirty();
    }

    public synchronized void resetAll() {
        for (Module m : registry.all()) m.resetToDefaults();
        markDirty();
    }

    public synchronized void saveProfile() {
        writeProfile(activeProfile);
    }

    // ---- internals ------------------------------------------------------------------------

    private Path profilePath(String name) { return profilesDir.resolve(name + ".json"); }

    private boolean writeProfile(String name) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject modules = new JsonObject();
        for (Module m : registry.all()) modules.add(m.name(), m.toJson());
        root.add("modules", modules);

        boolean ok = writeAtomic(profilePath(name), GSON.toJson(root));
        if (ok && name.equals(activeProfile)) dirty = false;
        return ok;
    }

    private void loadProfile(String name) {
        Path path = profilePath(name);
        JsonObject root = null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) root = parsed.getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Log.LOGGER.error("Profile {} is unreadable; using defaults", name, e);
        }
        if (root == null) quarantine(path);

        loading = true; // don't mark dirty because of values we're loading
        try {
            for (Module m : registry.all()) m.resetToDefaults();
            if (root != null) applyProfile(root);
        } finally {
            loading = false;
        }
        dirty = false;
    }

    private void applyProfile(JsonObject root) {
        int schema = root.has("schema") && root.get("schema").isJsonPrimitive()
                && root.get("schema").getAsJsonPrimitive().isNumber() ? root.get("schema").getAsInt() : 0;
        if (schema > SCHEMA_VERSION) {
            Log.LOGGER.warn("Profile schema {} is newer than supported {}; loading best-effort", schema, SCHEMA_VERSION);
        }
        // Future migrations go here: if (schema < 2) { ...rewrite root... }

        if (!root.has("modules") || !root.get("modules").isJsonObject()) return;
        JsonObject modules = root.getAsJsonObject("modules");
        for (Module m : registry.all()) {
            JsonElement element = modules.get(m.name());
            if (element != null && element.isJsonObject()) m.fromJson(element.getAsJsonObject());
        }
    }

    private String readActiveProfile() {
        if (!Files.exists(statePath)) return DEFAULT_PROFILE;
        try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            String name = o.get("activeProfile").getAsString();
            if (isValidProfileName(name)) return name;
        } catch (IOException | RuntimeException e) {
            Log.LOGGER.warn("Could not read state.json; using default profile", e);
        }
        return DEFAULT_PROFILE;
    }

    private void writeState() {
        JsonObject o = new JsonObject();
        o.addProperty("activeProfile", activeProfile);
        writeAtomic(statePath, GSON.toJson(o));
    }

    private void quarantine(Path path) {
        try {
            Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.LOGGER.warn("Could not move corrupt file {}", path, e);
        }
    }

    private static boolean writeAtomic(Path target, String content) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Log.LOGGER.error("Failed to write {}", target, e);
            return false;
        }
    }
}
