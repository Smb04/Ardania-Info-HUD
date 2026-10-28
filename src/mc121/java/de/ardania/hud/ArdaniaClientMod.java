package de.ardania.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public final class ArdaniaClientMod implements ClientModInitializer {

    public static final String MOD_ID = "ardania_hud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final boolean DEBUG = Boolean.getBoolean(MOD_ID + ".debug");
    private static final boolean ANY_SERVER = Boolean.getBoolean(MOD_ID + ".anyServer");

    private static final String SERVER_HOST = "ardania";
    private static final String KEY_CATEGORY = "key.category.ardania_hud.controls";

    private KeyMapping syncKey;
    private KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        ArdaniaConfig.load();

        ArdaniaState state = ArdaniaState.getInstance();

        new ArdaniaChatListener(new ArdaniaMessageParser(state)).register();

        ArdaniaHudRenderer hudRenderer = new ArdaniaHudRenderer(state);
        HudRenderCallback.EVENT.register(hudRenderer);

        registerKeybinds(state, hudRenderer);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (isOnArdania(client) && ArdaniaConfig.getInstance().autoSyncOnJoin) {
                ArdaniaSyncManager.getInstance().onServerJoin();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state.reset());

        LOGGER.info("Ardania Info HUD geladen");
    }

    private void registerKeybinds(ArdaniaState state, ArdaniaHudRenderer hudRenderer) {
        Object categoryObj = resolveCategoryObject();

        this.syncKey = KeyBindingHelper.registerKeyBinding(createKeyMapping(
                "key.ardania_hud.sync",
                InputConstants.KEY_J,
                categoryObj
        ));

        this.toggleKey = KeyBindingHelper.registerKeyBinding(createKeyMapping(
                "key.ardania_hud.toggle",
                InputConstants.UNKNOWN.getValue(),
                categoryObj
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ArdaniaSyncManager.getInstance().tick(client);

            while (this.syncKey.consumeClick()) {
                state.notifyStatUpdated();
                if (client.player != null) {
                    client.player.displayClientMessage(Component.translatable("message.ardania_hud.syncing"), true);
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
                        client.player.displayClientMessage(Component.translatable("message.ardania_hud.toggled_off"), true);
                    }
                } else {
                    if (!config.enabled) {
                        config.enabled = true;
                        ArdaniaConfig.save();
                    }
                    state.notifyStatUpdated();
                    if (client.player != null) {
                        client.player.displayClientMessage(Component.translatable("message.ardania_hud.toggled_on"), true);
                    }
                }
            }
        });
    }

    private static Object resolveCategoryObject() {
        try {
            KeyMapping.class.getConstructor(String.class, int.class, String.class);
            return KEY_CATEGORY;
        } catch (NoSuchMethodException ignored) {
            // Ab MC 1.21.9 wird KeyMapping.Category anstelle eines Strings verwendet
            for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 3 && params[0] == String.class && params[1] == int.class) {
                    Class<?> categoryClass = params[2];
                    try {
                        for (Method m : categoryClass.getMethods()) {
                            if (Modifier.isStatic(m.getModifiers())
                                    && m.getReturnType() == categoryClass
                                    && m.getParameterCount() == 1
                                    && m.getParameterTypes()[0] == ResourceLocation.class) {
                                return m.invoke(null, id("controls"));
                            }
                        }
                        for (Field f : categoryClass.getFields()) {
                            if (Modifier.isStatic(f.getModifiers()) && f.getType() == categoryClass) {
                                return f.get(null);
                            }
                        }
                    } catch (ReflectiveOperationException e) {
                        LOGGER.warn("Konnte Tastenkategorie nicht aufloesen", e);
                    }
                }
            }
            return KEY_CATEGORY;
        }
    }

    private static KeyMapping createKeyMapping(String name, int keyCode, Object categoryObj) {
        try {
            if (categoryObj instanceof String catStr) {
                return KeyMapping.class.getConstructor(String.class, int.class, String.class)
                        .newInstance(name, keyCode, catStr);
            }
            for (Constructor<?> ctor : KeyMapping.class.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 3 && params[0] == String.class && params[1] == int.class && params[2].isInstance(categoryObj)) {
                    return (KeyMapping) ctor.newInstance(name, keyCode, categoryObj);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Konnte KeyMapping nicht erstellen: " + name, e);
        }
        throw new IllegalStateException("Kein passender KeyMapping-Konstruktor gefunden");
    }

    public static boolean isOnArdania(Minecraft client) {
        if (ANY_SERVER) {
            return true;
        }
        ServerData server = client.getCurrentServer();
        return server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains(SERVER_HOST);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
