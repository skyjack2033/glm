# Wireless ME Hatch

A GTNH (GregTech New Horizons) mod that adds wireless ME input/output hatches.

## Features

- **Wireless Output Hatch (ME)** - Merges fluid output hatch and item output bus into one block. Stores multiblock machine outputs (items + fluids) directly into your ME network wirelessly.
- **Wireless Input Hatch (ME)** - Merges fluid input hatch and item input bus into one block. Pulls items and fluids from your ME network wirelessly for multiblock machine recipes.

### How it works

Both hatches connect to your AE2 ME network **without cables** using AE2's `createGridConnection` API (the same invisible connection mechanism as Quantum Bridges and P2P ME tunnels).

### How to connect

1. **Sneak-right-click** an AE2 Wireless Access Point (WAP) with an AE2 Memory Card to record its serial
2. **Right-click** the wireless hatch with the bound Memory Card to connect
3. **Screwdriver right-click** the hatch to unbind

Connections work cross-dimension with no distance limit.

## Requirements

- Minecraft 1.7.10
- Forge 10.13.4.1614
- GregTech 5-Unofficial 5.09.54.20 (GTNH 2.9.0)
- Applied Energistics 2 rv3-beta-1000-GTNH
- UniMixins (for Mixin support)

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

A config file (`wirelessmehatch.cfg`) is generated in the `config/` directory on first launch, allowing you to change the MetaTileEntity IDs if they conflict with other mods.

## Technical Details

- **Output hatch**: Extends `MTEHatchOutput`, implements custom `IDualOutputHatch` interface. A Mixin on `MTEMultiBlockBase` makes all multiblock controllers recognize the dual output.
- **Input hatch**: Extends `MTEHatchInput`, implements GT-native `IDualInputHatch`.
- **GUI**: ModularUI 2 (MUI2)
- **Mixin**: `MTEMultiBlockBaseMixin` extends `MTEMultiBlockBase` to recognize `IDualOutputHatch`

## License

MIT License - Copyright (c) 2026 skyjack2033
