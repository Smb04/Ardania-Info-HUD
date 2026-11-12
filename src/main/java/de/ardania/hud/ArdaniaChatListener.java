package de.ardania.hud;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Event-Listener fuer Chat- und Actionbar-Nachrichten sowie clientseitige Befehle.
 *
 * <p>Verantwortlich fuer:</p>
 * <ul>
 *   <li>Abfangen eingehender Nachrichten (ALLOW_GAME, ALLOW_CHAT) zur Wertextraktion</li>
 *   <li>Lautloses Unterdruecken automatischer Synchronisationsmeldungen im Chat</li>
 *   <li>Erfassung ausgehender Spieler-Befehle (zur AFK- und Unterdrueckungssteuerung)</li>
 *   <li>Registrierung des Client-Befehls {@code /ardaniahud [config|debug|debug copy]}</li>
 * </ul>
 */
public final class ArdaniaChatListener {

    private final ArdaniaMessageParser parser;

    /**
     * Erstellt einen neuen Listener mit dem angegebenen Nachrichten-Parser.
     *
     * @param parser Parser zum Extrahieren von Ardania-Statistiken aus Chat- und Actionbar-Texten
     */
    public ArdaniaChatListener(ArdaniaMessageParser parser) {
        this.parser = parser;
    }

    /**
     * Registriert alle Fabric-Chat-, Befehls- und Client-Command-Listener (inkl. {@code /ardaniahud}).
     */
    public void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::onAllowGameMessage);
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                onAllowGameMessage(message, false));
        ClientSendMessageEvents.CHAT.register(message ->
                ArdaniaSyncManager.getInstance().onPlayerChatMessage());
        ClientSendMessageEvents.COMMAND.register(command ->
                ArdaniaSyncManager.getInstance().onPlayerManualCommand(command));

        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                    dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ardaniahud")
                            .executes(ctx -> {
                                handleDebugCommand(false);
                                return 1;
                            })
                            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("config").executes(ctx -> {
                                ArdaniaClientMod.requestOpenScreen(new ArdaniaConfigScreen(null));
                                return 1;
                            }))
                            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("edit").executes(ctx -> {
                                ArdaniaClientMod.requestOpenScreen(new ArdaniaHudEditorScreen(null));
                                return 1;
                            }))
                            .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("debug")
                                    .executes(ctx -> {
                                        handleDebugCommand(false);
                                        return 1;
                                    })
                                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("copy").executes(ctx -> {
                                        handleDebugCommand(true);
                                        return 1;
                                    })))
                    )
            );
        } catch (Throwable ignored) {
        }

        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (command != null) {
                String cmd = command.trim().toLowerCase(java.util.Locale.ROOT);
                if (cmd.equals("afk")) {
                    Minecraft client = Minecraft.getInstance();
                    ArdaniaSyncManager.getInstance().onAfkCommand(client.player);
                    return true;
                }
                if (cmd.equals("ardaniahud edit")) {
                    ArdaniaClientMod.requestOpenScreen(new ArdaniaHudEditorScreen(null));
                    return false;
                }
                if (cmd.equals("ardaniahud config") || cmd.equals("ardaniahud settings")) {
                    ArdaniaClientMod.requestOpenScreen(new ArdaniaConfigScreen(null));
                    return false;
                }
                if (cmd.equals("ardaniahud debug") || cmd.equals("ardaniahud debug copy") || cmd.equals("ardaniahud")) {
                    handleDebugCommand(cmd.contains("copy"));
                    return false;
                }
            }
            return true;
        });
    }

    private void handleDebugCommand(boolean copyToClipboard) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        ArdaniaSyncManager sync = ArdaniaSyncManager.getInstance();
        ArdaniaState state = ArdaniaState.getInstance();
        ArdaniaConfig config = ArdaniaConfig.getInstance();
        ArdaniaServerDetector.Detection detection = sync.getLastDetection();

        ServerData server = client.getCurrentServer();
        String host = (server != null && server.ip != null) ? server.ip : "?";
        String header = detection != null ? detection.headerText() : TabListSnapshot.getHeader();
        String footer = detection != null ? detection.footerText() : TabListSnapshot.getFooter();
        String confidence = detection != null
                ? String.format(java.util.Locale.ROOT, "%.0f%%", detection.confidence())
                : "?";
        String source = detection != null ? detection.source().name() : "NONE";
        String age = detection != null
                ? Math.max(0L, System.currentTimeMillis() - detection.timestamp()) + " ms"
                : "nie";
        String selfTag = "-";
        if (client.getConnection() != null && client.getConnection().getPlayerInfo(client.player.getUUID()) != null) {
            var selfInfo = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (selfInfo != null && selfInfo.getTabListDisplayName() != null) {
                selfTag = selfInfo.getTabListDisplayName().getString();
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("§6=== [Ardania Info HUD Debug] ===");
        lines.add("§eServer-Typ: §f" + sync.getCurrentServerType());
        lines.add("§7  Konfidenz: " + confidence + ", Quelle: " + source + ", Alter: " + age);
        lines.add("§eVerbindung: §f" + host + " §7(Ardania erkannt: " + ArdaniaClientMod.isOnArdania(client) + ")");
        lines.add("§eTablist-Header: §f" + printable(header));
        lines.add("§eTablist-Footer: §f" + printable(footer));
        lines.add("§eHeader-Zahlen: §f" + (detection != null ? formatCounts(detection.headerCounts()) : "-")
                + " §7(Summe: " + (detection != null ? headerCountSum(detection) : 0)
                + ", Tabliste: " + (detection != null ? detection.tabListSize() : 0) + ")");
        lines.add("§eTablisten-Tags: §f" + (detection != null ? formatCounts(detection.tagCounts()) : "-")
                + " §7(Tabliste: " + (detection != null ? detection.tabListSize() : 0) + ", selfTag: " + selfTag + ")");
        lines.add("§eDelta (Header-Tag): §f" + (detection != null ? formatDeltas(detection) : "-")
                + " §7(erwartetes Minimum: " + (detection != null ? detection.expectedMinDelta() : 1) + ")");
        lines.add("§eUngetaggte andere: §f" + (detection != null ? detection.untaggedOthers() : 0)
                + " §7(davon gleiche Welt: " + (detection != null ? detection.sameWorldUntagged() : 0) + ")");
        lines.add("§eGleiche Welt (Tags): §f"
                + (detection != null && !detection.sameWorldTags().isEmpty()
                ? String.join(", ", detection.sameWorldTags())
                : "keine"));
        lines.add("§eBefehlsbaum: §f" + (detection != null && detection.commandTreeReady() ? "bereit" : "unvollstaendig")
                + " §7(geld=" + (detection != null && detection.moneyAvailable())
                + ", jobs=" + (detection != null && detection.jobsAvailable())
                + ", kreativ=" + (detection != null && detection.creative()) + ")");
        lines.add("§eOverride: §f" + config.serverTypeOverride);
        lines.add("§eGesperrte Befehle (Welt): §f"
                + (sync.getBlockedCommandsForTesting().isEmpty() ? "keine" : sync.getBlockedCommandsForTesting()));
        lines.add("§eBefehls-Queue: §f"
                + (sync.getCommandQueueForTesting().isEmpty() ? "leer" : sync.getCommandQueueForTesting()));
        lines.add("§eEuronen: §f" + state.getFormattedEuronen() + " §7(bekannt: " + state.isEuronenKnown() + ")");
        lines.add("§eArdaPoints: §f" + state.getFormattedArdaPoints() + " §7(bekannt: " + state.isArdaPointsKnown() + ")");
        lines.add("§eJobs: §f" + state.getJobs().stream().map(j -> j.name() + " " + j.formatBadge(true)).toList());
        lines.add("§ePlaytime: §f" + state.getFormattedPlaytime());
        lines.add("§eConfig: pos=" + config.position + ", autoHide=" + config.autoHide
                + ", jobXp=" + config.showJobXp + ", scale=" + config.hudScale);

        String report = String.join("\n", lines);

        if (client.player != null) {
            for (String line : lines) {
                sendLocalChatMessage(client, Component.literal(line));
            }

            if (copyToClipboard) {
                client.keyboardHandler.setClipboard(report.replaceAll("§[0-9a-fk-or]", ""));
                sendLocalChatMessage(client, Component.literal("§a[ArdaniaHUD] Debug-Bericht in Zwischenablage kopiert!"));
            }
        }
    }

    /**
     * Schreibt eine Nachricht rein clientseitig in den Chat.
     *
     * <p>Die passende Methode hat sich zwischen den Versionen verschoben:
     * In 1.21.x heisst sie (bzw. ihr Intermediary-Name) {@code displayClientMessage}
     * / {@code method_7353} und erwartet zusaetzlich ein Boolean (Actionbar).
     * Ab 26.x gibt es nur noch {@code sendSystemMessage}. Deshalb wird per
     * Reflection nach den bekannten Namen gesucht; ein direkter Aufruf wuerde
     * auf aelteren 1.21-Versionen mit {@link NoSuchMethodError} abstuerzen.</p>
     */
    private static void sendLocalChatMessage(Minecraft client, Component message) {
        if (client == null || client.player == null || message == null) {
            return;
        }
        Object player = client.player;

        for (String name : new String[] { "displayClientMessage", "method_7353" }) {
            try {
                Method method = player.getClass().getMethod(name, Component.class, boolean.class);
                method.invoke(player, message, false);
                return;
            } catch (NoSuchMethodException ignored) {
                // Name in dieser Version nicht vorhanden -> naechsten versuchen.
            } catch (Throwable t) {
                ArdaniaClientMod.LOGGER.warn("[ArdaniaHUD] Chat-Nachricht konnte nicht gesendet werden ({})", name, t);
                return;
            }
        }

        for (String name : new String[] { "sendSystemMessage", "method_43496" }) {
            try {
                Method method = player.getClass().getMethod(name, Component.class);
                method.invoke(player, message);
                return;
            } catch (NoSuchMethodException ignored) {
                // Name in dieser Version nicht vorhanden -> naechsten versuchen.
            } catch (Throwable t) {
                ArdaniaClientMod.LOGGER.warn("[ArdaniaHUD] Chat-Nachricht konnte nicht gesendet werden ({})", name, t);
                return;
            }
        }

        ArdaniaClientMod.LOGGER.warn("[ArdaniaHUD] Keine passende Chat-Methode fuer die Debug-Ausgabe gefunden.");
    }

    private static String printable(String text) {
        if (text == null || text.isBlank()) {
            return "(leer)";
        }
        return text.replace("\n", " | ").replace("\r", "");
    }

    private static String formatCounts(Map<ArdaniaSyncManager.ServerType, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "keine";
        }
        StringBuilder builder = new StringBuilder();
        counts.forEach((type, count) -> {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(type.name().charAt(0)).append('=').append(count);
        });
        return builder.toString();
    }

    private static int headerCountSum(ArdaniaServerDetector.Detection detection) {
        return detection.headerCounts().values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Zeigt pro Servertyp {@code header-tag=delta}, damit im Debug sofort
     * erkennbar ist, welcher Typ das groesste positive Delta besitzt.
     */
    private static String formatDeltas(ArdaniaServerDetector.Detection detection) {
        if (detection.headerCounts().isEmpty()) {
            return "keine";
        }
        StringBuilder builder = new StringBuilder();
        detection.headerCounts().forEach((type, count) -> {
            int tagged = detection.tagCounts().getOrDefault(type, 0);
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append(type.name().charAt(0))
                    .append(": ")
                    .append(count)
                    .append('-')
                    .append(tagged)
                    .append('=')
                    .append(count - tagged);
        });
        return builder.toString();
    }

    private boolean onAllowGameMessage(Component message, boolean overlay) {
        Minecraft client = Minecraft.getInstance();
        if (!ArdaniaClientMod.isOnArdania(client)) {
            return true;
        }

        String raw = message.getString();

        try {
            boolean foreignQuery = !overlay && ArdaniaSyncManager.getInstance().isForeignPlayerQueryActive();
            String selfName = client.player != null ? client.player.getName().getString() : null;
            boolean matched = !foreignQuery && this.parser.parse(raw, overlay, selfName);
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

            if (!overlay) {
                String lower = raw.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("nicht mehr afk") || lower.contains("no longer afk")) {
                    ArdaniaSyncManager.getInstance().exitAfkState();
                }
            }

            if (!overlay && ArdaniaSyncManager.getInstance().shouldSuppressMessage(raw, this.parser, selfName)) {
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
