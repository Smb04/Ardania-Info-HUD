package de.ardania.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Ingame-Einstellungsbildschirm fuer ModMenu und Vanilla.
 * Ermoeglicht das Umschalten aller HUD-Optionen sowie der Sync-Intervalle direkt im Spiel.
 */
public final class ArdaniaConfigScreen extends Screen {

    private final Screen parent;
    private final ArdaniaConfig config;

    public ArdaniaConfigScreen(Screen parent) {
        super(Component.translatable("config.ardania_hud.title"));
        this.parent = parent;
        this.config = ArdaniaConfig.getInstance();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int colWidth = 150;
        int buttonHeight = 20;
        int spacing = 24;
        int startY = 32;

        // --- Linke Spalte: Anzeige & Position ---
        int leftX = centerX - colWidth - 5;
        int yLeft = startY;

        // 1. HUD Aktiviert
        addRenderableWidget(Button.builder(
                getEnabledText(),
                btn -> {
                    config.enabled = !config.enabled;
                    btn.setMessage(getEnabledText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        // 2. Position
        addRenderableWidget(Button.builder(
                getPositionText(),
                btn -> {
                    switch (config.position) {
                        case MIDDLE_RIGHT -> config.position = ArdaniaConfig.HudPosition.TOP_RIGHT;
                        case TOP_RIGHT -> config.position = ArdaniaConfig.HudPosition.BOTTOM_RIGHT;
                        case BOTTOM_RIGHT -> config.position = ArdaniaConfig.HudPosition.MIDDLE_RIGHT;
                    }
                    btn.setMessage(getPositionText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        // 3. Euronen
        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_money", config.showMoney),
                btn -> {
                    config.showMoney = !config.showMoney;
                    btn.setMessage(getToggleText("config.ardania_hud.show_money", config.showMoney));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        // 4. ArdaPoints
        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_points", config.showPoints),
                btn -> {
                    config.showPoints = !config.showPoints;
                    btn.setMessage(getToggleText("config.ardania_hud.show_points", config.showPoints));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        // 5. Spielzeit
        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_playtime", config.showPlaytime),
                btn -> {
                    config.showPlaytime = !config.showPlaytime;
                    btn.setMessage(getToggleText("config.ardania_hud.show_playtime", config.showPlaytime));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        // 6. Jobs
        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_jobs", config.showJobs),
                btn -> {
                    config.showJobs = !config.showJobs;
                    btn.setMessage(getToggleText("config.ardania_hud.show_jobs", config.showJobs));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());

        // --- Rechte Spalte: Sync-Intervalle & Automatisierung ---
        int rightX = centerX + 5;
        int yRight = startY;

        // 7. AutoSync Periodisch an/aus
        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.auto_sync", config.autoSyncPeriodic),
                btn -> {
                    config.autoSyncPeriodic = !config.autoSyncPeriodic;
                    btn.setMessage(getToggleText("config.ardania_hud.auto_sync", config.autoSyncPeriodic));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        // 8. Geld Sync Cooldown (15s -> 30s -> 60s -> 120s)
        addRenderableWidget(Button.builder(
                getMoneyIntervalText(),
                btn -> {
                    config.moneySyncCooldownSeconds = switch (config.moneySyncCooldownSeconds) {
                        case 15 -> 30;
                        case 30 -> 60;
                        case 60 -> 120;
                        default -> 15;
                    };
                    btn.setMessage(getMoneyIntervalText());
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        // 9. ArdaPoints Intervall (30s -> 60s -> 120s -> 300s)
        addRenderableWidget(Button.builder(
                getPointsIntervalText(),
                btn -> {
                    config.pointsSyncIntervalSeconds = switch (config.pointsSyncIntervalSeconds) {
                        case 30 -> 60;
                        case 60 -> 120;
                        case 120 -> 300;
                        default -> 30;
                    };
                    btn.setMessage(getPointsIntervalText());
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        // 10. Spielzeit Intervall (15m -> 30m -> 60m -> 120m)
        addRenderableWidget(Button.builder(
                getPlaytimeIntervalText(),
                btn -> {
                    config.playtimeSyncIntervalMinutes = switch (config.playtimeSyncIntervalMinutes) {
                        case 15 -> 30;
                        case 30 -> 60;
                        case 60 -> 120;
                        default -> 15;
                    };
                    btn.setMessage(getPlaytimeIntervalText());
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        // 11. Join Sync
        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.sync_join", config.autoSyncOnJoin),
                btn -> {
                    config.autoSyncOnJoin = !config.autoSyncOnJoin;
                    btn.setMessage(getToggleText("config.ardania_hud.sync_join", config.autoSyncOnJoin));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());

        // --- Unten: Fertig Button ---
        int contentBottom = Math.max(yLeft, yRight) + buttonHeight;
        int doneY = Math.max(contentBottom + 16, this.height - 28);
        addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                btn -> onClose()
        ).bounds(centerX - 100, doneY, 200, buttonHeight).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        ArdaniaConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private Component getEnabledText() {
        return Component.translatable("config.ardania_hud.enabled")
                .append(": ")
                .append(config.enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private Component getPositionText() {
        return Component.translatable("config.ardania_hud.position")
                .append(": ")
                .append(Component.translatable("config.ardania_hud.position." + config.position.name().toLowerCase()));
    }

    private Component getMoneyIntervalText() {
        return Component.translatable("config.ardania_hud.money_interval")
                .append(": ")
                .append(config.moneySyncCooldownSeconds + "s");
    }

    private Component getPointsIntervalText() {
        return Component.translatable("config.ardania_hud.points_interval")
                .append(": ")
                .append(config.pointsSyncIntervalSeconds + "s");
    }

    private Component getPlaytimeIntervalText() {
        return Component.translatable("config.ardania_hud.playtime_interval")
                .append(": ")
                .append(config.playtimeSyncIntervalMinutes + "m");
    }

    private Component getToggleText(String key, boolean value) {
        return Component.translatable(key)
                .append(": ")
                .append(value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
