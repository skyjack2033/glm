package com.github.skyjack2033.wirelessmehatch.me;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.PlayerSource;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.core.localization.WirelessMessages;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WireLessToolHelper.BindResult;
import appeng.helpers.WirelessKitCommand;
import appeng.helpers.WirelessKitCommand.PinType;
import appeng.helpers.WirelessKitCommand.SubCommand;
import appeng.helpers.WirelessKitCommand.WirelessKitCommands;
import appeng.helpers.WirelessToolDataObject;
import appeng.items.contents.WirelessKitObject;
import appeng.tile.networking.TileWirelessBase;

public final class WirelessKitSuperModeHandler {

    private static final String FALLBACK_ASSEMBLY_NAME = "Wireless Unified Output Assembly";

    private WirelessKitSuperModeHandler() {}

    public static void appendAssemblyData(WirelessKitObject toolInv, List<WirelessToolDataObject> data) {
        if (toolInv == null || data == null) return;
        World world = toolInv.getWorld();
        ItemStack kit = toolInv.getItemStack();
        if (world == null || kit == null) return;

        Set<DimensionalCoord> seen = new HashSet<>();
        for (WirelessToolDataObject endpoint : data) {
            if (endpoint != null && endpoint.cord != null) seen.add(endpoint.cord);
        }

        for (DimensionalCoord networkAnchor : readAnchors(kit)) {
            IGrid grid = resolveGrid(world, networkAnchor);
            if (grid == null) continue;
            try {
                for (IGridNode node : grid.getMachines(MTEWirelessUnifiedOutputAssemblyME.class)) {
                    if (!(node.getMachine() instanceof MTEWirelessUnifiedOutputAssemblyME assembly)) continue;
                    DimensionalCoord position = assembly.getLocation();
                    if (position == null || !seen.add(position)) continue;
                    data.add(
                        createAssemblyData(
                            networkAnchor,
                            getDisplayName(assembly),
                            position,
                            getTargetCoordinate(assembly),
                            getUsedChannels(node)));
                }
            } catch (RuntimeException ignored) {}
        }
    }

    public static boolean handleCommand(WirelessKitCommand command, WirelessKitObject toolInv,
        List<WirelessToolDataObject> currentData, EntityPlayer player) {
        if (command == null || toolInv == null
            || currentData == null
            || player == null
            || (command.command != WirelessKitCommands.BIND && command.command != WirelessKitCommands.UNBIND)) {
            return false;
        }

        World world = toolInv.getWorld();
        ItemStack kit = toolInv.getItemStack();
        if (world == null || kit == null) return false;

        List<WirelessToolDataObject> snapshot = new ArrayList<>(currentData);
        List<WirelessToolDataObject> sources = expandSelections(command.toBindRow, snapshot);
        List<WirelessToolDataObject> targets = expandSelections(command.targetRow, snapshot);
        Predicate<DimensionalCoord> isLiveAssembly = coordinate -> isLiveAssembly(world, coordinate);
        boolean containsAssembly = command.command == WirelessKitCommands.BIND
            ? shouldHandleCommand(sources, targets, isLiveAssembly)
            : shouldHandleCommand(sources, Collections.emptyList(), isLiveAssembly);
        if (!containsAssembly) return false;

        if (command.command == WirelessKitCommands.BIND) {
            bindRows(
                world,
                player,
                filterForCommand(sources, command.command, isLiveAssembly),
                filterForCommand(targets, command.command, isLiveAssembly));
            deduplicateStoredAnchors(kit, world);
        } else {
            unbindRows(kit, world, player, filterForCommand(sources, command.command, isLiveAssembly));
        }
        return true;
    }

    public static List<WirelessToolDataObject> expandSelection(SubCommand selection,
        List<WirelessToolDataObject> data) {
        if (selection == null || selection.groupBy == null) return Collections.emptyList();

        List<WirelessToolDataObject> selected = new ArrayList<>();
        if (selection.groupBy == PinType.SINGLE) {
            for (WirelessToolDataObject endpoint : data) {
                if (selection.coord != null && selection.coord.equals(endpoint.cord)) {
                    selected.add(endpoint);
                    break;
                }
            }
            return selected;
        }

        for (WirelessToolDataObject endpoint : data) {
            if (selection.networkPos == null || !selection.networkPos.equals(endpoint.network)) continue;
            if (selection.groupBy == PinType.COLOR && selection.color != endpoint.color) continue;
            if (endpoint.isHub ? selection.includeHubs : selection.includeConnectors) {
                selected.add(endpoint);
            }
        }
        return selected;
    }

    public static List<WirelessToolDataObject> filterForCommand(List<WirelessToolDataObject> selected,
        WirelessKitCommands command, Predicate<DimensionalCoord> isAssembly) {
        List<WirelessToolDataObject> filtered = new ArrayList<>();
        for (WirelessToolDataObject endpoint : selected) {
            if (!isAssembly.test(endpoint.cord)) {
                if (command == WirelessKitCommands.BIND || endpoint.isConnected) filtered.add(endpoint);
            } else if (command == WirelessKitCommands.BIND && !endpoint.isConnected) {
                filtered.add(endpoint);
            } else if (command == WirelessKitCommands.UNBIND && endpoint.isConnected) {
                filtered.add(endpoint);
            }
        }
        return filtered;
    }

    public static boolean shouldHandleCommand(List<WirelessToolDataObject> sources,
        List<WirelessToolDataObject> targets, Predicate<DimensionalCoord> isAssembly) {
        return containsAssembly(sources, isAssembly) || containsAssembly(targets, isAssembly);
    }

    public static BindResult checkPairAvailability(boolean sourceAssembly, boolean sourceAvailable,
        boolean targetAssembly, boolean targetAvailable) {
        if (sourceAssembly && targetAssembly) return BindResult.INVALID_SOURCE;
        if (sourceAssembly != targetAssembly) {
            if (sourceAssembly && !sourceAvailable) return BindResult.INVALID_SOURCE;
            if (targetAssembly && !targetAvailable) return BindResult.INVALID_TARGET;
            return null;
        }
        if (!sourceAvailable) return BindResult.INVALID_SOURCE;
        if (!targetAvailable) return BindResult.INVALID_TARGET;
        return null;
    }

    public static WirelessToolDataObject createAssemblyData(DimensionalCoord network, String name,
        DimensionalCoord position, DimensionalCoord target, int channels) {
        boolean connected = target != null;
        return new WirelessToolDataObject(
            network,
            name,
            position,
            connected,
            connected ? Collections.singletonList(target) : Collections.emptyList(),
            AEColor.Transparent,
            channels,
            false,
            connected ? 0 : 1);
    }

    public static List<DimensionalCoord> prioritizeAnchors(List<DimensionalCoord> anchors,
        Predicate<DimensionalCoord> isAssembly) {
        List<DimensionalCoord> ordered = new ArrayList<>(anchors.size());
        for (DimensionalCoord anchor : anchors) {
            if (!isAssembly.test(anchor)) ordered.add(anchor);
        }
        for (DimensionalCoord anchor : anchors) {
            if (isAssembly.test(anchor)) ordered.add(anchor);
        }
        return ordered;
    }

    public static <T> List<DimensionalCoord> deduplicateAnchors(List<DimensionalCoord> anchors, int currentDimension,
        Predicate<DimensionalCoord> isAssembly, Function<DimensionalCoord, T> resolveGrid) {
        List<DimensionalCoord> current = new ArrayList<>();
        List<DimensionalCoord> otherDimensions = new ArrayList<>();
        for (DimensionalCoord anchor : anchors) {
            if (anchor == null) continue;
            if (anchor.getDimension() == currentDimension) current.add(anchor);
            else otherDimensions.add(anchor);
        }

        List<DimensionalCoord> deduplicated = new ArrayList<>();
        Set<T> represented = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DimensionalCoord anchor : prioritizeAnchors(current, isAssembly)) {
            T grid = resolveGrid.apply(anchor);
            if (grid != null && represented.add(grid)) deduplicated.add(anchor);
        }
        deduplicated.addAll(otherDimensions);
        return deduplicated;
    }

    private static List<WirelessToolDataObject> expandSelections(List<SubCommand> selections,
        List<WirelessToolDataObject> data) {
        List<WirelessToolDataObject> expanded = new ArrayList<>();
        if (selections == null) return expanded;
        for (SubCommand selection : selections) {
            expanded.addAll(expandSelection(selection, data));
        }
        return expanded;
    }

    private static boolean containsAssembly(List<WirelessToolDataObject> endpoints,
        Predicate<DimensionalCoord> isAssembly) {
        for (WirelessToolDataObject endpoint : endpoints) {
            if (endpoint != null && endpoint.cord != null && isAssembly.test(endpoint.cord)) return true;
        }
        return false;
    }

    private static void bindRows(World world, EntityPlayer player, List<WirelessToolDataObject> sourceData,
        List<WirelessToolDataObject> targetData) {
        List<WirelessKitEndpoint> sources = resolveEndpoints(world, sourceData);
        List<WirelessKitEndpoint> targets = resolveEndpoints(world, targetData);
        WirelessKitRowBinder.bindRows(sources, targets, (source, target) -> true, (source, target) -> {
            BindResult unavailable = checkPairAvailability(
                source.isAssembly(),
                isAvailableForBind(source),
                target.isAssembly(),
                isAvailableForBind(target));
            if (unavailable != null) return unavailable;

            BindResult result = WirelessKitBindingService.bind(target, source, player);
            WirelessKitInteractionHandler.sendRowMessage(player, source, target, result);
            return result;
        });
    }

    private static boolean isAvailableForBind(WirelessKitEndpoint endpoint) {
        if (endpoint.isAssembly()) return !endpoint.isBound();
        return endpoint.isHub() ? endpoint.getFreeSlots() > 0 : !endpoint.isBound();
    }

    private static void unbindRows(ItemStack kit, World world, EntityPlayer player,
        List<WirelessToolDataObject> selectedData) {
        List<DimensionalCoord> splitCandidates = new ArrayList<>();
        PlayerSource source = new PlayerSource(player, null);
        for (WirelessKitEndpoint endpoint : resolveEndpoints(world, selectedData)) {
            if (endpoint.isAssembly()) {
                if (!endpoint.canAccess(source)) {
                    player.addChatMessage(WirelessMessages.Security.toChat());
                    continue;
                }
                DimensionalCoord oldTarget = getTargetCoordinate(endpoint.getAssembly());
                if (oldTarget != null) splitCandidates.add(oldTarget);
                endpoint.getAssembly()
                    .clearWirelessTarget();
                splitCandidates.add(endpoint.getLocation());
            } else {
                TileWirelessBase wirelessBase = endpoint.getWirelessBase();
                for (TileWirelessBase connected : wirelessBase.getConnectedTiles()) {
                    splitCandidates.add(connected.getLocation());
                }
                WireLessToolHelper.breakConnection(wirelessBase, source);
                splitCandidates.add(wirelessBase.getLocation());
            }
        }

        List<DimensionalCoord> anchors = normalizeAnchors(readAnchors(kit), world);
        Set<IGrid> represented = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DimensionalCoord anchor : anchors) {
            IGrid grid = resolveGrid(world, anchor);
            if (grid != null) represented.add(grid);
        }
        Predicate<DimensionalCoord> isAssembly = coordinate -> isLiveAssembly(world, coordinate);
        for (DimensionalCoord candidate : prioritizeAnchors(splitCandidates, isAssembly)) {
            IGrid grid = resolveGrid(world, candidate);
            if (grid != null && represented.add(grid)) anchors.add(candidate);
        }
        writeAnchors(kit, normalizeAnchors(anchors, world));
    }

    private static List<WirelessKitEndpoint> resolveEndpoints(World world, List<WirelessToolDataObject> endpoints) {
        List<WirelessKitEndpoint> resolved = new ArrayList<>();
        for (WirelessToolDataObject endpoint : endpoints) {
            WirelessKitEndpoint live = endpoint == null ? null : WirelessKitEndpoint.fromWorld(world, endpoint.cord);
            if (live != null) resolved.add(live);
        }
        return resolved;
    }

    private static void deduplicateStoredAnchors(ItemStack kit, World world) {
        writeAnchors(kit, normalizeAnchors(readAnchors(kit), world));
    }

    private static List<DimensionalCoord> normalizeAnchors(List<DimensionalCoord> anchors, World world) {
        Predicate<DimensionalCoord> isAssembly = coordinate -> isLiveAssembly(world, coordinate);
        return deduplicateAnchors(
            anchors,
            world.provider.dimensionId,
            isAssembly,
            coordinate -> resolveGrid(world, coordinate));
    }

    private static boolean isLiveAssembly(World world, DimensionalCoord coordinate) {
        WirelessKitEndpoint endpoint = WirelessKitEndpoint.fromWorld(world, coordinate);
        return endpoint != null && endpoint.isAssembly();
    }

    private static IGrid resolveGrid(World world, DimensionalCoord coordinate) {
        if (world == null || coordinate == null || coordinate.getDimension() != world.provider.dimensionId) return null;
        TileEntity tile = world.getTileEntity(coordinate.x, coordinate.y, coordinate.z);
        if (!(tile instanceof IGridHost host)) return null;
        try {
            IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);
            return node == null ? null : node.getGrid();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int getUsedChannels(IGridNode node) {
        int channels = 0;
        for (IGridConnection connection : node.getConnections()) {
            channels = Math.max(channels, connection.getUsedChannels());
        }
        return channels;
    }

    private static String getDisplayName(MTEWirelessUnifiedOutputAssemblyME assembly) {
        try {
            ItemStack stack = assembly.getStackForm(1);
            if (stack != null && stack.hasDisplayName()) return stack.getDisplayName();
            if (stack != null && stack.getDisplayName() != null
                && !stack.getDisplayName()
                    .isEmpty()) {
                return stack.getDisplayName();
            }
        } catch (RuntimeException ignored) {}
        return FALLBACK_ASSEMBLY_NAME;
    }

    private static DimensionalCoord getTargetCoordinate(MTEWirelessUnifiedOutputAssemblyME assembly) {
        WirelessLinkTarget target = assembly.getWirelessTarget();
        if (target == null) return null;
        return new DimensionalCoord(target.getX(), target.getY(), target.getZ(), target.getDimensionId());
    }

    private static List<DimensionalCoord> readAnchors(ItemStack kit) {
        WirelessKitNbtState.ensureInitialized(kit);
        NBTTagCompound superTag = kit.getTagCompound()
            .getCompoundTag(WireLessToolHelper.NbtSuper);
        return new ArrayList<>(
            DimensionalCoord.readAsListFromNBT(superTag.getCompoundTag(WireLessToolHelper.NbtSuperPos)));
    }

    private static void writeAnchors(ItemStack kit, List<DimensionalCoord> anchors) {
        WirelessKitNbtState.ensureInitialized(kit);
        NBTTagCompound superTag = kit.getTagCompound()
            .getCompoundTag(WireLessToolHelper.NbtSuper);
        NBTTagCompound positions = new NBTTagCompound();
        DimensionalCoord.writeListToNBT(positions, anchors);
        superTag.setTag(WireLessToolHelper.NbtSuperPos, positions);
        kit.getTagCompound()
            .setTag(WireLessToolHelper.NbtSuper, superTag);
    }
}
