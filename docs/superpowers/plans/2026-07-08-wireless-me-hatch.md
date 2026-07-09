# Wireless ME Hatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build two wireless ME hatches (Wireless Output Hatch ME, Wireless Input Hatch ME) for GTNH that auto-connect to a bound ME network via AE2's `createGridConnection` API, each simultaneously handling items and fluids.

**Architecture:** Each hatch owns an `AENetworkProxy` (one `IGridNode`) and connects to a target ME network by binding an AE2 Memory Card to a Wireless Access Point's locatable serial, then calling `AEApi.instance().createGridConnection(myNode, wapNode)` to form an invisible connection (same API as Quantum Bridge / P2P ME tunnel). The Output hatch extends `MTEHatchOutput` and manually implements item-bus logic with two `MTEHatchOutputMEBase` providers; a Mixin on `MTEMultiBlockBase` adds `IDualOutputHatch` recognition so all multiblocks treat it as both fluid hatch and item bus. The Input hatch extends `MTEHatchInput` and implements GT-native `IDualInputHatch`.

**Tech Stack:** Minecraft 1.7.10, Forge 10.13.4.1614, GregTech 5-Unofficial 5.09.54.20, Applied-Energistics-2-Unofficial rv3-beta-1000-GTNH, ModularUI 2 (`com.cleanroommc.modularui`), UniMixins (SpongePowered Mixin), Jabel (modern Java syntax → J8 bytecode).

**Reference spec:** `docs/superpowers/specs/2026-07-08-wireless-me-hatch-design.md`

## Global Constraints

- **Minecraft/Forge:** 1.7.10 / 10.13.4.1614 (verbatim from `gradle.properties`)
- **GregTech version:** `5.09.54.20` (GTNH 2.9.0 nightly lock)
- **AE2 version:** `rv3-beta-1000-GTNH` (GTNH 2.9.0 nightly lock)
- **Maven group prefix:** `com.github.GTNewHorizons` for all GTNH deps; classifier always `:dev`
- **MetaTileEntity ID rule:** IDs < 2048 reserved by GT (forbidden); 4096-5095 (Frames) and 5096-6099 (Pipes) forbidden. Use IDs ≥ 17000, verify no collision with `-Dgt.debug=true` at runtime.
- **modId:** `wirelessmehatch`; **modGroup:** `com.github.skyjack2033.wirelessmehatch`
- **No distance check:** bound connection works cross-dimension (spec §2.2)
- **Single tier, max capacity:** Output hatch base cache capacity = `Long.MAX_VALUE`; cell slots retained for filtering (spec §6)
- **GUI framework:** ModularUI 2 only (`useMui2()` returns `true`); package `com.cleanroommc.modularui`
- **Java:** modern syntax via Jabel, compiles to J8 bytecode — do not use APIs not in J8 stdlib
- **Spotless:** project enforces spotless formatting; run `./gradlew spotlessApply` before each commit
- **Testing:** No unit test framework — verification via `./gradlew build` + in-game functional checks

---

## File Structure

### Files to create

| Path | Responsibility |
|---|---|
| `src/main/java/com/github/skyjack2033/wirelessmehatch/WirelessMEHatch.java` | `@Mod` entry, lifecycle hooks |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/CommonProxy.java` | Registration calls |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/ClientProxy.java` | Client-side (empty for now) |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/Config.java` | Forge config (no required keys initially) |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/api/IDualOutputHatch.java` | Interface for dual output (fluid+item) hatch recognition |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/api/IWirelessMEHatch.java` | Interface marking a wireless ME hatch (bind/unbind/query) |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/me/WirelessGridManager.java` | Manages `createGridConnection` lifecycle: bind, establish, tick-check, destroy |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/me/MemoryCardHandler.java` | Reads/writes WAP serial on AE2 Memory Card NBT |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessOutputHatchME.java` | Wireless merged output hatch (extends `MTEHatchOutput`, impl `IDualOutputHatch`) |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessInputHatchME.java` | Wireless merged input hatch (extends `MTEHatchInput`, impl `IDualInputHatch`) |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/loader/MetaTileEntityLoader.java` | Registers the two MTEs into `GregTechAPI.METATILEENTITIES` |
| `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/MTEMultiBlockBaseMixin.java` | Adds `mDualOutputHatches` list + `IDualOutputHatch` recognition + output dispatch |
| `src/main/resources/mixin.wirelessmehatch.json` | Mixin config (auto-generated name from modId) |
| `src/main/resources/assets/wirelessmehatch/textures/...` | Block/item/GUI textures |

### Files to modify

| Path | Change |
|---|---|
| `gradle.properties` | Rename modId/modGroup/modName/token class; enable mixins |
| `dependencies.gradle` | Add GT5-Unofficial + AE2 deps |
| `src/main/resources/mcmod.info` | Update description, authors, dependencies |

### Files to delete (template boilerplate replaced by renamed equivalents)

| Path | Note |
|---|---|
| `src/main/java/com/myname/mymodid/MyMod.java` | Replaced by `WirelessMEHatch.java` |
| `src/main/java/com/myname/mymodid/CommonProxy.java` | Replaced |
| `src/main/java/com/myname/mymodid/ClientProxy.java` | Replaced |
| `src/main/java/com/myname/mymodid/Config.java` | Replaced |

---

## Task 1: Project scaffolding — rename mod, add deps, enable mixins

**Goal:** Rename the template mod to `wirelessmehatch`, add GregTech + AE2 dependencies, enable Mixin. Verify the project still builds (mods won't load yet, but compilation must succeed).

**Files:**
- Modify: `gradle.properties`
- Modify: `dependencies.gradle`
- Modify: `src/main/resources/mcmod.info`
- Delete: `src/main/java/com/myname/mymodid/{MyMod,CommonProxy,ClientProxy,Config}.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/{WirelessMEHatch,CommonProxy,ClientProxy,Config}.java`

**Interfaces:** None (foundation).

- [ ] **Step 1: Update `gradle.properties`**

Edit the following keys (leave all others unchanged):

```properties
modName = Wireless ME Hatch
modId = wirelessmehatch
modGroup = com.github.skyjack2033.wirelessmehatch
useModGroupForPublishing = true
generateGradleTokenClass = com.github.skyjack2033.wirelessmehatch.Tags
usesMixins = true
mixinsPackage = com.github.skyjack2033.wirelessmehatch.mixin
```

Leave `forceEnableMixins`, `usesMixinDebug`, `mixinPlugin`, `separateMixinSourceSet`, `coreModClass`, `containsMixinsAndOrCoreModOnly` at their defaults.

- [ ] **Step 2: Add dependencies in `dependencies.gradle`**

Replace the body of the `dependencies { }` block with:

```groovy
dependencies {
    compileOnly("com.github.GTNewHorizons:GT5-Unofficial:5.09.54.20:dev") { transitive = false }
    devOnlyNonPublishable(rfg.deobf("com.github.GTNewHorizons:GT5-Unofficial:5.09.54.20:dev"))
    compileOnly("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-1000-GTNH:dev") { transitive = false }
    devOnlyNonPublishable(rfg.deobf("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-1000-GTNH:dev"))
}
```

`compileOnly` makes GT/AE2 visible at compile time; `devOnlyNonPublishable(rfg.deobf(...))` provides deobfuscated dev jars for `runClient`/`runServer` without publishing them as Maven deps. `transitive = false` on `compileOnly` avoids pulling GT's transitive deps into our compile classpath (the dev jar already has what we need).

- [ ] **Step 3: Update `mcmod.info`**

Replace the entire file contents with:

```json
{
	"modListVersion": 2,
	"modList": [{
		"modid": "${modId}",
		"name": "${modName}",
		"description": "Wireless ME input/output hatches for GregTech. Bind to an AE2 Wireless Access Point with a Memory Card to connect to your ME network without cables.",
		"version": "${modVersion}",
		"mcversion": "${minecraftVersion}",
		"url": "",
		"updateUrl": "",
		"authorList": ["skyjack2033"],
		"credits": "",
		"logoFile": "",
		"screenshots": [],
		"parent": "",
		"requiredMods": [],
		"dependencies": [],
		"dependants": [],
		"useDependencyInformation": false
	}]
}
```

- [ ] **Step 4: Delete old template source files**

Delete the four files under `src/main/java/com/myname/mymodid/`. The entire `com/myname/mymodid` directory tree should be removed.

- [ ] **Step 5: Create `WirelessMEHatch.java`**

```java
package com.github.skyjack2033.wirelessmehatch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = WirelessMEHatch.MODID, version = Tags.VERSION, name = "Wireless ME Hatch",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gregtech;required-after:appliedenergistics2")
public class WirelessMEHatch {

    public static final String MODID = "wirelessmehatch";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.github.skyjack2033.wirelessmehatch.ClientProxy",
        serverSide = "com.github.skyjack2033.wirelessmehatch.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
```

- [ ] **Step 6: Create `CommonProxy.java`**

```java
package com.github.skyjack2033.wirelessmehatch;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        WirelessMEHatch.LOG.info("Loading Wireless ME Hatch " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        MetaTileEntityLoader.register();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
```

- [ ] **Step 7: Create `ClientProxy.java`**

```java
package com.github.skyjack2033.wirelessmehatch;

public class ClientProxy extends CommonProxy {
    // Override CommonProxy methods here for client-specific behaviour.
}
```

- [ ] **Step 8: Create `Config.java`**

```java
package com.github.skyjack2033.wirelessmehatch;

public class Config {
    // Reserved for future config keys. No configuration needed yet.
}
```

- [ ] **Step 9: Create a stub `MetaTileEntityLoader` so compilation succeeds**

```java
package com.github.skyjack2033.wirelessmehatch.loader;

public final class MetaTileEntityLoader {

    private MetaTileEntityLoader() {}

    public static void register() {
        // Will be filled in Task 8.
    }
}
```

- [ ] **Step 10: Run `./gradlew spotlessApply` then `./gradlew build`**

Run:
```bash
./gradlew spotlessApply
./gradlew build
```
Expected: BUILD SUCCESSFUL. The build may emit warnings about mixin package not existing yet — that's fine, mixins won't be applied until the package has classes. If `build` fails on a missing `com.github.skyjack2033.wirelessmehatch.mixin` package, that is acceptable for this task; proceed but note it. If it fails on a compile error in our Java files, fix and rebuild.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "chore: rename mod to wirelessmehatch, add GT5/AE2 deps, enable mixins"
```

---

## Task 2: `IDualOutputHatch` and `IWirelessMEHatch` interfaces

**Goal:** Define the two marker/capability interfaces that the output hatch implements and that the Mixin + memory-card handler consume.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/api/IDualOutputHatch.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/api/IWirelessMEHatch.java`

**Interfaces:**
- Produces: `IDualOutputHatch` (consumed by `MTEMultiBlockBaseMixin` in Task 7), `IWirelessMEHatch` (consumed by `MemoryCardHandler` in Task 4 and both hatch classes in Tasks 5-6).

- [ ] **Step 1: Create `IDualOutputHatch.java`**

```java
package com.github.skyjack2033.wirelessmehatch.api;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IOutputBusTransaction;
import gregtech.api.interfaces.tileentity.IOutputHatchTransaction;
import net.minecraft.item.ItemStack;

/**
 * Marks a MetaTileEntity that acts as BOTH a fluid output hatch and an item output bus
 * simultaneously. Recognised by {@code MTEMultiBlockBaseMixin} so that multiblock controllers
 * treat the implementor as a dual output (added to {@code mDualOutputHatches}).
 *
 * Symmetric to GT's native {@code IDualInputHatch} for the input side.
 */
public interface IDualOutputHatch extends IMetaTileEntity {

    /** Create a transaction for fluid output (mirrors {@code IOutputHatch#createTransaction}). */
    IOutputHatchTransaction createFluidTransaction();

    /** Create a transaction for item output (mirrors {@code IOutputBus#createTransaction}). */
    IOutputBusTransaction createItemTransaction();

    /**
     * Try to store an item stack into this output (delegates to the item provider).
     *
     * @param stack    item to store (mutated: stack size reduced by amount stored)
     * @param simulate if true, only test whether storage is possible without mutating state
     * @return true if (in simulate mode) storage would succeed / (in real mode) storage happened
     */
    boolean storePartial(ItemStack stack, boolean simulate);
}
```

**Note:** The exact package paths for `IOutputBusTransaction` / `IOutputHatchTransaction` must be verified against GT 5.09.54.20 source. The implementer should grep the GT dev jar's sources (or `https://github.com/GTNewHorizons/GT5-Unofficial` master) for `interface IOutputHatchTransaction` and `interface IOutputBusTransaction` and correct the imports if they differ from `gregtech.api.interfaces.tileentity`.

- [ ] **Step 2: Create `IWirelessMEHatch.java`**

```java
package com.github.skyjack2033.wirelessmehatch.api;

/**
 * Marks a hatch that connects to an ME network wirelessly via a bound AE2 Wireless Access Point
 * serial. The Memory Card handler uses these methods to bind/unbind a WAP serial.
 */
public interface IWirelessMEHatch {

    /** @return the bound WAP locatable serial, or 0 if unbound. */
    long getBoundWapSerial();

    /**
     * Bind this hatch to a WAP serial. Pass 0 to unbind.
     * Implementations must persist the serial to NBT and (re)establish the grid connection.
     */
    void setBoundWapSerial(long serial);

    /** @return true if the grid connection to the bound network is currently active. */
    boolean isWirelessConnected();
}
```

- [ ] **Step 3: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(api): add IDualOutputHatch and IWirelessMEHatch interfaces"
```

---

## Task 3: `WirelessGridManager` — createGridConnection lifecycle

**Goal:** Implement the core wireless connection logic: bind to a WAP serial, lazily establish an invisible grid connection via `AEApi.instance().createGridConnection`, periodically verify it, and destroy it cleanly on unbind/invalidate.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/me/WirelessGridManager.java`

**Interfaces:**
- Consumes: AE2 `AENetworkProxy`, `IGridNode`, `ILocatable` registry, `createGridConnection`.
- Produces: `WirelessGridManager` (consumed by both hatch classes in Tasks 5-6). Methods: `bind(long)`, `unbind()`, `tickCheck()`, `isConnected()`, `invalidate()`, `writeToNBT(NBT)`, `readFromNBT(NBT)`.

- [ ] **Step 1: Create `WirelessGridManager.java`**

```java
package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridConnection;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import cpw.mods.fml.common.FMLLog;

/**
 * Manages a wireless (invisible) grid connection between a hatch's own {@link AENetworkProxy}
 * node and a bound AE2 Wireless Access Point's node, using
 * {@code AEApi.instance().createGridConnection}. Cross-dimension capable; no range check.
 *
 * Lifecycle mirrors AE2's QuantumBridge / P2P ME tunnel pattern: hold the IGridConnection,
 * verify on tick, destroy on invalidate or rebind.
 */
public class WirelessGridManager {

    private static final String NBT_KEY = "boundWapSerial";

    private final IGridProxyable host;
    private final Runnable onConnectionChanged;
    private long boundWapSerial = 0L;
    private IGridConnection connection;
    private int checkCooldown = 0;

    public WirelessGridManager(IGridProxyable host, Runnable onConnectionChanged) {
        this.host = host;
        this.onConnectionChanged = onConnectionChanged;
    }

    /** @return the bound WAP serial, or 0 if unbound. */
    public long getBoundWapSerial() {
        return boundWapSerial;
    }

    /** @return true if an active grid connection to the bound network currently exists. */
    public boolean isConnected() {
        return connection != null;
    }

    /** Bind to a WAP serial (0 to unbind). Re-establishes the connection if already connected. */
    public void bind(long serial) {
        if (serial == boundWapSerial) return;
        destroyConnection();
        boundWapSerial = serial;
        establishConnection();
        onConnectionChanged.run();
    }

    /** Unbind and tear down the connection. */
    public void unbind() {
        bind(0L);
    }

    /** Called every tile tick (throttled internally) to verify and re-establish the connection. */
    public void tickCheck() {
        if (checkCooldown-- > 0) return;
        checkCooldown = 20; // check once per second
        if (boundWapSerial == 0L) return;
        if (connection == null) {
            establishConnection();
        }
    }

    /** Tear down everything. Called when the host tile is invalidated or chunk unloaded. */
    public void invalidate() {
        destroyConnection();
    }

    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong(NBT_KEY, boundWapSerial);
    }

    public void readFromNBT(NBTTagCompound tag) {
        boundWapSerial = tag.getLong(NBT_KEY);
    }

    private void establishConnection() {
        if (boundWapSerial == 0L) return;
        destroyConnection();
        ILocatable target = AEApi.instance()
            .registries()
            .locatable()
            .getLocatableBy(boundWapSerial);
        if (!(target instanceof IGridHost gridHost)) {
            return; // WAP not loaded / destroyed — will retry on next tickCheck
        }
        IGridNode remoteNode = gridHost.getGridNode(ForgeDirection.UNKNOWN);
        IGridNode localNode = getLocalNode();
        if (remoteNode == null || localNode == null) return;
        try {
            connection = AEApi.instance()
                .createGridConnection(localNode, remoteNode);
            onConnectionChanged.run();
        } catch (Exception failed) {
            // FailedConnection: colour mismatch or security rule — log and leave disconnected
            FMLLog.warning(
                "[WirelessMEHatch] Failed to establish grid connection to WAP serial %d: %s",
                boundWapSerial,
                failed.getMessage());
        }
    }

    private void destroyConnection() {
        if (connection != null) {
            connection.destroy();
            connection = null;
            onConnectionChanged.run();
        }
    }

    private IGridNode getLocalNode() {
        try {
            return host.getProxy()
                .getNode();
        } catch (Exception ignored) {
            return null;
        }
    }
}
```

**Important implementation notes for the engineer:**
- The `AENetworkProxy` for the host must already be initialised and `onReady()`-ed before `getLocalNode()` will return a non-null node. The hatch classes (Tasks 5-6) own the proxy and call `proxy.onReady()` in `onFirstTick`.
- `AEApi.instance().createGridConnection` throws a checked `FailedConnectionException` (catch as `Exception` is safe; the concrete type is `appeng.api.exceptions.FailedConnection`). Verify the exact exception type by grepping AE2 sources.
- `IGridConnection.destroy()` is idempotent and null-safe after our guard.
- Do NOT hold a long-lived strong reference to the remote `IGridHost` — only the serial. The locatable registry is the source of truth; if the WAP is unloaded it returns null and we gracefully skip.
- The `AEColor` of the hatch's proxy must match the network's color for `createGridConnection` to succeed. The hatch classes synchronise color via `updateAE2ProxyColor()` (same as GT's existing ME hatches).

- [ ] **Step 2: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL. If `IGridConnection` import path is wrong, grep AE2 for `interface IGridConnection` and fix. If `FailedConnectionException` import is needed, add `import appeng.api.exceptions.FailedConnectionException;` and narrow the catch.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(me): add WirelessGridManager for invisible grid connections"
```

---
## Task 4: `MemoryCardHandler` - bind WAP serial via AE2 Memory Card

**Goal:** Implement the world-interaction binding: sneak-right-click a WAP with an AE2 Memory Card to record its `locatableSerial`; right-click a wireless hatch with the recorded card to bind. Also handle screwdriver-right-click to unbind.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/me/MemoryCardHandler.java`

**Interfaces:**
- Consumes: `IWirelessMEHatch` (Task 2), AE2 `IMemoryCard`, `IWirelessAccessPoint`, `ILocatable`.
- Produces: `MemoryCardHandler` with static helpers consumed by the hatch classes' `onRightClick`/`onScrewdriverRightClick`/WAP interaction.

**API verification (do this first):** The engineer must confirm the exact way GT hatches intercept right-clicks. In GTNH GregTech, `MetaTileEntity` exposes `onRightclick(EntityPlayer, byte, float, float, float)` and `onScrewdriverRightClick(EntityPlayer, float, float, float)`. Also confirm AE2's `IMemoryCard` interface path (`appeng.api.implementations.items.IMemoryCard`) and its methods `setMemoryCardContents(ItemStack, String, NBTTagCompound)`, `getData(ItemStack)`, `getSettingsName(ItemStack)`, `notifyUser(EntityPlayer, MemoryCardMessages)`. Verify `IWirelessAccessPoint` extends `ILocatable` and `ILocatable.getLocatableSerial()` returns `long`. Grep the AE2 dev jar sources (decompiled via `./gradlew genIntellijRuns` / IDE, or view on GitHub `Applied-Energistics-2-Unofficial` master) to confirm.

- [ ] **Step 1: Create `MemoryCardHandler.java`**

```java
package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;

import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;

/**
 * Handles binding a wireless hatch to an AE2 Wireless Access Point via the AE2 Memory Card.
 *
 * Flow:
 *   1. Sneak-right-click a WAP with a Memory Card  -> record WAP's locatable serial on the card.
 *   2. Right-click a wireless hatch with the card   -> bind the hatch to that serial.
 *   3. Screwdriver-right-click a wireless hatch     -> unbind.
 */
public final class MemoryCardHandler {

    private static final String CONFIG_KEY = "wirelessmehatch.wap_binding";
    private static final String DATA_KEY = "wapSerial";

    private MemoryCardHandler() {}

    /**
     * Called when a player sneak-right-clicks a WAP with a Memory Card.
     * Records the WAP's locatable serial onto the card.
     *
     * @return true if the serial was recorded (card was a valid Memory Card and WAP had a serial).
     */
    public static boolean recordWapSerial(ItemStack cardStack, long wapSerial, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        if (wapSerial == 0L) return false;
        NBTTagCompound data = new NBTTagCompound();
        data.setLong(DATA_KEY, wapSerial);
        card.setMemoryCardContents(cardStack, CONFIG_KEY, data);
        card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
        return true;
    }

    /**
     * Called when a player right-clicks a wireless hatch with a Memory Card.
     * Binds the hatch to the serial recorded on the card.
     *
     * @return true if binding occurred.
     */
    public static boolean bindHatchFromCard(IWirelessMEHatch hatch, ItemStack cardStack, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        NBTTagCompound data = card.getData(cardStack);
        if (data == null || !data.hasKey(DATA_KEY)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }
        long serial = data.getLong(DATA_KEY);
        if (serial == 0L) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }
        hatch.setBoundWapSerial(serial);
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return true;
    }

    /**
     * Called when a player screwdriver-right-clicks a wireless hatch.
     * Unbinds the hatch (sets serial to 0).
     */
    public static void unbindHatch(IWirelessMEHatch hatch, EntityPlayer player) {
        hatch.setBoundWapSerial(0L);
    }
}
```

**Note:** `MemoryCardMessages.SETTINGS_SAVED` / `SETTINGS_LOADED` / `INVALID_MACHINE` are AE2 enum values - confirm they exist by grepping `enum MemoryCardMessages` in AE2 sources.

- [ ] **Step 2: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(me): add MemoryCardHandler for WAP serial binding"
```

---

## Task 5: `MTEWirelessOutputHatchME` - wireless merged output hatch

**Goal:** Implement the wireless output hatch. Extends `MTEHatchOutput` (fluid base), implements `IDualOutputHatch` + `IWirelessMEHatch`, holds two `MTEHatchOutputMEBase` providers (fluid + item), shares one `AENetworkProxy`, and integrates `WirelessGridManager`.

This is the most complex task. The engineer MUST study GT's existing `MTEHatchOutputME` and `MTEHatchOutputBusME` source (in the GT dev jar, or on GitHub `GT5-Unofficial` master at `src/main/java/gregtech/common/tileentities/machines/outputme/`) before writing this class, because the `MTEHatchOutputMEBase.Environment<T>` interface must be implemented exactly as GT expects.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessOutputHatchME.java`

**Interfaces:**
- Consumes: `IDualOutputHatch`, `IWirelessMEHatch` (Task 2), `WirelessGridManager` (Task 3), `MemoryCardHandler` (Task 4), GT's `MTEHatchOutputMEBase`, `MTEHatchOutput`, AE2 `IGridProxyable`/`AENetworkProxy`.
- Produces: `MTEWirelessOutputHatchME` (consumed by `MetaTileEntityLoader` Task 8, `MTEMultiBlockBaseMixin` Task 7).

**API verification (do this first - critical):**
1. Open the GT dev jar sources (in IDE, the `GT5-Unofficial` dependency sources). Read `MTEHatchOutputMEBase.java` fully - note the `Environment<T>` interface, the `getProxy()` lazy-init, the `storePartial`/`flushCachedStack`/`updateCacheCapacity` methods, and the cell-slot handling.
2. Read `MTEHatchOutputME.java` and `MTEHatchOutputBusME.java` - note how each implements `Environment<IAEFluidStack>` / `Environment<IAEItemStack>`, how `getGridNode`/`getActionableNode`/`getProxy` delegate, how `onFirstTick` calls `getProxy().onReady()`, how NBT save/load works, and how the GUI `buildUI` is structured.
3. Read `MTEHatchOutput.java` - note `fill`, `canTankBeFilled`, `canTankBeEmptied`, `createTransaction`, `getTankProperties`.
4. Confirm the exact `MTEHatchOutputMEBase` constructor signature (likely `new MTEHatchOutputMEBase<T>(Environment<T> env, long baseCapacity)`).

- [ ] **Step 1: Create `MTEWirelessOutputHatchME.java`**

This class is large. Structure it as follows (the engineer fills in exact delegations by mirroring `MTEHatchOutputME`/`MTEHatchOutputBusME`):

```java
package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;

import com.github.skyjack2033.wirelessmehatch.a


## Task 4: `MemoryCardHandler` - bind WAP serial via AE2 Memory Card

**Goal:** Implement the world-interaction binding: sneak-right-click a WAP with an AE2 Memory Card to record its `locatableSerial`; right-click a wireless hatch with the recorded card to bind. Also handle screwdriver-right-click to unbind.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/me/MemoryCardHandler.java`

**Interfaces:**
- Consumes: `IWirelessMEHatch` (Task 2), AE2 `IMemoryCard`, `IWirelessAccessPoint`, `ILocatable`.
- Produces: `MemoryCardHandler` with static helpers consumed by the hatch classes' `onRightClick`/`onScrewdriverRightClick`/WAP interaction.

**API verification (do this first):** Confirm GT's right-click interception methods on `MetaTileEntity` (`onRightclick(EntityPlayer, byte, float, float, float)`, `onScrewdriverRightClick(EntityPlayer, float, float, float)`). Confirm AE2 `IMemoryCard` path `appeng.api.implementations.items.IMemoryCard` and methods `setMemoryCardContents(ItemStack, String, NBTTagCompound)`, `getData(ItemStack)`, `notifyUser(EntityPlayer, MemoryCardMessages)`. Confirm `IWirelessAccessPoint` extends `ILocatable` and `ILocatable.getLocatableSerial()` returns `long`. Grep the AE2 dev jar sources to confirm.

- [ ] **Step 1: Create `MemoryCardHandler.java`**

```java
package com.github.skyjack2033.wirelessmehatch.me;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;

import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;

/**
 * Handles binding a wireless hatch to an AE2 Wireless Access Point via the AE2 Memory Card.
 *
 * Flow:
 *   1. Sneak-right-click a WAP with a Memory Card  -> record WAP locatable serial on the card.
 *   2. Right-click a wireless hatch with the card   -> bind the hatch to that serial.
 *   3. Screwdriver-right-click a wireless hatch     -> unbind.
 */
public final class MemoryCardHandler {

    private static final String CONFIG_KEY = "wirelessmehatch.wap_binding";
    private static final String DATA_KEY = "wapSerial";

    private MemoryCardHandler() {}

    /**
     * Called when a player sneak-right-clicks a WAP with a Memory Card.
     * Records the WAP locatable serial onto the card.
     *
     * @return true if the serial was recorded.
     */
    public static boolean recordWapSerial(ItemStack cardStack, long wapSerial, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        if (wapSerial == 0L) return false;
        NBTTagCompound data = new NBTTagCompound();
        data.setLong(DATA_KEY, wapSerial);
        card.setMemoryCardContents(cardStack, CONFIG_KEY, data);
        card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
        return true;
    }

    /**
     * Called when a player right-clicks a wireless hatch with a Memory Card.
     * Binds the hatch to the serial recorded on the card.
     *
     * @return true if binding occurred.
     */
    public static boolean bindHatchFromCard(IWirelessMEHatch hatch, ItemStack cardStack, EntityPlayer player) {
        if (!(cardStack.getItem() instanceof IMemoryCard card)) return false;
        NBTTagCompound data = card.getData(cardStack);
        if (data == null || !data.hasKey(DATA_KEY)) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }
        long serial = data.getLong(DATA_KEY);
        if (serial == 0L) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
        }
        hatch.setBoundWapSerial(serial);
        card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        return true;
    }

    /**
     * Called when a player screwdriver-right-clicks a wireless hatch.
     * Unbinds the hatch (sets serial to 0).
     */
    public static void unbindHatch(IWirelessMEHatch hatch, EntityPlayer player) {
        hatch.setBoundWapSerial(0L);
    }
}
```

**Note:** Confirm `MemoryCardMessages.SETTINGS_SAVED` / `SETTINGS_LOADED` / `INVALID_MACHINE` exist by grepping `enum MemoryCardMessages` in AE2 sources.

- [ ] **Step 2: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(me): add MemoryCardHandler for WAP serial binding"
```

---

## Task 5: `MTEWirelessOutputHatchME` - wireless merged output hatch

**Goal:** Implement the wireless output hatch. Extends `MTEHatchOutput` (fluid base), implements `IDualOutputHatch` + `IWirelessMEHatch`, holds two `MTEHatchOutputMEBase` providers (fluid + item), shares one `AENetworkProxy`, integrates `WirelessGridManager`.

This is the most complex task. The engineer MUST study GT's `MTEHatchOutputME` and `MTEHatchOutputBusME` source (in the GT dev jar, or on GitHub `GT5-Unofficial` master at `src/main/java/gregtech/common/tileentities/machines/outputme/`) before writing this class, because the `MTEHatchOutputMEBase.Environment<T>` interface must be implemented exactly as GT expects.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessOutputHatchME.java`

**Interfaces:**
- Consumes: `IDualOutputHatch`, `IWirelessMEHatch` (Task 2), `WirelessGridManager` (Task 3), `MemoryCardHandler` (Task 4), GT `MTEHatchOutputMEBase`, `MTEHatchOutput`, AE2 `IGridProxyable`/`AENetworkProxy`.
- Produces: `MTEWirelessOutputHatchME` (consumed by `MetaTileEntityLoader` Task 8, `MTEMultiBlockBaseMixin` Task 7).

**API verification (do this first - critical):**
1. Read `MTEHatchOutputMEBase.java` fully from the GT dev jar sources - note the `Environment<T>` interface methods, `getProxy()` lazy-init, `storePartial`/`flushCachedStack`/`updateCacheCapacity`, cell-slot handling, constructor signature.
2. Read `MTEHatchOutputME.java` and `MTEHatchOutputBusME.java` - note how each implements `Environment<IAEFluidStack>`/`Environment<IAEItemStack>`, how `getGridNode`/`getActionableNode`/`getProxy` delegate, how `onFirstTick` calls `getProxy().onReady()`, NBT save/load, GUI `buildUI`.
3. Read `MTEHatchOutput.java` - note `fill`, `canTankBeFilled`, `canTankBeEmptied`, `createTransaction`.
4. Confirm `MTEHatchOutputMEBase` constructor: likely `new MTEHatchOutputMEBase<T>(Environment<T> env, long baseCapacity)`.

- [ ] **Step 1: Create `MTEWirelessOutputHatchME.java`**

```java
package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;

import com.github.skyjack2033.wirelessmehatch.api.IDualOutputHatch;
import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.me.MemoryCardHandler;
import com.github.skyjack2033.wirelessmehatch.me.WirelessGridManager;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IOutputBusTransaction;
import gregtech.api.interfaces.tileentity.IOutputHatchTransaction;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

/**
 * Wireless merged output hatch. Extends MTEHatchOutput (fluid) and manually implements item-bus
 * output via a second MTEHatchOutputMEBase<IAEItemStack> provider. Both providers share one
 * AENetworkProxy. Connects to a bound ME network wirelessly via WirelessGridManager.
 *
 * Recognised by multiblock controllers as IDualOutputHatch (via Mixin) so it receives BOTH
 * fluid output (via fill) and item output (via storePartial).
 */
public class MTEWirelessOutputHatchME extends MTEHatchOutput
    implements IDualOutputHatch, IWirelessMEHatch, IGridProxyable, IPowerChannelState {

    private static final long MAX_CACHE_CAPACITY = Long.MAX_VALUE;

    private final MTEHatchOutputMEBase<IAEFluidStack> fluidProvider;
    private final MTEHatchOutputMEBase<IAEItemStack> itemProvider;
    private final WirelessGridManager wirelessManager;
    private AENetworkProxy proxy;

    public MTEWirelessOutputHatchME(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        this.fluidProvider = new MTEHatchOutputMEBase<>(createFluidEnvironment(), MAX_CACHE_CAPACITY);
        this.itemProvider = new MTEHatchOutputMEBase<>(createItemEnvironment(), MAX_CACHE_CAPACITY);
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    public MTEWirelessOutputHatchME(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        this.fluidProvider = new MTEHatchOutputMEBase<>(createFluidEnvironment(), MAX_CACHE_CAPACITY);
        this.itemProvider = new MTEHatchOutputMEBase<>(createItemEnvironment(), MAX_CACHE_CAPACITY);
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWirelessOutputHatchME(mName, mTier, mDescriptionArray, mTextures);
    }

    // ---- IWirelessMEHatch ----

    @Override
    public long getBoundWapSerial() {
        return wirelessManager.getBoundWapSerial();
    }

    @Override
    public void setBoundWapSerial(long serial) {
        wirelessManager.bind(serial);
    }

    @Override
    public boolean isWirelessConnected() {
        return wirelessManager.isConnected();
    }

    private void onWirelessConnectionChanged() {
        getBaseMetaTileEntity().markDirty();
    }

    // ---- Right-click binding ----

    @Override
    public boolean onRightclick(EntityPlayer aPlayer, byte aSide, float aX, float aY, float aZ) {
        ItemStack held = aPlayer.getHeldItem();
        if (held != null && MemoryCardHandler.bindHatchFromCard(this, held, aPlayer)) {
            return true;
        }
        return super.onRightclick(aPlayer, aSide, aX, aY, aZ);
    }

    @Override
    public boolean onScrewdriverRightClick(EntityPlayer aPlayer, float aX, float aY, float aZ) {
        MemoryCardHandler.unbindHatch(this, aPlayer);
        return true;
    }

    // ---- IDualOutputHatch: item side (manual) ----

    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        IAEItemStack aeStack = Platform.itemToAEItemStack(stack); // confirm helper name in AE2
        if (aeStack == null) return false;
        boolean stored = itemProvider.storePartial(aeStack, simulate);
        if (stored && !simulate) {
            stack.stackSize = 0;
        }
        return stored;
    }

    @Override
    public IOutputHatchTransaction createFluidTransaction() {
        return fluidProvider.createTransaction(); // confirm method name on MTEHatchOutputMEBase
    }

    @Override
    public IOutputBusTransaction createItemTransaction() {
        return itemProvider.createTransaction(); // confirm method name on MTEHatchOutputMEBase
    }

    // ---- IDualOutputHatch: fluid side ----

    @Override
    public int fill(ForgeDirection side, FluidStack fluid, boolean doFill) {
        if (fluid == null) return 0;
        IAEFluidStack aeFluid = AEFluidStack.create(fluid); // confirm AE2 helper
        int amount = fluid.amount;
        boolean stored = fluidProvider.storePartial(aeFluid, !doFill);
        return stored ? amount : 0;
    }

    // ---- IGridProxyable (shared single proxy, wireless - no physical sides) ----

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class)); // wireless
        }
        return proxy;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public void securityBreak() {}

    // ---- Lifecycle ----

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        getProxy().onReady();
        wirelessManager.tickCheck();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            wirelessManager.tickCheck();
            fluidProvider.tick(aTick); // confirm tick method name on MTEHatchOutputMEBase
            itemProvider.tick(aTick);
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        wirelessManager.invalidate();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        wirelessManager.invalidate();
    }

    // ---- NBT ----

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        wirelessManager.writeToNBT(aNBT);
        // Save provider state per MTEHatchOutputME pattern (proxy NBT, cache, cell).
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        wirelessManager.readFromNBT(aNBT);
        // Load provider state per MTEHatchOutputME pattern.
    }

    // ---- Environment<T> implementations ----
    // Implement MTEHatchOutputMEBase.Environment<IAEFluidStack> and <IAEItemStack> exactly as
    // MTEHatchOutputME / MTEHatchOutputBusME do, but route getIGridProxyable() to THIS class
    // (shared proxy) rather than each provider owning its own proxy.
    //
    // KEY: if MTEHatchOutputMEBase.getProxy() creates its own AENetworkProxy (does NOT consult
    // env.getIGridProxyable().getProxy()), the engineer must refactor to share the single proxy
    // (e.g. pass the shared proxy into the Environment, or override). VERIFY by reading
    // MTEHatchOutputMEBase.getProxy() source.

    private MTEHatchOutputMEBase.Environment<IAEFluidStack> createFluidEnvironment() {
        // Copy body from MTEHatchOutputME's Environment<IAEFluidStack> anonymous class.
        // Change getIGridProxyable() to return this.
        // getChannel() == StorageChannel.FLUIDS
        // getNetworkInvtory() == getProxy().getStorage().getFluidInventory()
        throw new UnsupportedOperationException("TODO: mirror MTEHatchOutputME Environment");
    }

    private MTEHatchOutputMEBase.Environment<IAEItemStack> createItemEnvironment() {
        // Copy body from MTEHatchOutputBusME's Environment<IAEItemStack> anonymous class.
        // Change getIGridProxyable() to return this.
        // getChannel() == StorageChannel.ITEMS
        // getNetworkInvtory() == getProxy().getStorage().getItemInventory()
        throw new UnsupportedOperationException("TODO: mirror MTEHatchOutputBusME Environment");
    }

    // ---- GUI ----

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        // Defer to Task 9 (GUI). Minimal panel for now.
        return GTGuis.mteTemplatePanelBuilder(this, guiData, syncManager, uiSettings)
            .build();
    }
}
```

**The `throw new UnsupportedOperationException` placeholders are INTENTIONAL.** Resolve by:
1. Copy `MTEHatchOutputME`'s `Environment<IAEFluidStack>` anonymous class body into `createFluidEnvironment()`, change `getIGridProxyable()` to return `this`.
2. Copy `MTEHatchOutputBusME`'s `Environment<IAEItemStack>` anonymous class body into `createItemEnvironment()`, change `getIGridProxyable()` to return `this`.
3. Verify whether `MTEHatchOutputMEBase.getProxy()` consults `env.getIGridProxyable().getProxy()` or creates its own. If the latter, refactor to share the single proxy.

- [ ] **Step 2: Resolve all "confirm" comments by reading GT source**

Grep the GT dev jar sources for each commented method (`Platform.itemToAEItemStack`, `AEFluidStack.create`, `MTEHatchOutputMEBase.createTransaction`, `MTEHatchOutputMEBase.tick`, `getStackForm`). Fix wrong names. Remove `// confirm ...` comments once verified.

- [ ] **Step 3: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL. Fix all compile errors - mostly import/signature mismatches resolvable by reading source.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(hatch): add MTEWirelessOutputHatchME wireless dual output hatch"
```
---

## Task 6: `MTEWirelessInputHatchME` - wireless merged input hatch

**Goal:** Implement the wireless input hatch. Extends `MTEHatchInput` (fluid base), implements GT-native `IDualInputHatch` + `IWirelessMEHatch`, pulls both items and fluids from ME.

GT's input ME hatches (`MTEHatchInputME`, `MTEHatchInputBusME`) do NOT use a shared generic base like the output side does - they are copy-pasted. The engineer mirrors both classes' logic into a single hatch implementing `IDualInputHatch`, reusing `WirelessGridManager`.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/metatileentity/MTEWirelessInputHatchME.java`

**Interfaces:**
- Consumes: `IWirelessMEHatch` (Task 2), `WirelessGridManager` (Task 3), `MemoryCardHandler` (Task 4), GT `IDualInputHatch`, `MTEHatchInput`, AE2 `IGridProxyable`/`AENetworkProxy`/`IStackWatcherHost`.
- Produces: `MTEWirelessInputHatchME` (consumed by `MetaTileEntityLoader` Task 8).

**API verification (do this first - critical):**
1. Read `gregtech.common.tileentities.machines.IDualInputHatch` interface - note `inventories()` return type and `IDualInputInventory` shape.
2. Read `MTEHatchInputME.java` fully - note `drain`, `startRecipeProcessing`, `endRecipeProcessing`, `updateInformationSlot`, `Slot` inner class, `getProxy`, `IStackWatcherHost` impl, `onFirstTick`.
3. Read `MTEHatchInputBusME.java` fully - note `getStackInSlot`, `getSizeInventory`, `startRecipeProcessing`, `endRecipeProcessing`, `Slot` inner class, circuit/manual slots, `refreshItemList`.
4. Read `MTEHatchInput.java` - note `drain` signature, `canTankBeFilled`/`canTankBeEmptied`.

- [ ] **Step 1: Create `MTEWirelessInputHatchME.java`**

```java
package com.github.skyjack2033.wirelessmehatch.metatileentity;

import java.util.EnumSet;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;

import com.github.skyjack2033.wirelessmehatch.api.IWirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.me.MemoryCardHandler;
import com.github.skyjack2033.wirelessmehatch.me.WirelessGridManager;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

/**
 * Wireless merged input hatch. Extends MTEHatchInput (fluid) and implements IDualInputHatch
 * so GT multiblock controllers natively recognise it as a dual input (no Mixin needed).
 * Pulls both fluids (via drain) and items (via the IDualInputInventory iterator) from the bound
 * ME network wirelessly.
 */
public class MTEWirelessInputHatchME extends MTEHatchInput
    implements IDualInputHatch, IWirelessMEHatch, IGridProxyable {

    private static final int SLOT_COUNT = 16;

    private final FluidSlot[] fluidSlots = new FluidSlot[SLOT_COUNT];
    private final ItemSlotME[] itemSlots = new ItemSlotME[SLOT_COUNT];
    private final WirelessGridManager wirelessManager;
    private AENetworkProxy proxy;

    private boolean processingRecipe = false;
    private boolean cachedActivity = false;

    public MTEWirelessInputHatchME(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        initSlots();
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    public MTEWirelessInputHatchME(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        initSlots();
        this.wirelessManager = new WirelessGridManager(this, this::onWirelessConnectionChanged);
    }

    private void initSlots() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            fluidSlots[i] = new FluidSlot();
            itemSlots[i] = new ItemSlotME();
        }
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWirelessInputHatchME(mName, mTier, mDescriptionArray, mTextures);
    }

    // ---- IWirelessMEHatch ----

    @Override
    public long getBoundWapSerial() {
        return wirelessManager.getBoundWapSerial();
    }

    @Override
    public void setBoundWapSerial(long serial) {
        wirelessManager.bind(serial);
    }

    @Override
    public boolean isWirelessConnected() {
        return wirelessManager.isConnected();
    }

    private void onWirelessConnectionChanged() {
        getBaseMetaTileEntity().markDirty();
    }

    // ---- Right-click binding ----

    @Override
    public boolean onRightclick(EntityPlayer aPlayer, byte aSide, float aX, float aY, float aZ) {
        ItemStack held = aPlayer.getHeldItem();
        if (held != null && MemoryCardHandler.bindHatchFromCard(this, held, aPlayer)) {
            return true;
        }
        return super.onRightclick(aPlayer, aSide, aX, aY, aZ);
    }

    @Override
    public boolean onScrewdriverRightClick(EntityPlayer aPlayer, float aX, float aY, float aZ) {
        MemoryCardHandler.unbindHatch(this, aPlayer);
        return true;
    }

    // ---- IDualInputHatch ----

    @Override
    public Iterator<? extends IDualInputInventory> inventories() {
        // Return a single IDualInputInventory exposing both fluid and item slots.
        // Read IDualInputInventory source for getItemStacks()/getFluidStacks() signatures.
        throw new UnsupportedOperationException("TODO: implement IDualInputInventory iterator");
    }

    // ---- Fluid input (drain) - mirror MTEHatchInputME.drain ----

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, int amount, boolean doDrain) {
        // Mirror MTEHatchInputME.drain: only UNKNOWN side; during processingRecipe use slot snapshot,
        // otherwise Platform.poweredExtraction from ME fluid inventory.
        throw new UnsupportedOperationException("TODO: mirror MTEHatchInputME.drain");
    }

    // ---- Recipe processing - mirror both input ME hatches ----

    @Override
    public void startRecipeProcessing() {
        processingRecipe = true;
        cachedActivity = isAllowedToWork();
        updateAllInformationSlots();
    }

    @Override
    public void endRecipeProcessing(MTEMultiBlockBase controller) {
        // Merge both: for each fluid slot AND item slot,
        // toExtract = extractedAmount - extracted.amount,
        // Platform.poweredExtraction from ME, on failure controller.stopMachine.
        throw new UnsupportedOperationException("TODO: merge both endRecipeProcessing");
    }

    // ---- IGridProxyable (shared single proxy, wireless) ----

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, "proxy", getStackForm(1), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(EnumSet.noneOf(ForgeDirection.class)); // wireless
        }
        return proxy;
    }

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public void securityBreak() {}

    // ---- Lifecycle & NBT: mirror MTEHatchInputME ----
    // Copy onFirstTick (getProxy().onReady() + wirelessManager.tickCheck()),
    //     onPostTick (wirelessManager.tickCheck()),
    //     saveNBTData/loadNBTData (dual slots + wireless NBT),
    //     invalidate/onRemoval (wirelessManager.invalidate()).
    // TODO: engineer adds these by copying from MTEHatchInputME.

    // ---- inner slot classes ----
    // Copy FluidSlot from MTEHatchInputME.Slot, ItemSlotME from MTEHatchInputBusME.Slot.
    // TODO: engineer copies these inner classes.

    private void updateAllInformationSlots() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            updateFluidSlot(i);
            updateItemSlot(i);
        }
    }

    private void updateFluidSlot(int i) {
        // Mirror MTEHatchInputME.updateInformationSlot: simulate-extract from ME fluid inventory.
        throw new UnsupportedOperationException("TODO: mirror MTEHatchInputME.updateInformationSlot");
    }

    private void updateItemSlot(int i) {
        // Mirror MTEHatchInputBusME.updateInformationSlot: simulate-extract from ME item inventory.
        throw new UnsupportedOperationException("TODO: mirror MTEHatchInputBusME.updateInformationSlot");
    }

    private boolean isAllowedToWork() {
        try {
            return wirelessManager.isConnected() && getProxy().isActive();
        } catch (Exception ignored) {
            return false;
        }
    }

    // ---- GUI ----

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        // Defer to Task 9 (GUI).
        return GTGuis.mteTemplatePanelBuilder(this, guiData, syncManager, uiSettings)
            .build();
    }
}
```

- [ ] **Step 2: Resolve all TODO placeholders by merging the two input ME hatch sources**

For each `throw new UnsupportedOperationException`:
- `inventories()`: implement a single `IDualInputInventory` exposing `getItemStacks()` (from `itemSlots`) and `getFluidStacks()` (from `fluidSlots`). Read `IDualInputInventory` source.
- `drain(...)`: copy from `MTEHatchInputME.drain`, use `this.getProxy()`.
- `endRecipeProcessing(...)`: merge both - loop fluid slots AND item slots, `Platform.poweredExtraction` each.
- `updateFluidSlot`/`updateItemSlot`: copy from respective input ME hatch's `updateInformationSlot`.

Also add: `onFirstTick`, `onPostTick`, `saveNBTData`/`loadNBTData`, `invalidate`/`onRemoval`, `IStackWatcherHost` impl (`updateWatcher`, `configureWatchers`, `onStackChange`), `@MENetworkEventSubscribe` handlers, `FluidSlot`/`ItemSlotME` inner classes. Copy each from `MTEHatchInputME`, adapting to dual slots + wireless.

- [ ] **Step 3: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(hatch): add MTEWirelessInputHatchME wireless dual input hatch"
```---

## Task 7: Mixin - extend `MTEMultiBlockBase` to recognise `IDualOutputHatch`

**Goal:** Make all multiblock controllers (anything extending `MTEMultiBlockBase`) recognise `IDualOutputHatch` so the wireless output hatch is treated as both a fluid hatch and an item bus, without modifying GT source directly.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/mixin/MTEMultiBlockBaseMixin.java`
- Create: `src/main/resources/mixin.wirelessmehatch.json`

**Interfaces:**
- Consumes: `IDualOutputHatch` (Task 2), GT `MTEMultiBlockBase`.
- Produces: the Mixin modification (consumed at runtime by all multiblock controllers).

**API verification (do this first - critical):**
Read `MTEMultiBlockBase.java` from the GT dev jar sources. Locate and record exact line numbers / signatures for:
1. `addToMachineList(IGregTechTileEntity, int)` - the full method with all `instanceof` branches including `IDualInputHatch` (this is the template for our `IDualOutputHatch` injection).
2. `addOutputHatchToMachineList(IGregTechTileEntity, int)` and `addOutputBusToMachineList(IGregTechTileEntity, int)`.
3. The fields `mOutputHatches`, `mOutputBusses`, `mDualInputHatches` - their declarations.
4. `clearHatches()` - where the lists are cleared.
5. Every place `mOutputBusses` is iterated to call `storePartial` (item output dispatch).
6. Every place `mOutputHatches` is iterated to call `fill` or output fluids.
7. Every place `mDualInputHatches` is iterated (the template for how dual hatches are dispatched).

The exact injection points and method names depend on the GT source. The engineer must record these before writing the Mixin.

- [ ] **Step 1: Create `mixin.wirelessmehatch.json`**

```json
{
    "package": "com.github.skyjack2033.wirelessmehatch.mixin",
    "refmap": "mixins.wirelessmehatch.refmap.json",
    "target": [
        "@env(DEFAULT)"
    ],
    "minVersion": "0.8",
    "compatibilityLevel": "JAVA_8",
    "mixins": [
        "MTEMultiBlockBaseMixin"
    ],
    "client": []
}
```

- [ ] **Step 2: Create `MTEMultiBlockBaseMixin.java`**

The Mixin adds a `mDualOutputHatches` list and injects `IDualOutputHatch` recognition into `addToMachineList`, `addOutputHatchToMachineList`, `addOutputBusToMachineList`, and the output dispatch methods. The engineer fills in the exact `@Inject`/`@At` targets based on the GT source verification above.

```java
package com.github.skyjack2033.wirelessmehatch.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

import com.github.skyjack2033.wirelessmehatch.api.IDualOutputHatch;

@Mixin(value = MTEMultiBlockBase.class, remap = false)
public class MTEMultiBlockBaseMixin {

    @Unique
    private final List<IDualOutputHatch> wirelessmehatch$mDualOutputHatches = new ArrayList<>();

    /**
     * Inject at the HEAD of addToMachineList: if the MTE is an IDualOutputHatch, add it to our
     * dual list and cancel the original method (return true). Mirrors how IDualInputHatch is
     * handled at the top of the original method.
     *
     * The engineer must confirm the exact @At target by reading the GT source. If addToMachineList
     * already short-circuits IDualInputHatch at HEAD, inject BEFORE that or use @Redirect on the
     * instanceof check.
     */
    @Inject(method = "addToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity == null || aTileEntity.getMetaTileEntity() == null) return;
        if (aTileEntity.getMetaTileEntity() instanceof IDualOutputHatch dual) {
            // Apply texture/icon like the original does for other hatches - mirror IDualInputHatch branch.
            // dual.updateTexture(aBaseCasingIndex); // confirm MTEHatch method exists
            // dual.updateCraftingIcon(...); // confirm
            wirelessmehatch$mDualOutputHatches.add(dual);
            cir.setReturnValue(true);
        }
    }

    /**
     * Inject at HEAD of addOutputHatchToMachineList and addOutputBusToMachineList: if IDualOutputHatch,
     * add to dual list and return true.
     *
     * The engineer adds a second @Inject for addOutputBusToMachineList with the same body.
     */
    @Inject(method = "addOutputHatchToMachineList", at = @At("HEAD"), cancellable = true)
    private void wirelessmehatch$onAddOutputHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex,
        CallbackInfoReturnable<Boolean> cir) {
        if (aTileEntity != null && aTileEntity.getMetaTileEntity() instanceof IDualOutputHatch dual) {
            wirelessmehatch$mDualOutputHatches.add(dual);
            cir.setReturnValue(true);
        }
    }

    // TODO: engineer adds the same @Inject for addOutputBusToMachineList.

    /**
     * Output dispatch: wherever the original iterates mOutputBusses calling storePartial, we must
     * ALSO iterate mDualOutputHatches calling storePartial. And wherever it iterates mOutputHatches
     * calling fill, we ALSO iterate mDualOutputHatches calling fill.
     *
     * The engineer must find each such iteration site (from the verification step) and add a
     * matching @Inject(after the iteration) or @Redirect that appends a dual-output iteration.
     *
     * Approach: for each dispatch site, @Inject at TAIL with a CallbackInfo (non-cancel) that runs:
     *   for (IDualOutputHatch dual : wirelessmehatch$mDualOutputHatches) {
     *       dual.storePartial(stack, false);  // for item sites
     *       // or dual.fill(side, fluid, true); // for fluid sites
     *   }
     * The exact method names and local variable access depend on the GT source.
     */
    // TODO: engineer adds @Inject for each output dispatch site, mirroring the mDualInputHatches pattern.

    /**
     * Clear our list in clearHatches.
     */
    @Inject(method = "clearHatches", at = @At("HEAD"))
    private void wirelessmehatch$onClearHatches(CallbackInfo ci) {
        wirelessmehatch$mDualOutputHatches.clear();
    }
}
```

**Critical guidance for the engineer:**
- The `@Inject(method = "...")` targets MUST match exact method names in `MTEMultiBlockBase`. Confirm by reading the decompiled source.
- `remap = false` on `@Mixin` because GT is not obfuscated in dev; confirm whether the production environment needs `remap = true` (GTNH dev jars are deobf, so `false` is correct for GT classes).
- For output dispatch, accessing local variables (the `ItemStack` being output, or the `FluidStack`) from an `@Inject` requires `@Local` capture or `@Redirect`. If `@Inject` cannot see the locals, use `@Redirect` on the `storePartial`/`fill` call site, or refactor by redirecting the collection iteration. This is the hardest part - study how `mDualInputHatches` is dispatched and mirror it exactly.
- If `addToMachineList` is overridden in `MTEEnhancedMultiBlockBase` or other subclasses, the Mixin on the base class still applies (inheritance) unless the override calls `super`. Verify by grepping subclasses for `addToMachineList`.

- [ ] **Step 3: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL. The Mixin annotation processor should run and generate the refmap. If the build fails on `@Inject` target validation, fix the method names.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(mixin): add IDualOutputHatch recognition to MTEMultiBlockBase"
```

---

## Task 8: Register MetaTileEntities + recipes

**Goal:** Register the two wireless hatch MTEs into `GregTechAPI.METATILEENTITIES` and add crafting recipes.

**Files:**
- Modify: `src/main/java/com/github/skyjack2033/wirelessmehatch/loader/MetaTileEntityLoader.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/loader/RecipeLoader.java`
- Modify: `src/main/java/com/github/skyjack2033/wirelessmehatch/CommonProxy.java`

**Interfaces:**
- Consumes: `MTEWirelessOutputHatchME` (Task 5), `MTEWirelessInputHatchME` (Task 6).
- Produces: the two registered MTEs (consumed by GT's block/item generation).

**API verification:** Confirm how GT registers MTEs - `GregTechAPI.METATILEENTITIES[id] = new MTEClass(id, name, nameRegional)`. Confirm the constructor signature of `MTEHatchOutput`/`MTEHatchInput` (the `super(...)` calls in Tasks 5-6 already imply this). Confirm recipe registration API (`GTModHandler.addCraftingRecipe` or `GTValues.RA.stdBuilder()`).

- [ ] **Step 1: Update `MetaTileEntityLoader.java`**

```java
package com.github.skyjack2033.wirelessmehatch.loader;

import gregtech.api.GregTechAPI;

import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessInputHatchME;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessOutputHatchME;

public final class MetaTileEntityLoader {

    // ID range: >= 17000, verify no collision with -Dgt.debug=true at runtime.
    // GT reserves < 2048 and 4096-6099 (Frames/Pipes). Adjust if these IDs are taken.
    public static final int WIRELESS_OUTPUT_HATCH_ME_ID = 17001;
    public static final int WIRELESS_INPUT_HATCH_ME_ID = 17002;

    private MetaTileEntityLoader() {}

    public static void register() {
        GregTechAPI.METATILEENTITIES[WIRELESS_OUTPUT_HATCH_ME_ID] =
            new MTEWirelessOutputHatchME(
                WIRELESS_OUTPUT_HATCH_ME_ID,
                "wirelessmehatch.output_hatch_me",
                "Wireless Output Hatch (ME)");
        GregTechAPI.METATILEENTITIES[WIRELESS_INPUT_HATCH_ME_ID] =
            new MTEWirelessInputHatchME(
                WIRELESS_INPUT_HATCH_ME_ID,
                "wirelessmehatch.input_hatch_me",
                "Wireless Input Hatch (ME)");
    }
}
```

- [ ] **Step 2: Create `RecipeLoader.java`**

```java
package com.github.skyjack2033.wirelessmehatch.loader;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class RecipeLoader {

    private RecipeLoader() {}

    public static void register() {
        // Wireless Output Hatch (ME) = ME Output Hatch + ME Output Bus + AE2 Quantum Entangled Singularity
        // Confirm exact ItemList keys for GT ME hatches and AE2 singularity by grepping GT/AE2 ItemList.
        ItemStack meOutputHatch = ItemList.Hatch_Output_ME.get(1);        // confirm key
        ItemStack meOutputBus = ItemList.Hatch_Output_Bus_ME.get(1);      // confirm key
        ItemStack quantumSingularity = GTModHandler.getModItem(
            "appliedenergistics2", "item.ItemMultiMaterial", 1, 36);      // confirm meta for entangled singularity
        ItemStack wirelessOutput = GTModHandler.getModItem(
            "gregtech", "gt.blockmachines", 1,
            MetaTileEntityLoader.WIRELESS_OUTPUT_HATCH_ME_ID);             // confirm item path

        GTModHandler.addCraftingRecipe(
            wirelessOutput,
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] {
                "ABA", "CDC", "AEA",
                'A', meOutputHatch,
                'B', meOutputBus,
                'C', quantumSingularity,
                'D', GTValues.V[GTValues.UV], // or appropriate tier component
                'E', Items.ender_eye });

        // Wireless Input Hatch (ME) = ME Input Hatch + ME Input Bus + Quantum Singularity
        ItemStack meInputHatch = ItemList.Hatch_Input_ME.get(1);
        ItemStack meInputBus = ItemList.Hatch_Input_Bus_ME.get(1);
        ItemStack wirelessInput = GTModHandler.getModItem(
            "gregtech", "gt.blockmachines", 1,
            MetaTileEntityLoader.WIRELESS_INPUT_HATCH_ME_ID);

        GTModHandler.addCraftingRecipe(
            wirelessInput,
            GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] {
                "ABA", "CDC", "AEA",
                'A', meInputHatch,
                'B', meInputBus,
                'C', quantumSingularity,
                'D', GTValues.V[GTValues.UV],
                'E', Items.ender_eye });
    }
}
```

**The recipe items must be verified** - confirm `ItemList.Hatch_Output_ME` etc. exist, and the AE2 quantum entangled singularity item/meta. Adjust the pattern/ingredients to taste.

- [ ] **Step 3: Wire up in `CommonProxy.java`**

Update `CommonProxy.init` and `postInit`:

```java
public void init(FMLInitializationEvent event) {
    MetaTileEntityLoader.register();
}

public void postInit(FMLPostInitializationEvent event) {
    RecipeLoader.register();
}
```

- [ ] **Step 4: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(loader): register wireless ME hatches and recipes"
```

---

## Task 9: GUI (MUI2) for both hatches + final in-game verification

**Goal:** Implement the full MUI2 GUI for both hatches (connection status, dual cell slots / dual config slots, filtering, priority) and run end-to-end in-game verification.

**Files:**
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/gui/MTEWirelessOutputHatchMEGui.java`
- Create: `src/main/java/com/github/skyjack2033/wirelessmehatch/gui/MTEWirelessInputHatchMEGui.java`
- Modify: `MTEWirelessOutputHatchME.buildUI` and `MTEWirelessInputHatchME.buildUI` to delegate to the GUI classes.

**Interfaces:**
- Consumes: MUI2 API (`com.cleanroommc.modularui.*`), GT `GTGuis`, `GTBaseGuiBuilder`, the hatch classes.
- Produces: the two GUIs.

**API verification:** Read an existing GT MUI2 hatch GUI (e.g. `MTEHatchOutputMEGui` / `MTEHatchInputMEGui` in `gregtech.common.gui.modularui.hatch`) from the GT dev jar sources to see the exact `buildUI` pattern, sync value registration, and widget layout. Mirror that structure.

- [ ] **Step 1: Create `MTEWirelessOutputHatchMEGui.java`**

The GUI shows: connection status (bound serial + connected/disconnected via `IKey.dynamic` + `BooleanSyncValue`), dual cell slots (fluid cell `ItemSlot` + item cell `ItemSlot` with `isItemValidForSlot` filters), cache content lists (`GenericListSyncHandler`), priority (`IntSyncValue` + `TextFieldWidget`), and cache/check mode toggles (`ToggleButton`). The engineer builds the `ModularPanel` via `GTGuis.mteTemplatePanelBuilder(...)` and `Flow.col()` layout, mirroring `MTEHatchOutputMEGui`.

```java
package com.github.skyjack2033.wirelessmehatch.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.UiSettings;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.gui.modularui2.GTGuis;
import gregtech.api.modularui2.PosGuiData;

import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessOutputHatchME;

public class MTEWirelessOutputHatchMEGui {

    private final MTEWirelessOutputHatchME hatch;

    public MTEWirelessOutputHatchMEGui(MTEWirelessOutputHatchME hatch) {
        this.hatch = hatch;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        BooleanSyncValue connected = new BooleanSyncValue(hatch::isWirelessConnected);
        syncManager.syncValue("wireless_connected", connected);
        IntSyncValue boundSerial = new IntSyncValue(() -> (int) hatch.getBoundWapSerial(), v -> {});
        syncManager.syncValue("bound_serial", boundSerial);

        return GTGuis.mteTemplatePanelBuilder(hatch, guiData, syncManager, uiSettings)
            .setWidth(176)
            .setHeight(190)
            .build()
            .child(
                Flow.col()
                    .margin(8)
                    .child(
                        IKey.dynamic(() -> {
                            boolean c = connected.getBoolValue();
                            String state = c ? "Connected" : "Disconnected";
                            String serial = boundSerial.getIntValue() != 0
                                ? "WAP: 0x" + Integer.toHexString(boundSerial.getIntValue())
                                : "No WAP bound";
                            return (c ? "+ " : "- ") + state + " | " + serial;
                        })
                            .asWidget())
                    // TODO: add dual cell ItemSlots, cache lists, priority, toggles
                    // by mirroring MTEHatchOutputMEGui.
            );
    }
}
```

- [ ] **Step 2: Create `MTEWirelessInputHatchMEGui.java`**

Similar structure: connection status + 16 item ghost slots (`ItemSlotGridBuilder` 4x4) + 16 fluid config slots + circuit/manual slots. Mirror `MTEHatchInputMEGui`/`MTEHatchInputBusMEGui`.

- [ ] **Step 3: Wire `buildUI` in both hatch classes to delegate to the GUI classes**

In `MTEWirelessOutputHatchME.buildUI`:
```java
return new MTEWirelessOutputHatchMEGui(this).build(guiData, syncManager, uiSettings);
```
In `MTEWirelessInputHatchME.buildUI`:
```java
return new MTEWirelessInputHatchMEGui(this).build(guiData, syncManager, uiSettings);
```

- [ ] **Step 4: Run `./gradlew spotlessApply` then `./gradlew build`**

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(gui): add MUI2 GUIs for wireless ME hatches"
```

- [ ] **Step 6: In-game verification**

Run `./gradlew runClient` and verify (this is the primary test - no unit tests exist):

1. **Build/launch:** Game launches with no Mixin errors in the log. Search the log for `MTEMultiBlockBaseMixin` application success.
2. **MTE IDs:** Run with `-Dgt.debug=true`; confirm IDs 17001/17002 don't collide. If they do, change them and rebuild.
3. **Items exist:** `/give` the two hatches; they appear with correct names/textures.
4. **Binding flow:**
   - Place an AE2 WAP connected to an ME network (with a controller + drive).
   - Sneak-right-click the WAP with an AE2 Memory Card - confirm "Settings Saved" message.
   - Place a wireless output hatch; right-click it with the Memory Card - confirm "Settings Loaded".
   - Open the hatch GUI - confirm "Connected" status and the WAP serial shown.
5. **Output hatch:** Attach to a multiblock (e.g. an EBF); run a recipe producing items + fluids; confirm both enter the ME network wirelessly.
6. **Input hatch:** Attach to a multiblock; set config slots; run a recipe consuming items + fluids; confirm both are pulled from the ME network.
7. **Cross-dimension:** Place the hatch in the Nether, WAP in Overworld; confirm it still connects and works.
8. **Multiblock recognition:** Confirm the output hatch is recognised by multiple multiblock types (not just one).
9. **Connection lifecycle:** Break the hatch; confirm no ghost connection (check AE2 grid integrity). Unload the chunk; reload; confirm reconnection.
10. **Unbind:** Screwdriver-right-click the hatch; confirm it disconnects.

- [ ] **Step 7: Final commit (if any fixes were needed)**

```bash
git add -A
git commit -m "fix: resolve in-game verification issues"
```

---

## Self-Review Notes

**Spec coverage check:**
- Spec section 1 (project config): Task 1
- Spec section 2 (wireless connection): Task 3 (WirelessGridManager) + Task 4 (MemoryCardHandler)
- Spec section 3 (merge architecture): Task 5 (output) + Task 6 (input) + Task 2 (interfaces)
- Spec section 4 (data flow): Task 5 + Task 6 (provider reuse, shared proxy)
- Spec section 5 (GUI): Task 9
- Spec section 6 (capacity): Task 5 (`Long.MAX_VALUE`)
- Spec section 7 (Mixin): Task 7
- Spec section 8 (registration): Task 8
- Spec section 9 (file structure): all tasks

**Known limitations of this plan:**
- GT/AE2 API signatures could not be verified at plan-writing time (network rate limits). Each task that touches GT/AE2 APIs includes a "API verification" step instructing the engineer to read the dev jar sources first. This is intentional, not a placeholder gap.
- The Mixin output-dispatch injection (Task 7) is the highest-risk piece - the exact `@Inject`/`@Redirect` targets depend on GT source structure that must be read at implementation time. The plan provides the structure and guidance, not verifiable code.
- Textures are not specified in detail (placeholder in file structure). The engineer creates minimal textures; polish is out of scope.