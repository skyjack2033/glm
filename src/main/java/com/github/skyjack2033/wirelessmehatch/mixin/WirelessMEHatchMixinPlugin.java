package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class WirelessMEHatchMixinPlugin implements IMixinConfigPlugin {

    private static final String GT_VERSION = "5.09.52.594";
    private static final String GTNL_VERSION = "dev-290";
    private static final String MIXIN_PACKAGE = "com.github.skyjack2033.wirelessmehatch.mixin.";
    private static final String ORDINARY_MIXIN = MIXIN_PACKAGE + "MTEMultiBlockBaseMixin";
    private static final String STEAM_MIXIN = MIXIN_PACKAGE + "MTESteamMultiBlockBaseMixin";
    private static final String GTNL_STEAM_MIXIN = MIXIN_PACKAGE + "GTNLSteamMultiMachineBaseMixin";
    private static final String VOID_PROTECTION_MIXIN = MIXIN_PACKAGE + "VoidProtectionHelperMixin";

    private static final String OUTPUT_BUSSES = "getOutputBusses";
    private static final String OUTPUT_BUSSES_DESCRIPTOR = "()Ljava/util/List;";
    private static final String STEAM_OUTPUT = "addSteamBusOutput";
    private static final String STEAM_OUTPUT_DESCRIPTOR = "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z";
    private static final String FLUID_PARALLELS = "calculateMaxFluidParallels";
    private static final String FLUID_PARALLELS_DESCRIPTOR = "()I";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (ORDINARY_MIXIN.equals(mixinClassName)) {
            requireMethod(targetClassName, targetClass, OUTPUT_BUSSES, OUTPUT_BUSSES_DESCRIPTOR);
        } else if (STEAM_MIXIN.equals(mixinClassName)) {
            requireMethod(targetClassName, targetClass, OUTPUT_BUSSES, OUTPUT_BUSSES_DESCRIPTOR);
            requireMethod(targetClassName, targetClass, STEAM_OUTPUT, STEAM_OUTPUT_DESCRIPTOR);
        } else if (GTNL_STEAM_MIXIN.equals(mixinClassName)) {
            requireMethod(
                "GTNL " + GTNL_VERSION,
                targetClassName,
                targetClass,
                OUTPUT_BUSSES,
                OUTPUT_BUSSES_DESCRIPTOR);
        } else if (VOID_PROTECTION_MIXIN.equals(mixinClassName)) {
            requireMethod(targetClassName, targetClass, FLUID_PARALLELS, FLUID_PARALLELS_DESCRIPTOR);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private static void requireMethod(String targetClassName, ClassNode targetClass, String methodName,
        String descriptor) {
        requireMethod("GT " + GT_VERSION, targetClassName, targetClass, methodName, descriptor);
    }

    private static void requireMethod(String dependency, String targetClassName, ClassNode targetClass,
        String methodName, String descriptor) {
        for (MethodNode method : targetClass.methods) {
            if (methodName.equals(method.name) && descriptor.equals(method.desc)) return;
        }

        throw new IllegalStateException(
            "Wireless ME Hatch requires " + dependency
                + " target "
                + targetClassName
                + " to declare "
                + methodName
                + descriptor);
    }
}
