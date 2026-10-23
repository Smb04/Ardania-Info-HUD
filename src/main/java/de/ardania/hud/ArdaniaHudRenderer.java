package de.ardania.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scoreboard-artiges Info-HUD (Hypixel-Stil), modular anpassbar ueber ardania_hud.json.
 */
public final class ArdaniaHudRenderer implements HudElement {

    private static final int RIGHT_MARGIN = 3;
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 3;
    private static final int LINE_HEIGHT = 10;

    private static final int BACKGROUND_COLOR = 0x60000000;
    private static final int HEADER_BG_COLOR = 0x70000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final ArdaniaState state;
    private float slideProgress = 1.0f;
    private long lastFrameTime = -1;
    private long tabPressStartTime = -1;
    private boolean tabWasDown = false;

    public ArdaniaHudRenderer(ArdaniaState state) {
        this.state = state;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        ArdaniaConfig config = ArdaniaConfig.getInstance();

        if (!config.enabled || client.gui.hud.isHidden() || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            this.slideProgress = 0.0f;
            this.lastFrameTime = -1;
            this.tabPressStartTime = -1;
            this.tabWasDown = false;
            return;
        }

        long now = System.currentTimeMillis();

        // Tab-Tastenabfrage (Playerlist-Taste)
        boolean tabDown = config.showOnTab
                && client.options != null
                && client.options.keyPlayerList != null
                && client.options.keyPlayerList.isDown();

        if (tabDown) {
            if (!this.tabWasDown) {
                this.tabPressStartTime = now;
            }
            this.tabWasDown = true;
        } else {
            this.tabWasDown = false;
        }

        // Bei kurzem Tippen fuer ein paar angenehme Sekunden (3s) sichtbar bleiben
        long minTabDisplayMs = 3000L;
        boolean tabActive = isTabDisplayActive(config.showOnTab, tabDown, this.tabPressStartTime, now, minTabDisplayMs);

        // Berechnung der Sichtbarkeit fuer Auto-Ausblenden
        float targetProgress;
        if (!config.autoHide) {
            targetProgress = 1.0f;
        } else {
            long lastUpdate = this.state.getLastNonPlaytimeUpdateTimestamp();
            long displayDurationMs = (long) config.autoHideDurationSeconds * 1000L;
            boolean statUpdatedRecently = lastUpdate > 0 && (now - lastUpdate < displayDurationMs);
            boolean shouldBeVisible = statUpdatedRecently || tabActive;
            targetProgress = shouldBeVisible ? 1.0f : 0.0f;
        }

        // Weiche Slide-Animation basierend auf vergangener Zeit
        float dt = (this.lastFrameTime > 0) ? Math.min((now - this.lastFrameTime) / 1000.0f, 0.1f) : 0.016f;
        this.lastFrameTime = now;

        float slideSpeed = 3.5f; // ca. 0.28s fuer vollstaendiges Ein-/Ausfahren
        if (this.slideProgress < targetProgress) {
            this.slideProgress = Math.min(targetProgress, this.slideProgress + (slideSpeed * dt));
        } else if (this.slideProgress > targetProgress) {
            this.slideProgress = Math.max(targetProgress, this.slideProgress - (slideSpeed * dt));
        }

        if (this.slideProgress <= 0.001f) {
            return;
        }

        Font font = client.font;
        List<Component> lines = buildLines(config);
        if (lines.isEmpty()) {
            return;
        }

        int maxTextWidth = 0;
        for (Component line : lines) {
            maxTextWidth = Math.max(maxTextWidth, font.width(line));
        }

        int boxWidth = maxTextWidth + (PADDING_X * 2);
        int boxHeight = (lines.size() * LINE_HEIGHT) + (PADDING_Y * 2);

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Sanftes Cubic-Easing (Smoothstep) fuer das horizontale Hereingleiten
        float eased = this.slideProgress * this.slideProgress * (3.0f - 2.0f * this.slideProgress);
        int startX = (int) (screenWidth - (eased * (boxWidth + RIGHT_MARGIN)));
        int startY;

        switch (config.position) {
            case TOP_RIGHT:
                startY = 20;
                break;
            case BOTTOM_RIGHT:
                startY = screenHeight - boxHeight - 20;
                break;
            case MIDDLE_RIGHT:
            default:
                startY = (screenHeight - boxHeight) / 2;
                break;
        }

        // Hintergrund zeichnen
        graphics.fill(startX, startY, startX + boxWidth, startY + boxHeight, BACKGROUND_COLOR);
        graphics.fill(startX, startY, startX + boxWidth, startY + LINE_HEIGHT + PADDING_Y, HEADER_BG_COLOR);

        int currentY = startY + PADDING_Y;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            int textX;

            if (i == 0) {
                textX = startX + (boxWidth - font.width(line)) / 2;
            } else {
                textX = startX + PADDING_X;
            }

            graphics.text(font, line, textX, currentY, TEXT_COLOR, true);
            currentY += LINE_HEIGHT;
        }
    }

    private List<Component> buildLines(ArdaniaConfig config) {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal("ARDANIA").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        if (config.showMoney) {
            String money = this.state.isEuronenKnown() ? this.state.getFormattedEuronen() : "?";
            lines.add(Component.literal("Euronen: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(money).withStyle(ChatFormatting.GOLD)));
        }

        if (config.showPoints) {
            lines.add(Component.literal("ArdaPoints: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(this.state.getFormattedArdaPoints()).withStyle(ChatFormatting.AQUA)));
        }

        if (config.showPlaytime) {
            lines.add(Component.literal("Spielzeit: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(this.state.getFormattedPlaytime()).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }

        if (config.showJobs) {
            List<ArdaniaState.JobEntry> jobList = this.state.getJobs();
            if (jobList.isEmpty()) {
                lines.add(Component.literal("Job: ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("?").withStyle(ChatFormatting.DARK_GRAY)));
            } else if (jobList.size() == 1) {
                ArdaniaState.JobEntry j = jobList.getFirst();
                ChatFormatting levelColor = j.isMax() ? ChatFormatting.GOLD : ChatFormatting.GRAY;
                lines.add(Component.literal("Job: ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(j.name() + " ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("[" + j.displayLevel() + "]").withStyle(levelColor)));
            } else {
                lines.add(Component.literal("Jobs:").withStyle(ChatFormatting.WHITE));
                for (ArdaniaState.JobEntry j : jobList) {
                    ChatFormatting levelColor = j.isMax() ? ChatFormatting.GOLD : ChatFormatting.GRAY;
                    lines.add(Component.literal(" ").append(Component.literal(j.name() + " ").withStyle(ChatFormatting.GREEN))
                            .append(Component.literal("[" + j.displayLevel() + "]").withStyle(levelColor)));
                }
            }
        }

        return lines;
    }

    /**
     * Berechnet rein funktional, ob das HUD aufgrund von Tab-Tastendruecken aktiv sein soll.
     * Wenn Tab gehalten wird -> true.
     * Wenn Tab nur kurz angetippt wurde -> bleibt fuer minDisplayMs aktiv.
     * Wenn Tab nach laengerem Halten (> minDisplayMs) losgelassen wird -> sofort false.
     */
    public static boolean isTabDisplayActive(boolean showOnTab, boolean tabDown, long tabPressStartTime, long now, long minDisplayMs) {
        if (!showOnTab) {
            return false;
        }
        if (tabDown) {
            return true;
        }
        return tabPressStartTime > 0 && (now - tabPressStartTime < minDisplayMs);
    }
}
