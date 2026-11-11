package dev.pvpclient.settings;

import com.google.gson.JsonElement;
import dev.pvpclient.event.EventBus;
import dev.pvpclient.event.SettingChangedEvent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Base class for typed settings. Values are always passed through {@link #sanitize} so a setting
 * can never hold an invalid value, whether it was set from the GUI or loaded from disk.
 */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visibleWhen = () -> true;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public final String name() { return name; }
    public final String description() { return description; }
    public final T get() { return value; }
    public final T getDefault() { return defaultValue; }

    public final void set(T newValue) {
        T clean = sanitize(newValue);
        if (Objects.equals(clean, value)) return;
        value = clean;
        EventBus.GLOBAL.post(new SettingChangedEvent(this));
    }

    public final void reset() { set(defaultValue); }

    /** Lets the GUI hide settings that depend on others (e.g. only show "Range" when "Mode" is X). */
    public final void visibleWhen(BooleanSupplier condition) { this.visibleWhen = condition; }
    public final boolean isVisible() { return visibleWhen.getAsBoolean(); }

    /** Returns a valid value (clamped, snapped, or the default if the input is unusable). */
    protected abstract T sanitize(T value);

    public abstract JsonElement serialize();

    /** @throws RuntimeException if the element has the wrong shape; the caller falls back to the default. */
    public abstract void deserialize(JsonElement element);
}
