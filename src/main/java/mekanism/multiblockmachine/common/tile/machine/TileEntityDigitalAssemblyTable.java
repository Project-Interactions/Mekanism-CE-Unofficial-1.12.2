package mekanism.multiblockmachine.common.tile.machine;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.*;
import mekanism.common.Mekanism;
import mekanism.common.MekanismItems;
import mekanism.common.Upgrade;
import mekanism.common.base.FluidHandlerWrapper;
import mekanism.common.base.IFluidHandlerWrapper;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.CompositeInput;
import mekanism.common.recipe.machines.DigitalAssemblyTableRecipe;
import mekanism.common.recipe.outputs.CompositeOutput;
import mekanism.common.tier.FluidTankTier;
import mekanism.common.tier.GasTankTier;
import mekanism.common.util.*;
import mekanism.multiblockmachine.common.MultiblockMachineItems;
import mekanism.multiblockmachine.common.block.states.BlockStateMultiblockMachine;
import mekanism.multiblockmachine.common.tile.machine.prefab.TileEntityMultiblockBasicMachine;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;

public class TileEntityDigitalAssemblyTable extends TileEntityMultiblockBasicMachine<CompositeInput, CompositeOutput, DigitalAssemblyTableRecipe>
        implements IGasHandler, IFluidHandlerWrapper, ITankManager, ISustainedData /*, IAdvancedBoundingBlock*/ {

    private static Random Rand = new Random();
    public FluidTank inputFluidTank = new FluidTankSync(FluidTankTier.ULTIMATE.getStorage());
    public FluidTank outputFluidTank = new FluidTankSync(FluidTankTier.ULTIMATE.getStorage());
    public GasTank inputGasTank = new GasTank(GasTankTier.ULTIMATE.getStorage());
    public GasTank outputGasTank = new GasTank(GasTankTier.ULTIMATE.getStorage());
    public int output = 512;
    public DigitalAssemblyTableRecipe cachedRecipe;
    public int updateDelay;
    public boolean needsPacket;
    public int numPowering;
    private int currentRedstoneLevel;
    private boolean rendererInitialized = false;


    public TileEntityDigitalAssemblyTable() {
        super("digitalassemblytable", BlockStateMultiblockMachine.MultiblockMachineType.DIGITAL_ASSEMBLY_TABLE, 200, 0);
        inventory = NonNullListSynchronized.withSize(15, ItemStack.EMPTY);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!world.isRemote) {
            if (updateDelay > 0) {
                updateDelay--;
                if (updateDelay == 0) {
                    needsPacket = true;
                }
            }
            DigitalAssemblyTableRecipe recipe = getRecipe();
            ChargeUtils.discharge(1, this);
            if (canOperate(recipe) && MekanismUtils.canFunction(this) && getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy) && isMachiningTools()) {
                boolean update = BASE_TICKS_REQUIRED != recipe.ticks;
                BASE_TICKS_REQUIRED = recipe.ticks;
                if (update) {
                    recalculateUpgradables(Upgrade.SPEED);
                }
                setActive(true);
                for (int i = 11; i < 14; i++) {
                    if (inventory.get(i).attemptDamageItem(1, Rand, null)) {
                        inventory.set(i, ItemStack.EMPTY);
                    }
                }
                if ((operatingTicks + 1) < ticksRequired) {
                    operatingTicks++;
                    electricityStored.addAndGet(-MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy));
                } else if ((operatingTicks + 1) >= ticksRequired && getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy)) {
                    operate(recipe);
                    operatingTicks = 0;
                    electricityStored.addAndGet(-MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy));
                }
                int newRedstoneLevel = getRedstoneLevel();
                if (newRedstoneLevel != currentRedstoneLevel) {
                    world.updateComparatorOutputLevel(pos, getBlockType());
                    currentRedstoneLevel = newRedstoneLevel;
                }
                if (needsPacket) {
                    Mekanism.packetHandler.sendUpdatePacket(this);
                }
                needsPacket = false;
            } else {
                BASE_TICKS_REQUIRED = 100;
                if (prevEnergy >= getEnergy()) {
                    setActive(false);
                }
            }
            if (!canOperate(recipe)) {
                operatingTicks = 0;
            }
            prevEnergy = getEnergy();
        } else {
            if (updateDelay > 0) {
                updateDelay--;
                if (updateDelay == 0) {
                    MekanismUtils.updateBlock(world, getPos());
                }
            }
        }
    }

    private boolean isMachiningTools(){
        return inventory.get(11).getItem() == MultiblockMachineItems.PlasmaCutterNozzles && inventory.get(12).getItem() == MultiblockMachineItems.DrillBit && inventory.get(13).getItem() == MultiblockMachineItems.LaserLenses;
    }

    private void handleTank(GasTank tank, TileEntity tile) {
        if (tank.getGas() != null) {
            GasStack toSend = new GasStack(tank.getGas().getGas(), Math.min(tank.getStored(), output));
            tank.draw(GasUtils.emit(toSend, tile, EnumSet.of(EnumFacing.DOWN)), true);
        }
    }

    @Override
    public boolean canOperate(DigitalAssemblyTableRecipe recipe) {
        return recipe != null && recipe.canOperate(inventory, 2, 3, 4, 5, 6, 7, 8, 9, 10, inputFluidTank, inputGasTank, 14, outputFluidTank, outputGasTank);
    }

    @Override
    public void operate(DigitalAssemblyTableRecipe recipe) {
        recipe.operate(inventory, 2, 3, 4, 5, 6, 7, 8, 9, 10, inputFluidTank, inputGasTank, 14, outputFluidTank, outputGasTank);
        markForUpdateSync();
    }

    @Override
    public Map<CompositeInput, DigitalAssemblyTableRecipe> getRecipes() {
        return RecipeHandler.Recipe.DIGITAL_ASSEMBLY_TABLE.get();
    }

    @Override
    public DigitalAssemblyTableRecipe getRecipe() {
        CompositeInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getDigitalAssemblyTableRecipe(input);
        }
        return cachedRecipe;
    }

    @Override
    public CompositeInput getInput() {
        return new CompositeInput(
                inventory.get(2), inventory.get(3), inventory.get(4), inventory.get(5), inventory.get(6), inventory.get(7), inventory.get(8), inventory.get(9), inventory.get(10),
                inputFluidTank.getFluid(), inputGasTank.getGas());
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 14) {
            return false;
        } else if (slotID == 0) {
            return itemstack.getItem() == MekanismItems.SpeedUpgrade || itemstack.getItem() == MekanismItems.EnergyUpgrade;
        } else if (slotID == 1) {
            return ChargeUtils.canBeDischarged(itemstack);
        } else if (slotID == 2 || slotID == 3 || slotID == 4 || slotID == 5 || slotID == 6 || slotID == 7 || slotID == 8 || slotID == 9 || slotID == 10 || slotID == 11 || slotID == 12 || slotID == 13) {
            return InputItemValidForSlot(slotID, itemstack);
        }
        return false;
    }

    private boolean InputItemValidForSlot(int slotID, ItemStack itemstack) {
        for (CompositeInput input : getRecipes().keySet()) {
            if (slotID == 2) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput, itemstack)) {
                    return true;
                }
            } else if (slotID == 3) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput2, itemstack)) {
                    return true;
                }
            } else if (slotID == 4) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput3, itemstack)) {
                    return true;
                }
            } else if (slotID == 5) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput4, itemstack)) {
                    return true;
                }
            } else if (slotID == 6) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput5, itemstack)) {
                    return true;
                }
            } else if (slotID == 7) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput6, itemstack)) {
                    return true;
                }
            } else if (slotID == 8) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput7, itemstack)) {
                    return true;
                }
            } else if (slotID == 9) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput8, itemstack)) {
                    return true;
                }
            } else if (slotID == 10) {
                if (ItemHandlerHelper.canItemStacksStack(input.itemInput9, itemstack)) {
                    return true;
                }
            } else if (slotID == 11) {
                if (ItemHandlerHelper.canItemStacksStack(MultiblockMachineItems.PlasmaCutterNozzles.getDefaultInstance(), itemstack)) {
                    return true;
                }
            } else if (slotID == 12) {
                if (ItemHandlerHelper.canItemStacksStack(MultiblockMachineItems.DrillBit.getDefaultInstance(), itemstack)) {
                    return true;
                }
            } else if (slotID == 13) {
                if (ItemHandlerHelper.canItemStacksStack(MultiblockMachineItems.LaserLenses.getDefaultInstance(), itemstack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return ChargeUtils.canBeOutputted(itemstack, false);
        }
        return slotID == 14;
    }

    @Override
    public String[] getMethods() {
        return new String[0];
    }

    @Override
    public Object[] invoke(int method, Object[] args) throws NoSuchMethodException {
        return new Object[0];
    }

    @NotNull
    @Override
    public int[] getSlotsForFace(@NotNull EnumFacing side) {
        return InventoryUtils.EMPTY;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, inputFluidTank);
            TileUtils.readTankData(dataStream, inputGasTank);
            TileUtils.readTankData(dataStream, outputFluidTank);
            TileUtils.readTankData(dataStream, outputGasTank);
            numPowering = dataStream.readInt();
            if (updateDelay == 0) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, inputFluidTank);
        TileUtils.addTankData(data, inputGasTank);
        TileUtils.addTankData(data, outputFluidTank);
        TileUtils.addTankData(data, outputGasTank);
        data.add(numPowering);
        return data;
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbtTags) {
        super.readCustomNBT(nbtTags);
        inputFluidTank.readFromNBT(nbtTags.getCompoundTag("inputFluidTank"));
        inputGasTank.read(nbtTags.getCompoundTag("inputGasTank"));
        outputFluidTank.readFromNBT(nbtTags.getCompoundTag("outputFluidTank"));
        outputGasTank.read(nbtTags.getCompoundTag("outputGasTank"));
        numPowering = nbtTags.getInteger("numPowering");
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbtTags) {
        super.writeCustomNBT(nbtTags);
        nbtTags.setTag("inputFluidTank", inputFluidTank.writeToNBT(new NBTTagCompound()));
        nbtTags.setTag("inputGasTank", inputGasTank.write(new NBTTagCompound()));
        nbtTags.setTag("outputFluidTank", outputFluidTank.writeToNBT(new NBTTagCompound()));
        nbtTags.setTag("outputGasTank", outputGasTank.write(new NBTTagCompound()));
        nbtTags.setInteger("numPowering", numPowering);
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (canReceiveGas(side, stack != null ? stack.getGas() : null)) {
            return inputGasTank.receive(stack, doTransfer);
        }
        return 0;
    }

    @Override
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        if (canDrawGas(side, null)) {
            return outputGasTank.draw(amount, doTransfer);
        }
        return null;
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        if (side == EnumFacing.DOWN) {
            return inputGasTank.canReceive(type) && RecipeHandler.Recipe.DIGITAL_ASSEMBLY_TABLE.containsRecipe(type);
        }
        return false;
    }

    @Override
    public boolean canDrawGas(EnumFacing side, Gas type) {
        return outputGasTank.canDraw(type) && side == EnumFacing.DOWN;
    }

    @NotNull
    @Override
    public GasTankInfo[] getTankInfo() {
        return new GasTankInfo[]{inputGasTank, outputGasTank};
    }

    @Override
    public int fill(EnumFacing from, @NotNull FluidStack resource, boolean doFill) {
        return outputFluidTank.fill(resource, doFill);
    }

    @Nullable
    @Override
    public FluidStack drain(EnumFacing from, @NotNull FluidStack resource, boolean doDrain) {
        return IFluidHandlerWrapper.super.drain(from, resource, doDrain);
    }

    @Nullable
    @Override
    public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain) {
        return IFluidHandlerWrapper.super.drain(from, maxDrain, doDrain);
    }

    @Override
    public boolean canFill(EnumFacing from, @NotNull FluidStack fluid) {
        return RecipeHandler.Recipe.DIGITAL_ASSEMBLY_TABLE.containsRecipe(fluid.getFluid()) && from == EnumFacing.DOWN;
    }

    @Override
    public boolean canDrain(EnumFacing from, @Nullable FluidStack fluid) {
        return outputFluidTank.getFluidAmount() > 0 && FluidContainerUtils.canDrain(outputFluidTank.getFluid(), fluid);
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing from) {
        return new FluidTankInfo[]{inputFluidTank.getInfo(), outputFluidTank.getInfo()};
    }

    @Override
    public FluidTankInfo[] getAllTanks() {
        return new FluidTankInfo[]{inputFluidTank.getInfo(), outputFluidTank.getInfo()};
    }

    @Override
    public Object[] getTanks() {
        return new Object[]{inputFluidTank, inputGasTank, outputFluidTank, outputGasTank};
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.GAS_HANDLER_CAPABILITY || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        if (inputFluidTank.getFluid() != null) {
            ItemDataUtils.setCompound(itemStack, "inputFluidTank", inputFluidTank.getFluid().writeToNBT(new NBTTagCompound()));
        }
        if (inputGasTank.getGas() != null) {
            ItemDataUtils.setCompound(itemStack, "inputGasTank", inputGasTank.getGas().write(new NBTTagCompound()));
        }
        if (outputFluidTank.getFluid() != null) {
            ItemDataUtils.setCompound(itemStack, "outputFluidTank", outputFluidTank.getFluid().writeToNBT(new NBTTagCompound()));
        }
        if (outputGasTank.getGas() != null) {
            ItemDataUtils.setCompound(itemStack, "outputGasTank", outputGasTank.getGas().write(new NBTTagCompound()));
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        inputFluidTank.setFluid(FluidStack.loadFluidStackFromNBT(ItemDataUtils.getCompound(itemStack, "inputFluidTank")));
        inputGasTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "inputGasTank")));
        outputFluidTank.setFluid(FluidStack.loadFluidStackFromNBT(ItemDataUtils.getCompound(itemStack, "outputFluidTank")));
        outputGasTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "outputGasTank")));
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this);
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new FluidHandlerWrapper(this, side));
        }
        return super.getCapability(capability, side);
    }

    /*
    @Override
    public boolean canBoundReceiveEnergy(BlockPos coord, EnumFacing side) {
        EnumFacing back = MekanismUtils.getBack(facing);
        EnumFacing left = MekanismUtils.getLeft(facing);
        EnumFacing right = MekanismUtils.getRight(facing);
        if (coord.equals(getPos().offset(back,5).offset(left,4).up()) || coord.equals(getPos().offset(back,5).offset(right,4).up()) ) {
            return side == EnumFacing.DOWN;
        }
        return false;
    }

    @Override
    public boolean canBoundOutPutEnergy(BlockPos location, EnumFacing side) {
        return false;
    }

    @Override
    public void onPower() {
        numPowering++;
    }

    @Override
    public void onNoPower() {
        numPowering--;
    }

    @Override
    public NBTTagCompound getConfigurationData(NBTTagCompound nbtTags) {
        return nbtTags;
    }

    @Override
    public void setConfigurationData(NBTTagCompound nbtTags) {

    }

    @Override
    public String getDataType() {
        return getBlockType().getTranslationKey() + "." + fullName + ".name";
    }

    @Override
    public void onPlace() {

    }

    @Override
    public void onBreak() {

    }

    @Override
    public boolean hasOffsetCapability(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return false;
        }
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return true;
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        } else if (isStrictEnergy(capability) || capability == CapabilityEnergy.ENERGY || isTesla(capability, side)) {
            return true;
        }
        return hasCapability(capability, side);
    }

    @Nullable
    @Override
    public <T> T getOffsetCapability(@NotNull Capability<T> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        if (isOffsetCapabilityDisabled(capability, side, offset)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this);
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new FluidHandlerWrapper(this, side));
        } else if (isStrictEnergy(capability)) {
            return (T) this;
        } else if (isTesla(capability, side)) {
            return (T) getTeslaEnergyWrapper(side);
        } else if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(getForgeEnergyWrapper(side));
        }
        return getCapability(capability, side);
    }

    @Override
    public boolean isOffsetCapabilityDisabled(@NotNull Capability<?> capability, @Nullable EnumFacing side, @NotNull Vec3i offset) {
        return IAdvancedBoundingBlock.super.isOffsetCapabilityDisabled(capability, side, offset);
    }
     */
}
