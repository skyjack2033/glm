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
            CombinedTerminalLayout.INTERFACE_LIST_WIDTH,
            CombinedTerminalLayout.INTERFACE_SEARCH_HEIGHT,
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

        interfaceSearch.x = guiLeft + CombinedTerminalLayout.INTERFACE_LIST_X;
        interfaceSearch.y = guiTop + CombinedTerminalLayout.interfaceSearchTop(rows);
        interfaceSearch.setMaxStringLength(128);

        cacheScrollbar.setLeft(CombinedTerminalLayout.CACHE_SCROLLBAR_X)
            .setTop(CombinedTerminalLayout.CACHE_Y)
            .setHeight(Math.max(16, rows * CombinedTerminalLayout.SLOT_SIZE - 2));
        interfaceListScrollbar.setLeft(CombinedTerminalLayout.INTERFACE_LIST_SCROLLBAR_X)
            .setTop(CombinedTerminalLayout.interfaceViewportTop(rows))
            .setHeight(interfaceRows * CombinedTerminalLayout.SLOT_SIZE - 2);
        interfacePatternScrollbar.setLeft(CombinedTerminalLayout.INTERFACE_PATTERN_SCROLLBAR_X)
            .setTop(CombinedTerminalLayout.interfaceViewportTop(rows))
            .setHeight(interfaceRows * CombinedTerminalLayout.SLOT_SIZE - 2);

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
            slot.xDisplayPosition = CombinedTerminalLayout.MANUAL_GRID_X
                + manualIndex % 3 * CombinedTerminalLayout.SLOT_SIZE;
            slot.yDisplayPosition = CombinedTerminalLayout.manualGridTop(ySize)
                + manualIndex / 3 * CombinedTerminalLayout.SLOT_SIZE;
            return;
        }
        if (slot == container.getManualOutputSlot()) {
            slot.xDisplayPosition = CombinedTerminalLayout.MANUAL_OUTPUT_X;
            slot.yDisplayPosition = CombinedTerminalLayout.manualOutputTop(ySize);
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
        drawTexturedModalRect(offsetX, offsetY, 0, 0, CombinedTerminalLayout.LEFT_PANEL_WIDTH, 18);
        for (int row = 0; row < rows; row++) {
            drawTexturedModalRect(
                offsetX,
                offsetY + 18 + row * CombinedTerminalLayout.SLOT_SIZE,
                0,
                18,
                CombinedTerminalLayout.LEFT_PANEL_WIDTH,
                18);
        }

        int interfaceTop = CombinedTerminalLayout.interfaceTop(rows);
        int bottomTop = CombinedTerminalLayout.bottomSectionTop(ySize);
        drawPanel(
            offsetX + CombinedTerminalLayout.LEFT_PANEL_WIDTH,
            offsetY,
            WIDTH - CombinedTerminalLayout.LEFT_PANEL_WIDTH,
            interfaceTop);
        drawPanel(offsetX, offsetY + interfaceTop, WIDTH, CombinedTerminalLayout.interfacePanelHeight(ySize, rows));
        drawPanel(
            offsetX + CombinedTerminalLayout.LEFT_PANEL_WIDTH,
            offsetY + bottomTop,
            WIDTH - CombinedTerminalLayout.LEFT_PANEL_WIDTH,
            ySize - bottomTop);
        drawPanel(offsetX + WIDTH + 7, offsetY + 3, 24, 94);

        bindTexture("guis/pattern.png");
        drawTexturedModalRect(
            offsetX,
            offsetY + bottomTop,
            0,
            70,
            CombinedTerminalLayout.LEFT_PANEL_WIDTH,
            ySize - bottomTop);

        drawCacheSlotBackgrounds(offsetX, offsetY);
        drawInterfacePatternBackgrounds(offsetX, offsetY);
        drawManualSlotBackgrounds(offsetX, offsetY);
        drawViewCellSlotBackgrounds(offsetX, offsetY);

        searchField.drawTextBox();
        drawInterfaceSearchFrame();
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
            CombinedTerminalLayout.CACHE_X,
            6,
            GUI_TEXT_COLOR);

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
        fontRendererObj.drawString(
            trim(selectedName, 162),
            CombinedTerminalLayout.INTERFACE_PATTERN_X,
            CombinedTerminalLayout.interfaceTop(rows) + 5,
            selectedColor);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.pattern_encoder"),
            8,
            CombinedTerminalLayout.bottomTitleY(ySize),
            GUI_TEXT_COLOR);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.manual_crafting"),
            CombinedTerminalLayout.RIGHT_CONTENT_X,
            CombinedTerminalLayout.bottomTitleY(ySize),
            GUI_TEXT_COLOR);
        fontRendererObj.drawString(
            ">",
            CombinedTerminalLayout.MANUAL_ARROW_X,
            CombinedTerminalLayout.manualOutputTop(ySize) + 4,
            GUI_TEXT_COLOR);
        fontRendererObj.drawString(
            StatCollector.translateToLocal("gui.wirelessmehatch.combined_terminal.batch"),
            CombinedTerminalLayout.RIGHT_CONTENT_X,
            CombinedTerminalLayout.batchTitleY(ySize),
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
                guiLeft + CombinedTerminalLayout.RIGHT_CONTENT_X + column * 39,
                guiTop + CombinedTerminalLayout.batchButtonTop(ySize, row),
                34,
                18,
                labels[index]);
            scaleButtons[index] = button;
            buttonList.add(button);
        }
        itemSubstitutionButton = new GuiButton(
            0x5B6,
            guiLeft + CombinedTerminalLayout.RIGHT_CONTENT_X,
            guiTop + CombinedTerminalLayout.batchToggleTop(ySize),
            58,
            18,
            "");
        outputSubstitutionButton = new GuiButton(
            0x5B7,
            guiLeft + CombinedTerminalLayout.RIGHT_CONTENT_X + 60,
            guiTop + CombinedTerminalLayout.batchToggleTop(ySize),
            58,
            18,
            "");
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
            .size() + CombinedTerminalLayout.CACHE_COLUMNS
            - 1) / CombinedTerminalLayout.CACHE_COLUMNS;
        cacheScrollbar.setRange(0, Math.max(0, totalRows - visibleRows), 1);

        int firstSlot = cacheScrollbar.getCurrentScroll() * CombinedTerminalLayout.CACHE_COLUMNS;
        int visibleSlots = visibleRows * CombinedTerminalLayout.CACHE_COLUMNS;
        List<AppEngSlot> slots = container.getPatternCacheSlots();
        for (int index = 0; index < slots.size(); index++) {
            AppEngSlot slot = slots.get(index);
            int visibleIndex = index - firstSlot;
            if (visibleIndex >= 0 && visibleIndex < visibleSlots) {
                slot.xDisplayPosition = CombinedTerminalLayout.CACHE_X
                    + visibleIndex % CombinedTerminalLayout.CACHE_COLUMNS * CombinedTerminalLayout.SLOT_SIZE;
                slot.yDisplayPosition = CombinedTerminalLayout.CACHE_Y
                    + visibleIndex / CombinedTerminalLayout.CACHE_COLUMNS * CombinedTerminalLayout.SLOT_SIZE;
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

        int top = CombinedTerminalLayout.interfaceViewportTop(rows);
        int firstRow = interfacePatternScrollbar.getCurrentScroll();
        for (int row = 0; row < interfaceRows; row++) {
            for (int column = 0; column < CombinedTerminalLayout.INTERFACE_PATTERN_COLUMNS; column++) {
                int slot = InterfacePatternGrid.slotAt(selected.getInventorySize(), firstRow, row, column);
                if (slot < 0) return;
                drawSlotBackground(
                    offsetX + CombinedTerminalLayout.INTERFACE_PATTERN_X
                        + column * CombinedTerminalLayout.SLOT_SIZE
                        - 1,
                    offsetY + top + row * CombinedTerminalLayout.SLOT_SIZE - 1);
            }
        }
    }

    private void drawInterfaceList(int mouseX, int mouseY) {
        List<InterfaceTerminalModel.Entry> visible = interfaceModel.getVisibleEntries(interfaceSearch.getText());
        int first = interfaceListScrollbar.getCurrentScroll();
        int top = CombinedTerminalLayout.interfaceViewportTop(rows);
        for (int row = 0; row < interfaceRows && first + row < visible.size(); row++) {
            InterfaceTerminalModel.Entry entry = visible.get(first + row);
            int y = top + row * CombinedTerminalLayout.SLOT_SIZE;
            if (entry.getId() == interfaceModel.getSelectedEntryId()) {
                drawRect(
                    CombinedTerminalLayout.INTERFACE_LIST_X,
                    y,
                    CombinedTerminalLayout.INTERFACE_LIST_X + CombinedTerminalLayout.INTERFACE_LIST_WIDTH,
                    y + 17,
                    0x805A7FA8);
            } else if (mouseX >= CombinedTerminalLayout.INTERFACE_LIST_X
                && mouseX < CombinedTerminalLayout.INTERFACE_LIST_X + CombinedTerminalLayout.INTERFACE_LIST_WIDTH
                && mouseY >= y
                && mouseY < y + 17) {
                    drawRect(
                        CombinedTerminalLayout.INTERFACE_LIST_X,
                        y,
                        CombinedTerminalLayout.INTERFACE_LIST_X + CombinedTerminalLayout.INTERFACE_LIST_WIDTH,
                        y + 17,
                        0x40FFFFFF);
                }

            ItemStack icon = entry.getSelfRepresentation();
            int textX = CombinedTerminalLayout.INTERFACE_LIST_X + 2;
            if (icon != null) {
                drawItem(CombinedTerminalLayout.INTERFACE_LIST_X, y, icon);
                textX += CombinedTerminalLayout.SLOT_SIZE;
            }
            String name = entry.getDisplayName();
            if (name.isEmpty()) name = entry.getX() + ", " + entry.getY() + ", " + entry.getZ();
            fontRendererObj.drawString(
                trim(
                    name,
                    CombinedTerminalLayout.INTERFACE_LIST_X + CombinedTerminalLayout.INTERFACE_LIST_WIDTH - textX - 2),
                textX,
                y + 4,
                interfaceColor(entry));
        }
    }

    private void drawSelectedInterfacePatterns(int mouseX, int mouseY) {
        InterfaceTerminalModel.Entry selected = interfaceModel.getSelectedEntry();
        if (selected == null) return;

        int top = CombinedTerminalLayout.interfaceViewportTop(rows);
        int firstRow = interfacePatternScrollbar.getCurrentScroll();
        for (int row = 0; row < interfaceRows; row++) {
            for (int column = 0; column < CombinedTerminalLayout.INTERFACE_PATTERN_COLUMNS; column++) {
                int slot = InterfacePatternGrid.slotAt(selected.getInventorySize(), firstRow, row, column);
                if (slot < 0) return;
                int x = CombinedTerminalLayout.INTERFACE_PATTERN_X + column * CombinedTerminalLayout.SLOT_SIZE;
                int y = top + row * CombinedTerminalLayout.SLOT_SIZE;
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
        int row = (mouseY - CombinedTerminalLayout.interfaceViewportTop(rows)) / CombinedTerminalLayout.SLOT_SIZE;
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

        int column = (mouseX - CombinedTerminalLayout.INTERFACE_PATTERN_X) / CombinedTerminalLayout.SLOT_SIZE;
        int visibleRow = (mouseY - CombinedTerminalLayout.interfaceViewportTop(rows))
            / CombinedTerminalLayout.SLOT_SIZE;
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
        return x >= CombinedTerminalLayout.LEFT_PANEL_WIDTH && x < WIDTH
            && y >= 18
            && y < CombinedTerminalLayout.interfaceTop(rows);
    }

    private boolean isInInterfaceList(int x, int y) {
        int top = CombinedTerminalLayout.interfaceViewportTop(rows);
        return x >= CombinedTerminalLayout.INTERFACE_LIST_X
            && x < CombinedTerminalLayout.INTERFACE_LIST_X + CombinedTerminalLayout.INTERFACE_LIST_WIDTH
            && y >= top
            && y < top + interfaceRows * CombinedTerminalLayout.SLOT_SIZE;
    }

    private boolean isInInterfacePatterns(int x, int y) {
        int top = CombinedTerminalLayout.interfaceViewportTop(rows);
        return x >= CombinedTerminalLayout.INTERFACE_PATTERN_X
            && x < CombinedTerminalLayout.INTERFACE_PATTERN_X
                + CombinedTerminalLayout.INTERFACE_PATTERN_COLUMNS * CombinedTerminalLayout.SLOT_SIZE
            && y >= top
            && y < top + interfaceRows * CombinedTerminalLayout.SLOT_SIZE;
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

    private void drawInterfaceSearchFrame() {
        int x = interfaceSearch.x - 1;
        int y = interfaceSearch.y - 1;
        int width = interfaceSearch.w + 2;
        int height = interfaceSearch.h + 2;
        drawRect(x, y, x + width, y + height, 0xFF555555);
        drawRect(x + 1, y + 1, x + width, y + height, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF8B8B8B);
    }
}
