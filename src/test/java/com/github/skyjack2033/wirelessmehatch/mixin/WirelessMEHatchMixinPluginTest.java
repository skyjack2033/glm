package com.github.skyjack2033.wirelessmehatch.mixin;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

public class WirelessMEHatchMixinPluginTest {

    private static final String ORDINARY_TARGET = "gregtech.api.metatileentity.implementations.MTEMultiBlockBase";
    private static final String STEAM_TARGET = "gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase";
    private static final String GTNL_STEAM_TARGET = "com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase";
    private static final String VOID_TARGET = "gregtech.api.util.VoidProtectionHelper";

    private static final String ORDINARY_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.MTEMultiBlockBaseMixin";
    private static final String STEAM_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.MTESteamMultiBlockBaseMixin";
    private static final String GTNL_STEAM_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.GTNLSteamMultiMachineBaseMixin";
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
    public void gtnlSteamMixinRequiresOutputBusGetter() {
        plugin
            .preApply(GTNL_STEAM_TARGET, node(method("getOutputBusses", "()Ljava/util/List;")), GTNL_STEAM_MIXIN, null);
    }

    @Test
    public void gtnlSteamMixinRejectsChangedOutputBusGetterDescriptor() {
        assertMissing(
            GTNL_STEAM_TARGET,
            node(method("getOutputBusses", "()Ljava/util/Collection;")),
            GTNL_STEAM_MIXIN,
            "GTNL dev-290",
            "getOutputBusses",
            "()Ljava/util/List;");
    }

    @Test
    public void gtnlSteamMixinUsesOptionalStringTarget() throws IOException {
        ClassNode mixinClass = readClass(GTNL_STEAM_MIXIN);
        assertTrue(findAnnotation(mixinClass, Type.getDescriptor(Pseudo.class)) != null);

        AnnotationNode mixin = findAnnotation(mixinClass, Type.getDescriptor(Mixin.class));
        assertTrue(mixin != null);
        assertTrue(annotationStrings(mixin, "targets").contains(GTNL_STEAM_TARGET));
        assertTrue(annotationStrings(mixin, "value").isEmpty());
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
        assertMissing(targetClassName, targetClass, mixinClassName, "5.09.52.594", methodName, descriptor);
    }

    private void assertMissing(String targetClassName, ClassNode targetClass, String mixinClassName, String dependency,
        String methodName, String descriptor) {
        try {
            plugin.preApply(targetClassName, targetClass, mixinClassName, null);
            fail("Expected descriptor validation failure");
        } catch (IllegalStateException expected) {
            assertTrue(
                expected.getMessage()
                    .contains(dependency));
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

    private static ClassNode readClass(String className) throws IOException {
        String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream stream = WirelessMEHatchMixinPluginTest.class.getResourceAsStream(resource)) {
            assertTrue("Missing class resource " + resource, stream != null);
            ClassNode node = new ClassNode();
            new ClassReader(stream)
                .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static AnnotationNode findAnnotation(ClassNode node, String descriptor) {
        for (AnnotationNode annotation : annotations(node)) {
            if (descriptor.equals(annotation.desc)) return annotation;
        }
        return null;
    }

    private static List<AnnotationNode> annotations(ClassNode node) {
        List<AnnotationNode> annotations = new java.util.ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        return annotations;
    }

    @SuppressWarnings("unchecked")
    private static List<String> annotationStrings(AnnotationNode annotation, String key) {
        if (annotation.values == null) return Collections.emptyList();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                Object value = annotation.values.get(i + 1);
                return value instanceof List ? (List<String>) value : Arrays.asList((String) value);
            }
        }
        return Collections.emptyList();
    }
}
