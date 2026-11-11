package dev.pvpclient.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    @Override
    protected Boolean sanitize(Boolean value) {
        return value == null ? getDefault() : value;
    }

    @Override
    public JsonElement serialize() { return new JsonPrimitive(get()); }

    @Override
    public void deserialize(JsonElement element) {
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (!p.isBoolean()) throw new IllegalArgumentException("expected boolean");
        set(p.getAsBoolean());
    }
}
