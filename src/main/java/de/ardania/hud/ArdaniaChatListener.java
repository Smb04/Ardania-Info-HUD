package de.ardania.hud;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Verarbeitet Server-Nachrichten und unterdrueckt stille Abfragen im Chat.
 */
public final class ArdaniaChatListener {

    private final ArdaniaMessageParser parser;

    public ArdaniaChatListener(ArdaniaMessageParser parser) {
        this.parser = parser;
    }

    public void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::onAllowGameMessage);

        // Wenn der Spieler manuell z. B. /ontime tippt, heben wir die Unterdrueckung auf
        ClientSendMessageEvents.COMMAND.register(command -> {
            ArdaniaSyncManager.getInstance().onPlayerManualCommand(command);
        });
    }

    private boolean onAllowGameMessage(Component message, boolean overlay) {
        Minecraft client = Minecraft.getInstance();
        if (!ArdaniaClientMod.isOnArdania(client)) {
            return true;
        }

        String raw = message.getString();

        try {
            // 1. Nachricht parsen (ArdaBank-Aenderungen, Jobs oder Punktestand)
            boolean matched = this.parser.parse(raw, overlay);

            // 2. Action-Bar Job-Einnahmen: Markiert eine Aenderung fuer den 30s-Sync
            if (overlay && this.parser.isActionBarIncome(raw)) {
                ArdaniaSyncManager.getInstance().triggerMoneySync();
            }

            // 3. Stille Antworten (/geld, /ardapoints me, /jobs stats) im Chat unterdruecken
            if (!overlay && this.parser.isSilentServerResponse(raw)) {
                if (ArdaniaSyncManager.getInstance().isSuppressingResponses()) {
                    if (ArdaniaClientMod.DEBUG) {
                        ArdaniaClientMod.LOGGER.info("[ArdaniaHUD] Stille Serverantwort unterdrueckt: {}", raw);
                    }
                    return false;
                }
            }

            // 4. Fehlermeldungen bei nicht vorhandenen Befehlen (z. B. /jobs im Nether) unterdruecken
            if (!overlay && this.parser.isUnknownCommandError(raw)) {
                if (ArdaniaSyncManager.getInstance().isSuppressingResponses()) {
                    if (ArdaniaClientMod.DEBUG) {
                        ArdaniaClientMod.LOGGER.info("[ArdaniaHUD] Fehler wegen fehlendem Befehl unterdrueckt: {}", raw);
                    }
                    return false;
                }
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
