package dev.pvpclient.event;

import dev.pvpclient.settings.Setting;

/** Posted whenever a setting's value actually changes. */
public record SettingChangedEvent(Setting<?> setting) {}
