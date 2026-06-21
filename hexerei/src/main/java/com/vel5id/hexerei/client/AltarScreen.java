package com.vel5id.hexerei.client;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Read-only screen that displays the altar's current and maximum power. */
public class AltarScreen extends Screen {
    private static final ResourceLocation TEX = new ResourceLocation(HexereiMod.MODID, "textures/gui/altar.png");
    private static final int W = 176;
    private static final int H = 88;

    private final BlockPos corePos;

    public AltarScreen(BlockPos corePos) {
        super(Component.translatable("hexerei.book.altarpower"));
        this.corePos = corePos;
    }

    public static void open(BlockPos corePos) {
        Minecraft.getInstance().setScreen(new AltarScreen(corePos));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.blit(TEX, left, top, 0, 0, W, H);

        float power = 0;
        float max = 0;
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(corePos) instanceof AltarBlockEntity be) {
            power = be.clientPower();
            max = be.clientMaxPower();
        }
        String label = Component.translatable("hexerei.gui.altar.power", (int) power, (int) max).getString();
        g.drawCenteredString(font, label, width / 2, top + H / 2 - 4, 0xFFFFFF);

        super.render(g, mouseX, mouseY, partial);
    }
}
