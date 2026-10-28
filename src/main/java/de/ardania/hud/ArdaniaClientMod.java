package de.ardania.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class ArdaniaClientMod implements ClientModInitializer {

    public static final String MOD_ID = "ardania_hud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final boolean DEBUG = Boolean.getBoolean(MOD_ID + ".debug");
    private static final boolean ANY_SERVER = Boolean.getBoolean(MOD_ID + ".anyServer");

    private static final String SERVER_HOST = "ardania";

    private KeyMapping syncKey;
    private KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        ArdaniaConfig.load();

        ArdaniaState state = ArdaniaState.getInstance();
        state.loadCache();

        new ArdaniaChatListener(new ArdaniaMessageParser(state)).register();

        ArdaniaHudRenderer hudRenderer = new ArdaniaHudRenderer(state);
        HudElementRegistry.addLast(id("info_hud"), hudRenderer);

        registerKeybinds(state, hudRenderer);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (isOnArdania(client)) {
                state.onServerJoin();
                if (ArdaniaConfig.getInstance().autoSyncOnJoin) {
                    ArdaniaSyncManager.getInstance().onServerJoin();
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state.onServerDisconnect());

        LOGGER.info("Ardania Info HUD geladen");
    }

    private void registerKeybinds(ArdaniaState state, ArdaniaHudRenderer hudRenderer) {
        KeyMapping.Category category = KeyMapping.Category.register(id("controls"));

        this.syncKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ardania_hud.sync",
                InputConstants.KEY_J,
                category
        ));

        this.toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ardania_hud.toggle",
                InputConstants.UNKNOWN.getValue(),
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ArdaniaSyncManager.getInstance().tick(client);

            while (this.syncKey.consumeClick()) {
                state.notifyStatUpdated();
                if (client.player != null) {
                    client.player.sendOverlayMessage(ArdaniaI18n.translate("message.ardania_hud.syncing"));
                }
                ArdaniaSyncManager.getInstance().forceFullSync(client);
            }

            while (this.toggleKey.consumeClick()) {
                ArdaniaConfig config = ArdaniaConfig.getInstance();
                long now = System.currentTimeMillis();

                if (hudRenderer.isShowing(now)) {
                    if (config.autoHide) {
                        state.hideHudNow();
                    } else {
                        config.enabled = false;
                        ArdaniaConfig.save();
                    }
                    if (client.player != null) {
                        client.player.sendOverlayMessage(ArdaniaI18n.translate("message.ardania_hud.toggled_off"));
                    }
                } else {
                    if (!config.enabled) {
                        config.enabled = true;
                        ArdaniaConfig.save();
                    }
                    state.notifyStatUpdated();
                    if (client.player != null) {
                        client.player.sendOverlayMessage(ArdaniaI18n.translate("message.ardania_hud.toggled_on"));
                    }
                }
            }
        });
    }

    public static boolean isOnArdania(Minecraft client) {
        if (ANY_SERVER) {
            return true;
        }
        ServerData server = client.getCurrentServer();
        return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains(SERVER_HOST);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
