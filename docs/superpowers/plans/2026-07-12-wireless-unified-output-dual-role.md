# Wireless Unified Output Dual-Role Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make one physical wireless unified output assembly participate as both a valid GregTech fluid output hatch and item output bus for ordinary and steam multiblocks.

**Architecture:** The physical assembly remains the only MetaTileEntity and directly implements `IOutputBus`. Small RETURN Mixins augment ordinary and steam `getOutputBusses()` snapshots with valid dual-role hatches already present in `mOutputHatches`; a steam HEAD hook registers the same physical assembly through the native fluid-hatch path. Hidden delegates, raw controller-list reflection, and periodic reattachment are removed.

**Tech Stack:** Java 8/Jabel, Minecraft 1.7.10, GT5-Unofficial 5.09.52.594, GTNH Gradle, UniMixins/Sponge Mixin, JUnit 4.13.2

## Global Constraints

- Target exactly GT5-Unofficial `5.09.52.594`.
- Keep one physical output assembly; do not create a second MetaTileEntity delegate.
- Support ordinary `MTEMultiBlockBase` and GT++ `MTESteamMultiBlockBase`.
- Preserve GregTech `VoidProtectionHelper`, `ItemEjectionHelper`, atomic rollback, partial insertion, and transaction commit behavior.
- Keep the existing `WirelessUnifiedOutputCore.storeItem(..., simulate)` correction: simulation consumes the supplied stack remainder without mutating the cache.
- Use exact Mixin descriptors with `remap = false`; do not shadow controller fields or retain controller references.
- Preserve unrelated dirty-worktree changes, especially `ItemWirelessLinkTool` binding work.
- Do not commit without explicit user authorization.

---

## File Structure

**Create**

- `src/main/java/com/github/skyjack2033/wirelessmehatch/api/WirelessDualRoleOutput.java`: marker for physical output hatches that may also appear as `IOutputBus`.
- `src/main/java/com/github/skyjack2033/wirelessmehatch/output/DualRoleOutputBusHelper.java`: augment returned bus snapshots and handle steam native hatch registration.
- `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/MTEMultiBlockBaseMixin.java`: ordinary controller RETURN hook.
- `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/MTESteamMultiBlockBaseMixin.java`: steam RETURN and registration hooks.
- `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/WirelessMEHatchMixinPlugin.java`: verify pinned target method descriptors before applying Mixins.
- `src/test/java/com/github/skyjack2033/wirelessmehatch/output/DualRoleOutputBusHelperTest.java`: order and identity-deduplication tests for the pure list operation.
- `src/test/java/com/github/skyjack2033/wirelessmehatch/output/WirelessItemOutputTransactionTest.java`: transaction buffering and commit regression tests where GT classes can be loaded in the Gradle test runtime.
- `src/test/java/com/github/skyjack2033/wirelessmehatch/mixin/WirelessMEHatchMixinPluginTest.java`: descriptor-checking tests using synthetic ASM `ClassNode` data.

**Modify**

- `dependencies.gradle`: add JUnit and pinned test classpath dependencies.
- `gradle.properties`: enable Mixins and select the project plugin.
- `src/main/resources/mixins.wirelessmehatch.json`: require and list the two controller Mixins.
- `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessUnifiedOutputAssemblyME.java`: implement `IOutputBus` directly and remove attachment lifecycle.
- `docs/architecture/wireless-unified-output-assembly.md`: replace the invalid delegate design.
- `docs/architecture/gtnh-290-runtime-api-report.md`: document the validated identity invariant and pinned injection points.

**Delete**

- `src/main/java/com/github/skyjack2033/wirelessmehatch/output/MultiblockOutputAttachment.java`
- `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessItemOutputBusDelegate.java`
- `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessFluidOutputDelegate.java` remains deleted.
- `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/.gitkeep` after real Mixin classes exist.

---

### Task 1: Establish The Test And Mixin Build Baseline

**Files:**
- Modify: `dependencies.gradle`
- Modify: `gradle.properties`
- Modify: `src/main/resources/mixins.wirelessmehatch.json`

**Interfaces:**
- Consumes: the pinned dependency versions already declared by the project.
- Produces: a JUnit 4 test task with GT/AE types visible at compile time and an enabled project Mixin configuration.

- [ ] **Step 1: Record the current baseline**

Run:

```bash
./gradlew compileJava spotlessJavaCheck
```

Expected: both tasks pass before architecture edits. If either fails, preserve the output and separate pre-existing failure from subsequent changes.

- [ ] **Step 2: Add the test dependencies**

Add to the existing dependency block without changing production dependency versions:

```groovy
testImplementation("junit:junit:4.13.2")
testCompileOnly("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.594:dev") {
    transitive = false
}
testCompileOnly("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH:dev") {
    transitive = false
}
```

If Gradle tests require these classes at runtime rather than compile time, change only the two pinned game dependencies from `testCompileOnly` to `testRuntimeOnly` in addition to their compile declarations; do not add a second version.

- [ ] **Step 3: Enable the Mixin pipeline**

Set these existing properties:

```properties
usesMixins=true
mixinPlugin=mixin.WirelessMEHatchMixinPlugin
```

Update `mixins.wirelessmehatch.json` to retain its existing package/refmap compatibility values while setting:

```json
{
  "required": true,
  "plugin": "com.github.skyjack2033.wirelessmehatch.mixin.WirelessMEHatchMixinPlugin",
  "mixins": [
    "MTEMultiBlockBaseMixin",
    "MTESteamMultiBlockBaseMixin"
  ]
}
```

- [ ] **Step 4: Verify dependency and configuration resolution**

Run:

```bash
./gradlew dependencies --configuration testCompileClasspath
./gradlew processResources
```

Expected: JUnit 4.13.2 and the pinned GT/AE dev artifacts resolve; processed resources contain the project Mixin JSON without generation errors. Source compilation may remain blocked until Tasks 3 and 4 create the listed classes.

- [ ] **Step 5: Review checkpoint**

Run `git diff -- dependencies.gradle gradle.properties src/main/resources/mixins.wirelessmehatch.json` and confirm no unrelated dependency version or metadata changed.

---

### Task 2: Lock Down Item Transaction Semantics

**Files:**
- Test: `src/test/java/com/github/skyjack2033/wirelessmehatch/output/WirelessItemOutputTransactionTest.java`
- Modify only if a test proves a defect: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessItemOutputTransaction.java`
- Preserve: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessUnifiedOutputCore.java`

**Interfaces:**
- Consumes: `WirelessItemOutputTransaction(WirelessItemOutputHost, WirelessUnifiedOutputCore)` and GT `IOutputBusTransaction` methods already present in the worktree.
- Produces: verified local buffering until `commit()`, capacity-aware partial acceptance, and idempotent completion.

- [ ] **Step 1: Write regression tests against the current public/package API**

Cover these observable cases using an in-memory core with a no-op dirty callback and a small item capacity:

```java
@Test
public void uncommittedTransactionDoesNotMutateCore() { ... }

@Test
public void commitTransfersExactlyBufferedItems() { ... }

@Test
public void partialAcceptanceLeavesStackRemainder() { ... }

@Test
public void secondCommitDoesNotStoreTwice() { ... }

@Test
public void storeAfterCommitIsIgnored() { ... }
```

Use a real `ItemStack` and `GTUtility.ItemId`; do not mock GregTech transaction interfaces.

- [ ] **Step 2: Run the focused test and verify the red state**

Run:

```bash
./gradlew test --tests '*WirelessItemOutputTransactionTest'
```

Expected: tests either expose a specific current transaction defect or fail because test-only construction/access is unavailable. A classpath bootstrap failure is not a behavioral failure; fix Task 1 test runtime wiring before changing production logic.

- [ ] **Step 3: Make the smallest production correction if required**

Maintain this contract:

```java
public void commit() {
    if (!active) return;
    active = false;
    // Transfer each buffered quantity to core exactly once.
}
```

`storePartial` must update only transaction-local `Map<GTUtility.ItemId, Long>` state and the supplied stack remainder. It must not call `core.storeItem(..., false)` before `commit()`.

- [ ] **Step 4: Verify focused and aggregate tests**

Run:

```bash
./gradlew test --tests '*WirelessItemOutputTransactionTest'
./gradlew test
```

Expected: all transaction cases pass and no test reports duplicate core quantities.

- [ ] **Step 5: Review checkpoint**

Confirm that `WirelessUnifiedOutputCore.storeItem` still decrements `stack.stackSize` outside the `if (!simulate)` block and no fluid simulation behavior changed.

---

### Task 3: Add The Dual-Role Contract And Snapshot Helper

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/api/WirelessDualRoleOutput.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/DualRoleOutputBusHelper.java`
- Test: `src/test/java/com/github/skyjack2033/wirelessmehatch/output/DualRoleOutputBusHelperTest.java`

**Interfaces:**
- Produces: `WirelessDualRoleOutput` marker.
- Produces: `DualRoleOutputBusHelper.appendIdentity(List<IOutputBus>, IOutputBus)` for pure identity-deduplication.
- Produces: `DualRoleOutputBusHelper.augment(MTEMultiBlockBase, List<IOutputBus>)` for controller snapshots.
- Produces: `DualRoleOutputBusHelper.registerSteamOutput(MTESteamMultiBlockBase, IGregTechTileEntity, int)` returning nullable `Boolean` (`null` means not handled).

- [ ] **Step 1: Write the failing pure list tests**

Test that `appendIdentity`:

```java
@Test public void appendsCandidateAfterExistingBusses() { ... }
@Test public void doesNotAppendSameObjectTwice() { ... }
@Test public void distinctObjectsAreNotCollapsedByEquals() { ... }
```

Use lightweight `IOutputBus` stubs. Verify identity with `assertSame`, not equality.

- [ ] **Step 2: Run the helper test and verify failure**

Run:

```bash
./gradlew test --tests '*DualRoleOutputBusHelperTest'
```

Expected: FAIL because `DualRoleOutputBusHelper` does not exist.

- [ ] **Step 3: Add the marker and minimal helper**

Create an empty marker:

```java
public interface WirelessDualRoleOutput {}
```

Implement `appendIdentity` by scanning with `existing == candidate` and appending only when absent. Implement `augment` as:

```java
public static List<IOutputBus> augment(MTEMultiBlockBase controller, List<IOutputBus> busses) {
    for (MTEHatchOutput hatch : controller.mOutputHatches) {
        if (hatch != null
            && hatch.isValid()
            && hatch instanceof WirelessDualRoleOutput
            && hatch instanceof IOutputBus) {
            appendIdentity(busses, (IOutputBus) hatch);
        }
    }
    return busses;
}
```

Do not copy or mutate `controller.mOutputHatches`; mutate only the snapshot returned by `getOutputBusses()`.

Implement steam registration:

```java
public static Boolean registerSteamOutput(
    MTESteamMultiBlockBase controller,
    IGregTechTileEntity tile,
    int casingIndex) {
    if (!(tile.getMetaTileEntity() instanceof WirelessDualRoleOutput)) return null;
    return controller.addOutputHatchToMachineList(tile, casingIndex);
}
```

Guard null tile/meta-tile inputs according to surrounding project idiom.

- [ ] **Step 4: Run helper tests and compilation**

Run:

```bash
./gradlew test --tests '*DualRoleOutputBusHelperTest'
./gradlew compileJava
```

Expected: identity/order tests pass and all GT/GT++ method signatures compile against 5.09.52.594.

- [ ] **Step 5: Review checkpoint**

Check that the helper contains no reflection, cached controller references, raw-list insertion, delegate construction, or direct item-cache writes.

---

### Task 4: Make The Physical Assembly The Item Bus

**Files:**
- Modify: `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessUnifiedOutputAssemblyME.java`
- Preserve: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessItemOutputTransaction.java`

**Interfaces:**
- Consumes: `WirelessDualRoleOutput` and existing `WirelessItemOutputTransaction`.
- Produces: one valid `MTEHatchOutput` that also implements `IOutputBus`.

- [ ] **Step 1: Add a compile-level contract check**

Add a small test or compile assertion assigning an assembly reference to both interfaces where construction is practical:

```java
assertTrue(IOutputBus.class.isAssignableFrom(MTEWirelessUnifiedOutputAssemblyME.class));
assertTrue(WirelessDualRoleOutput.class.isAssignableFrom(MTEWirelessUnifiedOutputAssemblyME.class));
```

- [ ] **Step 2: Verify the check fails before implementation**

Run the focused test. Expected: FAIL because the assembly does not yet implement `IOutputBus` directly.

- [ ] **Step 3: Implement the direct bus contract**

Add `IOutputBus, WirelessDualRoleOutput` to the assembly declaration and implement the exact pinned API:

```java
@Override
public boolean isFiltered() {
    return false;
}

@Override
public boolean isFilteredToItem(GTUtility.ItemId itemId) {
    return false;
}

@Override
public OutputBusType getBusType() {
    return OutputBusType.MECacheUnfiltered;
}

@Override
public boolean storePartial(ItemStack stack, boolean simulate) {
    return outputCore.storeItem(stack, simulate);
}

@Override
public IOutputBusTransaction createTransaction() {
    return new WirelessItemOutputTransaction(this, outputCore);
}
```

Use the constructor expected by the current transaction source; do not introduce an adapter MTE.

- [ ] **Step 4: Remove attachment state from the assembly**

Delete the item-delegate and `MultiblockOutputAttachment` fields, constructor initialization, `onFirstTick` binding, periodic attachment calls, and invalidation/removal detach calls. Keep wireless connection checks, AE flush tick behavior, NBT, GUI, texture, and Link Tool logic unchanged.

- [ ] **Step 5: Verify compilation and transaction tests**

Run:

```bash
./gradlew compileJava test --tests '*WirelessItemOutputTransactionTest' --tests '*DualRoleOutputBusHelperTest'
```

Expected: compile succeeds; helper and transaction tests pass.

- [ ] **Step 6: Review checkpoint**

Confirm the assembly has exactly one base tile owner and no call path invokes `setBaseMetaTileEntity` on another MTE.

---

### Task 5: Add Exact Ordinary And Steam Mixins

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/MTEMultiBlockBaseMixin.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/MTESteamMultiBlockBaseMixin.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/WirelessMEHatchMixinPlugin.java`
- Test: `src/test/java/com/github/skyjack2033/wirelessmehatch/mixin/WirelessMEHatchMixinPluginTest.java`
- Delete: `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/.gitkeep`

**Interfaces:**
- Consumes: `DualRoleOutputBusHelper.augment(...)` and `.registerSteamOutput(...)`.
- Produces: exact runtime discovery hooks for ordinary and steam controllers.

- [ ] **Step 1: Test descriptor validation with synthetic ASM nodes**

Construct `ClassNode` objects containing or omitting these methods:

```text
getOutputBusses()Ljava/util/List;
addSteamBusOutput(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z
```

Assert the plugin validator accepts complete nodes and throws an `IllegalStateException` whose message contains `5.09.52.594`, target class, method name, and descriptor when missing.

- [ ] **Step 2: Run the plugin test and verify failure**

Run:

```bash
./gradlew test --tests '*WirelessMEHatchMixinPluginTest'
```

Expected: FAIL because the plugin/validator is absent.

- [ ] **Step 3: Implement the ordinary RETURN hook**

Target by string with `remap = false`:

```java
@Mixin(targets = "gregtech.api.metatileentity.implementations.MTEMultiBlockBase", remap = false)
public abstract class MTEMultiBlockBaseMixin {
    @Inject(
        method = "getOutputBusses()Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1,
        remap = false)
    private void wirelessmehatch$appendDualRoleOutputs(CallbackInfoReturnable<List> cir) {
        List result = cir.getReturnValue();
        DualRoleOutputBusHelper.augment((MTEMultiBlockBase) (Object) this, result);
        cir.setReturnValue(result);
    }
}
```

Keep the injected method raw/erased where required by Mixin. Do not add fields or shadows.

- [ ] **Step 4: Implement steam RETURN and HEAD hooks**

The steam RETURN hook mirrors the ordinary hook but targets:

```text
gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase
```

At cancellable HEAD of exact `addSteamBusOutput(...)`, call `registerSteamOutput`. When it returns non-null, set that Boolean as the return value and cancel; otherwise allow the original steam method to execute.

- [ ] **Step 5: Implement fail-fast descriptor checks**

Implement `IMixinConfigPlugin` without loading project MTE classes during plugin initialization. In `preApply`, inspect `ClassNode.methods` for only the methods required by the mixin being applied. Throw a precise `IllegalStateException` on mismatch. Return the configured mixin list unchanged and leave post-apply mutation empty.

- [ ] **Step 6: Run unit, compile, and Mixin processing checks**

Run:

```bash
./gradlew test --tests '*WirelessMEHatchMixinPluginTest'
./gradlew compileJava processResources
```

Expected: descriptor tests pass; both Mixins compile; the generated/refmap resources are present; no Mixin annotation processor warning reports an unresolved target descriptor.

- [ ] **Step 7: Review checkpoint**

Confirm both Mixins are stateless, exact-descriptor, `remap = false`, and call only the normal helper. Confirm no old broad injectors into output execution or void-protection classes remain.

---

### Task 6: Remove Invalid Delegates And Update Architecture Records

**Files:**
- Delete: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/MultiblockOutputAttachment.java`
- Delete: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessItemOutputBusDelegate.java`
- Keep deleted: `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessFluidOutputDelegate.java`
- Modify: `docs/architecture/wireless-unified-output-assembly.md`
- Modify: `docs/architecture/gtnh-290-runtime-api-report.md`
- Modify if necessary: `README.md`

**Interfaces:**
- Consumes: completed direct dual-role implementation.
- Produces: no hidden MetaTileEntity or dynamic controller-list attachment path.

- [ ] **Step 1: Search all stale references before deletion**

Run:

```bash
rg -n "MultiblockOutputAttachment|WirelessItemOutputBusDelegate|WirelessFluidOutputDelegate|setBaseMetaTileEntity|mOutputBusses" src/main docs README.md
```

Record each production reference; distinguish historical documentation from executable code.

- [ ] **Step 2: Delete the obsolete production classes**

Remove the attachment and both delegate source paths. Do not restore the already deleted fluid delegate.

- [ ] **Step 3: Update architecture documentation**

Document these verified facts:

- `MetaTileEntity.isValid()` requires physical tile identity.
- Hidden item and fluid delegates are invalid alternatives.
- The physical hatch implements `IOutputBus`.
- Ordinary and steam returned bus snapshots are augmented through exact pinned Mixins.
- Steam registration uses native `addOutputHatchToMachineList`.
- GT native transaction and void-protection paths remain authoritative.

Remove statements that claim no GT base-class Mixin is required or that a delegate may share the physical tile.

- [ ] **Step 4: Verify no stale executable references**

Run:

```bash
rg -n "MultiblockOutputAttachment|WirelessItemOutputBusDelegate|WirelessFluidOutputDelegate" src/main
```

Expected: no output.

- [ ] **Step 5: Run formatting and compilation**

Run:

```bash
./gradlew spotlessApply compileJava
```

Expected: formatting completes and Java compilation passes.

- [ ] **Step 6: Review checkpoint**

Use `git diff --check` and inspect the complete diff. Confirm `.zcode/` remains untracked/uncommitted and unrelated Link Tool edits are preserved.

---

### Task 7: Build And Runtime Verification

**Files:**
- No planned production edits; correct only failures proven by the following checks.
- Record manual results in: `docs/superpowers/plans/2026-07-12-wireless-unified-output-dual-role.md` under an appended execution-results section.

**Interfaces:**
- Consumes: the complete dual-role architecture.
- Produces: evidence for build, Mixin startup, and game behavior claims.

- [ ] **Step 1: Run the full static verification suite**

Run:

```bash
./gradlew spotlessJavaCheck compileJava test build
git diff --check
```

Expected: all Gradle tasks succeed and `git diff --check` emits no output.

- [ ] **Step 2: Start a development runtime**

List available run tasks first:

```bash
./gradlew tasks --group "GTNH development"
```

Run the available client or server task for the default environment, then the Java 17/multi-release variant if exposed by this build. Expected log evidence:

- no `MixinTransformerError`;
- no invalid injection descriptor;
- no `NoClassDefFoundError` involving `MTEExtendedPowerMultiBlockBase`;
- both controller Mixins report successful application;
- the main menu/server reaches a stable loaded state.

Stop the runtime after startup evidence is captured; do not claim game behavior from startup alone.

- [ ] **Step 3: Verify an ordinary mixed-output recipe in game**

With one valid assembly and a bound AE network, run a recipe producing at least one item and one fluid. Record machine start, item/fluid core cache deltas, and AE inventory deltas for:

```text
AE item free / fluid free
AE item full / fluid free
AE item free / fluid full
AE item full / fluid full
```

Expected: available channels progress; unavailable remainders remain cached; no output duplicates or disappears.

- [ ] **Step 4: Repeat the matrix on a steam multiblock**

Expected: the structure accepts the physical assembly, fluid fill reaches the same instance, item transaction creation reaches the same instance, and outcomes match the ordinary matrix.

- [ ] **Step 5: Verify structure and chunk lifecycle**

Break/reform each structure and unload/reload the chunk, then repeat one mixed-output recipe. Expected: one item transaction per output attempt, no duplicate snapshot entry, and no stale attachment because no attachment state exists.

- [ ] **Step 6: Report evidence and residual risks**

Append exact commands/results and manual scenarios completed. If in-game verification cannot be automated in this environment, state it as not run; do not replace it with a build-success claim. Keep legacy ID `17002`, old NBT migration, Link Tool redesign, and texture work listed as separate unresolved risks unless they directly blocked this test.
