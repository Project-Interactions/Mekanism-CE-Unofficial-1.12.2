package mekanism.multiblockmachine.client.render;

import mekanism.client.render.MekanismRenderer;
import mekanism.multiblockmachine.client.model.ModelLargeHeatGenerator;
import mekanism.multiblockmachine.common.tile.generator.TileEntityLargeHeatGenerator;
import mekanism.multiblockmachine.common.util.MekanismMultiblockMachineUtils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderLargeHeatGenerator extends TileEntitySpecialRenderer<TileEntityLargeHeatGenerator> {

    private ModelLargeHeatGenerator model = new ModelLargeHeatGenerator();

    @Override
    public void render(TileEntityLargeHeatGenerator tileEntity,double x, double y, double z, float partialTick, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        bindTexture(MekanismMultiblockMachineUtils.getResource(MekanismMultiblockMachineUtils.ResourceType.RENDER, "LargeHeatGenerator.png"));
        MekanismRenderer.rotate(tileEntity.facing, 180, 0, 270, 90);
        GlStateManager.rotate(180, 0, 0, 1);
        model.render(0.0625F,tileEntity.getActive(),rendererDispatcher.renderEngine);
        GlStateManager.popMatrix();
    }

}
