package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.api.IDualOutputHatch;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@Mixin(value = gregtech.api.metatileentity.implementations.MTEMultiBlockBase.class, remap = false)
public class MTEMultiBlockBaseMixin implements MTEMultiBlockBaseMixinAccessor {

    @Unique
    private final List<IDualOutputHatch> wirelessmehatch$mDualOutputHatches = new ArrayList<>();

    @Override
    public List<?> wirelessmehatch$getDualOutputHatches() {
        return wirelessmehatch$mDualOutputHatches;
    }

    @Inject(method = "addToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null || aTileEntity.getMetaTileEntity() == null) return;
        if (aTileEntity.getMetaTileEntity() instanceof IDualOutputHatch dual) {
            wirelessmehatch$mDualOutputHatches.add(dual);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "addOutputBusToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null || aTileEntity.getMetaTileEntity() == null) return;
        if (aTileEntity.getMetaTileEntity() instanceof IDualOutputHatch dual) {
            wirelessmehatch$mDualOutputHatches.add(dual);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "addOutputHatchToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddOutputHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null || aTileEntity.getMetaTileEntity() == null) return;
        if (aTileEntity.getMetaTileEntity() instanceof IDualOutputHatch dual) {
            wirelessmehatch$mDualOutputHatches.add(dual);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "clearHatches", at = @At("HEAD"))
    private void wirelessmehatch$onClearHatches(CallbackInfo ci) {
        wirelessmehatch$mDualOutputHatches.clear();
    }

    @Inject(method = "addItemOutputs([Lnet/minecraft/item/ItemStack;)Z", at = @At("TAIL"))
    private void wirelessmehatch$onAddItemOutputs(ItemStack[] aOutputItems, CallbackInfoReturnable<Boolean> cir) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty() || aOutputItems == null) return;
        for (ItemStack stack : aOutputItems) {
            if (stack == null || stack.stackSize <= 0) continue;
            for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
                if (stack.stackSize <= 0) break;
                dual.storePartial(stack, false);
            }
        }
    }

    @Inject(method = "addFluidOutputs([Lnet/minecraftforge/fluids/FluidStack;)Z", at = @At("TAIL"))
    private void wirelessmehatch$onAddFluidOutputs(FluidStack[] aOutputFluids, CallbackInfoReturnable<Boolean> cir) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty() || aOutputFluids == null) return;
        for (FluidStack fluid : aOutputFluids) {
            if (fluid == null || fluid.amount <= 0) continue;
            FluidStack remainder = fluid.copy();
            for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
                if (remainder.amount <= 0) break;
                int accepted = dual.fill(remainder, true);
                if (accepted > 0) remainder.amount -= accepted;
            }
        }
    }

    @Inject(method = "addOutputPartial(Lnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void wirelessmehatch$onAddOutputPartialItem(ItemStack aStack, CallbackInfo ci) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty() || aStack == null || aStack.stackSize <= 0) return;
        for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
            if (aStack.stackSize <= 0) break;
            dual.storePartial(aStack, false);
        }
    }

    @Inject(method = "addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V", at = @At("TAIL"))
    private void wirelessmehatch$onAddOutputPartialFluid(FluidStack aFluid, CallbackInfo ci) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty() || aFluid == null || aFluid.amount <= 0) return;
        FluidStack remainder = aFluid.copy();
        for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
            if (remainder.amount <= 0) break;
            int accepted = dual.fill(remainder, true);
            if (accepted > 0) remainder.amount -= accepted;
        }
    }

    @Inject(method = "getOutputBusses", at = @At("RETURN"))
    private void wirelessmehatch$onGetOutputBusses(CallbackInfoReturnable<List<IOutputBus>> cir) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty()) return;
        List<IOutputBus> busses = cir.getReturnValue();
        for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
            if (dual instanceof com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessOutputHatchME hatch) {
                busses.add(new com.github.skyjack2033.wirelessmehatch.metatileentity.WirelessOutputBusAdapter(hatch));
            }
        }
    }

    @Inject(method = "getOutputHatches", at = @At("RETURN"))
    private void wirelessmehatch$onGetOutputHatches(
        CallbackInfoReturnable<java.util.List<gregtech.api.interfaces.IOutputHatch>> cir) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty()) return;
        java.util.List<gregtech.api.interfaces.IOutputHatch> hatches = cir.getReturnValue();
        for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
            if (dual instanceof gregtech.api.interfaces.IOutputHatch hatch && !hatches.contains(hatch)) {
                hatches.add(hatch);
            }
        }
    }
}
