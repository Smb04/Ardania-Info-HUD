package de.ardania.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

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

        int leftX = centerX - colWidth - 5;
        int yLeft = startY;

        addRenderableWidget(Button.builder(
                getEnabledText(),
                btn -> {
                    config.enabled = !config.enabled;
                    btn.setMessage(getEnabledText());
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

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

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_money", config.showMoney),
                btn -> {
                    config.showMoney = !config.showMoney;
                    btn.setMessage(getToggleText("config.ardania_hud.show_money", config.showMoney));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_points", config.showPoints),
                btn -> {
                    config.showPoints = !config.showPoints;
                    btn.setMessage(getToggleText("config.ardania_hud.show_points", config.showPoints));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_playtime", config.showPlaytime),
                btn -> {
                    config.showPlaytime = !config.showPlaytime;
                    btn.setMessage(getToggleText("config.ardania_hud.show_playtime", config.showPlaytime));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_jobs", config.showJobs),
                btn -> {
                    config.showJobs = !config.showJobs;
                    btn.setMessage(getToggleText("config.ardania_hud.show_jobs", config.showJobs));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());
        yLeft += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.show_on_tab", config.showOnTab),
                btn -> {
                    config.showOnTab = !config.showOnTab;
                    btn.setMessage(getToggleText("config.ardania_hud.show_on_tab", config.showOnTab));
                    ArdaniaConfig.save();
                }
        ).bounds(leftX, yLeft, colWidth, buttonHeight).build());

        int rightX = centerX + 5;
        int yRight = startY;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.auto_sync", config.autoSyncPeriodic),
                btn -> {
                    config.autoSyncPeriodic = !config.autoSyncPeriodic;
                    btn.setMessage(getToggleText("config.ardania_hud.auto_sync", config.autoSyncPeriodic));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

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

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.sync_join", config.autoSyncOnJoin),
                btn -> {
                    config.autoSyncOnJoin = !config.autoSyncOnJoin;
                    btn.setMessage(getToggleText("config.ardania_hud.sync_join", config.autoSyncOnJoin));
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getToggleText("config.ardania_hud.auto_hide", config.autoHide),
                btn -> {
                    config.autoHide = !config.autoHide;
                    btn.setMessage(getToggleText("config.ardania_hud.auto_hide", config.autoHide));
                    ArdaniaState.getInstance().notifyStatUpdated();
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());
        yRight += spacing;

        addRenderableWidget(Button.builder(
                getAutoHideDurationText(),
                btn -> {
                    config.autoHideDurationSeconds = switch (config.autoHideDurationSeconds) {
                        case 3 -> 5;
                        case 5 -> 8;
                        case 8 -> 10;
                        case 10 -> 15;
                        default -> 3;
                    };
                    btn.setMessage(getAutoHideDurationText());
                    ArdaniaState.getInstance().notifyStatUpdated();
                    ArdaniaConfig.save();
                }
        ).bounds(rightX, yRight, colWidth, buttonHeight).build());

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
        ArdaniaState.getInstance().notifyStatUpdated();
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

    private Component getAutoHideDurationText() {
        return Component.translatable("config.ardania_hud.auto_hide_duration")
                .append(": ")
                .append(config.autoHideDurationSeconds + "s");
    }

    private Component getToggleText(String key, boolean value) {
        return Component.translatable(key)
                .append(": ")
                .append(value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
