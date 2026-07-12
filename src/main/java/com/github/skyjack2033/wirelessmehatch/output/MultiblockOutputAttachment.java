package com.github.skyjack2033.wirelessmehatch.output;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

public final class MultiblockOutputAttachment {

    private static final int SEARCH_RANGE = 6;

    private final IOutputBus ownerBus;
    private final WirelessFluidOutputDelegate delegate;
    private MTEMultiBlockBase controller;
    private Field outputBussesField;
    private Field outputHatchesField;
    private int checkCountdown;

    public MultiblockOutputAttachment(IOutputBus ownerBus, WirelessFluidOutputDelegate delegate) {
        this.ownerBus = ownerBus;
        this.delegate = delegate;
    }

    public void tick(IGregTechTileEntity baseTile) {
        if (baseTile == null || !baseTile.isServerSide()) return;
        if (checkCountdown-- > 0) return;
        checkCountdown = 20;
        attach(baseTile);
    }

    public void detach() {
        if (controller == null) return;
        try {
            getOutputHatches(controller).remove(delegate);
        } catch (ReflectiveOperationException | ClassCastException e) {
            WirelessMEHatch.LOG.warn("Could not detach wireless fluid delegate: {}", e.toString());
        } finally {
            controller = null;
        }
    }

    private void attach(IGregTechTileEntity baseTile) {
        try {
            MTEMultiBlockBase found = findOwningController(baseTile);
            if (found != controller) {
                detach();
                controller = found;
            }
            if (controller == null) return;

            ArrayList<MTEHatchOutput> hatches = getOutputHatches(controller);
            if (!hatches.contains(delegate)) {
                delegate.setBaseMetaTileEntity(baseTile);
                hatches.add(delegate);
                WirelessMEHatch.LOG.debug("Attached wireless fluid delegate to {}", controller.getLocalName());
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            WirelessMEHatch.LOG.warn("Could not attach wireless fluid delegate: {}", e.toString());
        }
    }

    private MTEMultiBlockBase findOwningController(IGregTechTileEntity baseTile) throws ReflectiveOperationException {
        World world = baseTile.getWorld();
        if (world == null) return null;
        int x = baseTile.getXCoord();
        int y = baseTile.getYCoord();
        int z = baseTile.getZCoord();
        for (int dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (int dy = -SEARCH_RANGE; dy <= SEARCH_RANGE; dy++) {
                for (int dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                    TileEntity tile = world.getTileEntity(x + dx, y + dy, z + dz);
                    if (tile instanceof IGregTechTileEntity gtTile
                        && gtTile.getMetaTileEntity() instanceof MTEMultiBlockBase multiblock
                        && ownsBus(multiblock)) {
                        return multiblock;
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean ownsBus(MTEMultiBlockBase multiblock) throws ReflectiveOperationException {
        List<IOutputBus> busses = (List<IOutputBus>) getOutputBussesField().get(multiblock);
        return busses.contains(ownerBus);
    }

    @SuppressWarnings("unchecked")
    private ArrayList<MTEHatchOutput> getOutputHatches(MTEMultiBlockBase multiblock)
        throws ReflectiveOperationException {
        return (ArrayList<MTEHatchOutput>) getOutputHatchesField().get(multiblock);
    }

    private Field getOutputBussesField() throws NoSuchFieldException {
        if (outputBussesField == null) {
            outputBussesField = MTEMultiBlockBase.class.getDeclaredField("mOutputBusses");
            outputBussesField.setAccessible(true);
        }
        return outputBussesField;
    }

    private Field getOutputHatchesField() throws NoSuchFieldException {
        if (outputHatchesField == null) {
            outputHatchesField = MTEMultiBlockBase.class.getDeclaredField("mOutputHatches");
            outputHatchesField.setAccessible(true);
        }
        return outputHatchesField;
    }
}
