package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.skyjack2033.wirelessmehatch.api.IDualOutputHatch;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Makes every multiblock controller (anything extending {@link MTEMultiBlockBase}) recognise {@link IDualOutputHatch}
 * (the wireless merged output hatch) as a combined fluid hatch + item output bus, without editing GT source.
 *
 * <p>
 * Strategy, verified by decompiling {@code MTEMultiBlockBase} from the GT5 dev jar (5.09.54.20):
 * <ul>
 * <li><b>Registration:</b> {@code @Inject} at {@code HEAD} of {@code addToMachineList},
 * {@code addOutputHatchToMachineList} and {@code addOutputBusToMachineList}. When the candidate is an
 * {@code IDualOutputHatch} we apply the texture/crafting icon (mirroring how GT handles {@code IDualInputHatch} and the
 * plain output hatch/bus branches), add it to our own {@code mDualOutputHatches} list, and short-circuit the original
 * method by returning {@code true}. This prevents the hatch (which extends {@code MTEHatchOutput}) from also being
 * added
 * to {@code mOutputHatches}, which would double-count its fluid side.</li>
 * <li><b>Dispatch:</b> GT routes item/fluid output through {@code ItemEjectionHelper}/{@code FluidEjectionHelper},
 * which
 * pull their target lists from {@code IVoidable.getOutputBusses()}/{@code getOutputHatches()}. Those return
 * {@code List<IOutputBus>}/{@code List<IOutputHatch>} - our dual hatch implements neither, so it cannot be injected
 * into those lists. Instead we {@code @Inject} at {@code TAIL} of every output-dispatch entry point
 * ({@code addItemOutputs}, {@code addOutputPartial(ItemStack)}, {@code addFluidOutputs}, {@code addOutputPartial(
 * FluidStack)}) and call {@link IDualOutputHatch#storePartial} / {@code fill} directly on each dual hatch, mirroring
 * the
 * per-stack/per-fluid dispatch the helpers perform for the regular hatches.</li>
 * <li><b>Clearing:</b> {@code @Inject} at {@code HEAD} of {@code clearHatches} clears {@code mDualOutputHatches}, so
 * the
 * list is rebuilt on every structure re-scan just like the native hatch lists.</li>
 * </ul>
 *
 * <p>
 * {@code remap = false} because GT ships deobfuscated in both dev and production (GTNH dev jars), so the method/field
 * names match the source directly.
 */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public class MTEMultiBlockBaseMixin {

    /**
     * Registered dual output hatches for this controller. Cleared in {@code clearHatches} alongside the native lists.
     * Held on the mixin-injected instance, so it persists with the controller and is per-controller.
     */
    @Unique
    private final List<IDualOutputHatch> wirelessmehatch$mDualOutputHatches = new ArrayList<>();

    // ---------------- Registration ----------------

    /**
     * Mirror the {@code IDualInputHatch} branch at the top of {@code addToMachineList}: if the MTE is an
     * {@link IDualOutputHatch}, texture it, register it, and return {@code true} so the later {@code MTEHatchOutput}
     * branch does not also add it to {@code mOutputHatches}.
     */
    @Inject(method = "addToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        IDualOutputHatch dual = wirelessmehatch$asDualOutputHatch(aTileEntity);
        if (dual == null) {
            return;
        }
        wirelessmehatch$register(dual, aBaseCasingIndex);
        cir.setReturnValue(true);
    }

    /**
     * For controllers that wire their output hatch explicitly via {@code addOutputHatchToMachineList}: intercept the
     * dual hatch here so it is registered once (and does not fall through to the {@code MTEHatchOutput} branch which
     * would only register its fluid side).
     */
    @Inject(method = "addOutputHatchToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddOutputHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        IDualOutputHatch dual = wirelessmehatch$asDualOutputHatch(aTileEntity);
        if (dual == null) {
            return;
        }
        wirelessmehatch$register(dual, aBaseCasingIndex);
        cir.setReturnValue(true);
    }

    /**
     * For controllers that wire their output bus explicitly via {@code addOutputBusToMachineList}: the dual hatch does
     * not extend {@code MTEHatchOutputBus}, so without this hook it would never receive item output.
     */
    @Inject(method = "addOutputBusToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        IDualOutputHatch dual = wirelessmehatch$asDualOutputHatch(aTileEntity);
        if (dual == null) {
            return;
        }
        wirelessmehatch$register(dual, aBaseCasingIndex);
        cir.setReturnValue(true);
    }

    // ---------------- Clearing ----------------

    /**
     * Clear our list in lockstep with the native hatch lists so structure re-scans rebuild it from scratch.
     */
    @Inject(method = "clearHatches", at = @At("HEAD"))
    private void wirelessmehatch$onClearHatches(CallbackInfo ci) {
        wirelessmehatch$mDualOutputHatches.clear();
    }

    // ---------------- Item output dispatch ----------------

    /**
     * After {@code addItemOutputs(ItemStack[])} has dispatched to the regular output busses (mutating the array's stack
     * sizes down to the leftovers), feed whatever remains to each dual output hatch via
     * {@link IDualOutputHatch#storePartial}. Mirrors how {@code ItemEjectionHelper.ejectItems} walks busses: each hatch
     * sees the running remainder.
     */
    @Inject(method = "addItemOutputs", at = @At("TAIL"))
    private void wirelessmehatch$onAddItemOutputs(ItemStack[] aOutputItems, CallbackInfoReturnable<Boolean> cir) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty() || aOutputItems == null) {
            return;
        }
        for (ItemStack stack : aOutputItems) {
            wirelessmehatch$dispatchItem(stack);
        }
    }

    /**
     * After {@code addOutputPartial(ItemStack)} has dispatched the single stack to the regular output busses, feed the
     * remainder to the dual output hatches.
     */
    @Inject(method = "addOutputPartial(Lnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void wirelessmehatch$onAddOutputPartialItem(ItemStack aStack, CallbackInfo ci) {
        if (!wirelessmehatch$mDualOutputHatches.isEmpty()) {
            wirelessmehatch$dispatchItem(aStack);
        }
    }

    // ---------------- Fluid output dispatch ----------------

    /**
     * After {@code addFluidOutputs(FluidStack[])} has dispatched to the regular output hatches, push whatever remains
     * of
     * each fluid to the dual output hatches via {@code fill}. The helper mutates the array stacks to the remainder, so
     * we iterate the (possibly-depleted) originals.
     */
    @Inject(method = "addFluidOutputs([Lnet/minecraftforge/fluids/FluidStack;)Z", at = @At("TAIL"))
    private void wirelessmehatch$onAddFluidOutputs(FluidStack[] aOutputFluids, CallbackInfoReturnable<Boolean> cir) {
        if (wirelessmehatch$mDualOutputHatches.isEmpty() || aOutputFluids == null) {
            return;
        }
        for (FluidStack fluid : aOutputFluids) {
            wirelessmehatch$dispatchFluid(fluid);
        }
    }

    /**
     * After {@code addOutputPartial(FluidStack)} has dispatched the single fluid to the regular output hatches, push
     * the
     * remainder to the dual output hatches.
     */
    @Inject(method = "addOutputPartial(Lnet/minecraftforge/fluids/FluidStack;)V", at = @At("TAIL"))
    private void wirelessmehatch$onAddOutputPartialFluid(FluidStack aFluid, CallbackInfo ci) {
        if (!wirelessmehatch$mDualOutputHatches.isEmpty()) {
            wirelessmehatch$dispatchFluid(aFluid);
        }
    }

    // ---------------- Helpers ----------------

    /**
     * @return the {@link IDualOutputHatch} behind {@code aTileEntity}, or {@code null} if the tile is null/invalid or
     *         not one of our dual hatches.
     */
    private static IDualOutputHatch wirelessmehatch$asDualOutputHatch(IGregTechTileEntity aTileEntity) {
        if (aTileEntity == null || aTileEntity.getMetaTileEntity() == null) {
            return null;
        }
        return aTileEntity.getMetaTileEntity() instanceof IDualOutputHatch dual ? dual : null;
    }

    /**
     * Apply the controller's texture index and crafting icon to the hatch (mirrors the {@code IDualInputHatch} branch
     * in
     * {@code addToMachineList} which calls {@code updateTexture}/{@code updateCraftingIcon}), then record it. All
     * concrete dual output hatches extend {@link MTEHatch} (via {@code MTEHatchOutput}), so the cast is safe.
     */
    private void wirelessmehatch$register(IDualOutputHatch dual, int aBaseCasingIndex) {
        // clearHatches runs before re-scan, but be defensive against duplicate registration.
        if (!wirelessmehatch$mDualOutputHatches.contains(dual)) {
            wirelessmehatch$mDualOutputHatches.add(dual);
        }
        if (dual instanceof MTEHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.updateCraftingIcon(((MTEMultiBlockBase) (Object) this).getMachineCraftingIcon());
        }
    }

    /**
     * Feed {@code stack} (the running remainder) to every dual output hatch in order, mutating it to whatever could not
     * be stored - matching the per-bus behaviour of {@code ItemEjectionHelper}.
     */
    private void wirelessmehatch$dispatchItem(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
            if (stack.stackSize <= 0) {
                break;
            }
            dual.storePartial(stack, false);
        }
    }

    /**
     * Push {@code fluid} (the running remainder) to every dual output hatch via Forge's {@code fill}. The hatch's
     * {@code fill_default} routes to its ME storage. {@code fill} returns the amount accepted without mutating the
     * passed
     * stack, so we reduce {@code fluid.amount} by the accepted amount and stop once nothing remains - matching
     * {@code FluidEjectionHelper}.
     */
    private void wirelessmehatch$dispatchFluid(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) {
            return;
        }
        for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
            if (fluid.amount <= 0) {
                break;
            }
            int accepted = dual.fill(ForgeDirection.UNKNOWN, fluid, true);
            if (accepted > 0) {
                fluid.amount -= accepted;
            }
        }
    }
}
