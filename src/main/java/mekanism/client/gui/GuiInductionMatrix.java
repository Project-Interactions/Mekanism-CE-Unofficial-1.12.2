package mekanism.client.gui;

import mekanism.client.gui.element.*;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.gauge.GuiNumberGauge;
import mekanism.client.gui.element.tab.GuiMatrixTab;
import mekanism.client.gui.element.tab.GuiMatrixTab.MatrixTab;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.ContainerInductionMatrix;
import mekanism.common.tile.TileEntityInductionCasing;
import mekanism.common.tile.TileEntityLaserAmplifier;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;

@SideOnly(Side.CLIENT)
public class GuiInductionMatrix extends GuiMekanismTile<TileEntityInductionCasing> {

    public GuiInductionMatrix(InventoryPlayer inventory, TileEntityInductionCasing tile) {
        super(tile, new ContainerInductionMatrix(inventory, tile));
        ResourceLocation resource = getGuiLocation();
        addGuiElement(new GuiMatrixTab(this, tileEntity, MatrixTab.STAT, resource));
        addGuiElement(new GuiEnergyInfo(() -> Arrays.asList(LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy()),
                LangUtils.localize("gui.input") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastInput()) + "/t",
                LangUtils.localize("gui.output") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getLastOutput()) + "/t"), this, resource));
        addGuiElement(new GuiInnerScreen(this, resource, 50, 23, 80, 41));
        addGuiElement(new GuiBucketIcon(GuiBucketIcon.IconType.INDUCTIONMATRIX, this, resource, 141, 15));
        addGuiElement(new GuiPlayerArmmorSlot(this, resource, -26, 37, false));
        addGuiElement(new GuiNumberGauge(new GuiNumberGauge.INumberInfoHandler() {
            @Override
            public TextureAtlasSprite getIcon() {
                return MekanismRenderer.energyIcon;
            }

            @Override
            public double getLevel() {
                return tileEntity.getEnergy();
            }

            @Override
            public double getMaxLevel() {
                return tileEntity.getMaxEnergy();
            }

            @Override
            public String getText(double level) {
                return LangUtils.localize("gui.storing") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy());
            }
        }, GuiGauge.Type.MEDIUM, this, resource, 6, 13));
        addGuiElement(new GuiPlayerSlot(this, resource));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tileEntity.getName(), (xSize / 2) - (fontRenderer.getStringWidth(tileEntity.getName()) / 2), 4, 0x404040);
        fontRenderer.drawString(LangUtils.localize("container.inventory"), 8, (ySize - 94) + 2, 0x404040);
        fontRenderer.drawString(LangUtils.localize("gui.energy") + ":" + MekanismUtils.getEnergyDisplay(tileEntity.getEnergy()), 53, 26, 0x00CD00);
        fontRenderer.drawString(LangUtils.localize("gui.capacity") + ":" + MekanismUtils.getEnergyDisplay(tileEntity.getMaxEnergy()), 53, 35, 0x00CD00);
        fontRenderer.drawString(LangUtils.localize("gui.input") + ":" + MekanismUtils.getEnergyDisplay(tileEntity.getLastInput()) + "/t", 53, 44, 0x00CD00);
        fontRenderer.drawString(LangUtils.localize("gui.output") + ":" + MekanismUtils.getEnergyDisplay(tileEntity.getLastOutput()) + "/t", 53, 53, 0x00CD00);
        int xAxis = mouseX - guiLeft;
        int yAxis = mouseY - guiTop;
        if (xAxis >= 7 && xAxis <= 39 && yAxis >= 14 && yAxis <= 72) {
            displayTooltip(MekanismUtils.getEnergyDisplay(tileEntity.getEnergy(), tileEntity.getMaxEnergy()), xAxis, yAxis);
        }
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }


    @Override
    protected ResourceLocation getGuiLocation() {
        return MekanismUtils.getResource(ResourceType.GUI, "Null.png");
    }
}