# Wireless Output Assembly Status Texture Design

Date: 2026-07-13

## Goal

Give the Wireless Unified Output Assembly a distinct GTNH-style face overlay that communicates whether its ME link is usable.

The selected visual direction is Option A, "Classic Wi-Fi":

- Connected and usable: a three-arc Wi-Fi symbol with a center dot.
- Disconnected or unusable: one large X symbol.

## Scope

This change affects only `MTEWirelessUnifiedOutputAssemblyME` and its two new block overlay assets.

It does not change:

- `wireless_link_tool.png` or any Link Tool behavior.
- The legacy wireless input hatch.
- The GT machine casing underneath the overlay.
- Wireless binding, security, channel, cache, or output behavior.
- Item models, GUIs, animations, or emissive glow layers.

## Visual Assets

Create two hard-edged, transparent 16x16 PNG overlays:

1. `assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY.png`
   - Large red X centered on the face.
   - Used for the disconnected/unusable state.
2. `assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY_ACTIVE.png`
   - Classic three-arc cyan Wi-Fi symbol with a bright center dot.
   - Used for the connected/usable state.

Both overlays must:

- Keep a two-pixel transparent border so the GT casing remains visible.
- Use only fully transparent or fully opaque pixels.
- Avoid antialiasing and partial-alpha edges.
- Remain legible when viewed at native 16x16 resolution.

Approved palette:

- Dark outline: `#1B2223`
- Dim cyan: `#23878F`
- Active cyan: `#43D9D7`
- Cyan highlight: `#D7FFFF`
- Dark red: `#7E2023`
- Active red: `#E14845`
- Red highlight: `#FF9A8F`

## Texture Registration

Register required icons with the GT5U API:

```java
Textures.BlockIcons.custom(
    "wirelessmehatch:iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY");
Textures.BlockIcons.custom(
    "wirelessmehatch:iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY_ACTIVE");
```

`getTexturesInactive(...)` returns the X overlay and `getTexturesActive(...)` returns the Wi-Fi overlay through `TextureFactory.of(...)`.

Do not use the removed `Textures.BlockIcons.CustomIcon` API or `customOptional`.

## Runtime State

The visual state follows actual output usability, not merely whether a target is stored:

```text
usable = isWirelessConnected() && getProxy().isActive()
```

On the server, synchronize the GT base tile active flag to this value every 20 ticks. This follows the native GT ME hatch pattern and lets the existing GT texture synchronization select the correct overlay.

State behavior:

| Link state | Proxy active | Overlay |
|---|---:|---|
| No target or no live grid connection | Any | X |
| Live connection, but network is unpowered or no channel is available | No | X |
| Live connection with a powered, active AE node | Yes | Wi-Fi |

The transition may take up to one second because status synchronization runs every 20 ticks.

## Error Handling

The icons are required resources. A missing resource must not be silently hidden; resource tests and the full build must fail before delivery if either PNG is absent or malformed.

No changes are made to connection retry or failure handling. A failed ME connection naturally leaves the base tile inactive and displays X.

## Verification

Automated checks:

- A truth-table unit test for the visual-state policy.
- A resource test that loads both PNGs and verifies 16x16 dimensions.
- A resource test that verifies the outer two-pixel border is transparent and all pixels use alpha 0 or 255.
- Existing 51 tests, Spotless, Checkstyle, full build, and `git diff --check`.

In-game checks:

1. An unbound assembly displays X.
2. A bound assembly with a usable ME connection changes to Wi-Fi within one second.
3. Removing ME power or channel access changes it back to X within one second.
4. Restoring the network changes it back to Wi-Fi without rebinding.
5. Cached item and fluid output behavior remains unchanged.
6. The Wireless Link Tool texture remains unchanged.
