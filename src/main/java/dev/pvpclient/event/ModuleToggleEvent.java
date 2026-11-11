package dev.pvpclient.event;

import dev.pvpclient.module.Module;

/** Posted after a module's enabled state changed (or an enable attempt was rejected/failed). */
public record ModuleToggleEvent(Module module, boolean enabled) {}
