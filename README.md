# Frostline — Forge 1.20.1

Java shim for the Frostline worldgen datapack. This mod registers exactly one
thing: the `frostline:progression` density function type.

Vanilla density functions can read Y, noise and splines but never X/Z position,
so "region N begins M blocks from spawn" cannot be expressed in a pure datapack.
This mod fills that single gap. All worldgen data — terrain, surfaces, biomes,
features, dimensions — lives in the separate Frostline datapack, which requires
this mod to load.

## `frostline:progression`

```
z       = blockZ * z_direction
along   = |z| / (z >= 0 ? north_range : south_range)
lateral = min(|x| / x_range, x_cap)
t       = min(1, along + lateral)
v       = lerp(start_value, end_value, t)
if z < 0: v = min(v, south_cap)
```

| Field | Default | Meaning |
|---|---|---|
| `north_range` | 50000 | blocks to reach `end_value` on the +z side |
| `south_range` | 50000 | blocks to reach `end_value` on the -z side |
| `x_range` | 250000 | blocks of lateral travel per unit of `t` |
| `x_cap` | 0.30 | max lateral contribution, so east/west travel alone never reaches the end |
| `south_cap` | 0.50 | ceiling on the -z side |
| `start_value` | -1.0 | value at origin |
| `end_value` | 1.0 | value at full progression |
| `z_direction` | 1.0 | -1 flips the payoff to compass north (-Z) |

Output is smooth and monotonic in |z|. Add border wobble datapack-side by
summing a low-frequency noise onto it.

Example:

```json
{
  "type": "frostline:progression",
  "north_range": 50000.0,
  "south_cap": -0.7,
  "z_direction": -1.0
}
```

## Building

```
./gradlew build
```

`build/libs/frostline-1.0.0.jar` → `mods/`. JDK 17. Install the datapack
separately.
