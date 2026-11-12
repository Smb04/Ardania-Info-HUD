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

/**
 * Client-Einstiegspunkt der Fabric-Mod fuer moderne Minecraft-Versionen (MC 26.2 &amp; 26.3).
 *
 * <p>Initialisiert Konfiguration, State, Chat-Listener, {@code HudElement}-Registrierung und
 * {@code KeyMapping}s (Taste {@code J} fuer Sofortabgleich, optionaler Toggle-Key).</p>
 */
public final class ArdaniaClientMod implements ClientModInitializer {

    /** Mod-ID fuer Ressourcen, Mixins und Konfiguration. */
    public static final String MOD_ID = "ardania_hud";
    /** Zentraler SLF4J-Logger der Mod. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Optionales Debug-Logging via Systemproperty {@code -Dardania_hud.debug=true}. */
    public static final boolean DEBUG = Boolean.getBoolean(MOD_ID + ".debug");
    private static final boolean ANY_SERVER = Boolean.getBoolean(MOD_ID + ".anyServer");

    private static final String SERVER_HOST = "ardania";

    private KeyMapping syncKey;
    private KeyMapping toggleKey;
    private static volatile net.minecraft.client.gui.screens.Screen pendingScreenToOpen = null;

    /**
     * Erstellt die Fabric-Client-Instanz.
     */
    public ArdaniaClientMod() {
    }

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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            state.onServerDisconnect();
            ArdaniaSyncManager.getInstance().onServerDisconnect();
        });

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
            boolean screenOpen = ArdaniaSyncManager.isAnyScreenOpen(client);

            net.minecraft.client.gui.screens.Screen toOpen = pendingScreenToOpen;
            if (toOpen != null && !screenOpen) {
                pendingScreenToOpen = null;
                openScreen(client, toOpen);
                screenOpen = true;
            }

            ArdaniaSyncManager.getInstance().tick(client);

            while (this.syncKey.consumeClick()) {
                if (screenOpen) {
                    continue;
                }
                state.notifyStatUpdated();
                if (client.player != null) {
                    client.player.sendOverlayMessage(ArdaniaI18n.translate("message.ardania_hud.syncing"));
                }
                ArdaniaSyncManager.getInstance().forceFullSync(client);
            }

            while (this.toggleKey.consumeClick()) {
                if (screenOpen) {
                    continue;
                }
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

    /**
     * Prueft, ob der Client aktuell mit Ardania.de (bzw. per {@code -Dardania_hud.anyServer=true}
     * mit einem beliebigen Testserver) verbunden ist.
     *
     * @param client Minecraft-Client-Instanz
     * @return {@code true}, wenn ein Ardania-Host oder Ardania-Tablist-Header erkannt wurde
     */
    public static boolean isOnArdania(Minecraft client) {
        if (ANY_SERVER) {
            return true;
        }
        ServerData server = client.getCurrentServer();
        if (server != null && server.ip != null) {
            String host = normalizeHost(server.ip);
            if (host.contains(SERVER_HOST)) {
                return true;
            }
        }
        // Fallback fuer Aliase ohne "ardania" im Hostnamen: Der Tablisten-Header
        // von Ardania.de enthaelt "Willkommen auf Ardania".
        String header = TabListSnapshot.getHeader();
        return header != null && header.toLowerCase(Locale.ROOT).contains(SERVER_HOST);
    }

    private static String normalizeHost(String address) {
        String value = address.trim().toLowerCase(Locale.ROOT);
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) {
                value = value.substring(0, end + 1);
            }
        } else {
            int colon = value.indexOf(':');
            if (colon >= 0 && value.indexOf(':', colon + 1) < 0) {
                value = value.substring(0, colon);
            }
        }
        return value;
    }

    /**
     * Oeffnet einen Bildschirm unmittelbar auf dem Client.
     *
     * @param client Minecraft-Client-Instanz
     * @param screen Zu oeffnender Bildschirm (oder {@code null} zum Schliessen)
     */
    public static void openScreen(Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        if (client != null) {
            client.setScreenAndShow(screen);
        }
    }

    /**
     * Merkt einen Bildschirm zum Oeffnen vor, sobald der Chat-Screen geschlossen wurde.
     *
     * @param screen Zu oeffnender Bildschirm
     */
    public static void requestOpenScreen(net.minecraft.client.gui.screens.Screen screen) {
        pendingScreenToOpen = screen;
    }

    /**
     * Erzeugt einen Mod-spezifischen {@link Identifier} unter dem Namespace {@link #MOD_ID}.
     *
     * @param path Ressourcenpfad innerhalb des Mods
     * @return Zusammengesetzter Identifier
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
