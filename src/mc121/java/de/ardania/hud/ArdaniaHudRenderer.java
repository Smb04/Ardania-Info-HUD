package de.ardania.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * HUD-Renderer fuer Minecraft 1.21.8 - 1.21.11 ueber Fabric HudElement.
 *
 * <p>Zeichnet das Scoreboard-HUD-Overlay in einem eigenen Stratum, berechnet weiche
 * Kanten-Einblendungen, skaliert ueber {@code Matrix3x2fStack} und prueft
 * weltbezogene Sichtbarkeitsprofile.</p>
 */
public final class ArdaniaHudRenderer implements HudElement {

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final float SLIDE_SPEED = 3.5f;

    private final ArdaniaState state;
    private float slideProgress = 1.0f;
    private long lastFrameTime = -1;
    private long tabPressStartTime = -1;
    private boolean tabWasDown = false;

    public ArdaniaHudRenderer(ArdaniaState state) {
        this.state = state;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        ArdaniaConfig config = ArdaniaConfig.getInstance();

        if (client.screen instanceof ArdaniaHudEditorScreen) {
            return;
        }

        if (client.options.hideGui || client.player == null || !ArdaniaClientMod.isOnArdania(client)) {
            this.slideProgress = 0.0f;
            this.lastFrameTime = -1;
            this.tabPressStartTime = -1;
            this.tabWasDown = false;
            return;
        }

        long now = System.currentTimeMillis();
        long displayDurationMs = Math.max(1, config.autoHideDurationSeconds) * 1000L;

        boolean tabDown = config.enabled
                && config.showOnTab
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

        if (this.state.getLastManualHideTimestamp() > this.tabPressStartTime) {
            this.tabPressStartTime = -1;
        }

        boolean tabActive = isTabDisplayActive(config.showOnTab, tabDown, this.tabPressStartTime, now, displayDurationMs);

        ArdaniaSyncManager.ServerType serverType = ArdaniaSyncManager.getInstance().getCurrentServerType();
        boolean visibleOnServer = HudLayout.isVisibleOnServer(config, serverType, tabDown || tabActive);

        float targetProgress;
        if (!config.enabled || !visibleOnServer) {
            targetProgress = 0.0f;
        } else if (!config.autoHide) {
            targetProgress = 1.0f;
        } else {
            boolean statUpdatedRecently = this.state.isAutoHideActive(now, displayDurationMs);
            targetProgress = (statUpdatedRecently || tabActive) ? 1.0f : 0.0f;
        }

        float dt = (this.lastFrameTime > 0) ? Math.min((now - this.lastFrameTime) / 1000.0f, 0.1f) : 0.016f;
        this.lastFrameTime = now;

        if (this.slideProgress < targetProgress) {
            this.slideProgress = Math.min(targetProgress, this.slideProgress + (SLIDE_SPEED * dt));
        } else if (this.slideProgress > targetProgress) {
            this.slideProgress = Math.max(targetProgress, this.slideProgress - (SLIDE_SPEED * dt));
        }

        if (this.slideProgress <= 0.001f) {
            return;
        }

        Font font = client.font;
        List<HudLayout.LineSpec> lines = HudLayout.buildLines(this.state, config, now);
        if (lines.isEmpty()) {
            return;
        }

        float scale = Math.clamp(config.hudScale, 0.7f, 1.5f);
        int scaledWidth = Math.max(1, Math.round(graphics.guiWidth() / scale));
        int scaledHeight = Math.max(1, Math.round(graphics.guiHeight() / scale));

        HudLayout.LayoutResult layout = HudLayout.calculate(
                lines,
                config,
                this.slideProgress,
                scaledWidth,
                scaledHeight,
                font::width
        );

        int alpha = (int) (Math.clamp(config.backgroundOpacity, 0.0f, 1.0f) * 255.0f);
        int bgColor = (alpha << 24) & 0xFF000000;
        int headerAlpha = Math.min(255, alpha + 0x15);
        int headerBgColor = (headerAlpha << 24) & 0xFF000000;

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            graphics.fill(layout.boxX(), layout.boxY(), layout.boxX() + layout.boxWidth(), layout.boxY() + layout.boxHeight(), bgColor);
            graphics.fill(layout.boxX(), layout.boxY(), layout.boxX() + layout.boxWidth(), layout.boxY() + layout.headerHeight(), headerBgColor);

            for (HudLayout.RenderableBar bar : layout.bars()) {
                graphics.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), bar.trackColor());
                int filledWidth = (int) Math.round(bar.width() * Math.clamp(bar.fillRatio(), 0.0f, 1.0f));
                if (filledWidth > 0) {
                    graphics.fill(bar.x(), bar.y(), bar.x() + filledWidth, bar.y() + bar.height(), bar.fillColor());
                }
            }

            for (HudLayout.RenderableLine line : layout.lines()) {
                if (line.centered()) {
                    graphics.drawString(font, line.left(), line.leftX(), line.y(), TEXT_COLOR, true);
                } else {
                    graphics.drawString(font, line.left(), line.leftX(), line.y(), TEXT_COLOR, true);
                    if (line.right() != null) {
                        graphics.drawString(font, line.right(), line.rightX(), line.y(), TEXT_COLOR, true);
                    }
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public boolean isShowing(long now) {
        ArdaniaConfig config = ArdaniaConfig.getInstance();
        if (!config.enabled) {
            return false;
        }
        if (!config.autoHide) {
            return true;
        }
        long displayDurationMs = Math.max(1, config.autoHideDurationSeconds) * 1000L;
        boolean tabActive = isTabDisplayActive(config.showOnTab, this.tabWasDown, this.tabPressStartTime, now, displayDurationMs);
        return this.state.isAutoHideActive(now, displayDurationMs) || tabActive;
    }

    public static boolean isTabDisplayActive(boolean showOnTab, boolean tabDown, long tabPressStartTime, long now, long displayDurationMs) {
        if (!showOnTab) {
            return false;
        }
        if (tabDown) {
            return true;
        }
        return tabPressStartTime > 0 && (now - tabPressStartTime < displayDurationMs);
    }
}
