# Frostline — Forge 1.20.1

Java shim for the Frostline worldgen datapack: everything the datapack needs that vanilla
cannot express, except railways.

| id / system | what |
|---|---|
| `frostline:progression` | density function that reads X/Z: "region N begins M blocks north" |
| `frostline:lake_field`, `frostline:frozen_lake` | lake sites by X/Z and the feature that carves them |
| `frostline:pond` | level-water pond feature |
| weather | scheduled snowstorms, snowfall, fog, wind, snow particles (`frostline-weather*.toml`) |

**Railways are a separate mod:** `FrostLineRailways` (modid `frostline_railways`), which
depends on this one. See `CUSTOMMOD.md` and `RAILWAYS.md` in the datapack.

## `frostline:progression`

```
z       = blockZ * z_direction
along   = |z| / (z >= 0 ? north_range : south_range)
lateral = min(|x| / x_range, x_cap)
t       = min(1, along + lateral)
blocks  = t * range + jitter_blocks * jitter_noise(x, z)
v       = lerp(start_value, end_value, blocks / range)   or   piecewise(knots, blocks)
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
| `knots` | none | `[[blocks, value(, jitter)], ...]` piecewise-linear map, overrides start/end |
| `jitter_noise`, `jitter_xz_scale`, `jitter_blocks` | none, 0.35, 0 | border wobble in blocks |

## Building

```
./gradlew build
```

`build/libs/frostline-1.0.0.jar` → `mods/`. JDK 17. Install the datapack separately.
