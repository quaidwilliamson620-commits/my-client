package dev.pvpclient.module;

import dev.pvpclient.modules.misc.FrameworkTestModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Central registry of all modules, in registration order. */
public final class ModuleRegistry {
    private final Map<String, Module> byName = new LinkedHashMap<>();

    public static ModuleRegistry createDefault() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FrameworkTestModule()); // placeholder; real modules arrive in phase 3
        return registry;
    }

    public void register(Module module) {
        String key = module.name().toLowerCase(Locale.ROOT);
        if (byName.putIfAbsent(key, module) != null) {
            throw new IllegalArgumentException("Duplicate module name: " + module.name());
        }
    }

    public Module get(String name) { return byName.get(name.toLowerCase(Locale.ROOT)); }

    public Collection<Module> all() { return Collections.unmodifiableCollection(byName.values()); }

    public List<Module> byCategory(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : byName.values()) if (m.category() == category) out.add(m);
        return out;
    }

    /** Case-insensitive substring search on name and description, for the GUI search box. */
    public List<Module> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Module> out = new ArrayList<>();
        for (Module m : byName.values()) {
            if (q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q)
                    || m.description().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(m);
            }
        }
        return out;
    }
}
