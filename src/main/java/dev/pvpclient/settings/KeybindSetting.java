package dev.pvpclient.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A GLFW key code, or {@link #NONE}. Stored as an int. */
public final class KeybindSetting extends Setting<Integer> {
    public static final int NONE = -1;
    private static final int MAX_KEY = 400; // above GLFW_KEY_LAST (348) with headroom

    public KeybindSetting(String name, String description, int defaultKey) {
        super(name, description, clean(defaultKey));
    }

    private static int clean(int key) {
        return key < 0 || key > MAX_KEY ? NONE : key;
    }

    @Override
    protected Integer sanitize(Integer value) {
        return value == null ? NONE : clean(value);
    }

    @Override
    public JsonElement serialize() { return new JsonPrimitive(get()); }

    @Override
    public void deserialize(JsonElement element) {
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (!p.isNumber()) throw new IllegalArgumentException("expected number");
        set(p.getAsInt());
    }
}
