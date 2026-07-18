# GTNH 2.9.0 Runtime API Report

This report records the runtime API surface used by Wireless ME Hatch when targeting the user's GTNH 2.9.0 beta test instance.

## Runtime Instance

Instance mods directory:

```text
I:\prism\instance\GT_New_Horizons_2.9.0-beta-1_Java_17-25\.minecraft\mods
```

Relevant runtime jars:

```text
+unimixins-all-1.7.10-0.3.1.jar
appliedenergistics2-rv3-beta-977-GTNH.jar
gregtech-5.09.52.594.jar
gtnhlib-0.11.24.jar
modularui-1.3.4.jar
modularui2-2.3.80-1.7.10.jar
```

The project must target these versions for compile-time compatibility with the test instance. The current dependencies were newer at the time of this report and must be pinned to:

```groovy
compileOnly("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.594:dev") { transitive = false }
devOnlyNonPublishable(rfg.deobf("com.github.GTNewHorizons:GT5-Unofficial:5.09.52.594:dev"))
compileOnly("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH:dev") { transitive = false }
devOnlyNonPublishable(rfg.deobf("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-977-GTNH:dev"))
```

## GregTech Output API

Runtime jar:

```text
gregtech-5.09.52.594.jar
```

Observed public signatures relevant to output integration:

```java
public interface gregtech.api.interfaces.IOutputBus {
    gregtech.api.enums.OutputBusType getBusType();
    default boolean storePartial(net.minecraft.item.ItemStack);
    boolean storePartial(net.minecraft.item.ItemStack, boolean);
    gregtech.api.interfaces.IOutputBusTransaction createTransaction();
}
```

```java
public class gregtech.api.metatileentity.implementations.MTEHatchOutputBus
    extends gregtech.api.metatileentity.implementations.MTEHatch
    implements gregtech.api.interfaces.IOutputBus {

    public MTEHatchOutputBus(int, String, String, int);
    public MTEHatchOutputBus(int, String, String, int, int);
    public MTEHatchOutputBus(int, String, String, int, String[]);
    public MTEHatchOutputBus(int, String, String, int, String[], int);
    public MTEHatchOutputBus(String, int, String[], gregtech.api.interfaces.ITexture[][][]);
    public MTEHatchOutputBus(String, int, int, String[], gregtech.api.interfaces.ITexture[][][]);

    public boolean storePartial(net.minecraft.item.ItemStack, boolean);
    public gregtech.api.interfaces.IOutputBusTransaction createTransaction();
    public gregtech.api.enums.OutputBusType getBusType();
}
```

```java
public class gregtech.api.metatileentity.implementations.MTEHatchOutput
    extends gregtech.api.metatileentity.implementations.MTEHatch {

    public MTEHatchOutput(int, String, String, int);
    public MTEHatchOutput(String, int, String[], gregtech.api.interfaces.ITexture[][][]);
    public MTEHatchOutput(int, String, String, int, String[], int);
    public MTEHatchOutput(String, int, int, String[], gregtech.api.interfaces.ITexture[][][]);
}
```

```java
public class gregtech.api.metatileentity.implementations.MTEMultiBlockBase {
    public java.util.ArrayList<gregtech.api.metatileentity.implementations.MTEHatchOutput> mOutputHatches;
    public java.util.ArrayList<gregtech.api.metatileentity.implementations.MTEHatchOutputBus> mOutputBusses;

    public void clearHatches();
    protected void addFluidOutputs(net.minecraftforge.fluids.FluidStack[]);
    public boolean addItemOutputs(net.minecraft.item.ItemStack[]);
    public boolean addOutputBusToMachineList(gregtech.api.interfaces.tileentity.IGregTechTileEntity, int);
    public boolean addOutputHatchToMachineList(gregtech.api.interfaces.tileentity.IGregTechTileEntity, int);
    public java.util.List<gregtech.api.interfaces.IOutputBus> getOutputBusses();
}
```

Important constraints:

- `addOutputBusToMachineList` accepts real `MTEHatchOutputBus` instances.
- `addOutputHatchToMachineList` accepts real `MTEHatchOutput` instances.
- `getOutputBusses()` returns a filtered snapshot, not the backing `mOutputBusses` list.
- `MetaTileEntity.isValid()` requires physical identity: the base tile's current MetaTileEntity must be the same object. A hidden item or fluid delegate is therefore invalid if it merely shares the physical tile reference, and assigning it as the tile's MTE invalidates the physical assembly instead.
- Ordinary Java adapters are not safe members of GT output lists. Output-list entries must be valid GT `MetaTileEntity` objects of the expected concrete output class.
- GT output-side classes do not provide a native public `IDualOutputHatch` equivalent to input-side `IDualInputHatch`.

The unified assembly resolves those constraints with one physical object:

- `MTEWirelessUnifiedOutputAssemblyME` extends `MTEHatchOutput` and directly implements `IOutputBus`, `WirelessDualRoleOutput`, and `SharedFluidOutputStore`.
- Exact-descriptor, `require = 1`, `remap = false` `RETURN` Mixins augment both ordinary and steam `getOutputBusses()` snapshots with that same instance, using object-identity deduplication.
- The steam `addSteamBusOutput(...)` `HEAD` hook intercepts only a complete `MTEHatchOutput` + `IOutputBus` + `WirelessDualRoleOutput` object and registers it through native `addOutputHatchToMachineList(...)`.
- No code writes controller output lists reflectively, periodically reattaches an object, or creates a second MetaTileEntity for either role.

GT's native item transaction and output dispatch remain authoritative, and the native void-protection path remains the integration point. The only fluid capacity correction is an exact-descriptor, pinned `RETURN` hook on `VoidProtectionHelper.calculateMaxFluidParallels()`: for finite shared stores it unwraps `OutputHatchWrapper`, deduplicates physical stores by identity, aggregates remaining capacity, and replaces the fluid parallel result with the combined multi-fluid limit using saturating `long` arithmetic, bounded by `maxParallel`. This can correct a conservative native early-zero return rather than merely lower it, and it does not modify cached output.

## AE2 Network API

Runtime jar:

```text
appliedenergistics2-rv3-beta-977-GTNH.jar
```

Observed public signatures relevant to wireless linking:

```java
public interface appeng.api.IAppEngApi {
    appeng.api.networking.IGridConnection createGridConnection(
        appeng.api.networking.IGridNode a,
        appeng.api.networking.IGridNode b)
        throws appeng.api.exceptions.FailedConnection;
}
```

```java
public interface appeng.api.networking.IGridNode {
    boolean isActive();
    void loadFromNBT(String name, net.minecraft.nbt.NBTTagCompound tag);
    void saveToNBT(String name, net.minecraft.nbt.NBTTagCompound tag);
    int getPlayerID();
    void setPlayerID(int playerId);
}
```

```java
public interface appeng.api.networking.IGridConnection {
    void destroy();
    appeng.api.networking.IGridNode a();
    appeng.api.networking.IGridNode b();
}
```

```java
public class appeng.me.helpers.AENetworkProxy {
    public void onChunkUnload();
    public void invalidate();
    public void onReady();
    public appeng.api.networking.IGridNode getNode();
    public appeng.api.networking.security.ISecurityGrid getSecurity() throws appeng.me.GridAccessException;
    public appeng.api.util.DimensionalCoord getLocation();
    public void gridChanged();
    public boolean isActive();
    public boolean isPowered();
    public void setOwner(net.minecraft.entity.player.EntityPlayer player);
}
```

```java
public interface appeng.me.helpers.IGridProxyable {
    appeng.me.helpers.AENetworkProxy getProxy();
    appeng.api.util.DimensionalCoord getLocation();
    void gridChanged();
}
```

Runtime Wireless Kit API and endpoint classes:

```java
public interface appeng.api.definitions.IItems {
    appeng.api.definitions.IItemDefinition toolWirelessKit();
}
```

```java
public interface appeng.api.definitions.IItemDefinition {
    boolean isEnabled();
    com.google.common.base.Optional<net.minecraft.item.ItemStack> maybeStack(int amount);
}
```

```java
public abstract class appeng.tile.networking.TileWirelessBase
    implements appeng.api.networking.IGridHost
```

```java
public class appeng.items.tools.ToolWirelessKit {
    public net.minecraft.item.ItemStack onItemRightClick(net.minecraft.item.ItemStack stack,
        net.minecraft.world.World world, net.minecraft.entity.player.EntityPlayer player);

    public boolean onItemUse(net.minecraft.item.ItemStack stack,
        net.minecraft.entity.player.EntityPlayer player, net.minecraft.world.World world,
        int x, int y, int z, int side, float hitX, float hitY, float hitZ);
}
```

Important constraints:

- `IGridNode` and `IGridConnection` are live runtime objects and must not be serialized directly.
- Persist the selected wireless connector/hub coordinates and binding-player identity, never a live node or connection.
- Persist the binding player as UUID/name. Resolve AE2's integer player ID after world load with `WorldData.instance().playerData().getPlayerID(GameProfile)`, then call `IGridNode.setPlayerID(int)`.
- The Wireless Kit's `Super.pins` data is GUI placement/grouping state, not a binding target. BIND/UNBIND selections arrive through `WirelessKitCommand` rows.
- `IGrid.getMachines(Class)` is keyed by exact runtime class in AE2 977. Super data discovery must query exact connector, hub, and assembly classes.
- Use `MachineSource(IActionHost)` for machine-originated AE2 storage actions.

## Integration Rules

The implementation follows these rules for GTNH 2.9.0 beta compatibility:

1. Use one physical `MTEHatchOutput` that directly implements the complete item and fluid contracts; never attach a hidden delegate to the same base tile.
2. Add the physical object to item-bus snapshots only through exact pinned ordinary and steam `getOutputBusses()` return hooks, with validity checks and identity deduplication.
3. Route steam registration through native `addOutputHatchToMachineList(...)`; do not write controller lists reflectively or reattach periodically.
4. Keep GT's native item transaction and output dispatch authoritative. Use native fluid void protection as the integration point, with the finite shared-store aggregate hook determining the final result as described above.
5. Extend only the exact AE2 977 Wireless Kit item/container methods. Do not replace its GUI, packet, or NBT protocol, and do not intercept pure connector/hub commands.
6. Persist the binding target and AE2 proxy state separately from output capacity and cache state.
