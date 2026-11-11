package dev.pvpclient.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An ARGB colour stored as "#AARRGGBB" (a 6-digit "#RRGGBB" is read as fully opaque). */
public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, String description, int argb) {
        super(name, description, argb);
    }

    @Override
    protected Integer sanitize(Integer value) {
        return value == null ? getDefault() : value;
    }

    @Override
    public JsonElement serialize() { return new JsonPrimitive(String.format("#%08X", get())); }

    @Override
    public void deserialize(JsonElement element) {
        String s = element.getAsJsonPrimitive().getAsString().trim();
        if (!s.startsWith("#")) throw new IllegalArgumentException("colour must start with #");
        String hex = s.substring(1);
        if (hex.length() == 6) hex = "FF" + hex;
        if (hex.length() != 8) throw new IllegalArgumentException("colour must be #RRGGBB or #AARRGGBB");
        set((int) Long.parseLong(hex, 16));
    }
}
