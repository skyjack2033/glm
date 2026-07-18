package com.github.skyjack2033.wirelessmehatch.me;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import appeng.api.AEApi;
import appeng.api.config.AdvancedWirelessToolMode;
import appeng.api.config.WirelessToolMode;
import appeng.api.networking.security.PlayerSource;
import appeng.api.util.DimensionalCoord;
import appeng.core.localization.WirelessMessages;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WireLessToolHelper.BindResult;
import appeng.server.ServerHelper;

public final class WirelessKitInteractionHandler {

    private WirelessKitInteractionHandler() {}

    public static boolean isWirelessKit(ItemStack stack) {
        return stack != null && AEApi.instance()
            .definitions()
            .items()
            .toolWirelessKit()
            .isSameAs(stack);
    }

    public static void handleServerRightClick(ItemStack kit, EntityPlayer player, World world) {
        if (world == null || world.isRemote || player == null || !isWirelessKit(kit)) return;
        if (ServerHelper.WIRELESS_MODE_SWITCH.isKeyDown(player)) return;
        if (!player.isSneaking() || !ServerHelper.WIRELESS_EXTRA_ACTION.isKeyDown(player)) return;

        switch (WireLessToolHelper.getMode(kit)) {
            case Simple -> WirelessKitNbtState.clearSimplePendingAnchor(kit);
            case Advanced -> WirelessKitNbtState.clearAdvancedPendingAnchor(kit);
            case Super -> {}
        }
    }

    /**
     * Handles only operations that contain an assembly. A {@code null} result tells the item mixin to leave the
     * complete native AE2 operation alone.
     */
    public static Boolean handleServerUse(ItemStack kit, EntityPlayer player, World world, int x, int y, int z) {
        if (world == null || world.isRemote || !isWirelessKit(kit)) return null;
        WirelessKitNbtState.ensureInitialized(kit);

        WirelessKitEndpoint current = WirelessKitEndpoint.fromTile(world.getTileEntity(x, y, z));
        if (current == null) return null;

        WirelessToolMode mode = WireLessToolHelper.getMode(kit);
        return switch (mode) {
            case Simple -> handleSimple(kit, player, world, current);
            case Advanced -> handleAdvanced(kit, player, world, current);
            case Super -> current.isAssembly()
                ? WireLessToolHelper.bindSuper(world.getTileEntity(x, y, z), kit, world, player)
                : null;
        };
    }

    static boolean containsAssembly(List<WirelessKitEndpoint> endpoints) {
        for (WirelessKitEndpoint endpoint : endpoints) {
            if (endpoint != null && endpoint.isAssembly()) return true;
        }
        return false;
    }

    private static Boolean handleSimple(ItemStack kit, EntityPlayer player, World world, WirelessKitEndpoint current) {
        DimensionalCoord pending = WirelessKitNbtState.readSimplePendingAnchor(kit);
        if (current.isAssembly() && pending != null) {
            if (!checkAccess(current, player)) return false;
            if (!isSameDimension(current, pending)) {
                player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
                return false;
            }
            WirelessKitEndpoint anchor = resolveEndpoint(pending);
            if (anchor == null) {
                WirelessKitNbtState.clearSimplePendingAnchor(kit);
                player.addChatMessage(WirelessMessages.InvalidTarget.toChat());
                return false;
            }
            BindResult result = WirelessKitBindingService.bind(current, anchor, player);
            WirelessKitNbtState.clearSimplePendingAnchor(kit);
            return wasSuccessful(result);
        }
        if (current.isWirelessBase() && pending != null) {
            if (!checkAccess(current, player)) return false;
            WirelessKitNbtState.clearSimplePendingAnchor(kit);
        }

        DimensionalCoord stored = WirelessKitNbtState.readPoint(kit, WireLessToolHelper.NbtSimple);
        if (stored == null) {
            if (current.isAssembly()) {
                if (!checkAccess(current, player)) return false;
                WirelessKitNbtState.storePoint(kit, WireLessToolHelper.NbtSimple, current.getLocation());
                player.addChatMessage(
                    WirelessMessages.SimpleBound.toChat(
                        current.getLocation()
                            .getGuiTextShortNoDim()));
                return true;
            }
            if (current.isHub() && current.getFreeSlots() <= 0) {
                if (!checkAccess(current, player)) return false;
                WirelessKitNbtState.storeSimplePendingAnchor(kit, current.getLocation());
                player.addChatMessage(WirelessMessages.TargetHubFull.toChat());
                return false;
            }
            return null;
        }

        if (current.isAssembly()) {
            if (!checkAccess(current, player)) return false;
            if (!isSameDimension(current, stored)) {
                player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
                return false;
            }
        }

        if (current.getLocation()
            .isEqual(stored)) {
            if (!current.isAssembly()) return null;
            WirelessKitNbtState.clear(kit, WireLessToolHelper.NbtSimple);
            return true;
        }

        WirelessKitEndpoint source = resolveEndpoint(stored);
        if (source == null) {
            if (!current.isAssembly()) return null;
            WirelessKitNbtState.clear(kit, WireLessToolHelper.NbtSimple);
            player.addChatMessage(WirelessMessages.InvalidTarget.toChat());
            return false;
        }
        if (!WirelessKitBindingService.isAssemblyPair(current, source)) return null;
        if (!current.isAssembly()) {
            if (!checkAccess(current, player)) return false;
            if (!isSameDimension(current, stored)) {
                player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
                return false;
            }
        }

        BindResult result = WirelessKitBindingService.bind(current, source, player);
        WirelessKitNbtState.clear(kit, WireLessToolHelper.NbtSimple);
        return wasSuccessful(result);
    }

    private static Boolean handleAdvanced(ItemStack kit, EntityPlayer player, World world,
        WirelessKitEndpoint current) {
        AdvancedWirelessToolMode mode = WireLessToolHelper.getConnectMode(kit);
        if (current.isAssembly() && !checkAccess(current, player)) return false;
        DimensionalCoord pending = WirelessKitNbtState.readAdvancedPendingAnchor(kit);
        if (pending != null) {
            if (current.isWirelessBase()) {
                if (!checkAccess(current, player)) return false;
                WirelessKitNbtState.clearAdvancedPendingAnchor(kit);
            } else if (mode != AdvancedWirelessToolMode.Binding) {
                WirelessKitNbtState.clearAdvancedPendingAnchor(kit);
            }
        }
        return switch (mode) {
            case Queueing -> handleQueueing(kit, player, current);
            case Binding -> handleBinding(kit, player, world, current);
            case QueueingLine -> handleQueueingLine(kit, player, current);
            case BindingLine -> handleBindingLine(kit, player, world, current);
        };
    }

    private static Boolean handleQueueing(ItemStack kit, EntityPlayer player, WirelessKitEndpoint current) {
        if (current.isWirelessBase()) {
            if (current.isHub() && current.getFreeSlots() <= 0) {
                if (!checkAccess(current, player)) return false;
                if (WirelessKitNbtState.readAdvanced(kit)
                    .isEmpty()) {
                    WirelessKitNbtState.storeAdvancedPendingAnchor(kit, current.getLocation());
                }
                player.addChatMessage(WirelessMessages.TargetHubFull.toChat());
                return false;
            }
            return null;
        }
        if (current.getFreeSlots() <= 0) {
            player.addChatMessage(WirelessMessages.TargetHubFull.toChat());
            return false;
        }

        for (DimensionalCoord queued : WirelessKitNbtState.readAdvanced(kit)) {
            if (current.getLocation()
                .isEqual(queued)) {
                player.addChatMessage(WirelessMessages.BoundAdvancedFilled.toChat());
                return false;
            }
        }
        WirelessKitNbtState.appendAdvanced(kit, current.getLocation());
        player.addChatMessage(
            WirelessMessages.AdvancedQueued.toChat(
                current.getLocation()
                    .getGuiTextShortNoDim()));
        return true;
    }

    private static Boolean handleBinding(ItemStack kit, EntityPlayer player, World world, WirelessKitEndpoint target) {
        List<DimensionalCoord> queue = new ArrayList<>(WirelessKitNbtState.readAdvanced(kit));
        DimensionalCoord pending = WirelessKitNbtState.readAdvancedPendingAnchor(kit);
        if (target.isAssembly() && pending != null) {
            if (!isSameDimension(target, pending)) {
                player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
                return false;
            }
            WirelessKitEndpoint anchor = resolveEndpoint(pending);
            if (anchor == null) {
                WirelessKitNbtState.clearAdvancedPendingAnchor(kit);
                player.addChatMessage(WirelessMessages.InvalidTarget.toChat());
                return false;
            }
            BindResult result = WirelessKitBindingService.bind(target, anchor, player);
            WirelessKitNbtState.clearAdvancedPendingAnchor(kit);
            return wasSuccessful(result);
        }
        if (queue.isEmpty()) {
            if (!target.isAssembly()) return null;
            player.addChatMessage(WirelessMessages.AdvancedNoConnectors.toChat());
            return false;
        }

        List<WirelessKitEndpoint> resolved = resolveCoordinates(queue);
        if (!target.isAssembly() && !containsAssembly(resolved)) return null;
        if (!target.isAssembly() && !checkAccess(target, player)) return false;

        boolean bindMultiple = ServerHelper.WIRELESS_EXTRA_ACTION.isKeyDown(player) && target.isHub();
        boolean success = false;
        int boundCount = 0;
        while (!queue.isEmpty()) {
            DimensionalCoord sourceLocation = queue.get(0);
            if (sourceLocation.getDimension() != world.provider.dimensionId) {
                player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
                break;
            }

            WirelessKitEndpoint source = WirelessKitEndpoint.fromWorld(world, sourceLocation);
            BindResult result;
            if (target.getLocation()
                .isEqual(sourceLocation)) {
                result = BindResult.ALREADY_BIND;
            } else if (source == null) {
                player.addChatMessage(WirelessMessages.InvalidTarget.toChat());
                result = BindResult.INVALID_SOURCE;
            } else {
                result = WirelessKitBindingService.bind(target, source, player);
            }
            queue.remove(0);
            WirelessKitNbtState.removeAdvancedHead(kit);
            if (result == BindResult.SUCCESS) {
                boundCount++;
                success = true;
            } else if (result == BindResult.ALREADY_BIND) {
                success = true;
            }

            if (!bindMultiple) break;
            if (target.getFreeSlots() <= 0 && !remainingContainsAssembly(queue)) break;
        }
        if (bindMultiple) player.addChatMessage(WirelessMessages.AdvancedBindingHub.toChat(boundCount));
        return success;
    }

    private static Boolean handleQueueingLine(ItemStack kit, EntityPlayer player, WirelessKitEndpoint current) {
        DimensionalCoord first = WirelessKitNbtState
            .readLinePoint(kit, WireLessToolHelper.NbtAdvancedLineQueue, WireLessToolHelper.NbtAdvanced1StPoint);
        if (!current.isAssembly() && first == null) return null;
        if (!current.isAssembly()) {
            WirelessKitEndpoint firstEndpoint = resolveEndpoint(first);
            if (firstEndpoint == null || !firstEndpoint.isAssembly()) return null;
            if (!checkAccess(current, player)) return false;
        }
        return addLinePoint(kit, player, WireLessToolHelper.NbtAdvancedLineQueue, current.getLocation());
    }

    private static Boolean handleBindingLine(ItemStack kit, EntityPlayer player, World world,
        WirelessKitEndpoint current) {
        DimensionalCoord queueFirst = WirelessKitNbtState
            .readLinePoint(kit, WireLessToolHelper.NbtAdvancedLineQueue, WireLessToolHelper.NbtAdvanced1StPoint);
        DimensionalCoord queueSecond = WirelessKitNbtState
            .readLinePoint(kit, WireLessToolHelper.NbtAdvancedLineQueue, WireLessToolHelper.NbtAdvanced2ndPoint);
        if (queueFirst == null || queueSecond == null) return current.isAssembly() ? false : null;

        String bindingKey = WireLessToolHelper.NbtAdvancedLineBinding;
        DimensionalCoord bindingFirst = WirelessKitNbtState
            .readLinePoint(kit, bindingKey, WireLessToolHelper.NbtAdvanced1StPoint);
        if (bindingFirst == null) {
            if (!current.isAssembly()) return null;
            return addLinePoint(kit, player, bindingKey, current.getLocation());
        }

        DimensionalCoord bindingSecond = current.getLocation();
        boolean containsAssembly = isAssemblyCoordinate(queueFirst) || isAssemblyCoordinate(queueSecond)
            || isAssemblyCoordinate(bindingFirst)
            || isAssemblyCoordinate(bindingSecond)
            || containsAssembly(resolveLineGlobally(queueFirst, queueSecond))
            || containsAssembly(resolveLineGlobally(bindingFirst, bindingSecond));
        if (!current.isAssembly() && !containsAssembly) return null;
        if (!current.isAssembly() && !checkAccess(current, player)) return false;

        if (!isSameCurrentDimension(world, queueFirst, queueSecond, bindingFirst, bindingSecond)) {
            player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            clearLineBindingState(kit);
            return false;
        }
        if (WirelessKitNbtState.expandLine(bindingFirst, bindingSecond)
            .isEmpty()) {
            player.addChatMessage(WirelessMessages.AdvancedLineNotLine.toChat());
            clearLineBindingState(kit);
            return false;
        }

        List<WirelessKitEndpoint> queueRow = resolveLine(world, queueFirst, queueSecond);
        List<WirelessKitEndpoint> targetRow = resolveLine(world, bindingFirst, bindingSecond);

        WirelessKitNbtState.storeLinePoint(kit, bindingKey, WireLessToolHelper.NbtAdvanced2ndPoint, bindingSecond);
        player.addChatMessage(WirelessMessages.AdvancedLine2ndAdded.toChat(bindingSecond.getGuiTextShortNoDim()));
        WirelessKitRowBinder
            .bindRows(queueRow, targetRow, (source, target) -> target.canAccept(source), (source, target) -> {
                BindResult result = WirelessKitBindingService.bind(target, source, player);
                sendRowMessage(player, source, target, result);
                return result;
            });
        clearLineBindingState(kit);
        return true;
    }

    private static Boolean addLinePoint(ItemStack kit, EntityPlayer player, String lineKey, DimensionalCoord point) {
        DimensionalCoord first = WirelessKitNbtState
            .readLinePoint(kit, lineKey, WireLessToolHelper.NbtAdvanced1StPoint);
        if (first == null) {
            WirelessKitNbtState.storeLinePoint(kit, lineKey, WireLessToolHelper.NbtAdvanced1StPoint, point);
            player.addChatMessage(WirelessMessages.AdvancedLine1stAdded.toChat(point.getGuiTextShortNoDim()));
            return true;
        }
        DimensionalCoord second = WirelessKitNbtState
            .readLinePoint(kit, lineKey, WireLessToolHelper.NbtAdvanced2ndPoint);
        if (second != null) {
            player.addChatMessage(WirelessMessages.AdvancedLineReset.toChat());
            return false;
        }
        if (first.getDimension() != point.getDimension()) {
            player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            kit.getTagCompound()
                .removeTag(lineKey);
            return false;
        }
        if (WirelessKitNbtState.expandLine(first, point)
            .isEmpty()) {
            player.addChatMessage(WirelessMessages.AdvancedLineNotLine.toChat());
            kit.getTagCompound()
                .removeTag(lineKey);
            return false;
        }
        WirelessKitNbtState.storeLinePoint(kit, lineKey, WireLessToolHelper.NbtAdvanced2ndPoint, point);
        player.addChatMessage(WirelessMessages.AdvancedLine2ndAdded.toChat(point.getGuiTextShortNoDim()));
        return true;
    }

    private static List<WirelessKitEndpoint> resolveCoordinates(List<DimensionalCoord> coordinates) {
        List<WirelessKitEndpoint> endpoints = new ArrayList<>();
        for (DimensionalCoord coordinate : coordinates) {
            WirelessKitEndpoint endpoint = resolveEndpoint(coordinate);
            if (endpoint != null) endpoints.add(endpoint);
        }
        return endpoints;
    }

    private static WirelessKitEndpoint resolveEndpoint(DimensionalCoord coordinate) {
        if (coordinate == null) return null;
        World endpointWorld = DimensionManager.getWorld(coordinate.getDimension());
        return WirelessKitEndpoint.fromWorld(endpointWorld, coordinate);
    }

    private static List<WirelessKitEndpoint> resolveLine(World world, DimensionalCoord first, DimensionalCoord second) {
        List<WirelessKitEndpoint> endpoints = new ArrayList<>();
        if (!isSameCurrentDimension(world, first, second)) return endpoints;
        for (DimensionalCoord coordinate : WirelessKitNbtState.expandLine(first, second)) {
            WirelessKitEndpoint endpoint = WirelessKitEndpoint.fromWorld(world, coordinate);
            if (endpoint != null) endpoints.add(endpoint);
        }
        return endpoints;
    }

    private static List<WirelessKitEndpoint> resolveLineGlobally(DimensionalCoord first, DimensionalCoord second) {
        List<WirelessKitEndpoint> endpoints = new ArrayList<>();
        if (first == null || second == null || first.getDimension() != second.getDimension()) return endpoints;
        World lineWorld = DimensionManager.getWorld(first.getDimension());
        if (lineWorld == null) return endpoints;
        for (DimensionalCoord coordinate : WirelessKitNbtState.expandLine(first, second)) {
            WirelessKitEndpoint endpoint = WirelessKitEndpoint.fromWorld(lineWorld, coordinate);
            if (endpoint != null) endpoints.add(endpoint);
        }
        return endpoints;
    }

    private static boolean isAssemblyCoordinate(DimensionalCoord coordinate) {
        WirelessKitEndpoint endpoint = resolveEndpoint(coordinate);
        return endpoint != null && endpoint.isAssembly();
    }

    private static boolean remainingContainsAssembly(List<DimensionalCoord> coordinates) {
        return containsAssembly(resolveCoordinates(coordinates));
    }

    private static boolean isSameCurrentDimension(World world, DimensionalCoord... points) {
        if (world == null) return false;
        for (DimensionalCoord point : points) {
            if (point == null || point.getDimension() != world.provider.dimensionId) return false;
        }
        return true;
    }

    private static boolean isSameDimension(WirelessKitEndpoint endpoint, DimensionalCoord coordinate) {
        DimensionalCoord location = endpoint == null ? null : endpoint.getLocation();
        return location != null && coordinate != null && location.getDimension() == coordinate.getDimension();
    }

    private static void clearLineBindingState(ItemStack kit) {
        NBTTagCompound tag = kit.getTagCompound();
        tag.removeTag(WireLessToolHelper.NbtAdvancedLineQueue);
        tag.removeTag(WireLessToolHelper.NbtAdvancedLineBinding);
    }

    private static boolean wasSuccessful(BindResult result) {
        return result == BindResult.SUCCESS || result == BindResult.ALREADY_BIND;
    }

    private static boolean checkAccess(WirelessKitEndpoint endpoint, EntityPlayer player) {
        if (endpoint.canAccess(new PlayerSource(player, null))) return true;
        player.addChatMessage(WirelessMessages.Security.toChat());
        return false;
    }

    static void sendRowMessage(EntityPlayer player, WirelessKitEndpoint source, WirelessKitEndpoint target,
        BindResult result) {
        switch (result) {
            case SUCCESS -> player.addChatMessage(
                WirelessMessages.rowBindSuccess.toChat(
                    source.getLocation()
                        .getGuiTextShortNoDim(),
                    target.getLocation()
                        .getGuiTextShortNoDim()));
            case INVALID_TARGET -> player.addChatMessage(
                WirelessMessages.rowBindInvalidTarget.toChat(
                    target.getLocation()
                        .getGuiTextShortNoDim()));
            case INVALID_SOURCE -> player.addChatMessage(
                WirelessMessages.rowBindInvalidSource.toChat(
                    source.getLocation()
                        .getGuiTextShortNoDim()));
            case FAILED -> player.addChatMessage(
                WirelessMessages.rowBindFailed.toChat(
                    source.getLocation()
                        .getGuiTextShortNoDim(),
                    target.getLocation()
                        .getGuiTextShortNoDim()));
            case ALREADY_BIND -> {}
        }
    }
}
