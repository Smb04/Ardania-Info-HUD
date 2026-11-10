package de.ardania.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Berechnet das ergonomische Layout, Zeilenplatzierungen und Viewport-Clamping
 * fuer das Ardania Info HUD gemaess DIN EN ISO 9241-110.
 *
 * <p>Verantwortlich fuer:</p>
 * <ul>
 *   <li>Dynamische Breiten- und Hoehenberechnung anhand von Schriftart und Textinhalten</li>
 *   <li>Unterstuetzung von 6 ergonomischen Bildschirm-Ankerpunkten (Snap-Presets)</li>
 *   <li>Rigoroses Viewport-Clamping, um das Overlay innerhalb des Bildschirms zu halten</li>
 *   <li>Smoothstep-Easing fuer weiche Kanten-Einblendungen</li>
 *   <li>Optionale Miniatur-Fortschrittsbalken unter Job-Zeilen</li>
 * </ul>
 */
public final class HudLayout {

    /** Abstand in Pixeln zum aeusseren Bildschirmrand. */
    public static final int EDGE_MARGIN = 3;
    /** Horizontaler Innenabstand der HUD-Box. */
    public static final int PADDING_X = 4;
    /** Vertikaler Innenabstand der HUD-Box. */
    public static final int PADDING_Y = 3;
    /** Zeilenhoehe fuer Standard-Textzeilen in Pixeln. */
    public static final int LINE_HEIGHT = 10;
    /** Hoehe des dezenten Fortschrittsbalkens in Pixeln. */
    public static final int BAR_HEIGHT = 2;
    /** Abstand zwischen Textzeile und Fortschrittsbalken in Pixeln. */
    public static final int BAR_GAP = 2;
    /** Mindestabstand zwischen linkem und rechtem Text bei geteilten Zeilen. */
    public static final int RIGHT_ALIGN_GAP = 6;

    /**
     * Spezifikation fuer einen dezenten Fortschrittsbalken unter einer Textzeile.
     *
     * @param fillRatio  Füllstand zwischen 0.0f und 1.0f
     * @param fillColor  Farbe des gefuellten Bereichs (ARGB)
     * @param trackColor Hintergrundfarbe der Leiste (ARGB)
     */
    public record ProgressBarSpec(float fillRatio, int fillColor, int trackColor) {}

    /**
     * Abstrakte Zeilendefinition vor der Koordinatenberechnung.
     *
     * @param left     Linker (oder zentrierter) Textinhalt
     * @param right    Optionaler rechtsbuendiger Textinhalt
     * @param centered Ob der linke Text horizontal im HUD zentriert werden soll
     * @param bar      Optionaler Fortschrittsbalken unter dieser Zeile
     */
    public record LineSpec(Component left, Component right, boolean centered, ProgressBarSpec bar) {
        public LineSpec(Component single) {
            this(single, null, false, null);
        }

        public LineSpec(Component left, Component right, boolean centered) {
            this(left, right, centered, null);
        }
    }

    /**
     * Berechnete Koordinaten fuer einen Fortschrittsbalken zur direkten Ausgabe.
     */
    public record RenderableBar(
            int x,
            int y,
            int width,
            int height,
            float fillRatio,
            int fillColor,
            int trackColor
    ) {}

    /**
     * Berechnete Koordinaten fuer eine Textzeile zur direkten Ausgabe.
     */
    public record RenderableLine(
            Component left,
            int leftX,
            Component right,
            int rightX,
            int y,
            boolean centered
    ) {}

    /**
     * Gesamtergebnis einer Layout-Berechnung inklusive Bounding-Box und Elementen.
     */
    public record LayoutResult(
            int boxX,
            int boxY,
            int boxWidth,
            int boxHeight,
            int headerHeight,
            List<RenderableLine> lines,
            List<RenderableBar> bars
    ) {}

    /**
     * Ermittelt anhand der Konfiguration und des Servertyps, ob das HUD sichtbar sein soll.
     *
     * @param config      Aktuelle Mod-Konfiguration
     * @param serverType  Erkannter Unterserver-Typ (Hauptserver, Mine, Event, etc.)
     * @param tabPressed  Ob die Spielerlisten-Taste (Tab) aktuell gedrueckt wird
     * @return true, falls das HUD gezeichnet werden soll
     */
    public static boolean isVisibleOnServer(ArdaniaConfig config, ArdaniaSyncManager.ServerType serverType, boolean tabPressed) {
        if (!config.enabled) {
            return false;
        }
        ArdaniaConfig.ServerVisibility visibility = config.getVisibilityForServer(serverType);
        return switch (visibility) {
            case SHOW -> true;
            case TAB_ONLY -> tabPressed;
            case HIDE -> false;
        };
    }

    /**
     * Baut die formatierte Zeilenliste des HUDs basierend auf dem aktuellen Spielerzustand.
     *
     * @param state  Datenhalter mit Kontostaenden, Jobs und Zeiten
     * @param config Benutzerkonfiguration (Reihenfolge, Anzeigeoptionen)
     * @param now    Aktueller Zeitstempel in Millisekunden
     * @return Liste abstrakter Zeilenspezifikationen
     */
    public static List<LineSpec> buildLines(ArdaniaState state, ArdaniaConfig config, long now) {
        List<LineSpec> lines = new ArrayList<>();

        lines.add(new LineSpec(Component.literal("ARDANIA").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), null, true));

        List<ArdaniaConfig.HudRow> order = (config.rowOrder != null && !config.rowOrder.isEmpty())
                ? config.rowOrder
                : List.of(
                ArdaniaConfig.HudRow.MONEY,
                ArdaniaConfig.HudRow.SESSION_STATS,
                ArdaniaConfig.HudRow.POINTS,
                ArdaniaConfig.HudRow.PLAYTIME,
                ArdaniaConfig.HudRow.SESSION_TIMER,
                ArdaniaConfig.HudRow.JOBS
        );

        for (ArdaniaConfig.HudRow row : order) {
            if (row == null) continue;
            switch (row) {
                case MONEY -> appendMoney(lines, state, config);
                case SESSION_STATS -> appendSessionStats(lines, state, config, now);
                case POINTS -> appendPoints(lines, state, config);
                case PLAYTIME -> appendPlaytime(lines, state, config);
                case SESSION_TIMER -> appendSessionTimer(lines, state, config, now);
                case JOBS -> appendJobs(lines, state, config);
            }
        }

        return lines;
    }

    private static void appendMoney(List<LineSpec> lines, ArdaniaState state, ArdaniaConfig config) {
        if (!config.showMoney) return;
        String money;
        if (!state.isEuronenKnown()) {
            money = "?";
        } else if (config.compactNumbers) {
            money = ArdaniaState.formatCompactMoney(state.getEuronen());
        } else {
            money = state.getFormattedEuronen();
        }
        lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.money").append(": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(money).withStyle(ChatFormatting.GOLD))));
    }

    private static void appendSessionStats(List<LineSpec> lines, ArdaniaState state, ArdaniaConfig config, long now) {
        if (!config.showSessionStats) return;
        lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.session_stats").append(": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(state.getFormattedSessionStats(now, config.compactNumbers)).withStyle(ChatFormatting.YELLOW))));
    }

    private static void appendPoints(List<LineSpec> lines, ArdaniaState state, ArdaniaConfig config) {
        if (!config.showPoints) return;
        lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.points").append(": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(state.getFormattedArdaPoints()).withStyle(ChatFormatting.AQUA))));
    }

    private static void appendPlaytime(List<LineSpec> lines, ArdaniaState state, ArdaniaConfig config) {
        if (!config.showPlaytime) return;
        if (config.combinePlaytime) {
            lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.playtime").append(": ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(state.getFormattedPlaytime(config)).withStyle(ChatFormatting.LIGHT_PURPLE))));
        } else {
            List<ArdaniaState.ServerPlaytimeEntry> selectedServers = state.getSelectedServerPlaytimes(config);
            if (selectedServers.isEmpty()) {
                lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.playtime").append(": ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("0m").withStyle(ChatFormatting.LIGHT_PURPLE))));
            } else if (selectedServers.size() == 1) {
                ArdaniaState.ServerPlaytimeEntry s = selectedServers.getFirst();
                lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.playtime")
                        .append(" (" + s.displayName() + "): ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(s.formattedTime()).withStyle(ChatFormatting.LIGHT_PURPLE))));
            } else {
                lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.playtime").append(":").withStyle(ChatFormatting.WHITE)));
                for (ArdaniaState.ServerPlaytimeEntry s : selectedServers) {
                    lines.add(new LineSpec(
                            Component.literal(" " + s.displayName() + ":").withStyle(ChatFormatting.GRAY),
                            Component.literal(s.formattedTime()).withStyle(ChatFormatting.LIGHT_PURPLE),
                            false
                    ));
                }
            }
        }
    }

    private static void appendSessionTimer(List<LineSpec> lines, ArdaniaState state, ArdaniaConfig config, long now) {
        if (!config.showSessionTimer) return;
        lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.session").append(": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(state.getFormattedSessionTime(now)).withStyle(ChatFormatting.YELLOW))));
    }

    private static void appendJobs(List<LineSpec> lines, ArdaniaState state, ArdaniaConfig config) {
        if (!config.showJobs) return;
        List<ArdaniaState.JobEntry> jobList = state.getJobs();
        boolean isPercent = (config.jobProgressStyle == ArdaniaConfig.JobProgressStyle.PERCENT);
        boolean isBar = (config.jobProgressStyle == ArdaniaConfig.JobProgressStyle.BAR);

        if (jobList.isEmpty()) {
            lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.job").append(": ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("?").withStyle(ChatFormatting.DARK_GRAY))));
        } else if (jobList.size() == 1) {
            ArdaniaState.JobEntry j = jobList.getFirst();
            ChatFormatting levelColor = j.isMax() ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            ProgressBarSpec bar = createJobBar(j, isBar);
            lines.add(new LineSpec(
                    ArdaniaI18n.translate("hud.ardania_hud.job").append(": ").withStyle(ChatFormatting.WHITE)
                            .append(Component.literal(j.name()).withStyle(ChatFormatting.GREEN)),
                    Component.literal(j.formatBadge(isPercent)).withStyle(levelColor),
                    false,
                    bar
            ));
        } else {
            lines.add(new LineSpec(ArdaniaI18n.translate("hud.ardania_hud.jobs").append(":").withStyle(ChatFormatting.WHITE)));
            for (ArdaniaState.JobEntry j : jobList) {
                ChatFormatting levelColor = j.isMax() ? ChatFormatting.GOLD : ChatFormatting.GRAY;
                ProgressBarSpec bar = createJobBar(j, isBar);
                lines.add(new LineSpec(
                        Component.literal(" ").append(Component.literal(j.name()).withStyle(ChatFormatting.GREEN)),
                        Component.literal(j.formatBadge(isPercent)).withStyle(levelColor),
                        false,
                        bar
                ));
            }
        }
    }

    private static ProgressBarSpec createJobBar(ArdaniaState.JobEntry j, boolean enabled) {
        if (!enabled) return null;
        if (j.isMax()) {
            return new ProgressBarSpec(1.0f, 0xFFFFAA00, 0x60000000);
        }
        if (j.hasXpInfo()) {
            float ratio = (float) Math.clamp(j.currentXp() / j.maxXp(), 0.0, 1.0);
            return new ProgressBarSpec(ratio, 0xFF55FF55, 0x60000000);
        }
        return null;
    }

    /**
     * Erzeugt eine repraesentative Zeilenliste mit Beispieldaten fuer das Konfigurationsmenue.
     *
     * @param config Benutzerkonfiguration zur Steuerung von Reihenfolge und Format
     * @return Zeilenliste mit Mock-Daten
     */
    public static List<LineSpec> buildPreviewLines(ArdaniaConfig config) {
        ArdaniaState dummyState = new ArdaniaState();
        dummyState.setEuronen(new BigDecimal("28980.30"));
        dummyState.startSession(System.currentTimeMillis() - 75_000L);
        dummyState.setEuronen(new BigDecimal("30214.80"));
        dummyState.setArdaPoints(3918);
        dummyState.updateServerPlaytime("hauptserver", 17, 9, 21);
        dummyState.updateServerPlaytime("mine", 0, 12, 10);
        dummyState.addOrUpdateJob("Mobhunter", 24, false, 2497.42, 3120.0);
        dummyState.addOrUpdateJob("Farmer", 13, false, 73.57, 1897.0);
        dummyState.addOrUpdateJob("Miner", 30, true, 0.0, 0.0);
        List<LineSpec> preview = buildLines(dummyState, config, System.currentTimeMillis());
        if (preview.size() <= 1) {
            ArdaniaConfig fallback = new ArdaniaConfig();
            fallback.rowOrder = config.rowOrder;
            fallback.jobProgressStyle = config.jobProgressStyle;
            fallback.compactNumbers = config.compactNumbers;
            return buildLines(dummyState, fallback, System.currentTimeMillis());
        }
        return preview;
    }

    /**
     * Berechnet die exakten Bildschirmkoordinaten fuer Box, Linien und Fortschrittsbalken.
     *
     * <p>Beinhaltet Viewport-Clamping gemaess DIN EN ISO 9241-110 sowie
     * Kanten-Einblendungs-Animationen per Smoothstep.</p>
     *
     * @param specs          Liste der zu zeichnenden Zeilenspezifikationen
     * @param config         Konfiguration mit Preset-Position
     * @param slideProgress  Animationsfortschritt (0.0f = verborgen, 1.0f = vollstaendig sichtbar)
     * @param screenWidth    Effektive sichtbare Breite in skalierten Pixeln
     * @param screenHeight   Effektive sichtbare Hoehe in skalierten Pixeln
     * @param textWidthFunc  Funktion zur Ermittlung der Textbreite einer Komponente
     * @return Berechnetes LayoutResultat
     */
    public static LayoutResult calculate(
            List<LineSpec> specs,
            ArdaniaConfig config,
            float slideProgress,
            int screenWidth,
            int screenHeight,
            ToIntFunction<Component> textWidthFunc
    ) {
        if (specs == null || specs.isEmpty()) {
            return new LayoutResult(0, 0, 0, 0, 0, List.of(), List.of());
        }

        int maxTextWidth = 0;
        int totalContentHeight = 0;

        for (LineSpec spec : specs) {
            int lineWidth = textWidthFunc.applyAsInt(spec.left());
            if (spec.right() != null) {
                lineWidth += RIGHT_ALIGN_GAP + textWidthFunc.applyAsInt(spec.right());
            }
            maxTextWidth = Math.max(maxTextWidth, lineWidth);

            totalContentHeight += LINE_HEIGHT;
            if (spec.bar() != null) {
                totalContentHeight += BAR_HEIGHT + BAR_GAP;
            }
        }

        int boxWidth = maxTextWidth + (PADDING_X * 2);
        int boxHeight = totalContentHeight + (PADDING_Y * 2);
        int headerHeight = LINE_HEIGHT + PADDING_Y;

        int maxX = Math.max(0, screenWidth - boxWidth);
        int maxY = Math.max(0, screenHeight - boxHeight);

        int targetX;
        int targetY;
        if (config.useCustomPosition && (config.customX >= 0 || config.customXRatio >= 0.0f)) {
            int rawX = config.customXRatio >= 0.0f
                    ? Math.round(config.customXRatio * maxX)
                    : config.customX;
            int rawY = config.customYRatio >= 0.0f
                    ? Math.round(config.customYRatio * maxY)
                    : config.customY;
            targetX = Math.clamp(rawX, 0, maxX);
            targetY = Math.clamp(rawY, 0, maxY);
        } else {
            targetX = config.position.isLeft()
                    ? Math.min(EDGE_MARGIN, maxX)
                    : Math.max(0, screenWidth - boxWidth - EDGE_MARGIN);

            targetY = switch (config.position) {
                case TOP_RIGHT, TOP_LEFT -> Math.min(20, maxY);
                case BOTTOM_RIGHT, BOTTOM_LEFT -> (screenHeight - boxHeight >= 20) ? (screenHeight - boxHeight - 20) : maxY;
                case MIDDLE_RIGHT, MIDDLE_LEFT -> maxY / 2;
            };
        }

        // Rigoroses Viewport-Clamping: Die Box darf NIEMALS ueber den sichtbaren Bildschirmrand ragen
        targetX = Math.clamp(targetX, 0, maxX);
        targetY = Math.clamp(targetY, 0, maxY);

        // Smoothstep-Easing fuer weiche Kanten-Slide-Animation
        float clampedProgress = Math.clamp(slideProgress, 0.0f, 1.0f);
        float eased = clampedProgress * clampedProgress * (3.0f - 2.0f * clampedProgress);

        boolean isLeft = (targetX + boxWidth / 2) < (screenWidth / 2);
        int startX;
        if (isLeft) {
            startX = (int) (targetX - (1.0f - eased) * (targetX + boxWidth + EDGE_MARGIN));
        } else {
            startX = (int) (targetX + (1.0f - eased) * (screenWidth - targetX + EDGE_MARGIN));
        }
        int startY = targetY;

        List<RenderableLine> renderableLines = new ArrayList<>(specs.size());
        List<RenderableBar> renderableBars = new ArrayList<>();
        int currentY = startY + PADDING_Y;

        for (LineSpec spec : specs) {
            int leftX;
            int rightX = 0;

            if (spec.centered()) {
                int textW = textWidthFunc.applyAsInt(spec.left());
                leftX = startX + (boxWidth - textW) / 2;
            } else {
                leftX = startX + PADDING_X;
                if (spec.right() != null) {
                    int rightW = textWidthFunc.applyAsInt(spec.right());
                    rightX = startX + boxWidth - PADDING_X - rightW;
                }
            }

            renderableLines.add(new RenderableLine(
                    spec.left(),
                    leftX,
                    spec.right(),
                    rightX,
                    currentY,
                    spec.centered()
            ));

            if (spec.bar() != null) {
                int barX = startX + PADDING_X;
                int barY = currentY + LINE_HEIGHT - 1;
                int barW = boxWidth - (PADDING_X * 2);
                renderableBars.add(new RenderableBar(
                        barX,
                        barY,
                        barW,
                        BAR_HEIGHT,
                        spec.bar().fillRatio(),
                        spec.bar().fillColor(),
                        spec.bar().trackColor()
                ));
                currentY += LINE_HEIGHT + BAR_HEIGHT + BAR_GAP;
            } else {
                currentY += LINE_HEIGHT;
            }
        }

        return new LayoutResult(startX, startY, boxWidth, boxHeight, headerHeight, renderableLines, renderableBars);
    }
}
