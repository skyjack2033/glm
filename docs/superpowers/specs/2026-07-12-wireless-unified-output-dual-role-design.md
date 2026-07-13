# Wireless Unified Output Dual-Role Design

Date: 2026-07-12
Target dependency: GT5-Unofficial 5.09.52.594

## Goal

Keep one physical wireless unified output assembly that participates in a multiblock as both a fluid output hatch and an item output bus. It must preserve GregTech void protection and transaction behavior for ordinary and steam multiblocks, then flush both cached resource types to the bound AE network.

## Confirmed Root Cause

The existing hidden-delegate designs violate GregTech's MetaTileEntity identity invariant.

`MetaTileEntity.isValid()` requires the base tile's active MTE to be the same object as the MTE being checked. A second fluid or item delegate cannot share the physical tile and remain valid:

- Calling the normal `setBaseMetaTileEntity` on the delegate replaces the physical assembly as the tile's active MTE and invalidates the assembly.
- Writing only the delegate's private base-tile field avoids replacement, but the delegate remains invalid because the base tile still returns the physical assembly.
- GregTech filters output buses and hatches through validity checks before output simulation and dispatch.

The current worktree uses the second form. The real assembly is a valid `MTEHatchOutput`, while `WirelessItemOutputBusDelegate` is inserted into a raw controller list but filtered out before `createTransaction()` can run. Periodic reattachment cannot change that validity result.

The AE flush path is not the primary fault. `WirelessUnifiedOutputCore.flush()` obtains item and fluid inventories and flushes both independently. The missing item path occurs before item data reaches the shared cache.

## Chosen Architecture

### One real dual-role MTE

`MTEWirelessUnifiedOutputAssemblyME` remains the only MTE attached to the physical GregTech tile. It continues to extend `MTEHatchOutput` and also directly implements GregTech's `IOutputBus` interface.

The assembly provides its item output role through `createTransaction()`, returning `WirelessItemOutputTransaction`. The transaction remains local until GregTech invokes `commit()`. Fluid insertion continues through the real hatch's `fill(...)` implementation.

The following invalid architecture components are removed:

- `WirelessItemOutputBusDelegate`
- `MultiblockOutputAttachment`
- Neighbor-controller scanning
- Periodic raw-list reattachment
- Reflection that writes a delegate base-tile field or controller output list

No second `MetaTileEntity` is created for either output role.

### Ordinary multiblock discovery

Add a precise RETURN injection to:

```text
gregtech.api.metatileentity.implementations.MTEMultiBlockBase.getOutputBusses()Ljava/util/List;
```

The hook passes the returned snapshot and controller to a normal project helper. The helper scans the controller's valid output hatches and appends hatches that implement the project's dual-role marker and `IOutputBus`.

The appended object is the physical assembly itself. It satisfies the MTE identity invariant and enters GregTech's existing `ItemEjectionHelper` transaction path.

### Steam multiblock discovery

`MTESteamMultiBlockBase` overrides `getOutputBusses()` and returns only its steam output list. Add a second precise RETURN injection to:

```text
gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase.getOutputBusses()Ljava/util/List;
```

It invokes the same helper so item discovery has the same behavior as ordinary multiblocks.

Steam structure discovery also needs to accept the physical unified assembly at an output position and register it as a real fluid output hatch. Add a narrowly scoped compatibility hook around:

```text
MTESteamMultiBlockBase.addSteamBusOutput(IGregTechTileEntity, int)Z
```

When the tile contains the unified assembly, the compatibility path delegates to GregTech's native `addOutputHatchToMachineList(...)`. It does not create an item delegate or mutate raw item-bus lists. Once the assembly is in `mOutputHatches`, the steam `getOutputBusses()` RETURN hook exposes the same instance as `IOutputBus`.

## Native Transaction Semantics

The design intentionally augments `getOutputBusses()` instead of bypassing GregTech output call sites.

GregTech continues to own:

- `VoidProtectionHelper` item-space simulation
- `ItemEjectionHelper` transaction creation
- Atomic whole-stack rollback in `addOutputAtomic`
- Partial acceptance in `addOutputPartial`
- Batch commit behavior in `addItemOutputs`

`WirelessItemOutputTransaction` buffers accepted item quantities until `commit()`. Creating or abandoning a transaction must not mutate `WirelessUnifiedOutputCore`. Repeated commit and post-commit store attempts must have no effect.

`WirelessUnifiedOutputCore.storeItem(..., simulate)` must retain the current correction: both real and simulated calls reduce the supplied stack to its unaccepted remainder, while only real calls mutate the core cache.

Forge fluid simulation remains separate: `fill(..., false)` returns the accepted amount and does not mutate the supplied `FluidStack`.

## Helper Rules

The output-list augmentation helper must:

- Work on the returned snapshot rather than controller-owned raw lists.
- Append only valid physical hatches that implement the dual-role contract and `IOutputBus`.
- Avoid duplicate object identities if another compatibility layer already exposed the assembly.
- Preserve the order of all existing output buses.
- Avoid retaining controller or hatch references after the call.

The helper must contain the project-specific type checks so Mixin methods remain small and minimize transformer-time class resolution.

## Mixin Compatibility Rules

The mixins target GT5-Unofficial 5.09.52.594 only. They use exact method descriptors and `remap = false`.

Callback signatures use erased `List` types where needed. Mixins do not shadow unrelated GT classes or add controller fields. The implementation must account for both the root and Java 17 variants in the multi-release GT jar.

If the dependency version or target API is incompatible, the compatibility layer must emit a clear error naming the target version and entry point. It must not fall back to hidden delegates or direct item commits outside GregTech's void-protection transaction.

## Shared AE Flush

The real assembly remains responsible for ticking and calling `WirelessUnifiedOutputCore.flush()`.

Item and fluid caches are flushed independently to their corresponding AE inventories. A failure or lack of capacity on one channel must leave that channel's remainder cached without preventing the other channel from making progress.

This design does not change wireless binding, capacity configuration, Link Tool behavior, or the current cache NBT schema except where compilation requires removal of delegate lifecycle state.

## Validation

### Automated behavior

Verify at minimum:

- An uncommitted item transaction does not change the core.
- Full and partial item acceptance consumes the simulated stack correctly.
- Capacity exhaustion leaves the correct remainder.
- Commit transfers only buffered quantities.
- Repeated commit and stores after commit have no effect.
- Output-list augmentation appends one valid dual-role assembly, preserves existing order, excludes invalid/non-dual hatches, and avoids duplicates.

### Build and startup

Run formatting checks, Java compilation, and the full Gradle build. Start the test client or server and confirm both target classes transform without `MixinTransformerError`, descriptor mismatch, or `NoClassDefFoundError`.

### In-game matrix

For one ordinary and one steam multiblock, run a recipe that produces both an item and a fluid. Test:

1. AE has capacity for both.
2. AE item storage is full.
3. AE fluid storage is full.
4. Both AE storage channels are full.

Record whether the machine starts, item/fluid core cache changes, AE inventory deltas, and retained remainders. No scenario may duplicate or silently discard output.

Repeat after structure teardown/rebuild and chunk unload/reload. The assembly must not appear twice in an output snapshot and no stale controller attachment may remain.

## Out of Scope

Legacy meta-tile ID `17002`, complete old-world NBT migration, Link Tool redesign, and texture changes remain separate risks unless they block mixed-output validation. They must not be bundled into this architecture fix.

## Acceptance Criteria

The change is complete when:

- The workspace contains no hidden output MTE delegate or dynamic controller attachment path.
- The physical assembly is valid and discoverable as both a fluid hatch and an `IOutputBus`.
- GregTech's native item simulation and commit flow reaches `WirelessItemOutputTransaction` for ordinary and steam multiblocks.
- A mixed-output recipe reaches both AE inventories without duplication or loss under the capacity matrix.
- Structure and chunk lifecycle tests do not create duplicate or stale output roles.
- The pinned GT version builds and starts with all targeted Mixins applied successfully.
