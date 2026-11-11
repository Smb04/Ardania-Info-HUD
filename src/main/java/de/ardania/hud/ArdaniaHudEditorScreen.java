package de.ardania.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Interaktiver In-Game HUD-Editor zum freien Verschieben des Overlays per Maus (Drag &amp; Drop)
 * und Pfeiltasten fuer moderne Minecraft-Versionen (MC 26.2 &amp; 26.3).
 *
 * <p>Verwendet eine Dual-Layer Input Engine (Frame-synchrones Polling via
 * {@code mouseHandler.isLeftPressed()} kombiniert mit nativen {@code Screen}-Events),
 * um sowohl unter GLFW als auch unter SDL3 zuverlaessiges Ziehen zu gewaehrleisten.</p>
 */
public final class ArdaniaHudEditorScreen extends Screen {

    private final Screen parent;
    private final ArdaniaConfig config;
    private List<HudLayout.LineSpec> previewLines;

    private boolean draggingHud = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean wasLeftDown = false;

    private Button resetBtn;
    private Button scaleBtn;
    private Button doneBtn;

    private int lastHudBoxX = 0;
    private int lastHudBoxY = 0;
    private int lastHudBoxWidth = 0;
    private int lastHudBoxHeight = 0;
    private int snapGuideX = -1;
    private int snapGuideY = -1;

    /**
     * Erstellt einen neuen HUD-Editor-Bildschirm.
     *
     * @param parent Uebergeordneter Bildschirm, zu dem beim Schliessen zurueckgekehrt wird
     */
    public ArdaniaHudEditorScreen(Screen parent) {
        super(ArdaniaI18n.translate("config.ardania_hud.editor.title"));
        this.parent = parent;
        this.config = ArdaniaConfig.getInstance();
    }

    @Override
    protected void init() {
        this.previewLines = HudLayout.buildPreviewLines(this.config);

        int btnWidth = 104;
        int btnHeight = 20;
        int gap = 6;
        int totalW = (btnWidth * 3) + (gap * 2);
        int startX = (this.width - totalW) / 2;
        int startY = this.height - 30;

        this.resetBtn = Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.editor.reset"),
                btn -> {
                    this.config.resetLayoutToPreset();
                    ArdaniaConfig.save();
                    updateButtonPositions();
                }
        ).bounds(startX, startY, btnWidth, btnHeight).build();

        this.scaleBtn = Button.builder(
                getScaleText(),
                btn -> {
                    this.config.hudScale = switch (Math.round(this.config.hudScale * 100.0f)) {
                        case 75 -> 0.9f;
                        case 90 -> 1.0f;
                        case 100 -> 1.1f;
                        case 110 -> 1.25f;
                        case 125 -> 0.75f;
                        default -> 1.0f;
                    };
                    btn.setMessage(getScaleText());
                    ArdaniaConfig.save();
                    updateButtonPositions();
                }
        ).bounds(startX + btnWidth + gap, startY, btnWidth, btnHeight).build();

        this.doneBtn = Button.builder(
                ArdaniaI18n.translate("config.ardania_hud.done"),
                btn -> onClose()
        ).bounds(startX + (btnWidth + gap) * 2, startY, btnWidth, btnHeight).build();

        addRenderableWidget(this.resetBtn);
        addRenderableWidget(this.scaleBtn);
        addRenderableWidget(this.doneBtn);

        updateButtonPositions();
    }

    private Component getScaleText() {
        return ArdaniaI18n.translate("config.ardania_hud.scale")
                .append(": ")
                .append(Math.round(this.config.hudScale * 100.0f) + "%");
    }

    private void updateButtonPositions() {
        if (this.resetBtn == null || this.scaleBtn == null || this.doneBtn == null) return;
        int btnWidth = 104;
        int gap = 6;
        int totalW = (btnWidth * 3) + (gap * 2);
        int startX = (this.width - totalW) / 2;

        boolean hudInBottom = (this.lastHudBoxY + this.lastHudBoxHeight) > (this.height - 56);
        int btnY = hudInBottom ? 12 : (this.height - 30);

        this.resetBtn.setX(startX);
        this.resetBtn.setY(btnY);
        this.scaleBtn.setX(startX + btnWidth + gap);
        this.scaleBtn.setY(btnY);
        this.doneBtn.setX(startX + (btnWidth + gap) * 2);
        this.doneBtn.setY(btnY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTracker) {
        updateDragState(mouseX, mouseY);

        float scale = Math.clamp(this.config.hudScale, 0.7f, 1.5f);
        int scaledWidth = Math.max(1, Math.round(this.width / scale));
        int scaledHeight = Math.max(1, Math.round(this.height / scale));

        HudLayout.LayoutResult layout = HudLayout.calculate(
                this.previewLines,
                this.config,
                1.0f,
                scaledWidth,
                scaledHeight,
                this.font::width
        );

        this.lastHudBoxX = Math.round(layout.boxX() * scale);
        this.lastHudBoxY = Math.round(layout.boxY() * scale);
        this.lastHudBoxWidth = Math.round(layout.boxWidth() * scale);
        this.lastHudBoxHeight = Math.round(layout.boxHeight() * scale);
        updateButtonPositions();

        // Dunkler Hintergrund
        graphics.fill(0, 0, this.width, this.height, 0x55000000);

        // Magnetische Cyan-Hilfslinien
        if (this.snapGuideX >= 0) {
            graphics.fill(this.snapGuideX, 0, this.snapGuideX + 1, this.height, 0xAA00E5FF);
        }
        if (this.snapGuideY >= 0) {
            graphics.fill(0, this.snapGuideY, this.width, this.snapGuideY + 1, 0xAA00E5FF);
        }

        // HUD zeichnen
        int alpha = (int) (Math.clamp(this.config.backgroundOpacity, 0.0f, 1.0f) * 255.0f);
        int bgColor = (alpha << 24) & 0xFF000000;
        int headerAlpha = Math.min(255, alpha + 0x15);
        int headerBgColor = (headerAlpha << 24) & 0xFF000000;

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
                    graphics.text(this.font, line.left(), line.leftX(), line.y(), 0xFFFFFFFF, true);
                } else {
                    graphics.text(this.font, line.left(), line.leftX(), line.y(), 0xFFFFFFFF, true);
                    if (line.right() != null) {
                        graphics.text(this.font, line.right(), line.rightX(), line.y(), 0xFFFFFFFF, true);
                    }
                }
            }
        } finally {
            graphics.pose().popMatrix();
        }

        // Umrandung um HUD
        int outlineColor = this.draggingHud ? 0xFF00E5FF : 0x88AAAAAA;
        drawHollowRect(graphics, this.lastHudBoxX - 1, this.lastHudBoxY - 1, this.lastHudBoxWidth + 2, this.lastHudBoxHeight + 2, outlineColor);

        // Anleitungstext
        boolean buttonsAtTop = (this.resetBtn != null && this.resetBtn.getY() <= 20);
        int infoY = buttonsAtTop ? (this.height - 28) : 12;
        Component infoMsg = ArdaniaI18n.translate("config.ardania_hud.editor.info");
        int infoW = this.font.width(infoMsg);
        graphics.text(this.font, infoMsg, (this.width - infoW) / 2, infoY, 0xFFE0E0E0, true);

        super.extractRenderState(graphics, mouseX, mouseY, deltaTracker);
    }

    private static void drawHollowRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private boolean isShiftDown() {
        try {
            if (this.minecraft == null) return false;
            try {
                var m = com.mojang.blaze3d.platform.InputConstants.class.getMethod("isKeyDown", int.class);
                return ((boolean) m.invoke(null, 340)) || ((boolean) m.invoke(null, 344));
            } catch (NoSuchMethodException e) {
                var m = com.mojang.blaze3d.platform.InputConstants.class.getMethod("isKeyDown", com.mojang.blaze3d.platform.Window.class, int.class);
                var win = this.minecraft.getWindow();
                return ((boolean) m.invoke(null, win, 340)) || ((boolean) m.invoke(null, win, 344));
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateDragState(int mouseX, int mouseY) {
        boolean leftDown = this.minecraft != null && this.minecraft.mouseHandler != null && this.minecraft.mouseHandler.isLeftPressed();
        if (leftDown && !this.wasLeftDown) {
            if (!isMouseOverButtons(mouseX, mouseY) && isMouseOverHud(mouseX, mouseY)) {
                startDragging(mouseX, mouseY);
            }
        } else if (leftDown && this.draggingHud) {
            boolean shiftDown = isShiftDown();
            handleDrag(mouseX, mouseY, shiftDown);
        } else if (!leftDown && this.wasLeftDown) {
            if (this.draggingHud) {
                stopDragging();
            }
        }
        this.wasLeftDown = leftDown;
    }

    private void startDragging(int mouseX, int mouseY) {
        if (isMouseOverButtons(mouseX, mouseY)) {
            return;
        }
        if (isMouseOverHud(mouseX, mouseY)) {
            this.draggingHud = true;
            this.dragOffsetX = mouseX - this.lastHudBoxX;
            this.dragOffsetY = mouseY - this.lastHudBoxY;
        }
    }

    private void handleDrag(int mouseX, int mouseY, boolean shiftDown) {
        if (!this.draggingHud) return;

        float scale = Math.clamp(this.config.hudScale, 0.7f, 1.5f);
        int hudW = this.lastHudBoxWidth;
        int hudH = this.lastHudBoxHeight;

        int rawX = mouseX - this.dragOffsetX;
        int rawY = mouseY - this.dragOffsetY;

        this.snapGuideX = -1;
        this.snapGuideY = -1;

        int finalX = rawX;
        int finalY = rawY;

        if (!shiftDown) {
            int snapMargin = 7;
            if (Math.abs(rawX - HudLayout.EDGE_MARGIN) <= snapMargin) {
                finalX = HudLayout.EDGE_MARGIN;
                this.snapGuideX = HudLayout.EDGE_MARGIN;
            }
            int rightEdge = this.width - hudW - HudLayout.EDGE_MARGIN;
            if (Math.abs(rawX - rightEdge) <= snapMargin) {
                finalX = rightEdge;
                this.snapGuideX = this.width - HudLayout.EDGE_MARGIN;
            }
            int centerX = (this.width - hudW) / 2;
            if (Math.abs(rawX - centerX) <= snapMargin) {
                finalX = centerX;
                this.snapGuideX = this.width / 2;
            }

            if (Math.abs(rawY - HudLayout.EDGE_MARGIN) <= snapMargin) {
                finalY = HudLayout.EDGE_MARGIN;
                this.snapGuideY = HudLayout.EDGE_MARGIN;
            }
            int bottomEdge = this.height - hudH - HudLayout.EDGE_MARGIN;
            if (Math.abs(rawY - bottomEdge) <= snapMargin) {
                finalY = bottomEdge;
                this.snapGuideY = this.height - HudLayout.EDGE_MARGIN;
            }
            int centerY = (this.height - hudH) / 2;
            if (Math.abs(rawY - centerY) <= snapMargin) {
                finalY = centerY;
                this.snapGuideY = this.height / 2;
            }
        }

        int maxScreenX = Math.max(0, this.width - hudW);
        int maxScreenY = Math.max(0, this.height - hudH);
        finalX = Math.clamp(finalX, 0, maxScreenX);
        finalY = Math.clamp(finalY, 0, maxScreenY);

        int scaledX = Math.round(finalX / scale);
        int scaledY = Math.round(finalY / scale);
        int scaledMaxX = Math.max(1, Math.round(maxScreenX / scale));
        int scaledMaxY = Math.max(1, Math.round(maxScreenY / scale));

        this.config.useCustomPosition = true;
        this.config.customX = scaledX;
        this.config.customY = scaledY;
        this.config.customXRatio = (float) scaledX / (float) scaledMaxX;
        this.config.customYRatio = (float) scaledY / (float) scaledMaxY;
    }

    private void stopDragging() {
        if (this.draggingHud) {
            this.draggingHud = false;
            this.snapGuideX = -1;
            this.snapGuideY = -1;
            ArdaniaConfig.save();
        }
    }

    private void nudge(int dx, int dy) {
        float scale = Math.clamp(this.config.hudScale, 0.7f, 1.5f);
        int hudW = this.lastHudBoxWidth;
        int hudH = this.lastHudBoxHeight;
        int maxScreenX = Math.max(0, this.width - hudW);
        int maxScreenY = Math.max(0, this.height - hudH);

        int currentX = this.lastHudBoxX;
        int currentY = this.lastHudBoxY;

        int newX = Math.clamp(currentX + dx, 0, maxScreenX);
        int newY = Math.clamp(currentY + dy, 0, maxScreenY);

        int scaledX = Math.round(newX / scale);
        int scaledY = Math.round(newY / scale);
        int scaledMaxX = Math.max(1, Math.round(maxScreenX / scale));
        int scaledMaxY = Math.max(1, Math.round(maxScreenY / scale));

        this.config.useCustomPosition = true;
        this.config.customX = scaledX;
        this.config.customY = scaledY;
        this.config.customXRatio = (float) scaledX / (float) scaledMaxX;
        this.config.customYRatio = (float) scaledY / (float) scaledMaxY;
        ArdaniaConfig.save();
    }

    private boolean isMouseOverHud(double mouseX, double mouseY) {
        int margin = 4;
        return mouseX >= (this.lastHudBoxX - margin) &&
                mouseX <= (this.lastHudBoxX + this.lastHudBoxWidth + margin) &&
                mouseY >= (this.lastHudBoxY - margin) &&
                mouseY <= (this.lastHudBoxY + this.lastHudBoxHeight + margin);
    }

    private boolean isMouseOverButtons(double mouseX, double mouseY) {
        if (this.resetBtn != null && this.resetBtn.isMouseOver(mouseX, mouseY)) return true;
        if (this.scaleBtn != null && this.scaleBtn.isMouseOver(mouseX, mouseY)) return true;
        if (this.doneBtn != null && this.doneBtn.isMouseOver(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int btn = event.button();
        boolean isLeft = (btn == 0 || btn == 1) && !isRightOrMiddleDown();
        if (isLeft) {
            startDragging((int) event.x(), (int) event.y());
            return this.draggingHud;
        }
        return false;
    }

    private boolean isRightOrMiddleDown() {
        if (this.minecraft == null || this.minecraft.mouseHandler == null) return false;
        return this.minecraft.mouseHandler.isRightPressed() || this.minecraft.mouseHandler.isMiddlePressed();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (super.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        if (this.draggingHud) {
            handleDrag((int) event.x(), (int) event.y(), event.hasShiftDown());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingHud) {
            stopDragging();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int step = event.hasShiftDown() ? 5 : 1;
        if (event.isLeft()) {
            nudge(-step, 0);
            return true;
        }
        if (event.isRight()) {
            nudge(step, 0);
            return true;
        }
        if (event.isUp()) {
            nudge(0, -step);
            return true;
        }
        if (event.isDown()) {
            nudge(0, step);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        stopDragging();
        ArdaniaClientMod.openScreen(this.minecraft, this.parent);
    }
}
