package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class WirelessMEHatchMixinPlugin implements IMixinConfigPlugin {

    private static final String GT_VERSION = "5.09.52.594";
    private static final String GTNL_VERSION = "dev-290";
    private static final String AE2_VERSION = "rv3-beta-977-GTNH";
    private static final String MIXIN_PACKAGE = "com.github.skyjack2033.wirelessmehatch.mixin.";
    private static final String ORDINARY_MIXIN = MIXIN_PACKAGE + "MTEMultiBlockBaseMixin";
    private static final String STEAM_MIXIN = MIXIN_PACKAGE + "MTESteamMultiBlockBaseMixin";
    private static final String GTNL_STEAM_MIXIN = MIXIN_PACKAGE + "GTNLSteamMultiMachineBaseMixin";
    private static final String VOID_PROTECTION_MIXIN = MIXIN_PACKAGE + "VoidProtectionHelperMixin";
    private static final String WIRELESS_KIT_MIXIN = MIXIN_PACKAGE + "ToolWirelessKitMixin";
    private static final String WIRELESS_KIT_CONTAINER_MIXIN = MIXIN_PACKAGE + "ContainerWirelessKitMixin";
    private static final String COMBINED_TERMINAL_ACTIVATION_MIXIN = MIXIN_PACKAGE + "CombinedTerminalActivationMixin";
    private static final String INTERFACE_TERMINAL_ACCESSOR = MIXIN_PACKAGE + "ContainerInterfaceTerminalAccessor";
    private static final String INTERFACE_TRACKER_ACCESSOR = MIXIN_PACKAGE + "InterfaceTerminalInvTrackerAccessor";
    private static final String GUI_MONITOR_ACCESSOR = MIXIN_PACKAGE + "GuiMEMonitorableAccessor";

    private static final String OUTPUT_BUSSES = "getOutputBusses";
    private static final String OUTPUT_BUSSES_DESCRIPTOR = "()Ljava/util/List;";
    private static final String STEAM_OUTPUT = "addSteamBusOutput";
    private static final String STEAM_OUTPUT_DESCRIPTOR = "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z";
    private static final String FLUID_PARALLELS = "calculateMaxFluidParallels";
    private static final String FLUID_PARALLELS_DESCRIPTOR = "()I";
    private static final String ON_ITEM_USE = "onItemUse";
    private static final String ON_ITEM_USE_SRG = "func_77648_a";
    private static final String ON_ITEM_USE_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;"
        + "Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;IIIIFFF)Z";
    private static final String ON_ITEM_RIGHT_CLICK = "onItemRightClick";
    private static final String ON_ITEM_RIGHT_CLICK_SRG = "func_77659_a";
    private static final String ON_ITEM_RIGHT_CLICK_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;"
        + "Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;";
    private static final String UPDATE_DATA = "updateData";
    private static final String UPDATE_DATA_DESCRIPTOR = "()V";
    private static final String PROCESS_COMMAND = "processCommand";
    private static final String PROCESS_COMMAND_DESCRIPTOR = "(Lappeng/helpers/WirelessKitCommand;)V";

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
        } else if (WIRELESS_KIT_MIXIN.equals(mixinClassName)) {
            requireAnyMethod(
                "AE2 " + AE2_VERSION,
                targetClassName,
                targetClass,
                ON_ITEM_USE_DESCRIPTOR,
                ON_ITEM_USE,
                ON_ITEM_USE_SRG);
            requireAnyMethod(
                "AE2 " + AE2_VERSION,
                targetClassName,
                targetClass,
                ON_ITEM_RIGHT_CLICK_DESCRIPTOR,
                ON_ITEM_RIGHT_CLICK,
                ON_ITEM_RIGHT_CLICK_SRG);
        } else if (WIRELESS_KIT_CONTAINER_MIXIN.equals(mixinClassName)) {
            requireMethod("AE2 " + AE2_VERSION, targetClassName, targetClass, UPDATE_DATA, UPDATE_DATA_DESCRIPTOR);
            requireMethod(
                "AE2 " + AE2_VERSION,
                targetClassName,
                targetClass,
                PROCESS_COMMAND,
                PROCESS_COMMAND_DESCRIPTOR);
        } else if (COMBINED_TERMINAL_ACTIVATION_MIXIN.equals(mixinClassName)) {
            requireMethod(
                "AE2 " + AE2_VERSION,
                targetClassName,
                targetClass,
                "onPartActivate",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/Vec3;)Z");
        } else if (INTERFACE_TERMINAL_ACCESSOR.equals(mixinClassName)) {
            requireField("AE2 " + AE2_VERSION, targetClassName, targetClass, "trackedById", "Ljava/util/Map;");
        } else if (INTERFACE_TRACKER_ACCESSOR.equals(mixinClassName)) {
            requireField(
                "AE2 " + AE2_VERSION,
                targetClassName,
                targetClass,
                "patterns",
                "Lnet/minecraft/inventory/IInventory;");
        } else if (GUI_MONITOR_ACCESSOR.equals(mixinClassName)) {
            requireField("AE2 " + AE2_VERSION, targetClassName, targetClass, "standardSize", "I");
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

    private static void requireAnyMethod(String dependency, String targetClassName, ClassNode targetClass,
        String descriptor, String... methodNames) {
        for (MethodNode method : targetClass.methods) {
            if (!descriptor.equals(method.desc)) continue;
            for (String methodName : methodNames) {
                if (methodName.equals(method.name)) return;
            }
        }

        throw new IllegalStateException(
            "Wireless ME Hatch requires " + dependency
                + " target "
                + targetClassName
                + " to declare one of ["
                + String.join(", ", methodNames)
                + "]"
                + descriptor);
    }

    private static void requireField(String dependency, String targetClassName, ClassNode targetClass, String fieldName,
        String descriptor) {
        for (FieldNode field : targetClass.fields) {
            if (fieldName.equals(field.name) && descriptor.equals(field.desc)) return;
        }

        throw new IllegalStateException(
            "Wireless ME Hatch requires " + dependency
                + " target "
                + targetClassName
                + " to declare "
                + fieldName
                + ":"
                + descriptor);
    }
}
