package dev.pvpclient.modules.misc;

import dev.pvpclient.module.Category;
import dev.pvpclient.module.Module;
import dev.pvpclient.settings.BooleanSetting;
import dev.pvpclient.settings.ColorSetting;
import dev.pvpclient.settings.EnumSetting;
import dev.pvpclient.settings.NumberSetting;

/** Exercises every setting type so config save/load can be verified. Removed once real modules exist. */
public final class FrameworkTestModule extends Module {
    public enum Mode { ALPHA, BETA, GAMMA }

    private final BooleanSetting flag = add(new BooleanSetting("Flag", "A boolean.", true));
    private final NumberSetting amount = add(new NumberSetting("Amount", "A slider from 0 to 10.", 5, 0, 10, 0.5));
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", "A dropdown.", Mode.ALPHA));
    private final ColorSetting color = add(new ColorSetting("Accent", "A colour.", 0xFF7C5CFF));

    public FrameworkTestModule() {
        super("Framework Test", "Placeholder module for verifying the framework.", Category.MISC, -1);
    }

    @Override
    public String hudInfo() { return mode.get().name(); }
}
