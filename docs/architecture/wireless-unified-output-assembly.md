# Wireless Unified Output Assembly

## Purpose

`Wireless Unified Output Assembly (ME)` is one physical GregTech MetaTileEntity that lets a GT multiblock output both items and fluids into a bound AE2 ME network without cables.

The block targets GTNH 2.9.0 beta runtime jars:

- GregTech `5.09.52.594`
- AE2 `rv3-beta-977-GTNH`

## Public Behavior

- The player places one block in a GT multiblock structure.
- GT treats the block as an output bus for item outputs.
- GT also treats the same physical block as an output hatch for fluid outputs through an internal delegate.
- The player binds the block to an ME network using this mod's custom wireless link tool.
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
- Default values may be `Long.MAX_VALUE` for initial behavior, but the fields must be persisted and exposed for future configuration.
- Item and fluid totals must never be reduced to `int` except when creating one chunk of `ItemStack.stackSize` or `FluidStack.amount` for insertion.

## Binding Target

The wireless link target is explicit. The implementation must not try to globally discover "my ME network".

```java
public final class WirelessLinkTarget {

    public enum AnchorType {
        ME_CONTROLLER,
        SECURITY_TERMINAL
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

- `ME_CONTROLLER` uses `dimensionId/x/y/z` to resolve an AE2 controller tile and its grid node.
- `SECURITY_TERMINAL` stores both coordinates and `locatableSerial`; serial is preferred, coordinates are fallback.
- `ownerUuid` and `ownerName` are persisted so AE2's per-world integer player ID can be re-resolved after load.
- Live `IGridNode` and `IGridConnection` objects are not serialized.

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
output.itemCapacity: long
output.fluidCapacity: long
output.itemCache: compound
output.fluidCache: compound
```

Wireless link tool NBT:

```text
target.bound: boolean
target.anchorType: string
target.dim: int
target.x: int
target.y: int
target.z: int
target.locatableSerial: long
target.ownerUuid: string
target.ownerName: string
```

## GT Integration Architecture

The implementation uses one physical block with two GT views:

```text
BaseMetaTileEntity in world
    |
    |-- real MTE: MTEWirelessUnifiedOutputAssemblyME extends MTEHatchOutputBus
    |       - visible player-facing block
    |       - item output bus role
    |       - owns wireless link, AE2 proxy, NBT, GUI, output core
    |
    |-- delegate MTE: WirelessFluidOutputDelegate extends MTEHatchOutput
            - no public item/block registration
            - shares the real MTE base tile
            - fluid output hatch role
            - delegates all storage to the real MTE output core
```

Item side:

- GT sees the real MTE as `MTEHatchOutputBus`.
- `storePartial(ItemStack, boolean)` delegates to `WirelessUnifiedOutputCore.storeItem`.
- `createTransaction()` returns an item transaction backed by the same core.

Fluid side:

- `MultiblockOutputAttachment` finds the current `MTEMultiBlockBase` controller.
- It inserts `WirelessFluidOutputDelegate` into `mOutputHatches`.
- The delegate is a real `MTEHatchOutput`, not a plain adapter.
- The delegate's `fill`, fluid store method, and transaction delegate to `WirelessUnifiedOutputCore.storeFluid`.
- Reattachment runs periodically because GT can clear hatch lists during structure rechecks.

## AE2 Link Lifecycle

`WirelessLinkManager` owns the AE2 connection lifecycle:

1. Create an `AENetworkProxy` for the real MTE.
2. On first tick, call `proxy.onReady()`.
3. Load proxy node state from NBT.
4. Resolve owner UUID/name to AE2 player ID and call `IGridNode.setPlayerID(int)`.
5. Resolve remote target node from controller coordinates or security terminal serial/coordinates.
6. Call `AEApi.instance().createGridConnection(localNode, remoteNode)`.
7. Periodically verify `connection.a()/b()` still match the current local and remote nodes.
8. On removal or chunk unload, destroy the connection and invalidate/unload the proxy.

## Wireless Link Tool

First version uses a custom item, not AE2 Memory Card mixins.

Tool interactions:

- Right-click `appeng.tile.networking.TileController`: save `ME_CONTROLLER` target with coordinates and owner identity.
- Right-click `appeng.tile.misc.TileSecurity`: save `SECURITY_TERMINAL` target with coordinates, locatable serial, and owner identity.
- Right-click the assembly: call `bindWirelessTarget` using the tool's saved target.
- Sneak-right-click: clear the tool target.

All state-changing behavior is server-side. Client side only provides tooltip and feedback display.

## GUI Requirements

The assembly GUI should show:

- connection state
- target type
- target dimension and coordinates
- owner name
- `itemCapacity`
- `fluidCapacity`
- cached item count
- cached fluid amount
- last connection failure message

The GUI must not cast long values through `IntSyncValue`. If MUI2 long synchronization is awkward, capacity can be read-only in the first implementation and configured through config/defaults until a safe long editor is added.

## Non-Goals For This Refactor

- No input hatch work.
- No GT base class mixins.
- No AE2 Memory Card mixin.
- No global automatic discovery of a player's ME networks.
- No two-public-block fallback unless the single-physical-block delegate strategy fails in runtime testing.

## Verification Checklist

Runtime verification must be done in the user's GTNH 2.9.0 beta instance:

1. Client starts without this mod applying GT or AE2 class mixins.
2. Link tool records an ME Controller.
3. Link tool records a Security Terminal.
4. Link tool binds the assembly.
5. GT multiblock structure accepts the assembly.
6. Item outputs enter ME.
7. Fluid outputs enter ME.
8. Recipes with both item and fluid outputs work.
9. Security Terminal permissions work with the bound owner.
10. Disconnecting the target leaves cached output intact.
11. Restoring the target reconnects and flushes cached output.
12. World reload preserves target, proxy state, capacities, and caches.
13. `itemCapacity` and `fluidCapacity` limits are enforced without truncating long totals.
