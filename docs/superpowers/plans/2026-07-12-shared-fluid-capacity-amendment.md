# Shared Fluid Capacity Amendment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the dual-role output architecture by making its inherited fluid hatch write-only and applying exact aggregate void protection to its finite shared multi-fluid cache.

**Architecture:** The physical assembly remains the only MetaTileEntity and directly implements `IOutputBus`. It also implements a small shared-fluid-capacity interface that exposes exact long remaining capacity while all external tank, drain, container, and sided-fill paths are closed. A pure calculator determines the number of complete recipe parallels that fit all fluid outputs together, and a pinned RETURN Mixin applies that result only when `VoidProtectionHelper` sees a marked shared store.

**Tech Stack:** Java 8/Jabel, Minecraft Forge 1.7.10, GT5-Unofficial 5.09.52.594, GTNH Gradle, Sponge Mixin 0.8.7, JUnit 4.13.2

## Global Constraints

- Target exactly GT5-Unofficial `5.09.52.594`.
- Keep one physical output assembly; never create a second MetaTileEntity delegate.
- Support ordinary `MTEMultiBlockBase` and GT++ `MTESteamMultiBlockBase`.
- Preserve GregTech item transactions and prevent fluid output admission from exceeding the shared cache's remaining capacity.
- The assembly accepts recipe fluid only through unsided `fill(FluidStack, boolean)`; pipes, containers, GUI tank access, and all drain paths must not mutate a second inherited `mFluid` store.
- Use exact Mixin descriptors with `remap = false`, `require = 1`, string targets, stateless injection classes, and fail-fast plugin validation.
- Preserve all unrelated dirty-worktree changes and do not commit without explicit authorization.

---

### Task 4: Seal The Physical Hatch Fluid Contract

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/api/SharedFluidOutputStore.java`
- Modify: `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessUnifiedOutputAssemblyME.java`
- Create: `src/test/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessUnifiedOutputAssemblyMETest.java`

**Interfaces:**
- Produces: `SharedFluidOutputStore.getSharedFluidRemainingCapacity()` returning an exact nonnegative `long`.
- Preserves: direct `IOutputBus` and `WirelessDualRoleOutput` implementation from original Task 4.
- Consumes later: the aggregate void-protection helper in Amendment Task 5.

- [ ] **Step 1: Write the failing fluid-contract tests**

Use a package-private constructor/test seam only if constructing the assembly without a live tile is otherwise impossible. Cover:

```java
@Test public void sharedRemainingCapacityUsesLongAggregateOccupancy() { ... }
@Test public void unsidedFillSimulatesThenCommitsAgainstCoreCapacity() { ... }
@Test public void sidedFillAndAllDrainPathsAreDenied() { ... }
@Test public void containerAndInventoryTankPathsAreDisabled() { ... }
@Test public void inheritedFluidIsClearedWhenUnifiedNbtLoads() { ... }
```

For a capacity of 10, simulate 6 mB without mutation, commit 6, verify remaining 4, commit 4, then verify remaining 0 and a further fill returns 0. Assert the supplied `FluidStack.amount` is unchanged by simulation and commit.

- [ ] **Step 2: Run the focused test and verify the red state**

Run:

```bash
./gradlew test --tests '*MTEWirelessUnifiedOutputAssemblyMETest' --rerun-tasks
```

Expected: contract assertions fail because the assembly still exposes inherited sided/tank behavior and does not implement `SharedFluidOutputStore`.

- [ ] **Step 3: Add the exact shared-capacity interface**

Create:

```java
public interface SharedFluidOutputStore {
    long getSharedFluidRemainingCapacity();
}
```

The assembly implementation returns:

```java
Math.max(0L, outputCore.getFluidCapacity() - outputCore.getFluidCached())
```

Use saturating/nonnegative arithmetic; cached values at or above capacity report zero.

- [ ] **Step 4: Seal external fluid and inventory paths**

Keep only unsided `fill(FluidStack, boolean)` connected to `outputCore.storeFluid(fluid, !doFill)`. Override the pinned APIs so:

```text
fill(ForgeDirection, FluidStack, boolean) -> 0
all drain overloads -> null
canFill/canDrain -> false
canTankBeFilled/canTankBeEmptied -> false
doesFillContainers -> false
isFluidInputAllowed/isLiquidOutput -> false
isValidSlot/allowPutStack/allowPullStack -> false
getFluid -> null
getTankInfo(side) -> GTValues.emptyFluidTankInfo
```

`getFluidAmount()` returns the bounded aggregate cached amount and `getCapacity()` returns the bounded configured capacity for diagnostics only. `isEmptyAndAcceptsAnyFluid()` reflects the core's actual empty/capacity state. `canStoreFluid()` requires a nonnull valid fluid, positive amount, and positive shared remaining capacity.

After Link Tool handling, both right-click overloads return `true` without calling the inherited fluid-hatch GUI.

- [ ] **Step 5: Eliminate inherited `mFluid` persistence**

Before superclass save, force `mFluid = null`; after superclass save remove the `mFluid` key. After superclass load, copy and clear inherited `mFluid` before reading unified output data.

Treat `wirelessOutput` as authoritative. If it is absent, preserve the existing legacy provider migration. Migrate a standalone inherited `mFluid` only when neither `wirelessOutput` nor a legacy `fluidProvider` cache is present, then clear `mFluid`. Expand core capacity temporarily if needed so migration does not truncate, and restore the configured capacity afterward.

Clamp `setFluidCapacity` to at least `outputCore.getFluidCached()` so runtime configuration cannot invalidate already admitted/cache-resident output.

- [ ] **Step 6: Run focused and existing tests**

Run:

```bash
./gradlew compileJava test \
  --tests '*MTEWirelessUnifiedOutputAssemblyMETest' \
  --tests '*WirelessItemOutputTransactionTest' \
  --tests '*DualRoleOutputBusHelperTest'
```

Expected: all tests pass and direct item-bus behavior remains intact.

- [ ] **Step 7: Review checkpoint**

Confirm the assembly has one cache, `mFluid` cannot be populated through supported runtime paths, no inherited tank GUI opens, and no delegate/attachment lifecycle was reintroduced.

---

### Task 5: Apply Aggregate Multi-Fluid Void Protection

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/SharedFluidCapacityCalculator.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/SharedFluidVoidProtectionHelper.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/VoidProtectionHelperMixin.java`
- Modify: `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/WirelessMEHatchMixinPlugin.java`
- Modify: `src/main/resources/mixins.wirelessmehatch.json`
- Test: `src/test/java/com/github/skyjack2033/wirelessmehatch/output/SharedFluidCapacityCalculatorTest.java`
- Test: `src/test/java/com/github/skyjack2033/wirelessmehatch/mixin/WirelessMEHatchMixinPluginTest.java`

**Interfaces:**
- Consumes: `SharedFluidOutputStore.getSharedFluidRemainingCapacity()`.
- Produces: `SharedFluidCapacityCalculator.limit(int maxParallel, long remaining, FluidStack[] outputs, IntUnaryOperator chanceGetter)`.
- Produces: a RETURN override for `VoidProtectionHelper.calculateMaxFluidParallels()I` only when marked shared stores are present.

- [ ] **Step 1: Write pure aggregate-calculator tests**

Cover exact fit and overcommit:

```text
remaining 10000, outputs 4000 + 6000 -> 1 parallel
remaining 10000, outputs 6000 + 6000 -> 0 parallels
remaining 24000, outputs 4000 + 6000 -> 2 parallels
```

Also cover duplicate fluid types, null/zero outputs, chances `1`, `10000`, and `10001`, `maxParallel` clamping, long remaining capacity above `Integer.MAX_VALUE`, and saturating multiplication/addition.

- [ ] **Step 2: Verify the calculator tests fail before implementation**

Run:

```bash
./gradlew test --tests '*SharedFluidCapacityCalculatorTest' --rerun-tasks
```

Expected: FAIL because the calculator does not exist.

- [ ] **Step 3: Implement the pure calculator**

For each nonnull positive output, calculate:

```java
long occurrences = Math.max(0L, ((long) chanceGetter.applyAsInt(index) + 9999L) / 10000L);
requiredPerParallel += saturatingMultiply(output.amount, occurrences);
```

Use saturating arithmetic. If required per parallel is zero, leave `maxParallel` unchanged. Otherwise return:

```java
(int) Math.min((long) maxParallel, remaining / requiredPerParallel)
```

Never return a negative value.

- [ ] **Step 4: Add the normal helper around GT stores**

`SharedFluidVoidProtectionHelper` receives the machine, fluid outputs, max parallel, and chance getter. It calls `machine.getFluidOutputSlots(fluidOutputs)`, identity-deduplicates stores implementing `SharedFluidOutputStore`, and saturating-sums their exact remaining capacities.

Return a nullable `Integer`: `null` when no shared store is present, otherwise the calculator result. Ordinary GT machines/hatches therefore retain the original return value unchanged.

- [ ] **Step 5: Add the exact VoidProtectionHelper RETURN Mixin**

Use:

```text
target: gregtech.api.util.VoidProtectionHelper
method: calculateMaxFluidParallels()I
at: RETURN
cancellable: true
require: 1
remap: false
```

Shadow only the exact pinned fields required to call the normal helper:

```text
machine Lgregtech/api/interfaces/tileentity/IVoidable;
fluidOutputs [Lnet/minecraftforge/fluids/FluidStack;
maxParallel I
fluidOutputChanceGetter Lit/unimi/dsi/fastutil/ints/Int2IntFunction;
```

At every return, call the normal helper. Replace the return only when its result is nonnull. Do not reserve capacity or write caches during recipe checking.

- [ ] **Step 6: Extend plugin validation and Mixin configuration**

Add `VoidProtectionHelperMixin` to the required JSON list. Extend the plugin's synthetic-ASM tests and `preApply` checks for:

```text
calculateMaxFluidParallels()I
```

The error must name GT `5.09.52.594`, target class, method, and descriptor. Keep project MTE/helper classes out of plugin initialization.

- [ ] **Step 7: Run focused and aggregate checks**

Run:

```bash
./gradlew test --tests '*SharedFluidCapacityCalculatorTest' --tests '*WirelessMEHatchMixinPluginTest'
./gradlew compileJava processResources test
```

Expected: pure aggregate cases, descriptor validation, all existing tests, compilation, and resource processing pass.

- [ ] **Step 8: Review checkpoint**

Confirm the Mixin does not change ordinary machines, the calculator limits the sum of all fluid outputs rather than crediting shared capacity per fluid, and no simulation path mutates core state.

---

### Task 7: Extended Verification

**Files:**
- Modify execution-results section only: `docs/superpowers/plans/2026-07-12-wireless-unified-output-dual-role.md`

- [ ] **Step 1: Run full static verification**

```bash
./gradlew spotlessJavaCheck compileJava test build
git diff --check
```

- [ ] **Step 2: Audit all three Mixin targets in root and Java 17 class variants**

Verify exact descriptors for ordinary `getOutputBusses`, steam `getOutputBusses`/`addSteamBusOutput`, and `VoidProtectionHelper.calculateMaxFluidParallels` in both selected runtime variants.

- [ ] **Step 3: Start a Java 17 development runtime**

Confirm there is no `MixinTransformerError`, descriptor mismatch, or GT/GTPP hierarchy `NoClassDefFoundError`, and all three Mixins apply.

- [ ] **Step 4: Extend the in-game matrix**

In addition to item+fluid recipes, run a recipe with at least two fluid outputs sharing a finite remaining cache:

```text
exact aggregate fit -> recipe admitted and all outputs cached
aggregate one mB short -> recipe rejected before execution
one fluid individually fits but total does not -> recipe rejected
```

Repeat on ordinary and steam multiblocks. Verify no excess output is discarded.

- [ ] **Step 5: Record unrun manual scenarios honestly**

A successful build/startup does not prove in-game recipe behavior. Record each scenario as passed, failed, blocked, or not run with exact evidence.
