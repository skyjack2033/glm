<p align="right"><a href="./README.md">简体中文</a></p>

<div align="center">
  <h1>Wireless ME Hatch</h1>
  <p>Wireless ME unified output assembly for GregTech multiblocks</p>
  <p>
    <a href="https://github.com/skyjack2033/glm/actions/workflows/build-and-test.yml"><img alt="CI" src="https://github.com/skyjack2033/glm/actions/workflows/build-and-test.yml/badge.svg?branch=master"></a>
    <a href="https://github.com/skyjack2033/glm/releases"><img alt="Release" src="https://img.shields.io/github/v/release/skyjack2033/glm"></a>
    <a href="./LICENSE"><img alt="MIT License" src="https://img.shields.io/github/license/skyjack2033/glm"></a>
  </p>
</div>

> [!IMPORTANT]
> Wireless ME Hatch is an unofficial GTNH community mod. It is neither affiliated with nor maintained by the GTNH team. Report installation, compatibility, and runtime problems in this repository's [Issues](https://github.com/skyjack2033/glm/issues), not through official GTNH support channels.

## Overview

- The Wireless Unified Output Assembly is one real `MTEHatchOutput` that directly implements `IOutputBus`. The same valid MetaTileEntity serves as both a fluid output hatch and an item output bus; there is no hidden delegate and no second MTE.
- Exact-descriptor Mixins connect that instance to the native output paths of ordinary GT multiblocks and GT++ steam multiblocks; item output, fluid output, and multi-fluid parallel handling remain in the corresponding GT paths.
- The Wireless Link Tool records an explicit AE2 ME Controller or Security Terminal and then binds the assembly to that target network; the implementation does not globally discover a player's ME networks.

## Behavior and Limits

- The assembly is output-only; the mod registers no input hatch or legacy input-hatch placeholder MTE.
- Items use one aggregate cache capacity shared by all item types. Fluids use one aggregate cache capacity shared by all fluid types, not a separate capacity for each fluid.
- Multi-fluid recipe parallelism is calculated from the combined demand of all fluid outputs and the shared remaining fluid capacity, bounded by the recipe's `maxParallel`.
- When the network is disconnected, ME space is insufficient, or insertion energy is insufficient, uninserted items and fluids remain in the persistent cache and are retried after recovery. Capacities and caches are saved in the block NBT.
- The wireless proxy still requires AE2 power, a channel, and security permission. The assembly has no physical cable face and cannot be connected directly with ME cable.
- `Bound` only means that a target record exists; the green binding chat message does not mean the assembly is already `Connected`. If the target chunk is unloaded, power is unavailable, no channel is available, or security checks fail, the target remains `Bound` while the connection state is `Disconnected`.
- The cyan Wi-Fi `ACTIVE` state only means that a connection exists and the proxy is active. It does not mean that the ME network still has storage space available.

## Compatibility and Requirements

| Component | Exact version |
| --- | --- |
| Minecraft | `1.7.10` |
| Minecraft Forge | `10.13.4.1614` |
| GTNH test environment | `2.9.0 beta test environment` |
| GT5-Unofficial | `5.09.52.594` |
| Applied Energistics 2 | `rv3-beta-977-GTNH` |

These versions are the current development and verification baseline. This documentation does not extrapolate compatibility to any older version.

## Downloads and Installation

| Channel | Update rule | Intended use |
| --- | --- | --- |
| [Stable Release](https://github.com/skyjack2033/glm/releases) | Created when a `v*` tag is pushed | Stable use and reproducible versions |
| [`dev-build` prerelease](https://github.com/skyjack2033/glm/releases/tag/dev-build) | Updated after every successful `master` build | Testing the latest development state |

1. Back up the world and instance configuration.
2. From the Release assets or local `build/libs/`, select only the single production JAR whose filename does not contain `-dev`, `-sources`, or `-api`.
3. Install the same production JAR in the `mods/` directory on both the client and the server.
4. Remove or move old and duplicate Wireless ME Hatch JARs so that the mod is not loaded twice, then start the game.

## Binding and Unbinding

1. A player with permission on the target ME network uses the Wireless Link Tool to right-click an ME Controller or Security Terminal and record the target in the tool.
2. Use the same tool to right-click the Wireless Unified Output Assembly and bind the stored target to the assembly.
3. Connection state refreshes at most about once per second (20 ticks). The binding record remains while the target is temporarily unavailable, and retries continue.
4. Sneak-right-clicking any block clears the target stored in the tool but does not unbind an already-bound assembly.
5. Screwdriver-right-clicking the assembly clears its target and unbinds it.

If a scanner shows `Bound` + `Disconnected`, check in order that the target chunk is loaded, the AE2 network is powered, a channel is available, and the player who recorded the target has security permission on that network.

## Status Icons

| State | Icon | Meaning |
| --- | --- | --- |
| `ACTIVE` | <img src="src/main/resources/assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY_ACTIVE.png" width="32" height="32" alt="ACTIVE cyan Wi-Fi"> | Cyan Wi-Fi; shown only when the wireless connection exists and the AE2 proxy is active. |
| Not `ACTIVE` | <img src="src/main/resources/assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY.png" width="32" height="32" alt="Inactive red X"> | Red X; no usable active connection is currently available. |

Scanner information shows item cache/capacity, fluid cache/capacity, `Bound`/`Unbound`, and `Connected`/`Disconnected`. The status icon updates about every 20 ticks; cyan Wi-Fi does not guarantee free insertion space in ME storage.

## Configuration

After the first launch, the configuration file is at `config/wirelessmehatch.cfg`.

| Key | Default | Range or unit | Meaning |
| --- | --- | --- | --- |
| `metaTileEntityIds.wirelessUnifiedOutputAssemblyMeId` | `31701` | `17000..32000` | MetaTileEntity ID for the Wireless Unified Output Assembly. |
| `outputCapacity.defaultItemCapacity` | `9223372036854775807` | item | Aggregate item cache capacity for newly placed assemblies. |
| `outputCapacity.defaultFluidCapacity` | `9223372036854775807` | mB | Shared aggregate fluid cache capacity for newly placed assemblies. |

The old `legacyWirelessInputHatchMeId` and `wirelessInputHatchMeId` keys are no longer read and can be removed. Before upgrading, back up the world and remove every legacy Wireless Input Hatch while still running the old version; this version no longer reserves `17002` for them. Existing configurations do not automatically change the unified assembly ID, which avoids silently breaking saves; remove assemblies using the old ID before changing it. Capacity defaults affect only newly placed assemblies; saved blocks restore their own `long` capacities and caches from NBT.

## Build and Verification

| Stage | Tool | JVM or artifact target |
| --- | --- | --- |
| Build | Gradle `9.4.0`, gtnhgradle `2.0.26` | Build JVM is JDK `25` |
| Development client | `runClient17` | Uses the Java `17` toolchain |
| Production JAR | Jabel | Java `8` class file major `52` |

Full POSIX verification command:

```bash
./gradlew clean spotlessJavaCheck test build --rerun-tasks --console=plain
```

Full Windows verification command:

```powershell
.\gradlew.bat clean spotlessJavaCheck test build --rerun-tasks --console=plain
```

Start the Java 17 development client:

```bash
./gradlew runClient17
```

```powershell
.\gradlew.bat runClient17
```

CI covers pushes to every temporary branch, pull requests targeting `master`, and manual runs. CI uses the same complete verification tasks.

## Technical Documentation

- [Wireless Unified Output Assembly architecture](docs/architecture/wireless-unified-output-assembly.md)
- [GTNH 2.9.0 runtime API report](docs/architecture/gtnh-290-runtime-api-report.md)
- [Repository presentation and automation contract](docs/architecture/repository-presentation-and-automation.md)

## Contributing

1. Create a temporary feature branch from `master`.
2. Commit changes on that branch and wait for branch CI to pass.
3. Open a pull request targeting `master`.
4. Merge after review and CI pass, then delete the local and remote temporary branch.

`master` is the only long-lived branch. Only a successful `master` build updates the moving `dev-build` prerelease; only `v*` tags create stable Releases.

## License and Acknowledgements

This project is licensed under the [MIT License](LICENSE), Copyright (c) 2026 skyjack2033.

Thanks to GTNH, GT5-Unofficial, Applied Energistics 2, TST, GTNL, and GTNH Actions and their contributors for the runtime environment, implementation references, and build infrastructure.
