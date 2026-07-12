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
- Ordinary Java adapters are not safe members of GT output lists. Output-list entries must be valid GT `MetaTileEntity` objects of the expected concrete output class.
- GT output-side classes do not provide a native public `IDualOutputHatch` equivalent to input-side `IDualInputHatch`.

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

Runtime tile classes for the binding tool:

```java
public class appeng.tile.networking.TileController extends appeng.tile.grid.AENetworkPowerTile
```

```java
public class appeng.tile.misc.TileSecurity extends appeng.tile.grid.AENetworkTile
    implements appeng.api.features.ILocatable,
               appeng.api.networking.security.ISecurityProvider
```

Important constraints:

- `IGridNode` and `IGridConnection` are live runtime objects and must not be serialized directly.
- Persist binding targets as coordinates, locatable serials, and owner identity.
- Persist owner as UUID/name. Resolve AE2's integer player ID after world load with `WorldData.instance().playerData().getPlayerID(GameProfile)`, then call `IGridNode.setPlayerID(int)`.
- Security Terminal can be used as an anchor via coordinates and `ILocatable` serial; ME Controller can be used as an anchor via coordinates.
- Use `MachineSource(IActionHost)` for machine-originated AE2 storage actions.

## Integration Rules

The implementation must follow these rules for GTNH 2.9.0 beta compatibility:

1. Do not mix into GregTech base classes such as `MTEMultiBlockBase`.
2. Do not mix into AE2's `ToolMemoryCard` for the first custom-tool implementation.
3. The public block must be a real `MTEHatchOutputBus` so item output is GT-native.
4. The fluid view must be a real `MTEHatchOutput` delegate attached to the same base tile.
5. The delegate may be reflectively inserted into `mOutputHatches`, but ordinary Java adapters must not be inserted into GT output lists.
6. Reattachment must tolerate GT structure rechecks and `clearHatches()`.
7. The binding target and AE2 proxy state must be persisted separately from output capacity and cache state.
