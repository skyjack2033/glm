# Wireless ME Hatch

A GTNH (GregTech New Horizons) mod that adds a wireless unified ME output assembly for GregTech multiblocks.

## Features

- **Wireless Unified Output Assembly (ME)** - One physical GregTech output block that acts as both an item output bus and a fluid output hatch, routing item and fluid outputs into a bound AE2 ME network wirelessly.
- **Wireless Link Tool** - Records an AE2 ME Controller or Security Terminal target, then binds the output assembly to that network.

### How it works

The assembly owns an AE2 `AENetworkProxy` and connects to the bound AE2 node with AE2's `createGridConnection` API, the same invisible connection mechanism used by Quantum Bridges and P2P ME tunnels.

The physical assembly extends `MTEHatchOutput` and directly implements `IOutputBus`, so the same valid MetaTileEntity serves both fluid-hatch and item-bus roles. Exact pinned GT Mixins add that instance to ordinary and steam output-bus snapshots; steam registration still goes through GT's native output-hatch registration. GT's native item transaction and output dispatch remain authoritative. Native fluid void protection is the integration point; when a finite shared store is present, the exact aggregate return hook determines the final fluid parallel result.

### How to connect

1. Right-click an AE2 ME Controller or Security Terminal with the Wireless Link Tool.
2. Right-click the Wireless Unified Output Assembly (ME) with the tool to bind it.
3. Screwdriver right-click the assembly to unbind.

Connections are wireless and re-established automatically after load when the bound target is available.

## Requirements

- Minecraft 1.7.10
- Forge 10.13.4.1614
- GregTech 5-Unofficial 5.09.52.594 (GTNH 2.9.0 beta test instance)
- Applied Energistics 2 rv3-beta-977-GTNH

## Building

```bash
./gradlew build
```

The output jar will be in `build/libs/` (use the one without `-dev` or `-api` suffix).

## Installation

1. Build the mod with `./gradlew build`
2. Copy the jar from `build/libs/` into your GTNH instance's `mods/` folder
3. Launch the game

## Configuration

A config file (`wirelessmehatch.cfg`) is generated in the `config/` directory on first launch. It currently exposes:

- `wirelessUnifiedOutputAssemblyMeId` - MetaTileEntity ID for the unified output assembly
- `defaultItemCapacity` - cached item capacity, in item units
- `defaultFluidCapacity` - cached fluid capacity, in millibuckets

## Technical Details

- **Output assembly**: One physical `MTEHatchOutput` implementing `IOutputBus`, `WirelessDualRoleOutput`, and `SharedFluidOutputStore`; there is no hidden delegate or second MetaTileEntity.
- **GT integration**: Exact pinned Mixins augment ordinary and steam `getOutputBusses()` snapshots with identity deduplication. Steam registration calls native `addOutputHatchToMachineList`, and shared multi-fluid void protection uses an aggregate, wrapper-aware, saturating-`long` capacity limit.
- **AE2 link**: Uses `AENetworkProxy`, `IGridNode.setPlayerID`, and `AEApi.instance().createGridConnection`.
- **Mixin scope**: Three narrow GT Mixins cover ordinary/steam output-bus snapshots, steam dual-role registration, and shared fluid capacity. The custom Link Tool requires no AE2 class Mixin.

See `docs/architecture/` for the runtime API report and unified output assembly interface notes.

## License

MIT License - Copyright (c) 2026 skyjack2033
