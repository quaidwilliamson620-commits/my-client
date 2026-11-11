package dev.pvpclient.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.pvpclient.event.EventBus;
import dev.pvpclient.event.ModuleToggleEvent;
import dev.pvpclient.settings.BooleanSetting;
import dev.pvpclient.settings.KeybindSetting;
import dev.pvpclient.settings.Setting;
import dev.pvpclient.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for every module. Subclasses add settings with {@link #add} in their constructor and
 * override the lifecycle hooks. All hooks run inside a guard: if one throws, the module is disabled
 * and the error is recorded in {@link #lastError()} instead of crashing the client.
 */
public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final KeybindSetting keybind;
    private final BooleanSetting showInHud;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final Set<String> settingNames = new HashSet<>();

    private boolean enabled;
    private String lastError;

    protected Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = new KeybindSetting("Keybind", "Key that toggles " + name + ".", defaultKey);
        this.showInHud = add(new BooleanSetting("Show in HUD", "Show this module in the enabled-module list.", true));
    }

    // ---- identity -------------------------------------------------------------------------

    public final String name() { return name; }
    public final String description() { return description; }
    public final Category category() { return category; }
    public final KeybindSetting keybind() { return keybind; }
    public final boolean isEnabled() { return enabled; }
    public final List<Setting<?>> settings() { return Collections.unmodifiableList(settings); }

    /** Most recent failure or rejection message, shown in the GUI as status feedback. Null if none. */
    public final String lastError() { return lastError; }

    public final boolean isShownInHud() { return showInHud.get(); }

    /** Registers a setting. Names must be unique within a module because they are JSON keys. */
    protected final <S extends Setting<?>> S add(S setting) {
        if (!settingNames.add(setting.name())) {
            throw new IllegalArgumentException("Duplicate setting '" + setting.name() + "' in module " + name);
        }
        settings.add(setting);
        return setting;
    }

    // ---- overridable hooks ----------------------------------------------------------------

    /** Called when the module turns on. */
    protected void onEnable() {}

    /** Called when the module turns off (including after a failure). */
    protected void onDisable() {}

    /** Called every client tick while enabled. Prefer events over doing work here when possible. */
    protected void onTick() {}

    /** Called after settings were loaded from a profile. */
    protected void onConfigLoaded() {}

    /** Optional short text shown next to the name in the HUD list (e.g. a mode). Null for none. */
    public String hudInfo() { return null; }

    /** True for modules that change gameplay and must only run in a private test environment. */
    public boolean requiresTestEnvironment() { return false; }

    // ---- state changes --------------------------------------------------------------------

    public final void toggle() { setEnabled(!enabled); }

    /** @return true if the module is now in the requested state. */
    public final boolean setEnabled(boolean target) {
        if (target == enabled) return true;

        if (target) {
            if (requiresTestEnvironment() && !TestEnvironmentGuard.isAllowed()) {
                lastError = name + ": " + TestEnvironmentGuard.DENIED_MESSAGE;
                EventBus.GLOBAL.post(new ModuleToggleEvent(this, false));
                return false;
            }
            lastError = null;
            enabled = true;
            if (!run("onEnable", this::onEnable)) return false; // run() already disabled us
        } else {
            enabled = false;
            run("onDisable", this::onDisable);
        }
        EventBus.GLOBAL.post(new ModuleToggleEvent(this, enabled));
        return enabled == target;
    }

    /** Driven by the client tick event (wired up in phase 2). */
    public final void tick() {
        if (enabled) run("onTick", this::onTick);
    }

    /** Disables the module and clears settings, keybind, and status back to defaults. */
    public final void resetToDefaults() {
        setEnabled(false);
        for (Setting<?> s : settings) s.reset();
        keybind.reset();
        lastError = null;
    }

    private boolean run(String stage, Runnable action) {
        try {
            action.run();
            return true;
        } catch (Exception | LinkageError e) { // LinkageError: a version-specific API that no longer exists
            lastError = name + " failed in " + stage + ": " + e;
            Log.LOGGER.error("Module {} failed in {}; disabling it", name, stage, e);
            boolean wasEnabled = enabled;
            enabled = false;
            if (wasEnabled && !stage.equals("onDisable")) {
                try { onDisable(); } catch (Exception | LinkageError ignored) { /* already failing */ }
            }
            EventBus.GLOBAL.post(new ModuleToggleEvent(this, false));
            return false;
        }
    }

    // ---- persistence ----------------------------------------------------------------------

    public final JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.add("keybind", keybind.serialize());
        JsonObject s = new JsonObject();
        for (Setting<?> setting : settings) s.add(setting.name(), setting.serialize());
        o.add("settings", s);
        return o;
    }

    /** Applies saved values. Missing or invalid entries keep their (already reset) defaults. */
    public final void fromJson(JsonObject json) {
        try {
            if (json.has("keybind")) keybind.deserialize(json.get("keybind"));
        } catch (RuntimeException e) {
            Log.LOGGER.warn("Ignoring invalid keybind for {}: {}", name, e.toString());
        }

        if (json.has("settings") && json.get("settings").isJsonObject()) {
            JsonObject saved = json.getAsJsonObject("settings");
            for (Setting<?> setting : settings) {
                JsonElement element = saved.get(setting.name());
                if (element == null) continue; // setting added in a newer version: keep default
                try {
                    setting.deserialize(element);
                } catch (RuntimeException e) {
                    Log.LOGGER.warn("Ignoring invalid value for {}.{}: {}", name, setting.name(), e.toString());
                    setting.reset();
                }
            }
        }

        run("onConfigLoaded", this::onConfigLoaded);

        boolean wantEnabled = json.has("enabled") && json.get("enabled").isJsonPrimitive()
                && json.get("enabled").getAsJsonPrimitive().isBoolean() && json.get("enabled").getAsBoolean();
        if (wantEnabled) setEnabled(true); // respects the test-environment guard
    }
}
