# Wireless Unified Output Assembly

## Purpose

`Wireless Unified Output Assembly (ME)` is one physical GregTech MetaTileEntity that lets a GT multiblock output both items and fluids into a bound AE2 ME network without cables.

The block targets GTNH 2.9.0 beta runtime jars:

- GregTech `5.09.52.594`
- AE2 `rv3-beta-977-GTNH`
- GT: Not Leisure `dev-290` when its optional steam-machine compatibility hook is active

## Public Behavior

- The player places one block in a GT multiblock structure.
- GT treats the block as an output bus for item outputs.
- The same physical MetaTileEntity directly provides the fluid-hatch role, while exact pinned `getOutputBusses()` snapshot Mixins expose that same instance as the item bus.
- The player pairs the block to a wireless connector or hub network using GTNH AE2's native Wireless Kit.
- The block reconnects automatically after world load, chunk load, or transient network loss.
- Screwdriver right-click clears the block's wireless target.
- The block caches outputs when the ME network is unavailable, up to configured long capacities.

## Capacity Model

The assembly has two independent long capacities:

```java
long itemCapacity;  // item count
long fluidCapacity; // millibuckets
```

Capacity rules:

- `itemCapacity` limits the aggregate number of cached item units.
- `fluidCapacity` limits the aggregate number of cached fluid millibuckets.
- Default values are loaded from `config/wirelessmehatch.cfg`; both currently default to `Long.MAX_VALUE` for a newly placed assembly, while each block persists its own values in NBT.
- Item and fluid totals must never be reduced to `int` except when creating one chunk of `ItemStack.stackSize` or `FluidStack.amount` for insertion.
- GT's native fluid void-protection path remains the integration point. When a finite shared store is present, the exact pinned aggregate return hook determines the final fluid parallel result from the remaining shared long capacity and the combined fluid outputs, bounded by `maxParallel`; this can correct a conservative native early-zero result and does not modify cached output.

## Binding Target

The wireless link target is explicit. The implementation must not try to globally discover "my ME network".

```java
public final class WirelessLinkTarget {

    public enum AnchorType {
        ME_CONTROLLER,
        SECURITY_TERMINAL,
        WIRELESS_CONNECTOR
    }

    private final AnchorType anchorType;
    private final int dimensionId;
    private final int x;
    private final int y;
    private final int z;
    private final long locatableSerial;
    private final UUID ownerUuid;
    private final String ownerName;
}
```

Target semantics:

- `WIRELESS_CONNECTOR` is the only target created by the current Wireless Kit integration. It resolves an exact `TileWirelessBase` connector or hub by dimension and coordinates.
- `ME_CONTROLLER` and `SECURITY_TERMINAL` remain readable only for existing block NBT written by older releases.
- `ownerUuid` and `ownerName` are persisted so AE2's per-world integer player ID can be re-resolved after load.
- Live `IGridNode` and `IGridConnection` objects are not serialized.
- The connector/hub is a network anchor only. The assembly keeps its own `REQUIRE_CHANNEL` proxy, consumes one channel, and is never inserted into the connector/hub link map.

## Public Interfaces

```java
public interface WirelessBindable {
    boolean bindWirelessTarget(WirelessLinkTarget target);
    void clearWirelessTarget();
    boolean hasWirelessTarget();
    boolean isWirelessConnected();
}
```

```java
public interface WirelessOutputCapacityHost {
    long getItemCapacity();
    void setItemCapacity(long capacity);
    long getFluidCapacity();
    void setFluidCapacity(long capacity);
}
```

## NBT Schema

Block NBT:

```text
wirelessTarget.bound: boolean
wirelessTarget.anchorType: string
wirelessTarget.dim: int
wirelessTarget.x: int
wirelessTarget.y: int
wirelessTarget.z: int
wirelessTarget.locatableSerial: long
wirelessTarget.ownerUuid: string
wirelessTarget.ownerName: string
wirelessLink.proxy: compound
wirelessOutput.itemCapacity: long
wirelessOutput.fluidCapacity: long
wirelessOutput.itemCache: compound
wirelessOutput.fluidCache: compound
```

The mod does not replace AE2 Wireless Kit's item NBT schema. It reuses the native `Simple`, `Advanced`, `advancedLineQueue`, `advancedLineBinding`, and `Super` compounds. Two mode-scoped sidecar keys, `wirelessmehatch.pendingSimpleAnchor` and `wirelessmehatch.pendingAdvancedAnchor`, temporarily retain a full-hub coordinate only when AE2 would otherwise reject that first point before a later assembly click can reveal the intended pair. Clearing the corresponding native Kit mode clears its sidecar as well.

## GT Integration Architecture

The implementation uses one physical block and one MetaTileEntity instance for both GT output roles:

```text
BaseMetaTileEntity in world
    |
    |-- physical MTE: MTEWirelessUnifiedOutputAssemblyME extends MTEHatchOutput
            implements IOutputBus
            implements WirelessDualRoleOutput
            implements SharedFluidOutputStore
            - visible player-facing block
            - item output bus and fluid output hatch roles
            - owns wireless link, AE2 proxy, NBT, scanner information, and output core
```

`MetaTileEntity.isValid()` requires its base tile to point back to that exact MetaTileEntity instance. A hidden item or fluid delegate cannot share the physical base tile and still be valid: assigning the tile to the delegate replaces the physical assembly's identity, while not assigning it leaves the delegate invalid. The architecture therefore has no second MTE.

Item output:

- The physical `MTEHatchOutput` implements `IOutputBus` directly.
- `storePartial(ItemStack, boolean)` delegates to `WirelessUnifiedOutputCore.storeItem`.
- `createTransaction()` returns an item transaction backed by the same core.
- GT's native item transaction and output dispatch paths remain authoritative. Fluid void protection uses the native integration point and the finite shared-store replacement described below.

Controller registration and snapshots:

- The assembly registers normally as the real `MTEHatchOutput` in `mOutputHatches`; there are no reflective controller-list writes or periodic reattachment calls.
- The mod registers exactly one MetaTileEntity, the unified output assembly, at configurable ID `31701` by default. It does not reserve a second ID for a legacy input hatch.
- Exact-descriptor, `require = 1`, `remap = false` `RETURN` Mixins augment the ordinary GT, GT++ steam, and optional GTNL steam `getOutputBusses()` snapshots with the same physical instance.
- Snapshot augmentation accepts only a valid object implementing all of `MTEHatchOutput`, `IOutputBus`, and `WirelessDualRoleOutput`, and deduplicates by object identity.
- The steam controller's exact `addSteamBusOutput(...)` `HEAD` hook handles only that complete dual-role object and calls the controller's native `addOutputHatchToMachineList(...)`; all other objects continue through the unmodified steam method.

GTNL `dev-290` compatibility:

- GTNL's `SteamMultiMachineBase` overrides `getOutputBusses()` and otherwise hides the assembly registered in `mOutputHatches` from both `VoidProtectionHelper` and `ItemEjectionHelper`.
- An optional `@Pseudo` Mixin augments that exact override at `RETURN`. When GTNL is absent, the string target is not loaded; when it is present, the Mixin plugin pins `getOutputBusses()Ljava/util/List;` and reports a version-specific startup error if the descriptor changes.
- The assembly is never inserted into GTNL's backing `mSteamOutputs` or `mOutputBusses` lists. GTNL iterates those lists as `MTEHatchOutputBus`, so inserting the physical `MTEHatchOutput` assembly there would cause invalid casts.
- This hook covers standard ProcessingLogic item-space checks and item commits, including the Large Steam Lathe. It deliberately does not rewrite GTNL custom paths that directly inspect backing lists: `SteamApiaryModule`, `SteamItemVault`, `FurnaceArray`, `LargeSteamFurnace`, or the legacy `addOutput(ItemStack)` / `tryAddOutput(ItemStack)` methods. `SteamGreenhouseModule` standard `addItemOutputs` calls are covered; its legacy migration call is not.

Fluid output and void protection:

- The physical assembly's `fill` and fluid-store methods delegate to `WirelessUnifiedOutputCore.storeFluid`.
- GT's native fluid void-protection calculation runs first. When a finite `SharedFluidOutputStore` is present, an exact-descriptor, pinned `RETURN` hook on `VoidProtectionHelper.calculateMaxFluidParallels()` replaces that result with the aggregate shared-capacity result, including correction of a conservative native early-zero return.
- The helper unwraps `OutputHatchWrapper`, deduplicates physical stores by identity, aggregates remaining shared capacity, and uses saturating `long` addition and multiplication for the combined multi-fluid requirement.

## AE2 Link Lifecycle

`WirelessLinkManager` owns the AE2 connection lifecycle:

1. Create an `AENetworkProxy` for the real MTE.
2. On first tick, call `proxy.onReady()`.
3. Load proxy node state from NBT.
4. Resolve owner UUID/name to AE2 player ID and call `IGridNode.setPlayerID(int)`.
5. Resolve the remote target from the persisted wireless connector/hub coordinates. Legacy controller/security-terminal targets remain loadable.
6. Call `AEApi.instance().createGridConnection(localNode, remoteNode)`.
7. Periodically verify `connection.a()/b()` still match the current local and remote nodes.
8. On removal or chunk unload, destroy the connection and invalidate/unload the proxy.

## GTNH AE2 Wireless Kit Integration

- Simple mode accepts the assembly and a wireless connector/hub in either click order.
- Advanced Queueing/Binding and QueueingLine/BindingLine resolve coordinates as either native wireless tiles or assemblies. Line expansion checks every coordinate so an assembly between two ordinary endpoints is not skipped.
- Super mode registers the GT base tile as an `IGridHost`, appends assembly entries to the native data packet, and routes only BIND/UNBIND commands whose expanded rows contain an assembly.
- NETWORK/COLOR groups intentionally treat an assembly as a non-hub connector-shaped entry. BIND includes only unbound assemblies; UNBIND includes only bound assemblies.
- Pure connector/hub interactions and commands remain on AE2's original code path.
- Every state change is server-side. The client consumes the GT block click but does not mutate Kit or assembly NBT.

## Status Reporting

`getInfoData()` exposes four categories of scanner information:

- cached item count and `itemCapacity`
- cached fluid amount and `fluidCapacity`
- wireless target state as `Bound` or `Unbound`
- ME connection state as `Connected` or `Disconnected`

The block's status texture is refreshed every 20 ticks. It shows the cyan Wi-Fi overlay only when a wireless grid connection exists and the AE2 proxy is active; otherwise it shows the red X overlay. The active Wi-Fi overlay does not report whether ME storage has room for another insertion.

There is currently no configuration GUI. Empty-hand right-click is consumed and does not open an editor. Default capacities come from `config/wirelessmehatch.cfg`; every placed block persists its own `long` capacities and item/fluid caches under `wirelessOutput.itemCapacity`, `wirelessOutput.fluidCapacity`, `wirelessOutput.itemCache`, and `wirelessOutput.fluidCache` in block NBT.

## Non-Goals For This Refactor

- No input hatch functionality.
- No replacement Wireless Kit item, GUI, packet, or serialized data format.
- No global automatic discovery of a player's ME networks.
- No hidden output delegate, second MetaTileEntity, reflective controller-list mutation, or periodic reattachment path.

## Verification Checklist

Runtime verification must be done in the user's GTNH 2.9.0 beta instance:

1. Client starts with the exact pinned GT and AE2 Wireless Kit Mixins, plus the optional GTNL steam Mixin when GTNL is installed.
2. Simple mode pairs in both click orders.
3. All four Advanced submodes include assemblies at endpoints and inside lines.
4. Super single/network/color entries bind and unbind through the native three-column GUI.
5. The assembly consumes exactly one ME channel and never provides an 8/32-channel bridge or consumes a connector/hub wireless slot.
6. GT multiblock structure accepts the assembly.
7. Item outputs enter ME.
8. Fluid outputs enter ME.
9. Recipes with both item and fluid outputs work.
10. Target-network security permissions are enforced for the player performing the pair.
11. Disconnecting the target leaves cached output intact.
12. Restoring the target reconnects and flushes cached output.
13. World reload preserves target, proxy state, capacities, and caches.
14. `itemCapacity` and `fluidCapacity` limits are enforced without truncating long totals.
15. A GTNL Large Steam Lathe with only the connected assembly passes item void protection and commits its item output to the assembly cache.
