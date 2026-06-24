package com.vel5id.hexerei.client;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.menu.CharmPouchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The Charm Pouch GUI: the 3 charm slots over the player inventory. Standard 176x166 container. */
@OnlyIn(Dist.CLIENT)
public class CharmPouchScreen extends AbstractContainerScreen<CharmPouchMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(HexereiMod.MODID, "textures/gui/charm_pouch.png");

    public CharmPouchScreen(CharmPouchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // 9-arg blit: declare the real texture size (176x166). The 6-arg overload assumes a 256x256
        // sheet and would sample only the top-left ~69% of this texture, shifting slots and clipping the inventory.
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
