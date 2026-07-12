package com.github.skyjack2033.wirelessmehatch.output;

import net.minecraftforge.fluids.FluidStack;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;

public final class WirelessFluidOutputDelegate extends MTEHatchOutput {

    private final WirelessUnifiedOutputCore core;

    public WirelessFluidOutputDelegate(String name, int tier, String[] description, ITexture[][][] textures,
        WirelessUnifiedOutputCore core) {
        super(name + ".fluid_delegate", tier, description, textures);
        this.core = core;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        throw new UnsupportedOperationException("Wireless fluid output delegate is not a public MetaTileEntity");
    }

    @Override
    public int fill(FluidStack fluid, boolean doFill) {
        return core.storeFluid(fluid, !doFill);
    }

    @Override
    public int fill(net.minecraftforge.common.util.ForgeDirection side, FluidStack fluid, boolean doFill) {
        return fill(fluid, doFill);
    }

    @Override
    public boolean canStoreFluid(FluidStack fluid) {
        return fluid != null && fluid.amount > 0 && core.getFluidCached() < core.getFluidCapacity();
    }

    @Override
    public int getCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, core.getFluidCapacity());
    }
}
