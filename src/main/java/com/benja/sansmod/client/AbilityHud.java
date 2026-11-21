package com.benja.sansmod.client;

import com.benja.sansmod.Ability;
import com.benja.sansmod.SansMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class AbilityHud {
    public static final Identifier[] ICONS = new Identifier[Ability.ALL.length];

    static {
        for (Ability a : Ability.ALL) {
            ICONS[a.ordinal()] = SansMod.id("textures/gui/ability/" + a.id + ".png");
        }
    }

    private AbilityHud() {}

    /** Dibuja el icono de 16x16 escalado a size x size. */
    public static void icon(GuiGraphicsExtractor g, int index, int x, int y, int size) {
        g.blit(RenderPipelines.GUI_TEXTURED, ICONS[index], x, y, 0, 0, size, size, 16, 16, 16, 16);
    }

    /** Oscurece el icono según el cooldown restante. */
    public static void cooldownOverlay(GuiGraphicsExtractor g, int index, int x, int y, int size) {
        int rem = ClientState.remaining[index];
        int tot = ClientState.total[index];
        if (rem > 0 && tot > 0) {
            int h = Math.min(size, (int) Math.ceil(size * (rem / (double) tot)));
            g.fill(x, y, x + size, y + h, 0xB0000000);
        }
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !SansMod.hasCharm(player)) return;

        Font font = mc.font;
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = 8;
        int y = screenH - 70;
        int sel = ClientState.selected;
        Ability ability = Ability.ALL[sel];

        // tira con todas las habilidades (la elegida resaltada)
        for (int i = 0; i < Ability.ALL.length; i++) {
            int ix = x + i * 14;
            icon(g, i, ix, y - 17, 12);
            cooldownOverlay(g, i, ix, y - 17, 12);
            if (i == sel) g.outline(ix - 1, y - 18, 14, 14, 0xFFFFFF55);
        }

        // panel de la habilidad elegida
        g.fill(x - 4, y - 2, x + 156, y + 52, 0x90000000);
        icon(g, sel, x, y, 24);
        cooldownOverlay(g, sel, x, y, 24);

        Component name = Component.translatable("ability.sansmod." + ability.id);
        g.text(font, name, x + 30, y + 1, 0xFFFFFFFF, true);

        Component status;
        int color = 0xFF55FF55;
        if (ability.toggle) {
            boolean on = ability == Ability.DODGE ? ClientState.dodgeOn : ClientState.karmaOn;
            status = Component.translatable(on ? "hud.sansmod.on" : "hud.sansmod.off");
            color = on ? 0xFF55FF55 : 0xFFAAAAAA;
        } else if (ClientState.remaining[sel] > 0) {
            status = Component.literal(String.format("%.1f s", ClientState.remaining[sel] / 20.0));
            color = 0xFFFF5555;
        } else {
            status = Component.translatable("hud.sansmod.ready");
        }
        g.text(font, status, x + 30, y + 13, color, true);

        // energía de esquive
        int barX = x;
        int barY = y + 30;
        int barW = 100;
        g.text(font, Component.translatable("hud.sansmod.energy"), barX, barY - 1, 0xFFAAAAAA, false);
        g.fill(barX, barY + 9, barX + barW, barY + 13, 0xFF202020);
        int filled = (int) (barW * (1.0 - ClientState.fatigue / (double) ClientState.maxFatigue));
        g.fill(barX, barY + 9, barX + Math.max(0, filled), barY + 13, 0xFF55CCFF);

        // estados temporales
        int ty = y + 16;
        if (ClientState.serious > 0) {
            g.text(font, Component.translatable("hud.sansmod.eye",
                    String.format("%.0f", ClientState.serious / 20.0)), x + 62, ty, 0xFF55FFFF, true);
        }
        if (ClientState.badTime > 0) {
            g.text(font, Component.translatable("hud.sansmod.bad_time",
                    String.format("%.0f", ClientState.badTime / 20.0)), x + 62, ty + 10, 0xFFFF77FF, true);
        }
        if (ClientState.karmaOn && ability != Ability.KARMA) {
            g.text(font, Component.translatable("ability.sansmod.karma"), x + 110, y + 1, 0xFFFF77FF, true);
        }
    }
}
