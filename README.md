# Wireless ME Hatch

A GTNH (GregTech New Horizons) mod that adds a wireless unified ME output assembly for GregTech multiblocks.

## Features

- **Wireless Unified Output Assembly (ME)** - One physical GregTech output block that acts as both an item output bus and a fluid output hatch, routing item and fluid outputs into a bound AE2 ME network wirelessly.
- **Wireless Link Tool** - Records an AE2 ME Controller or Security Terminal target, then binds the output assembly to that network.

### How it works

The assembly owns an AE2 `AENetworkProxy` and connects to the bound AE2 node with AE2's `createGridConnection` API, the same invisible connection mechanism used by Quantum Bridges and P2P ME tunnels.

GT item output uses the native `MTEHatchOutputBus` path. Fluid output uses an internal `MTEHatchOutput` delegate attached to the same physical block, so GT sees the assembly as both output roles without using GT class Mixins.

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

- **Output assembly**: Extends `MTEHatchOutputBus`, so GT handles item output natively.
- **Fluid delegate**: Internal `MTEHatchOutput` view attached to the same controller output list for fluid output.
- **AE2 link**: Uses `AENetworkProxy`, `IGridNode.setPlayerID`, and `AEApi.instance().createGridConnection`.
- **Mixin**: None. The mod no longer patches GT or AE2 classes for the first custom-tool implementation.

See `docs/architecture/` for the runtime API report and unified output assembly interface notes.

## License

MIT License - Copyright (c) 2026 skyjack2033
