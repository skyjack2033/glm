package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.Test;

import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.WireLessToolHelper.BindResult;
import appeng.helpers.WirelessKitCommand.PinType;
import appeng.helpers.WirelessKitCommand.SubCommand;
import appeng.helpers.WirelessKitCommand.WirelessKitCommands;
import appeng.helpers.WirelessToolDataObject;

public class WirelessKitSuperModeHandlerTest {

    private static final String HANDLER_CLASS = "com.github.skyjack2033.wirelessmehatch.me.WirelessKitSuperModeHandler";

    private static final DimensionalCoord NETWORK_A = coord(100, 64, 100);
    private static final DimensionalCoord NETWORK_B = coord(200, 64, 200);
    private static final WirelessToolDataObject RED_CONNECTOR = data(
        NETWORK_A,
        "Red connector",
        coord(101, 64, 100),
        AEColor.Red,
        false);
    private static final WirelessToolDataObject ASSEMBLY = data(
        NETWORK_A,
        "Wireless Unified Output Assembly",
        coord(102, 64, 100),
        AEColor.Transparent,
        false);
    private static final WirelessToolDataObject BOUND_CONNECTOR = data(
        NETWORK_A,
        "Bound connector",
        coord(106, 64, 100),
        AEColor.Red,
        false,
        true);
    private static final WirelessToolDataObject BOUND_ASSEMBLY = data(
        NETWORK_A,
        "Bound Wireless Unified Output Assembly",
        coord(105, 64, 100),
        AEColor.Transparent,
        false,
        true);
    private static final WirelessToolDataObject RED_HUB = data(
        NETWORK_A,
        "Red hub",
        coord(103, 64, 100),
        AEColor.Red,
        true);
    private static final WirelessToolDataObject BLUE_CONNECTOR = data(
        NETWORK_A,
        "Blue connector",
        coord(104, 64, 100),
        AEColor.Blue,
        false);
    private static final WirelessToolDataObject OTHER_NETWORK_CONNECTOR = data(
        NETWORK_B,
        "Other connector",
        coord(201, 64, 200),
        AEColor.Red,
        false);
    private static final List<WirelessToolDataObject> DATA = Arrays
        .asList(RED_CONNECTOR, ASSEMBLY, RED_HUB, BLUE_CONNECTOR, OTHER_NETWORK_CONNECTOR);

    @Test
    public void singleSelectionUsesExactCoordinate() throws Exception {
        SubCommand selection = command(PinType.SINGLE);
        selection.setCoord(ASSEMBLY.cord);

        assertEquals(Collections.singletonList(ASSEMBLY), expand(selection));
    }

    @Test
    public void networkConnectorGroupIncludesNonHubAssemblyData() throws Exception {
        SubCommand selection = command(PinType.NETWORK);
        selection.setNetworkPos(NETWORK_A);
        selection.includeConnectors();

        assertEquals(Arrays.asList(RED_CONNECTOR, ASSEMBLY, BLUE_CONNECTOR), expand(selection));
    }

    @Test
    public void networkHubGroupExcludesConnectorAndAssemblyData() throws Exception {
        SubCommand selection = command(PinType.NETWORK);
        selection.setNetworkPos(NETWORK_A);
        selection.includeHubs();

        assertEquals(Collections.singletonList(RED_HUB), expand(selection));
    }

    @Test
    public void networkGroupHonorsBothIncludeFlags() throws Exception {
        SubCommand selection = command(PinType.NETWORK);
        selection.setNetworkPos(NETWORK_A);
        selection.includeConnectors();
        selection.includeHubs();

        assertEquals(Arrays.asList(RED_CONNECTOR, ASSEMBLY, RED_HUB, BLUE_CONNECTOR), expand(selection));
    }

    @Test
    public void colorGroupFiltersWithinNetworkAndEndpointKinds() throws Exception {
        SubCommand redSelection = command(PinType.COLOR);
        redSelection.setNetworkPos(NETWORK_A);
        redSelection.setColor(AEColor.Red);
        redSelection.includeConnectors();
        redSelection.includeHubs();
        assertEquals(Arrays.asList(RED_CONNECTOR, RED_HUB), expand(redSelection));

        SubCommand assemblySelection = command(PinType.COLOR);
        assemblySelection.setNetworkPos(NETWORK_A);
        assemblySelection.setColor(AEColor.Transparent);
        assemblySelection.includeConnectors();
        assertEquals(Collections.singletonList(ASSEMBLY), expand(assemblySelection));
    }

    @Test
    public void commandFilteringUsesBoundStateAndKeepsNativeBindAnchors() throws Exception {
        List<WirelessToolDataObject> selected = Arrays.asList(RED_CONNECTOR, BOUND_CONNECTOR, ASSEMBLY, BOUND_ASSEMBLY);
        Predicate<DimensionalCoord> isAssembly = coordinate -> coordinate.equals(ASSEMBLY.cord)
            || coordinate.equals(BOUND_ASSEMBLY.cord);

        assertEquals(
            Arrays.asList(RED_CONNECTOR, BOUND_CONNECTOR, ASSEMBLY),
            filterForCommand(selected, WirelessKitCommands.BIND, isAssembly));
        assertEquals(
            Arrays.asList(BOUND_CONNECTOR, BOUND_ASSEMBLY),
            filterForCommand(selected, WirelessKitCommands.UNBIND, isAssembly));
    }

    @Test
    public void pairAvailabilityFiltersNativePairsButKeepsUnavailableNativeAssemblyAnchors() throws Exception {
        assertEquals(BindResult.INVALID_SOURCE, checkPairAvailability(false, false, false, true));
        assertEquals(BindResult.INVALID_TARGET, checkPairAvailability(false, true, false, false));
        assertEquals(BindResult.INVALID_SOURCE, checkPairAvailability(true, false, false, false));
        assertEquals(BindResult.INVALID_TARGET, checkPairAvailability(false, false, true, false));

        assertNull(checkPairAvailability(false, false, true, true));
        assertNull(checkPairAvailability(true, true, false, false));
    }

    @Test
    public void assemblyDataNeverReportsConnectedWithoutATargetCoordinate() throws Exception {
        DimensionalCoord position = coord(110, 64, 100);
        WirelessToolDataObject unbound = createAssemblyData(NETWORK_A, "Assembly", position, null, 0);
        assertFalse(unbound.isConnected);
        assertTrue(unbound.targets.isEmpty());
        assertEquals(1, unbound.slots);

        DimensionalCoord target = coord(111, 64, 100);
        WirelessToolDataObject bound = createAssemblyData(NETWORK_A, "Assembly", position, target, 1);
        assertTrue(bound.isConnected);
        assertEquals(Collections.singletonList(target), bound.targets);
        assertEquals(0, bound.slots);
    }

    @Test
    public void connectorAnchorsAreOrderedBeforeAssemblyAnchors() throws Exception {
        DimensionalCoord assemblyA = coord(301, 64, 300);
        DimensionalCoord connectorA = coord(302, 64, 300);
        DimensionalCoord assemblyB = coord(303, 64, 300);
        DimensionalCoord connectorB = coord(304, 64, 300);
        Predicate<DimensionalCoord> isAssembly = coordinate -> coordinate.equals(assemblyA)
            || coordinate.equals(assemblyB);

        assertEquals(
            Arrays.asList(connectorA, connectorB, assemblyA, assemblyB),
            prioritizeAnchors(Arrays.asList(assemblyA, connectorA, assemblyB, connectorB), isAssembly));
    }

    @Test
    public void pureAe2RowsFallThroughAndMixedRowsAreHandled() throws Exception {
        Predicate<DimensionalCoord> isAssembly = coordinate -> coordinate.equals(ASSEMBLY.cord);

        assertFalse(
            shouldHandleCommand(
                Collections.singletonList(RED_CONNECTOR),
                Collections.singletonList(RED_HUB),
                isAssembly));
        assertTrue(
            shouldHandleCommand(
                Arrays.asList(RED_CONNECTOR, ASSEMBLY),
                Collections.singletonList(RED_HUB),
                isAssembly));
    }

    @Test
    public void anchorDeduplicationDropsInvalidCurrentDimensionAndKeepsOtherDimensions() throws Exception {
        DimensionalCoord assemblyA = coord(301, 64, 300);
        DimensionalCoord connectorA = coord(302, 64, 300);
        DimensionalCoord invalid = coord(303, 64, 300);
        DimensionalCoord otherDimension = new DimensionalCoord(304, 64, 300, 1);
        Object sharedGrid = new Object();
        Map<DimensionalCoord, Object> grids = new HashMap<>();
        grids.put(assemblyA, sharedGrid);
        grids.put(connectorA, sharedGrid);
        Predicate<DimensionalCoord> isAssembly = coordinate -> coordinate.equals(assemblyA);

        assertEquals(
            Arrays.asList(connectorA, otherDimension),
            deduplicateAnchors(Arrays.asList(assemblyA, invalid, otherDimension, connectorA), 0, isAssembly, grids));
    }

    private static SubCommand command(PinType type) {
        SubCommand command = new SubCommand();
        command.setGroupBy(type);
        return command;
    }

    @SuppressWarnings("unchecked")
    private static List<WirelessToolDataObject> expand(SubCommand selection) throws Exception {
        Class<?> handler = Class.forName(HANDLER_CLASS);
        Method method = handler.getMethod("expandSelection", SubCommand.class, List.class);
        try {
            return (List<WirelessToolDataObject>) method.invoke(null, selection, DATA);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<WirelessToolDataObject> filterForCommand(List<WirelessToolDataObject> selected,
        WirelessKitCommands command, Predicate<DimensionalCoord> isAssembly) throws Exception {
        Class<?> handler = Class.forName(HANDLER_CLASS);
        Method method = handler.getMethod("filterForCommand", List.class, WirelessKitCommands.class, Predicate.class);
        try {
            return (List<WirelessToolDataObject>) method.invoke(null, selected, command, isAssembly);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }

    private static WirelessToolDataObject createAssemblyData(DimensionalCoord network, String name,
        DimensionalCoord position, DimensionalCoord target, int channels) throws Exception {
        Class<?> handler = Class.forName(HANDLER_CLASS);
        Method method = handler.getMethod(
            "createAssemblyData",
            DimensionalCoord.class,
            String.class,
            DimensionalCoord.class,
            DimensionalCoord.class,
            int.class);
        return (WirelessToolDataObject) method.invoke(null, network, name, position, target, channels);
    }

    @SuppressWarnings("unchecked")
    private static List<DimensionalCoord> prioritizeAnchors(List<DimensionalCoord> anchors,
        Predicate<DimensionalCoord> isAssembly) throws Exception {
        Class<?> handler = Class.forName(HANDLER_CLASS);
        Method method = handler.getMethod("prioritizeAnchors", List.class, Predicate.class);
        return (List<DimensionalCoord>) method.invoke(null, anchors, isAssembly);
    }

    private static boolean shouldHandleCommand(List<WirelessToolDataObject> sources,
        List<WirelessToolDataObject> targets, Predicate<DimensionalCoord> isAssembly) throws Exception {
        Class<?> handler = Class.forName(HANDLER_CLASS);
        Method method = handler.getMethod("shouldHandleCommand", List.class, List.class, Predicate.class);
        return (Boolean) method.invoke(null, sources, targets, isAssembly);
    }

    private static BindResult checkPairAvailability(boolean sourceAssembly, boolean sourceAvailable,
        boolean targetAssembly, boolean targetAvailable) throws Exception {
        Class<?> handler = Class.forName(HANDLER_CLASS);
        Method method = handler
            .getMethod("checkPairAvailability", boolean.class, boolean.class, boolean.class, boolean.class);
        return (BindResult) method.invoke(null, sourceAssembly, sourceAvailable, targetAssembly, targetAvailable);
    }

    @SuppressWarnings("unchecked")
    private static List<DimensionalCoord> deduplicateAnchors(List<DimensionalCoord> anchors, int currentDimension,
        Predicate<DimensionalCoord> isAssembly, Map<DimensionalCoord, Object> grids) throws Exception {
        Class<?> handler = Class.forName(HANDLER_CLASS);
        Method method = handler
            .getMethod("deduplicateAnchors", List.class, int.class, Predicate.class, java.util.function.Function.class);
        return (List<DimensionalCoord>) method.invoke(
            null,
            anchors,
            currentDimension,
            isAssembly,
            (java.util.function.Function<DimensionalCoord, Object>) grids::get);
    }

    private static WirelessToolDataObject data(DimensionalCoord network, String name, DimensionalCoord position,
        AEColor color, boolean hub) {
        return data(network, name, position, color, hub, false);
    }

    private static WirelessToolDataObject data(DimensionalCoord network, String name, DimensionalCoord position,
        AEColor color, boolean hub, boolean connected) {
        return new WirelessToolDataObject(
            network,
            name,
            position,
            connected,
            connected ? Collections.singletonList(coord(999, 64, 999)) : Collections.emptyList(),
            color,
            1,
            hub,
            hub ? 8 : 1);
    }

    private static DimensionalCoord coord(int x, int y, int z) {
        return new DimensionalCoord(x, y, z, 0);
    }
}
