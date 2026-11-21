package com.benja.sansmod.client;

import com.benja.sansmod.Ability;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Menú para elegir la habilidad (haz clic en un icono; se cierra solo). */
public class AbilityScreen extends Screen {
    private static final int CELL = 40;
    private static final int GAP = 4;
    private static final int COLS = 5;

    private int originX;
    private int originY;

    public AbilityScreen() {
        super(Component.translatable("screen.sansmod.title"));
    }

    @Override
    protected void init() {
        int rows = (Ability.ALL.length + COLS - 1) / COLS;
        int gridW = COLS * CELL + (COLS - 1) * GAP;
        int gridH = rows * CELL + (rows - 1) * GAP;
        originX = (this.width - gridW) / 2;
        originY = Math.max(40, (this.height - gridH) / 2 - 20);

        for (int i = 0; i < Ability.ALL.length; i++) {
            final int index = i;
            int cx = originX + (i % COLS) * (CELL + GAP);
            int cy = originY + (i / COLS) * (CELL + GAP);
            addRenderableWidget(Button.builder(Component.empty(), b -> {
                ClientState.selected = index;
                this.onClose();
            }).bounds(cx, cy, CELL, CELL).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        g.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 16, 0xFFFFFFFF, true);

        int hovered = -1;
        for (int i = 0; i < Ability.ALL.length; i++) {
            int cx = originX + (i % COLS) * (CELL + GAP);
            int cy = originY + (i / COLS) * (CELL + GAP);
            AbilityHud.icon(g, i, cx + 4, cy + 4, 32);
            AbilityHud.cooldownOverlay(g, i, cx + 4, cy + 4, 32);
            if (i == ClientState.selected) g.outline(cx - 1, cy - 1, CELL + 2, CELL + 2, 0xFFFFFF55);
            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) hovered = i;
        }

        int shown = hovered >= 0 ? hovered : ClientState.selected;
        Ability a = Ability.ALL[shown];
        int rows = (Ability.ALL.length + COLS - 1) / COLS;
        int textY = originY + rows * (CELL + GAP) + 10;
        Component name = Component.translatable("ability.sansmod." + a.id);
        Component desc = Component.translatable("ability.sansmod." + a.id + ".desc");
        g.text(this.font, name, (this.width - this.font.width(name)) / 2, textY, 0xFFFFFF55, true);
        g.text(this.font, desc, (this.width - this.font.width(desc)) / 2, textY + 14, 0xFFCCCCCC, false);
        if (ClientState.remaining[shown] > 0) {
            Component cd = Component.literal(String.format("%.1f s", ClientState.remaining[shown] / 20.0));
            g.text(this.font, cd, (this.width - this.font.width(cd)) / 2, textY + 28, 0xFFFF5555, false);
        }
    }
}
