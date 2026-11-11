package dev.pvpclient.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A dropdown backed by an enum. Stored by constant name so reordering the enum is safe. */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final Class<E> type;

    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue);
        this.type = defaultValue.getDeclaringClass();
    }

    public E[] options() { return type.getEnumConstants(); }
    public boolean is(E other) { return get() == other; }

    @Override
    protected E sanitize(E value) {
        return value == null ? getDefault() : value;
    }

    @Override
    public JsonElement serialize() { return new JsonPrimitive(get().name()); }

    @Override
    public void deserialize(JsonElement element) {
        String s = element.getAsJsonPrimitive().getAsString();
        set(Enum.valueOf(type, s)); // throws IllegalArgumentException for removed/renamed constants
    }
}
