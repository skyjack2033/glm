package com.github.skyjack2033.wirelessmehatch.mixin;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;

public class WirelessMEHatchMixinPluginTest {

    private static final String ORDINARY_TARGET = "gregtech.api.metatileentity.implementations.MTEMultiBlockBase";
    private static final String STEAM_TARGET = "gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase";
    private static final String VOID_TARGET = "gregtech.api.util.VoidProtectionHelper";

    private static final String ORDINARY_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.MTEMultiBlockBaseMixin";
    private static final String STEAM_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.MTESteamMultiBlockBaseMixin";
    private static final String VOID_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.VoidProtectionHelperMixin";

    private final WirelessMEHatchMixinPlugin plugin = new WirelessMEHatchMixinPlugin();

    @Test
    public void ordinaryMixinRequiresOnlyOutputBusGetter() {
        plugin.preApply(ORDINARY_TARGET, node(method("getOutputBusses", "()Ljava/util/List;")), ORDINARY_MIXIN, null);
    }

    @Test
    public void steamMixinRequiresOutputBusGetterAndSteamRegistration() {
        plugin.preApply(
            STEAM_TARGET,
            node(
                method("getOutputBusses", "()Ljava/util/List;"),
                method("addSteamBusOutput", "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z")),
            STEAM_MIXIN,
            null);
    }

    @Test
    public void voidProtectionMixinRequiresOnlyFluidParallelCalculator() {
        plugin.preApply(VOID_TARGET, node(method("calculateMaxFluidParallels", "()I")), VOID_MIXIN, null);
    }

    @Test
    public void ordinaryMixinRejectsMissingOutputBusGetterDescriptor() {
        assertMissing(
            ORDINARY_TARGET,
            node(method("getOutputBusses", "()Ljava/util/Collection;")),
            ORDINARY_MIXIN,
            "getOutputBusses",
            "()Ljava/util/List;");
    }

    @Test
    public void steamMixinRejectsMissingOutputBusGetterWithoutCheckingOrdinaryRequirements() {
        ClassNode target = node(
            method("addSteamBusOutput", "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z"));

        assertMissing(STEAM_TARGET, target, STEAM_MIXIN, "getOutputBusses", "()Ljava/util/List;");
    }

    @Test
    public void steamMixinRejectsMissingRegistrationDescriptor() {
        ClassNode target = node(
            method("getOutputBusses", "()Ljava/util/List;"),
            method("addSteamBusOutput", "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;)Z"));

        assertMissing(
            STEAM_TARGET,
            target,
            STEAM_MIXIN,
            "addSteamBusOutput",
            "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z");
    }

    @Test
    public void voidProtectionMixinRejectsMissingFluidParallelCalculatorDescriptor() {
        assertMissing(
            VOID_TARGET,
            node(method("calculateMaxFluidParallels", "(I)I")),
            VOID_MIXIN,
            "calculateMaxFluidParallels",
            "()I");
    }

    @Test
    public void unrelatedMixinsAreNotValidatedAgainstTaskFiveDescriptors() {
        plugin.preApply("example.Target", node(), "example.UnrelatedMixin", null);
    }

    private void assertMissing(String targetClassName, ClassNode targetClass, String mixinClassName, String methodName,
        String descriptor) {
        try {
            plugin.preApply(targetClassName, targetClass, mixinClassName, null);
            fail("Expected descriptor validation failure");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains("5.09.52.594"));
            assertTrue(
                expected.getMessage()
                    .contains(targetClassName));
            assertTrue(
                expected.getMessage()
                    .contains(methodName));
            assertTrue(
                expected.getMessage()
                    .contains(descriptor));
        }
    }

    private static ClassNode node(MethodNode... methods) {
        ClassNode node = new ClassNode();
        for (MethodNode method : methods) {
            node.methods.add(method);
        }
        return node;
    }

    private static MethodNode method(String name, String descriptor) {
        return new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
    }
}
