package dev.pvpclient.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A bounded number that snaps to a step (used by sliders). */
public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, initial(defaultValue, min, max, step));
        this.min = min;
        this.max = max;
        this.step = step;
    }

    private static double initial(double def, double min, double max, double step) {
        if (!(min <= max) || !(step > 0)) throw new IllegalArgumentException("invalid range for number setting");
        return snap(def, min, max, step);
    }

    private static double snap(double v, double min, double max, double step) {
        if (Double.isNaN(v)) v = min;
        double clamped = Math.max(min, Math.min(max, v));
        double snapped = Math.round((clamped - min) / step) * step + min;
        snapped = Math.max(min, Math.min(max, snapped));
        return Math.round(snapped * 1_000_000d) / 1_000_000d; // strip floating-point noise
    }

    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public float getFloat() { return get().floatValue(); }
    public int getInt() { return (int) Math.round(get()); }

    @Override
    protected Double sanitize(Double value) {
        return value == null ? getDefault() : snap(value, min, max, step);
    }

    @Override
    public JsonElement serialize() { return new JsonPrimitive(get()); }

    @Override
    public void deserialize(JsonElement element) {
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (!p.isNumber()) throw new IllegalArgumentException("expected number");
        double d = p.getAsDouble();
        if (!Double.isFinite(d)) throw new IllegalArgumentException("number not finite");
        set(d);
    }
}
