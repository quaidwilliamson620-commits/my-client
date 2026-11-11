package dev.pvpclient;

import dev.pvpclient.config.ConfigManager;
import dev.pvpclient.module.ModuleRegistry;
import dev.pvpclient.util.Log;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Client entrypoint. Phase 1: registry + config. The GUI, HUD and key handling arrive in phase 2. */
public final class PvpClient implements ClientModInitializer {
    public static final String MOD_ID = "pvpclient";

    private static ModuleRegistry registry;
    private static ConfigManager config;

    @Override
    public void onInitializeClient() {
        registry = ModuleRegistry.createDefault();
        config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), registry);
        config.init();
        Log.LOGGER.info("PvpClient loaded {} module(s), profile '{}'", registry.all().size(), config.activeProfile());
    }

    public static ModuleRegistry registry() { return registry; }
    public static ConfigManager config() { return config; }
}
