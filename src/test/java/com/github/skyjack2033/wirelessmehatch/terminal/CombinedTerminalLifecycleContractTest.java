package com.github.skyjack2033.wirelessmehatch.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.IntInsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.LdcInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.TryCatchBlockNode;
import org.spongepowered.asm.lib.tree.TypeInsnNode;

public class CombinedTerminalLifecycleContractTest {

    private static final String PACKAGE = "com/github/skyjack2033/wirelessmehatch/terminal/";
    private static final String CLIENT_PACKAGE = PACKAGE + "client/";
    private static final String CONTAINER = PACKAGE + "CombinedTerminalContainer";
    private static final String PRIMARY_GUI = PACKAGE + "CombinedTerminalPrimaryGui";
    private static final String GUI_HANDLER = PACKAGE + "CombinedTerminalGuiHandler";
    private static final String CACHE_SLOT = PACKAGE + "PatternCacheSlot";
    private static final String GUI = CLIENT_PACKAGE + "GuiCombinedTerminal";
    private static final String LAYOUT = CLIENT_PACKAGE + "CombinedTerminalLayout";
    private static final String GUI_ACCESSOR = "com/github/skyjack2033/wirelessmehatch/mixin/GuiMEMonitorableAccessor";
    private static final String GUI_SCROLLBAR = "appeng/client/gui/widgets/GuiScrollbar";
    private static final String GUI_PATTERN_TERM = "appeng/client/gui/implementations/GuiPatternTerm";
    private static final String GUI_TEXT_FIELD = "appeng/client/gui/widgets/MEGuiTextField";
    private static final int PINNED_WIDTH = 338;

    @Test
    public void containerInstallsCompleteOpenContext() throws IOException {
        ClassNode container = readClass(CONTAINER);
        MethodNode constructor = findMethod(
            container,
            "<init>",
            "(Lnet/minecraft/entity/player/InventoryPlayer;L" + PACKAGE + "PartCombinedTerminal;)V");
        assertTrue(hasCall(constructor, CONTAINER, "createOpenContext"));
        assertTrue(hasCall(constructor, CONTAINER, "setOpenContext"));

        MethodNode context = findMethod(
            container,
            "createOpenContext",
            "(L" + PACKAGE + "PartCombinedTerminal;)Lappeng/container/ContainerOpenContext;");
        assertTrue(hasCall(context, "appeng/container/ContainerOpenContext", "setWorld"));
        assertTrue(hasCall(context, "appeng/container/ContainerOpenContext", "setX"));
        assertTrue(hasCall(context, "appeng/container/ContainerOpenContext", "setY"));
        assertTrue(hasCall(context, "appeng/container/ContainerOpenContext", "setZ"));
        assertTrue(hasCall(context, "appeng/container/ContainerOpenContext", "setSide"));
    }

    @Test
    public void primaryGuiReopensTheCombinedTerminalHandler() throws IOException {
        ClassNode container = readClass(CONTAINER);
        MethodNode create = findMethod(container, "createPrimaryGui", "()Lappeng/container/PrimaryGui;");
        assertTrue(hasTypeInstruction(create, Opcodes.NEW, PRIMARY_GUI));

        ClassNode primaryGui = readClass(PRIMARY_GUI);
        assertEquals("appeng/container/PrimaryGui", primaryGui.superName);
        MethodNode open = findMethod(primaryGui, "open", "(Lnet/minecraft/entity/player/EntityPlayer;)V");
        assertTrue(
            hasCall(
                open,
                GUI_HANDLER,
                "open",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/tileentity/TileEntity;"
                    + "Lnet/minecraftforge/common/util/ForgeDirection;)V"));
    }

    @Test
    public void cacheSlotsUseADedicatedEncodedPatternClass() throws IOException {
        ClassNode container = readClass(CONTAINER);
        MethodNode constructor = findMethod(
            container,
            "<init>",
            "(Lnet/minecraft/entity/player/InventoryPlayer;L" + PACKAGE + "PartCombinedTerminal;)V");
        assertTrue(hasTypeInstruction(constructor, Opcodes.NEW, CACHE_SLOT));

        ClassNode cacheSlot = readClass(CACHE_SLOT);
        assertEquals("appeng/container/slot/SlotRestrictedInput", cacheSlot.superName);
        MethodNode cacheConstructor = findMethod(
            cacheSlot,
            "<init>",
            "(Lnet/minecraft/inventory/IInventory;IIILnet/minecraft/entity/player/InventoryPlayer;)V");
        assertTrue(
            hasFieldRead(
                cacheConstructor,
                "appeng/container/slot/SlotRestrictedInput$PlacableItemType",
                "ENCODED_PATTERN"));
        assertTrue(hasCall(cacheConstructor, CACHE_SLOT, "setStackLimit", "(I)Lnet/minecraft/inventory/Slot;"));
    }

    @Test
    public void guiPinsNineColumnsBeforeAe2BuildsMonitorSlots() throws IOException {
        MethodNode init = findMethod(readClass(GUI), "initGui", "()V");
        int pin = firstCallIndex(init, GUI_ACCESSOR, "wirelessmehatch$setStandardSize");
        int ae2Init = firstCallIndex(init, "appeng/client/gui/implementations/GuiPatternTerm", "initGui");

        assertTrue(pin >= 0);
        assertTrue(ae2Init > pin);
        assertTrue(countCalls(init, GUI_ACCESSOR, "wirelessmehatch$setStandardSize") >= 2);
    }

    @Test
    public void guiRestoresPinnedWidthWhenAe2InitializationThrows() throws IOException {
        MethodNode init = findMethod(readClass(GUI), "initGui", "()V");

        assertTrue(hasFinallyRestoreAroundCall(init, GUI_PATTERN_TERM, "initGui"));
    }

    @Test
    public void guiRoutesDragEventsToOnlyTheActiveScrollbar() throws IOException {
        ClassNode gui = readClass(GUI);
        MethodNode clicked = findMethod(gui, "mouseClicked", "(III)V");
        MethodNode moved = findMethod(gui, "mouseClickMove", "(IIIJ)V");
        MethodNode clickScrollbar = findMethod(gui, "clickScrollbar", "(L" + GUI_SCROLLBAR + ";II)Z");

        assertTrue(hasFieldWrite(clicked, GUI, "activeScrollbar"));
        assertTrue(hasFieldWrite(clickScrollbar, GUI, "activeScrollbar"));
        assertTrue(hasInstanceFieldRead(moved, GUI, "activeScrollbar"));
        assertEquals(1, countCalls(moved, GUI_SCROLLBAR, "clickMove"));
        assertTrue(
            hasOpcodeBetweenCalls(
                moved,
                GUI_SCROLLBAR,
                "clickMove",
                Opcodes.RETURN,
                GUI_PATTERN_TERM,
                "mouseClickMove"));

        int blockParentDrag = firstBooleanFieldWriteIndex(clicked, GUI, "parentDragAllowed", false);
        int searchClick = firstCallIndex(clicked, GUI_TEXT_FIELD, "mouseClicked");
        int allowParentDrag = firstBooleanFieldWriteIndex(clicked, GUI, "parentDragAllowed", true);
        int lastCustomClick = firstCallIndex(clicked, GUI, "clickInterfacePattern");
        int parentClick = firstCallIndex(clicked, GUI_PATTERN_TERM, "mouseClicked");
        assertTrue(blockParentDrag >= 0 && blockParentDrag < searchClick);
        assertTrue(allowParentDrag > lastCustomClick && allowParentDrag < parentClick);
        assertTrue(
            returnsOnTrueBeforeBooleanFieldWrite(clicked, GUI, "clickInterfacePattern", GUI, "parentDragAllowed"));
        assertTrue(hasInstanceFieldRead(moved, GUI, "parentDragAllowed"));
        assertTrue(fieldTrueBranchesToCall(moved, GUI, "parentDragAllowed", GUI_PATTERN_TERM, "mouseClickMove"));
    }

    @Test
    public void interfaceScrollbarTracksAcceptMouseWheelInput() throws IOException {
        MethodNode wheel = findMethod(readClass(GUI), "mouseWheelEvent", "(III)Z");

        assertTrue(containsTrueCallsWheelOnSameField(wheel, "interfaceListScrollbar"));
        assertTrue(containsTrueCallsWheelOnSameField(wheel, "interfacePatternScrollbar"));
    }

    @Test
    public void guiRefreshWiresInSelectedRowVisibility() throws IOException {
        MethodNode refresh = findMethod(readClass(GUI), "refreshInterfaceSelection", "(Z)V");

        assertTrue(
            callFeedsImmediatelyInto(refresh, LAYOUT, "scrollToReveal", GUI_SCROLLBAR, "setCurrentScroll", "(I)V"));
    }

    private static ClassNode readClass(String internalName) throws IOException {
        String resource = "/" + internalName + ".class";
        try (InputStream stream = CombinedTerminalLifecycleContractTest.class.getResourceAsStream(resource)) {
            assertTrue("Missing class resource " + resource, stream != null);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        }
        throw new AssertionError("Missing method " + name + descriptor);
    }

    private static boolean hasCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name)) return true;
            }
        }
        return false;
    }

    private static boolean hasCall(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc)) return true;
            }
        }
        return false;
    }

    private static boolean hasTypeInstruction(MethodNode method, int opcode, String type) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof TypeInsnNode) {
                TypeInsnNode typeInstruction = (TypeInsnNode) instruction;
                if (opcode == typeInstruction.getOpcode() && type.equals(typeInstruction.desc)) return true;
            }
        }
        return false;
    }

    private static boolean hasFieldRead(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() == Opcodes.GETSTATIC && owner.equals(field.owner) && name.equals(field.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFieldWrite(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() == Opcodes.PUTFIELD && owner.equals(field.owner) && name.equals(field.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasInstanceFieldRead(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() == Opcodes.GETFIELD && owner.equals(field.owner) && name.equals(field.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFinallyRestoreAroundCall(MethodNode method, String owner, String name) {
        int callIndex = firstCallIndex(method, owner, name);
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            int startIndex = method.instructions.indexOf(block.start);
            int endIndex = method.instructions.indexOf(block.end);
            if (block.type == null && callIndex >= startIndex
                && callIndex < endIndex
                && handlerRestoresPinnedWidth(block.handler)) return true;
        }
        return false;
    }

    private static boolean handlerRestoresPinnedWidth(AbstractInsnNode handler) {
        boolean restoresStandardSize = false;
        boolean restoresXSize = false;
        for (AbstractInsnNode instruction = handler; instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (GUI_ACCESSOR.equals(call.owner) && "wirelessmehatch$setStandardSize".equals(call.name)
                    && pushesInt(previousOpcodeInstruction(instruction), PINNED_WIDTH)) {
                    restoresStandardSize = true;
                }
            } else if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() == Opcodes.PUTFIELD && "xSize".equals(field.name)
                    && pushesInt(previousOpcodeInstruction(instruction), PINNED_WIDTH)) restoresXSize = true;
            }
            if (instruction.getOpcode() == Opcodes.ATHROW) return restoresStandardSize && restoresXSize;
        }
        return false;
    }

    private static int firstCallIndex(MethodNode method, String owner, String name) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            if (instructions[index] instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instructions[index];
                if (owner.equals(call.owner) && name.equals(call.name)) return index;
            }
        }
        return -1;
    }

    private static int countCalls(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name)) count++;
            }
        }
        return count;
    }

    private static boolean containsTrueCallsWheelOnSameField(MethodNode method, String fieldName) {
        int contains = firstCallOnFieldIndex(
            method,
            GUI,
            fieldName,
            GUI_SCROLLBAR,
            "contains",
            0,
            method.instructions.size());
        if (contains < 0) return false;

        AbstractInsnNode[] instructions = method.instructions.toArray();
        AbstractInsnNode branchInstruction = nextOpcodeInstruction(instructions[contains]);
        if (!(branchInstruction instanceof JumpInsnNode) || branchInstruction.getOpcode() != Opcodes.IFEQ) return false;
        int falseTarget = method.instructions.indexOf(((JumpInsnNode) branchInstruction).label);
        int wheel = firstCallOnFieldIndex(
            method,
            GUI,
            fieldName,
            GUI_SCROLLBAR,
            "wheel",
            method.instructions.indexOf(branchInstruction) + 1,
            falseTarget);
        if (wheel < 0) return false;

        for (int index = wheel + 1; index < falseTarget; index++) {
            if (instructions[index].getOpcode() == Opcodes.IRETURN
                && pushesInt(previousOpcodeInstruction(instructions[index]), 1)) return true;
        }
        return false;
    }

    private static int firstCallOnFieldIndex(MethodNode method, String fieldOwner, String fieldName, String callOwner,
        String callName, int start, int end) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(instructions.length, end);
        for (int index = safeStart; index < safeEnd; index++) {
            if (!(instructions[index] instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instructions[index];
            if (!callOwner.equals(call.owner) || !callName.equals(call.name)) continue;

            int remaining = 8;
            for (AbstractInsnNode previous = instructions[index].getPrevious(); previous != null
                && remaining-- > 0; previous = previous.getPrevious()) {
                if (previous instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) previous;
                    if (field.getOpcode() == Opcodes.GETFIELD && fieldOwner.equals(field.owner)
                        && fieldName.equals(field.name)) return index;
                }
                if (previous instanceof MethodInsnNode) break;
            }
        }
        return -1;
    }

    private static AbstractInsnNode previousOpcodeInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static AbstractInsnNode nextOpcodeInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }

    private static boolean pushesInt(AbstractInsnNode instruction, int value) {
        if (instruction == null) return false;
        int opcode = instruction.getOpcode();
        if (opcode == Opcodes.ICONST_M1) return value == -1;
        if (opcode >= Opcodes.ICONST_0 && opcode <= Opcodes.ICONST_5) {
            return value == opcode - Opcodes.ICONST_0;
        }
        if (instruction instanceof IntInsnNode) return ((IntInsnNode) instruction).operand == value;
        return instruction instanceof LdcInsnNode && Integer.valueOf(value)
            .equals(((LdcInsnNode) instruction).cst);
    }

    private static int firstBooleanFieldWriteIndex(MethodNode method, String owner, String name, boolean value) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        int expectedValue = value ? Opcodes.ICONST_1 : Opcodes.ICONST_0;
        for (int index = 1; index < instructions.length; index++) {
            if (instructions[index] instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instructions[index];
                if (field.getOpcode() == Opcodes.PUTFIELD && owner.equals(field.owner)
                    && name.equals(field.name)
                    && instructions[index - 1].getOpcode() == expectedValue) return index;
            }
        }
        return -1;
    }

    private static boolean fieldTrueBranchesToCall(MethodNode method, String fieldOwner, String fieldName,
        String callOwner, String callName) {
        int fieldRead = firstFieldReadIndex(method, fieldOwner, fieldName);
        int call = firstCallIndex(method, callOwner, callName);
        if (fieldRead < 0 || call < 0) return false;

        AbstractInsnNode[] instructions = method.instructions.toArray();
        AbstractInsnNode branchInstruction = nextOpcodeInstruction(instructions[fieldRead]);
        if (!(branchInstruction instanceof JumpInsnNode) || branchInstruction.getOpcode() != Opcodes.IFNE) return false;
        int branch = method.instructions.indexOf(branchInstruction);
        int target = method.instructions.indexOf(((JumpInsnNode) branchInstruction).label);
        if (target < 0 || target > call) return false;
        for (int index = branch + 1; index < target; index++) {
            if (instructions[index].getOpcode() == Opcodes.RETURN) return true;
        }
        return false;
    }

    private static boolean returnsOnTrueBeforeBooleanFieldWrite(MethodNode method, String callOwner, String callName,
        String fieldOwner, String fieldName) {
        int call = firstCallIndex(method, callOwner, callName);
        int fieldWrite = firstBooleanFieldWriteIndex(method, fieldOwner, fieldName, true);
        if (call < 0 || fieldWrite < 0) return false;

        AbstractInsnNode[] instructions = method.instructions.toArray();
        AbstractInsnNode branchInstruction = nextOpcodeInstruction(instructions[call]);
        if (!(branchInstruction instanceof JumpInsnNode) || branchInstruction.getOpcode() != Opcodes.IFEQ) return false;
        int branch = method.instructions.indexOf(branchInstruction);
        int target = method.instructions.indexOf(((JumpInsnNode) branchInstruction).label);
        if (target < 0 || target > fieldWrite) return false;
        for (int index = branch + 1; index < target; index++) {
            if (instructions[index].getOpcode() == Opcodes.RETURN) return true;
        }
        return false;
    }

    private static boolean callFeedsImmediatelyInto(MethodNode method, String sourceOwner, String sourceName,
        String targetOwner, String targetName, String targetDescriptor) {
        int source = firstCallIndex(method, sourceOwner, sourceName);
        if (source < 0) return false;
        AbstractInsnNode targetInstruction = nextOpcodeInstruction(method.instructions.get(source));
        if (!(targetInstruction instanceof MethodInsnNode)) return false;
        MethodInsnNode target = (MethodInsnNode) targetInstruction;
        return targetOwner.equals(target.owner) && targetName.equals(target.name)
            && targetDescriptor.equals(target.desc);
    }

    private static int firstFieldReadIndex(MethodNode method, String owner, String name) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            if (instructions[index] instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instructions[index];
                if (field.getOpcode() == Opcodes.GETFIELD && owner.equals(field.owner) && name.equals(field.name)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean hasOpcodeBetweenCalls(MethodNode method, String firstOwner, String firstName, int opcode,
        String secondOwner, String secondName) {
        int firstCall = firstCallIndex(method, firstOwner, firstName);
        int secondCall = firstCallIndex(method, secondOwner, secondName);
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = firstCall + 1; index < secondCall; index++) {
            if (instructions[index].getOpcode() == opcode) return true;
        }
        return false;
    }
}
