package de.ardania.hud;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ArdaniaChatListener {

    private final ArdaniaMessageParser parser;

    public ArdaniaChatListener(ArdaniaMessageParser parser) {
        this.parser = parser;
    }

    public void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::onAllowGameMessage);
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                onAllowGameMessage(message, false));
        ClientSendMessageEvents.COMMAND.register(command ->
                ArdaniaSyncManager.getInstance().onPlayerManualCommand(command));
    }

    private boolean onAllowGameMessage(Component message, boolean overlay) {
        Minecraft client = Minecraft.getInstance();
        if (!ArdaniaClientMod.isOnArdania(client)) {
            return true;
        }

        String raw = message.getString();

        try {
            boolean matched = this.parser.parse(raw, overlay);
            if (matched) {
                ArdaniaState.getInstance().saveCache();
            }

            if (overlay && this.parser.isActionBarIncome(raw)) {
                ArdaniaSyncManager.getInstance().triggerMoneySync();
                ArdaniaState.getInstance().notifyStatUpdated();
            } else if (!overlay && this.parser.isJobNotification(raw)) {
                ArdaniaSyncManager.getInstance().triggerJobsSync();
                ArdaniaState.getInstance().notifyStatUpdated();
            } else if (!overlay && this.parser.isVoteNotification(raw)) {
                ArdaniaSyncManager.getInstance().triggerPointsSync();
                ArdaniaState.getInstance().notifyStatUpdated();
            }

            if (!overlay && ArdaniaSyncManager.getInstance().shouldSuppressMessage(raw, this.parser)) {
                if (ArdaniaClientMod.DEBUG) {
                    ArdaniaClientMod.LOGGER.info("[ArdaniaHUD] Stille Serverantwort unterdrueckt: {}", raw);
                }
                return false;
            }

            if (matched && ArdaniaClientMod.DEBUG) {
                ArdaniaClientMod.LOGGER.info("[ArdaniaHUD] [{}] verarbeitet: {}", overlay ? "ActionBar" : "Chat", raw);
            }
        } catch (RuntimeException e) {
            ArdaniaClientMod.LOGGER.warn("Konnte Nachricht nicht verarbeiten: {}", raw, e);
        }

        return true;
    }
}
