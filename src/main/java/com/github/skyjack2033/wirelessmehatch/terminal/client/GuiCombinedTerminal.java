package com.github.skyjack2033.wirelessmehatch.terminal.client;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.github.skyjack2033.wirelessmehatch.mixin.GuiMEMonitorableAccessor;
import com.github.skyjack2033.wirelessmehatch.terminal.CombinedTerminalContainer;
import com.github.skyjack2033.wirelessmehatch.terminal.PartCombinedTerminal;
import com.github.skyjack2033.wirelessmehatch.terminal.PatternCacheBatchCommand;
import com.github.skyjack2033.wirelessmehatch.terminal.PatternCacheFlagState;

import appeng.client.gui.IInterfaceTerminalPostUpdate;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.slot.AppEngSlot;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInterfaceTerminalUpdate.PacketEntry;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;

public final class GuiCombinedTerminal extends GuiPatternTerm implements IInterfaceTerminalPostUpdate {

    private static final int GUI_TEXT_COLOR = 0x404040;
    private static final int WIDTH = CombinedTerminalLayout.WIDTH;

    private static final int CACHE_X = 204;
    private static final int CACHE_Y = 19;
    private static final int CACHE_COLUMNS = 6;

    private static final int INTERFACE_LIST_X = 8;
    private static final int INTERFACE_LIST_WIDTH = 116;
    private static final int INTERFACE_PATTERN_X = 145;
    private static final int INTERFACE_PATTERN_COLUMNS = InterfacePatternGrid.COLUMNS;

    private static final int MANUAL_GRID_X = 213;
    private static final int MANUAL_OUTPUT_X = 307;

    private final CombinedTerminalContainer container;
    private final InterfaceTerminalModel interfaceModel = new InterfaceTerminalModel();
    private final GuiScrollbar cacheScrollbar = new GuiScrollbar();
    private final GuiScrollbar interfaceListScrollbar = new GuiScrollbar();
    private final GuiScrollbar interfacePatternScrollbar = new GuiScrollbar();
    private final MEGuiTextField interfaceSearch;

    private final GuiButton[] scaleButtons = new GuiButton[6];
    private GuiScrollbar activeScrollbar;
    private boolean parentDragAllowed;
    private GuiButton itemSubstitutionButton;
    private GuiButton outputSubstitutionButton;
    private ItemStack hoveredInterfaceStack;
    private int interfaceRows = CombinedTerminalLayout.NORMAL_INTERFACE_ROWS;
    private int interfaceReservedSpace = CombinedTerminalLayout.interfaceReservedSpace(interfaceRows);

    public GuiCombinedTerminal(InventoryPlayer playerInventory, PartCombinedTerminal terminal) {
        super(playerInventory, terminal, new CombinedTerminalContainer(playerInventory, terminal));
        container = (CombinedTerminalContainer) inventorySlots;
        setReservedSpace(CombinedTerminalLayout.reservedSpace(interfaceRows));
        interfaceSearch = new MEGuiTextField(
            INTERFACE_LIST_WIDTH,
            12,
            StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.interface_search")) {

            @Override
            public void onTextChange(String oldText) {
                refreshInterfaceSelection();
            }
        };
    }

    @Override
    public void initGui() {
        interfaceRows = CombinedTerminalLayout.interfaceRows(height);
        interfaceReservedSpace = CombinedTerminalLayout.interfaceReservedSpace(interfaceRows);
        setReservedSpace(CombinedTerminalLayout.reservedSpace(interfaceRows));

        GuiMEMonitorableAccessor monitorable = (GuiMEMonitorableAccessor) (Object) this;
        monitorable.wirelessmehatch$setStandardSize(width);
        xSize = WIDTH;
        try {
            super.initGui();
        } finally {
            monitorable.wirelessmehatch$setStandardSize(WIDTH);
            xSize = WIDTH;
        }
        guiLeft = CombinedTerminalLayout.guiLeft(width);
        repositionViewCellSlots();

        int interfaceTop = getInterfaceTop();
        interfaceSearch.x = guiLeft + INTERFACE_LIST_X;
        interfaceSearch.y = guiTop + interfaceTop + 3;
        interfaceSearch.setMaxStringLength(128);

        cacheScrollbar.setLeft(321)
            .setTop(CACHE_Y)
            .setHeight(Math.max(16, rows * 18 - 2));
        interfaceListScrollbar.setLeft(127)
            .setTop(interfaceTop + 18)
            .setHeight(interfaceRows * 18 - 2);
        interfacePatternScrollbar.setLeft(312)
            .setTop(interfaceTop + 18)
            .setHeight(interfaceRows * 18 - 2);

        initBatchButtons();
        refreshCacheSlots();
        refreshInterfaceSelection();
    }

    @Override
    protected int calculateRowsCount() {
        return CombinedTerminalLayout.isCompact(height) ? 1 : super.calculateRowsCount();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        for (int index = 0; index < scaleButtons.length; index++) {
            if (button == scaleButtons[index]) {
                sendCacheCommand(PatternCacheBatchCommand.values()[index]);
                return;
            }
        }
        if (button == itemSubstitutionButton) {
            boolean enable = container.getItemSubstitutionState()
                .nextValue();
            sendCacheCommand(
                enable ? PatternCacheBatchCommand.ITEM_SUBSTITUTION_ON
                    : PatternCacheBatchCommand.ITEM_SUBSTITUTION_OFF);
            return;
        }
        if (button == outputSubstitutionButton) {
            boolean enable = container.getOutputSubstitutionState()
                .nextValue();
            sendCacheCommand(
                enable ? PatternCacheBatchCommand.OUTPUT_SUBSTITUTION_ON
                    : PatternCacheBatchCommand.OUTPUT_SUBSTITUTION_OFF);
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected int getInputSlotOffsetY() {
        return super.getInputSlotOffsetY() + interfaceReservedSpace;
    }

    @Override
    protected int getOutputSlotOffsetY() {
        return super.getOutputSlotOffsetY() + interfaceReservedSpace;
    }

    @Override
    protected void repositionSlot(AppEngSlot slot) {
        int viewCellIndex = viewCellIndex(slot);
        if (viewCellIndex >= 0) {
            slot.xDisplayPosition = WIDTH + 11;
            slot.yDisplayPosition = 8 + viewCellIndex * 18;
            return;
        }
        int manualIndex = container.getManualCraftingSlots()
            .indexOf(slot);
        if (manualIndex >= 0) {
            slot.xDisplayPosition = MANUAL_GRID_X + manualIndex % 3 * 18;
            slot.yDisplayPosition = ySize - 157 + manualIndex / 3 * 18;
            return;
        }
        if (slot == container.getManualOutputSlot()) {
            slot.xDisplayPosition = MANUAL_OUTPUT_X;
            slot.yDisplayPosition = ySize - 139;
            return;
        }
        if (container.getPatternCacheSlots()
            .contains(slot)) {
            slot.xDisplayPosition = -9000;
            slot.yDisplayPosition = -9000;
            return;
        }
        super.repositionSlot(slot);
    }

    @Override
    protected void drawTitle() {}

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        bindTexture("guis/terminal.png");
        drawTexturedModalRect(offsetX, offsetY, 0, 0, 195, 18);
        for (int row = 0; row < rows; row++) {
            drawTexturedModalRect(offsetX, offsetY + 18 + row * 18, 0, 18, 195, 18);
        }

        int interfaceTop = getInterfaceTop();
        drawPanel(offsetX + 195, offsetY, WIDTH - 195, 18 + rows * 18);
        drawPanel(offsetX, offsetY + interfaceTop, WIDTH, interfaceReservedSpace);
        drawPanel(offsetX + 195, offsetY + ySize - 180, WIDTH - 195, 180);
        drawPanel(offsetX + WIDTH + 7, offsetY + 3, 24, 94);

        bindTexture("guis/pattern.png");
        drawTexturedModalRect(offsetX, offsetY + ySize - 180, 0, 70, 195, 180);

        drawCacheSlotBackgrounds(offsetX, offsetY);
        drawInterfacePatternBackgrounds(offsetX, offsetY);
        drawManualSlotBackgrounds(offsetX, offsetY);
        drawViewCellSlotBackgrounds(offsetX, offsetY);

        searchField.drawTextBox();
        interfaceSearch.drawTextBox();
        updateViewCells();
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        hoveredInterfaceStack = null;
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        drawRect(7, 5, 78, 16, 0xFFC6C6C6);
        fontRendererObj.drawString(
            trim(StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.title"), 69),
            8,
            6,
            GUI_TEXT_COLOR);
        fontRendererObj.drawString(
            trim(StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.cache"), 108),
            CACHE_X,
            6,
            GUI_TEXT_COLOR);

        int interfaceTop = getInterfaceTop();
        InterfaceTerminalModel.Entry selected = interfaceModel.getSelectedEntry();
        String selectedName;
        int selectedColor;
        if (!interfaceModel.isOnline()) {
            selectedName = StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.disconnected");
            selectedColor = 0xAA2020;
        } else {
            selectedName = selected == null
                ? StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.no_interface")
                : selected.getDisplayName();
            selectedColor = interfaceColor(selected);
        }
        fontRendererObj.drawString(trim(selectedName, 162), INTERFACE_PATTERN_X, interfaceTop + 5, selectedColor);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.pattern_encoder"),
            8,
            ySize - 176,
            GUI_TEXT_COLOR);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.manual_crafting"),
            204,
            ySize - 176,
            GUI_TEXT_COLOR);
        fontRendererObj.drawString(">", 291, ySize - 135, GUI_TEXT_COLOR);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.batch"),
            204,
            ySize - 94,
            GUI_TEXT_COLOR);

        drawInterfaceList(mouseX - guiLeft, mouseY - guiTop);
        drawSelectedInterfacePatterns(mouseX - guiLeft, mouseY - guiTop);

        cacheScrollbar.draw(this);
        interfaceListScrollbar.draw(this);
        interfacePatternScrollbar.draw(this);

    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        handleTooltip(mouseX, mouseY, interfaceSearch);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hoveredInterfaceStack != null) renderToolTip(hoveredInterfaceStack, mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        activeScrollbar = null;
        parentDragAllowed = false;
        interfaceSearch.mouseClicked(mouseX, mouseY, button);
        if (interfaceSearch.isMouseIn(mouseX, mouseY)) return;

        int relativeX = mouseX - guiLeft;
        int relativeY = mouseY - guiTop;
        if (clickScrollbar(cacheScrollbar, relativeX, relativeY)) {
            refreshCacheSlots();
            return;
        }
        if (clickScrollbar(interfaceListScrollbar, relativeX, relativeY)) return;
        if (clickScrollbar(interfacePatternScrollbar, relativeX, relativeY)) return;
        if (clickInterfaceList(relativeX, relativeY)) return;
        if (clickInterfacePattern(relativeX, relativeY, button)) return;

        parentDragAllowed = true;
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsedTime) {
        if (activeScrollbar != null) {
            activeScrollbar.clickMove(mouseY - guiTop);
            if (activeScrollbar == cacheScrollbar) refreshCacheSlots();
            return;
        }
        if (!parentDragAllowed) return;
        super.mouseClickMove(mouseX, mouseY, button, elapsedTime);
    }

    @Override
    protected boolean mouseWheelEvent(int mouseX, int mouseY, int wheel) {
        int relativeX = mouseX - guiLeft;
        int relativeY = mouseY - guiTop;
        if (isInCache(relativeX, relativeY)) {
            cacheScrollbar.wheel(wheel);
            refreshCacheSlots();
            return true;
        }
        if (isInInterfaceList(relativeX, relativeY) || interfaceListScrollbar.contains(relativeX, relativeY)) {
            interfaceListScrollbar.wheel(wheel);
            return true;
        }
        if (isInInterfacePatterns(relativeX, relativeY) || interfacePatternScrollbar.contains(relativeX, relativeY)) {
            interfacePatternScrollbar.wheel(wheel);
            return true;
        }
        return super.mouseWheelEvent(mouseX, mouseY, wheel);
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (interfaceSearch.textboxKeyTyped(character, keyCode)) return;
        super.keyTyped(character, keyCode);
    }

    @Override
    public void onGuiClosed() {
        interfaceSearch.setFocused(false);
        super.onGuiClosed();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updateBatchToggleLabels();
    }

    @Override
    public boolean isOverTextField(int mouseX, int mouseY) {
        return interfaceSearch.isMouseIn(mouseX, mouseY) || super.isOverTextField(mouseX, mouseY);
    }

    @Override
    public void setTextFieldValue(String displayName, int mouseX, int mouseY, ItemStack stack) {
        if (interfaceSearch.isMouseIn(mouseX, mouseY)) {
            interfaceSearch.setText(displayName);
        } else {
            super.setTextFieldValue(displayName, mouseX, mouseY, stack);
        }
    }

    @Override
    public void postUpdate(List<PacketEntry> updates, int statusFlags) {
        refreshInterfaceSelection(interfaceModel.applyUpdates(updates, statusFlags));
    }

    private void initBatchButtons() {
        String[] labels = { "x2", "x3", "x5", "/2", "/3", "/5" };
        for (int index = 0; index < scaleButtons.length; index++) {
            int column = index % 3;
            int row = index / 3;
            GuiButton button = new GuiButton(
                0x5B0 + index,
                guiLeft + 204 + column * 39,
                guiTop + ySize - 82 + row * 21,
                34,
                18,
                labels[index]);
            scaleButtons[index] = button;
            buttonList.add(button);
        }
        itemSubstitutionButton = new GuiButton(0x5B6, guiLeft + 204, guiTop + ySize - 40, 58, 18, "");
        outputSubstitutionButton = new GuiButton(0x5B7, guiLeft + 264, guiTop + ySize - 40, 58, 18, "");
        buttonList.add(itemSubstitutionButton);
        buttonList.add(outputSubstitutionButton);
        updateBatchToggleLabels();
    }

    private void updateBatchToggleLabels() {
        itemSubstitutionButton.displayString = checkbox(container.getItemSubstitutionState())
            + StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.item_substitution");
        outputSubstitutionButton.displayString = checkbox(container.getOutputSubstitutionState())
            + StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.output_substitution");
    }

    private static String checkbox(PatternCacheFlagState state) {
        if (state == PatternCacheFlagState.ON) return "[x] ";
        return state == PatternCacheFlagState.MIXED ? "[-] " : "[ ] ";
    }

    private static void sendCacheCommand(PatternCacheBatchCommand command) {
        NetworkHandler.instance.sendToServer(
            new PacketInventoryAction(
                InventoryAction.PICKUP_OR_SET_DOWN,
                0,
                CombinedTerminalContainer.encodeCacheCommand(command)));
    }

    private void refreshCacheSlots() {
        int visibleRows = Math.max(1, rows);
        int totalRows = (container.getPatternCacheSlots()
            .size() + CACHE_COLUMNS
            - 1) / CACHE_COLUMNS;
        cacheScrollbar.setRange(0, Math.max(0, totalRows - visibleRows), 1);

        int firstSlot = cacheScrollbar.getCurrentScroll() * CACHE_COLUMNS;
        int visibleSlots = visibleRows * CACHE_COLUMNS;
        List<AppEngSlot> slots = container.getPatternCacheSlots();
        for (int index = 0; index < slots.size(); index++) {
            AppEngSlot slot = slots.get(index);
            int visibleIndex = index - firstSlot;
            if (visibleIndex >= 0 && visibleIndex < visibleSlots) {
                slot.xDisplayPosition = CACHE_X + visibleIndex % CACHE_COLUMNS * 18;
                slot.yDisplayPosition = CACHE_Y + visibleIndex / CACHE_COLUMNS * 18;
            } else {
                slot.xDisplayPosition = -9000;
                slot.yDisplayPosition = -9000;
            }
        }
    }

    private void refreshInterfaceSelection() {
        refreshInterfaceSelection(false);
    }

    private void refreshInterfaceSelection(boolean selectionChanged) {
        InterfaceTerminalModel.Entry previous = interfaceModel.getSelectedEntry();
        InterfaceTerminalModel.Entry selected = interfaceModel.selectFirstVisible(interfaceSearch.getText());
        List<InterfaceTerminalModel.Entry> visible = interfaceModel.getVisibleEntries(interfaceSearch.getText());
        interfaceListScrollbar.setRange(0, Math.max(0, visible.size() - interfaceRows), 1);
        interfaceListScrollbar.setCurrentScroll(
            CombinedTerminalLayout.scrollToReveal(
                interfaceListScrollbar.getCurrentScroll(),
                visible.indexOf(selected),
                interfaceRows,
                visible.size()));
        if (selectionChanged || previous != selected) interfacePatternScrollbar.setCurrentScroll(0);
        refreshInterfacePatternScrollbar(selected);
    }

    private void refreshInterfacePatternScrollbar(InterfaceTerminalModel.Entry selected) {
        int totalRows = selected == null ? 0 : InterfacePatternGrid.totalRows(selected.getInventorySize());
        interfacePatternScrollbar.setRange(0, Math.max(0, totalRows - interfaceRows), 1);
    }

    private void drawCacheSlotBackgrounds(int offsetX, int offsetY) {
        for (AppEngSlot slot : container.getPatternCacheSlots()) {
            if (slot.xDisplayPosition >= 0) {
                drawSlotBackground(offsetX + slot.xDisplayPosition - 1, offsetY + slot.yDisplayPosition - 1);
            }
        }
    }

    private void drawManualSlotBackgrounds(int offsetX, int offsetY) {
        for (AppEngSlot slot : container.getManualCraftingSlots()) {
            drawSlotBackground(offsetX + slot.xDisplayPosition - 1, offsetY + slot.yDisplayPosition - 1);
        }
        Slot output = container.getManualOutputSlot();
        drawSlotBackground(offsetX + output.xDisplayPosition - 1, offsetY + output.yDisplayPosition - 1);
    }

    private void drawViewCellSlotBackgrounds(int offsetX, int offsetY) {
        for (AppEngSlot slot : container.getCellViewSlots()) {
            if (slot != null) {
                drawSlotBackground(offsetX + slot.xDisplayPosition - 1, offsetY + slot.yDisplayPosition - 1);
            }
        }
    }

    private void drawInterfacePatternBackgrounds(int offsetX, int offsetY) {
        InterfaceTerminalModel.Entry selected = interfaceModel.getSelectedEntry();
        if (selected == null) return;

        int top = getInterfaceTop() + 18;
        int firstRow = interfacePatternScrollbar.getCurrentScroll();
        for (int row = 0; row < interfaceRows; row++) {
            for (int column = 0; column < INTERFACE_PATTERN_COLUMNS; column++) {
                int slot = InterfacePatternGrid.slotAt(selected.getInventorySize(), firstRow, row, column);
                if (slot < 0) return;
                drawSlotBackground(offsetX + INTERFACE_PATTERN_X + column * 18 - 1, offsetY + top + row * 18 - 1);
            }
        }
    }

    private void drawInterfaceList(int mouseX, int mouseY) {
        List<InterfaceTerminalModel.Entry> visible = interfaceModel.getVisibleEntries(interfaceSearch.getText());
        int first = interfaceListScrollbar.getCurrentScroll();
        int top = getInterfaceTop() + 18;
        for (int row = 0; row < interfaceRows && first + row < visible.size(); row++) {
            InterfaceTerminalModel.Entry entry = visible.get(first + row);
            int y = top + row * 18;
            if (entry.getId() == interfaceModel.getSelectedEntryId()) {
                drawRect(INTERFACE_LIST_X, y, INTERFACE_LIST_X + INTERFACE_LIST_WIDTH, y + 17, 0x805A7FA8);
            } else if (mouseX >= INTERFACE_LIST_X && mouseX < INTERFACE_LIST_X + INTERFACE_LIST_WIDTH
                && mouseY >= y
                && mouseY < y + 17) {
                    drawRect(INTERFACE_LIST_X, y, INTERFACE_LIST_X + INTERFACE_LIST_WIDTH, y + 17, 0x40FFFFFF);
                }

            ItemStack icon = entry.getSelfRepresentation();
            int textX = INTERFACE_LIST_X + 2;
            if (icon != null) {
                drawItem(INTERFACE_LIST_X, y, icon);
                textX += 18;
            }
            String name = entry.getDisplayName();
            if (name.isEmpty()) name = entry.getX() + ", " + entry.getY() + ", " + entry.getZ();
            fontRendererObj.drawString(
                trim(name, INTERFACE_LIST_X + INTERFACE_LIST_WIDTH - textX - 2),
                textX,
                y + 4,
                interfaceColor(entry));
        }
    }

    private void drawSelectedInterfacePatterns(int mouseX, int mouseY) {
        InterfaceTerminalModel.Entry selected = interfaceModel.getSelectedEntry();
        if (selected == null) return;

        int top = getInterfaceTop() + 18;
        int firstRow = interfacePatternScrollbar.getCurrentScroll();
        for (int row = 0; row < interfaceRows; row++) {
            for (int column = 0; column < INTERFACE_PATTERN_COLUMNS; column++) {
                int slot = InterfacePatternGrid.slotAt(selected.getInventorySize(), firstRow, row, column);
                if (slot < 0) return;
                int x = INTERFACE_PATTERN_X + column * 18;
                int y = top + row * 18;
                ItemStack stack = InterfacePatternDisplay.resolve(selected.getStack(slot));
                if (stack != null) drawItem(x, y, stack);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    drawRect(x, y, x + 16, y + 16, 0x80FFFFFF);
                    hoveredInterfaceStack = stack;
                }
            }
        }
    }

    private boolean clickInterfaceList(int mouseX, int mouseY) {
        if (!isInInterfaceList(mouseX, mouseY)) return false;
        int row = (mouseY - getInterfaceTop() - 18) / 18;
        List<InterfaceTerminalModel.Entry> visible = interfaceModel.getVisibleEntries(interfaceSearch.getText());
        int index = interfaceListScrollbar.getCurrentScroll() + row;
        if (index < 0 || index >= visible.size()) return true;
        interfaceModel.select(
            visible.get(index)
                .getId());
        interfacePatternScrollbar.setCurrentScroll(0);
        refreshInterfacePatternScrollbar(interfaceModel.getSelectedEntry());
        return true;
    }

    private boolean clickInterfacePattern(int mouseX, int mouseY, int button) {
        if (!isInInterfacePatterns(mouseX, mouseY) || button < 0 || button > 2) return false;
        InterfaceTerminalModel.Entry selected = interfaceModel.getSelectedEntry();
        if (selected == null) return true;

        int column = (mouseX - INTERFACE_PATTERN_X) / 18;
        int visibleRow = (mouseY - getInterfaceTop() - 18) / 18;
        int slot = InterfacePatternGrid
            .slotAt(selected.getInventorySize(), interfacePatternScrollbar.getCurrentScroll(), visibleRow, column);
        if (slot < 0) return true;

        InventoryAction action;
        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            action = InventoryAction.MOVE_REGION;
            slot = 0;
        } else if (GuiScreen.isShiftKeyDown()) {
            action = InventoryAction.SHIFT_CLICK;
        } else if (button == 2) {
            action = InventoryAction.CREATIVE_DUPLICATE;
        } else {
            action = InventoryAction.PICKUP_OR_SET_DOWN;
        }
        NetworkHandler.instance.sendToServer(
            new PacketInventoryAction(
                action,
                slot,
                CombinedTerminalContainer.encodeInterfaceEntryId(selected.getId())));
        return true;
    }

    private boolean clickScrollbar(GuiScrollbar scrollbar, int mouseX, int mouseY) {
        if (!scrollbar.contains(mouseX, mouseY)) return false;
        activeScrollbar = scrollbar;
        scrollbar.click(this, mouseX, mouseY);
        return true;
    }

    private boolean isInCache(int x, int y) {
        return x >= 195 && x < WIDTH && y >= 18 && y < 18 + rows * 18;
    }

    private boolean isInInterfaceList(int x, int y) {
        int top = getInterfaceTop() + 18;
        return x >= INTERFACE_LIST_X && x < INTERFACE_LIST_X + INTERFACE_LIST_WIDTH
            && y >= top
            && y < top + interfaceRows * 18;
    }

    private boolean isInInterfacePatterns(int x, int y) {
        int top = getInterfaceTop() + 18;
        return x >= INTERFACE_PATTERN_X && x < INTERFACE_PATTERN_X + INTERFACE_PATTERN_COLUMNS * 18
            && y >= top
            && y < top + interfaceRows * 18;
    }

    private int getInterfaceTop() {
        return 18 + rows * 18;
    }

    private int viewCellIndex(AppEngSlot slot) {
        AppEngSlot[] viewCells = container.getCellViewSlots();
        for (int index = 0; index < viewCells.length; index++) {
            if (viewCells[index] == slot) return index;
        }
        return -1;
    }

    private void repositionViewCellSlots() {
        AppEngSlot[] viewCells = container.getCellViewSlots();
        for (int index = 0; index < viewCells.length; index++) {
            AppEngSlot slot = viewCells[index];
            if (slot == null) continue;
            slot.xDisplayPosition = WIDTH + 11;
            slot.yDisplayPosition = 8 + index * 18;
        }
    }

    private String trim(String value, int width) {
        return fontRendererObj.trimStringToWidth(value == null ? "" : value, Math.max(0, width));
    }

    private static int interfaceColor(InterfaceTerminalModel.Entry entry) {
        return entry == null || !entry.isOnline() ? 0x777777 : GUI_TEXT_COLOR;
    }

    private void drawPanel(int x, int y, int width, int height) {
        drawRect(x, y, x + width, y + height, 0xFFC6C6C6);
        drawRect(x, y, x + width, y + 1, 0xFFFFFFFF);
        drawRect(x, y, x + 1, y + height, 0xFFFFFFFF);
        drawRect(x, y + height - 1, x + width, y + height, 0xFF555555);
        drawRect(x + width - 1, y, x + width, y + height, 0xFF555555);
    }

    private void drawSlotBackground(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF555555);
        drawRect(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
