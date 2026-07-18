package com.github.skyjack2033.wirelessmehatch.mixin;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.spongepowered.asm.mixin.injection.Inject;

public class WirelessMEHatchMixinPluginTest {

    private static final String ORDINARY_TARGET = "gregtech.api.metatileentity.implementations.MTEMultiBlockBase";
    private static final String STEAM_TARGET = "gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase";
    private static final String GTNL_STEAM_TARGET = "com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase";
    private static final String VOID_TARGET = "gregtech.api.util.VoidProtectionHelper";
    private static final String WIRELESS_KIT_TARGET = "appeng.items.tools.ToolWirelessKit";
    private static final String WIRELESS_KIT_CONTAINER_TARGET = "appeng.container.implementations.ContainerWirelessKit";

    private static final String ORDINARY_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.MTEMultiBlockBaseMixin";
    private static final String STEAM_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.MTESteamMultiBlockBaseMixin";
    private static final String GTNL_STEAM_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.GTNLSteamMultiMachineBaseMixin";
    private static final String VOID_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.VoidProtectionHelperMixin";
    private static final String WIRELESS_KIT_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.ToolWirelessKitMixin";
    private static final String WIRELESS_KIT_CONTAINER_MIXIN = "com.github.skyjack2033.wirelessmehatch.mixin.ContainerWirelessKitMixin";

    private static final String ON_ITEM_USE_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;"
        + "Lnet/minecraft/world/World;IIIIFFF)Z";
    private static final String ON_ITEM_RIGHT_CLICK_DESCRIPTOR = "(Lnet/minecraft/item/ItemStack;"
        + "Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;";
    private static final String PROCESS_COMMAND_DESCRIPTOR = "(Lappeng/helpers/WirelessKitCommand;)V";

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
    public void wirelessKitMixinRequiresExactOnItemUseDescriptor() {
        plugin.preApply(
            WIRELESS_KIT_TARGET,
            node(
                method("onItemUse", ON_ITEM_USE_DESCRIPTOR),
                method("onItemRightClick", ON_ITEM_RIGHT_CLICK_DESCRIPTOR)),
            WIRELESS_KIT_MIXIN,
            null);
    }

    @Test
    public void wirelessKitMixinRejectsMissingOnItemUse() {
        assertMissingAe2(
            WIRELESS_KIT_TARGET,
            node(method("onItemRightClick", ON_ITEM_RIGHT_CLICK_DESCRIPTOR)),
            WIRELESS_KIT_MIXIN,
            "onItemUse",
            ON_ITEM_USE_DESCRIPTOR);
    }

    @Test
    public void wirelessKitMixinRejectsChangedOnItemUseDescriptor() {
        assertMissingAe2(
            WIRELESS_KIT_TARGET,
            node(
                method(
                    "onItemUse",
                    "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;"
                        + "Lnet/minecraft/world/World;IIIIFF)Z"),
                method("onItemRightClick", ON_ITEM_RIGHT_CLICK_DESCRIPTOR)),
            WIRELESS_KIT_MIXIN,
            "onItemUse",
            ON_ITEM_USE_DESCRIPTOR);
    }

    @Test
    public void wirelessKitMixinRejectsMissingOnItemRightClick() {
        assertMissingAe2(
            WIRELESS_KIT_TARGET,
            node(method("onItemUse", ON_ITEM_USE_DESCRIPTOR)),
            WIRELESS_KIT_MIXIN,
            "onItemRightClick",
            ON_ITEM_RIGHT_CLICK_DESCRIPTOR);
    }

    @Test
    public void wirelessKitMixinRejectsChangedOnItemRightClickDescriptor() {
        assertMissingAe2(
            WIRELESS_KIT_TARGET,
            node(
                method("onItemUse", ON_ITEM_USE_DESCRIPTOR),
                method(
                    "onItemRightClick",
                    "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/entity/player/EntityPlayer;)V")),
            WIRELESS_KIT_MIXIN,
            "onItemRightClick",
            ON_ITEM_RIGHT_CLICK_DESCRIPTOR);
    }

    @Test
    public void wirelessKitContainerMixinRequiresExactUpdateAndCommandDescriptors() {
        plugin.preApply(
            WIRELESS_KIT_CONTAINER_TARGET,
            node(method("updateData", "()V"), method("processCommand", PROCESS_COMMAND_DESCRIPTOR)),
            WIRELESS_KIT_CONTAINER_MIXIN,
            null);
    }

    @Test
    public void wirelessKitContainerMixinRejectsMissingUpdateData() {
        assertMissingAe2(
            WIRELESS_KIT_CONTAINER_TARGET,
            node(method("processCommand", PROCESS_COMMAND_DESCRIPTOR)),
            WIRELESS_KIT_CONTAINER_MIXIN,
            "updateData",
            "()V");
    }

    @Test
    public void wirelessKitContainerMixinRejectsChangedUpdateDataDescriptor() {
        assertMissingAe2(
            WIRELESS_KIT_CONTAINER_TARGET,
            node(method("updateData", "(Z)V"), method("processCommand", PROCESS_COMMAND_DESCRIPTOR)),
            WIRELESS_KIT_CONTAINER_MIXIN,
            "updateData",
            "()V");
    }

    @Test
    public void wirelessKitContainerMixinRejectsMissingProcessCommand() {
        assertMissingAe2(
            WIRELESS_KIT_CONTAINER_TARGET,
            node(method("updateData", "()V")),
            WIRELESS_KIT_CONTAINER_MIXIN,
            "processCommand",
            PROCESS_COMMAND_DESCRIPTOR);
    }

    @Test
    public void wirelessKitContainerMixinRejectsChangedProcessCommandDescriptor() {
        assertMissingAe2(
            WIRELESS_KIT_CONTAINER_TARGET,
            node(method("updateData", "()V"), method("processCommand", "(Lappeng/helpers/WirelessKitCommand;)Z")),
            WIRELESS_KIT_CONTAINER_MIXIN,
            "processCommand",
            PROCESS_COMMAND_DESCRIPTOR);
    }

    @Test
    public void mixinConfigurationListsToolWirelessKitMixin() throws IOException {
        String configuration = readResource("/mixins.wirelessmehatch.json");
        assertTrue("Missing ToolWirelessKitMixin entry", configuration.contains("\"ToolWirelessKitMixin\""));
    }

    @Test
    public void mixinConfigurationListsContainerWirelessKitMixin() throws IOException {
        String configuration = readResource("/mixins.wirelessmehatch.json");
        assertTrue("Missing ContainerWirelessKitMixin entry", configuration.contains("\"ContainerWirelessKitMixin\""));
    }

    @Test
    public void toolWirelessKitMixinPinsUseAndRightClickEntryPoints() throws IOException {
        ClassNode mixinClass = readClass(WIRELESS_KIT_MIXIN);

        MethodNode use = findMethod(mixinClass, "wirelessmehatch$handleAssemblyEndpoint");
        AnnotationNode useInject = findAnnotation(use, Type.getDescriptor(Inject.class));
        assertTrue(useInject != null);
        assertTrue(annotationStrings(useInject, "method").contains("onItemUse" + ON_ITEM_USE_DESCRIPTOR));
        assertTrue(annotationBoolean(useInject, "cancellable"));
        assertTrue(annotationInteger(useInject, "require") == 1);

        MethodNode rightClick = findMethod(mixinClass, "wirelessmehatch$clearPendingStateWithNativeMode");
        AnnotationNode rightClickInject = findAnnotation(rightClick, Type.getDescriptor(Inject.class));
        assertTrue(rightClickInject != null);
        assertTrue(
            annotationStrings(rightClickInject, "method")
                .contains("onItemRightClick" + ON_ITEM_RIGHT_CLICK_DESCRIPTOR));
        assertTrue(annotationInteger(rightClickInject, "require") == 1);
        AnnotationNode rightClickAt = firstNestedAnnotation(rightClickInject, "at");
        assertTrue(annotationStrings(rightClickAt, "value").contains("HEAD"));
    }

    @Test
    public void containerWirelessKitMixinUsesExactRequiredInjectionContracts() throws IOException {
        ClassNode mixinClass = readClass(WIRELESS_KIT_CONTAINER_MIXIN);
        assertTrue(findAnnotation(mixinClass, Type.getDescriptor(Mixin.class)) != null);

        MethodNode update = findMethod(mixinClass, "wirelessmehatch$appendAssemblyData");
        AnnotationNode updateInject = findAnnotation(update, Type.getDescriptor(Inject.class));
        assertTrue(updateInject != null);
        assertTrue(annotationStrings(updateInject, "method").contains("updateData()V"));
        assertTrue(annotationInteger(updateInject, "require") == 1);
        AnnotationNode updateAt = firstNestedAnnotation(updateInject, "at");
        assertTrue(annotationStrings(updateAt, "value").contains("INVOKE"));
        assertTrue(annotationStrings(updateAt, "target").contains("Lnet/minecraft/nbt/NBTTagCompound;hasNoTags()Z"));
        assertTrue("BEFORE".equals(annotationEnumName(updateAt, "shift")));

        MethodNode command = findMethod(mixinClass, "wirelessmehatch$handleAssemblyCommand");
        AnnotationNode commandInject = findAnnotation(command, Type.getDescriptor(Inject.class));
        assertTrue(commandInject != null);
        assertTrue(
            annotationStrings(commandInject, "method")
                .contains("processCommand(Lappeng/helpers/WirelessKitCommand;)V"));
        assertTrue(annotationBoolean(commandInject, "cancellable"));
        assertTrue(annotationInteger(commandInject, "require") == 1);
        AnnotationNode commandAt = firstNestedAnnotation(commandInject, "at");
        assertTrue(annotationStrings(commandAt, "value").contains("HEAD"));
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

    private void assertMissingAe2(String targetClassName, ClassNode targetClass, String mixinClassName,
        String methodName, String descriptor) {
        assertMissing(targetClassName, targetClass, mixinClassName, "AE2 rv3-beta-977-GTNH", methodName, descriptor);
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

    private static String readResource(String resource) throws IOException {
        try (InputStream stream = WirelessMEHatchMixinPluginTest.class.getResourceAsStream(resource)) {
            assertTrue("Missing resource " + resource, stream != null);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static AnnotationNode findAnnotation(ClassNode node, String descriptor) {
        for (AnnotationNode annotation : annotations(node)) {
            if (descriptor.equals(annotation.desc)) return annotation;
        }
        return null;
    }

    private static AnnotationNode findAnnotation(MethodNode node, String descriptor) {
        for (AnnotationNode annotation : annotations(node)) {
            if (descriptor.equals(annotation.desc)) return annotation;
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name)) return method;
        }
        throw new AssertionError("Missing method " + name);
    }

    private static List<AnnotationNode> annotations(ClassNode node) {
        List<AnnotationNode> annotations = new java.util.ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        return annotations;
    }

    private static List<AnnotationNode> annotations(MethodNode node) {
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

    private static int annotationInteger(AnnotationNode annotation, String key) {
        Object value = annotationValue(annotation, key);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static boolean annotationBoolean(AnnotationNode annotation, String key) {
        Object value = annotationValue(annotation, key);
        return value instanceof Boolean && (Boolean) value;
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static AnnotationNode firstNestedAnnotation(AnnotationNode annotation, String key) {
        Object value = annotationValue(annotation, key);
        if (value instanceof AnnotationNode) return (AnnotationNode) value;
        if (value instanceof List && !((List<?>) value).isEmpty()) {
            return (AnnotationNode) ((List<Object>) value).get(0);
        }
        throw new AssertionError("Missing nested annotation " + key);
    }

    private static String annotationEnumName(AnnotationNode annotation, String key) {
        Object value = annotationValue(annotation, key);
        return value instanceof String[] ? ((String[]) value)[1] : null;
    }
}
