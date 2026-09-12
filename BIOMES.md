# Frostline — Biome Dictionary

Every region, every biome, and exactly what generates in it. Generated from
`gen3.py`'s tables — if you change the generator, regenerate this by hand or it
goes stale.

Companion docs: `CONTROLS.md` (what to edit to change things),
`WORLDGEN.md` (techniques), `STRUCTURES.md` (everything that needs NBT, sounds
or hand-built set-pieces — i.e. everything data cannot do),
`README.md` (design overview).

---

## The story the world is telling

Read this first; every number below is downstream of it.

A storm started in **region 1** and walked north. It got stronger the further
it went, so **damage increases with distance**: region 1 is the countryside it
passed through, region 4 is where it is *standing right now*. The green fields
of region 5 sit behind a wall of ice it cannot climb.

Behind you, in the south, is **The Quiet** — region 0. Different disaster,
older, quieter, still spreading. The world is a corridor between two things
that are wrong, and the whole game is running up the middle of it.

| moving north | snow | life | civilisation | terrain |
|---|---|---|---|---|
| 1 Hollow Drifts | thin, ground shows | trees, some with leaves | almost none | hills only, no peaks |
| 2 Deadly Snowfields | normal | dead trunks only | scattered, hiding | overhangs, rare peaks |
| 3 Riven Wastes | deep | none | towns | rifts, big cliffs, ice spikes |
| 4 Stormheart | deep | none | most, most destroyed | red mountains, deep valleys |
| 4b Hard Snowcliff | heaviest | none | cliff-edge only | one wall, 40 000 north |
| 5 Verdant Reach | none | everything | villages | low, soft, green |
| 0 The Quiet *(south)* | none | sculk | none, ever | sunken, shelved, cratered |

---

## How to read this

**Climate** columns say where a biome is allowed to appear:

| column | meaning |
|---|---|
| **tier** | erosion band — `PEAK` −1.00…−0.55, `CRAG` −0.55…−0.375, `ROLL` −0.375…0.20, `FLAT` 0.20…1.00. Drives terrain height *and* biome choice from the same value, so a PEAK biome cannot land on flat ground. |
| **hum** | humidity — `LOW_H` −1…0, `HIGH_H` 0…1, `FULL` both |
| **wd** | weirdness — `LOW_W` −1…0, `HIGH_W` 0…1, `FULL` both |

Two biomes in the same region and tier **must** differ on hum or wd, or they
overlap and one wins arbitrarily.

**Surface** columns are the palette's four slots:

| slot | when it fires |
|---|---|
| **steep** | cliff faces (`minecraft:steep`). Always bare rock or ice — this is what the snow drifts bank against. |
| **top** | the surface block. Listed rarest-first; entries before the last are noise-gated patches, the last is the default. |
| **under** | the few blocks below the surface |
| **deep** | the layer under that |

**Features** are listed by generation step: `s2` local modifications (boulders,
sheets, rubble, craters), `s4` surface structures (spires, needles, icicles),
`s9` vegetal decoration (trees, grass, sculk), `s10` top layer (snow, and the
bare-ground patches that punch back through it). `carv` is a carver.

---

## Region index

| # | Region | Distance from spawn | temperature band | Snow | Biomes |
|---|---|---|---|---|---|
| **0** | **The Quiet** | **5 000 south, forever** | *(continentalness 0.60…1.00)* | none | 12 |
| 1 | **Hollow Drifts** *(spawn)* | 0 north — **and the entire south to 5 000** | −1.00 … −0.55 | `light` | 9 |
| 2 | **Deadly Snowfields** | 11 250 north | −0.55 … −0.20 | `normal` | 9 |
| 3 | **Riven Wastes** | 20 000 north | −0.20 … 0.15 | `deep` | 9 |
| 4 | **Stormheart** | 28 750 north | 0.15 … 0.54 | `deep` | 9 |
| 4b | **Hard Snowcliff** | 38 500 north | 0.54 … 0.60 | `heavy` | 4 |
| 5 | **Verdant Reach** | **40 000 north** | 0.60 … 1.00 | none | 7 |

**59 biomes total.** Regions 1–5 are contiguous bands on `temperature`, so you
physically cannot reach region 4 without crossing 2 and 3. The Quiet rides a
second axis and is pinned out of existence everywhere north of spawn — which is
why it is region **0**: it is not a stop on the journey, it is the thing the
journey is running away from.

### Altitude, region by region

The world climbs the whole way north and then falls off a cliff. Approximate
surface heights, ignoring 3D noise and jagged tips:

| Region | PEAK | CRAG | ROLL | FLAT |
|---|---|---|---|---|
| 1 Hollow Drifts *(x0.64)* | 119 | 97 | 77 | 70 |
| 2 Deadly Snowfields | 151 | 116 | 84 | 74 |
| 3 Riven Wastes *(plateau climbing)* | 168 | 134 | 102 | 92 |
| 4 Stormheart *(x1.17, +plateau)* | 201 | 161 | 123 | 112 |
| 4 Stormheart valley floors | 177 | 136 | 99 | 88 |
| 4b Hard Snowcliff — **the lip** | 201 | 161 | **123** | **112** |
| 5 Verdant Reach *(x0.32, dropped)* | **84** | 73 | 62 | 59 |

The last two rows are the set-piece: everything in region 5 is below everything
on the lip.

---

## Region 0 — The Quiet

5 000 blocks compass south, and it never ends. Reached on `continentalness`, not
`temperature`, so it is a hard overlay rather than a step in the sequence.

No snow (`has_precipitation: false`) · particle `warped_spore` · music
`deep_dark` · mobs `QUIET_M` (silverfish-led) · no creatures · biome temp
0.72 … 0.83

**One material family, graded.** Host rock is smooth_basalt / blackstone /
deepslate / obsidian; `sculk` is the growth. The seven palettes differ **only**
in how low the `frostline:infest` noise threshold sits before sculk wins:

| palette | rule | reads as |
|---|---|---|
| `q_bare` | sculk where infest ≥ 0.30 | host rock with sculk in it |
| `q_flecked` | sculk where infest ≥ 0.10 | half and half, rock winning |
| `q_glass` | sculk where infest ≥ 0.00 | obsidian shelves in a sculk field |
| `q_veined` | **rock** where infest ≥ 0.52 | sculk with rock in it |
| `q_creep` | **rock** where infest ≥ 0.62 | sculk, rock islands |
| `q_bloom` | **rock** where infest ≥ 0.78 | sculk, rare rock |
| `q_total` | sculk, always | solid sculk, steep faces included |

**The test inverts at `q_veined`.** Below it, host rock is the default and sculk
has to earn its place; from `q_veined` up, **sculk is the default and the rock
is what needs a high noise value to survive**. That is the difference between a
rock field with a rash on it and a region that has been eaten, and it is the one
number to change if The Quiet ever looks too clean again.

`humidity` is repurposed here as **infestation stage** — `LOW_H` sparse,
`HIGH_H` overtaken. `weirdness` gives variety within a stage.

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `whisper_spires` | PEAK | LOW_H | FULL | `q_bare` | deepslate | sculk → blackstone → **smooth_basalt** | smooth_basalt | deepslate |
| `sculkspine_peaks` | PEAK | HIGH_H | FULL | `q_veined` | **sculk** | blackstone → smooth_basalt → **sculk** | blackstone | deepslate |
| `hushed_shelf` | CRAG | LOW_H | FULL | `q_flecked` | cobbled_deepslate | sculk → smooth_basalt → **blackstone** | smooth_basalt | deepslate |
| `resonant_crag` | CRAG | HIGH_H | FULL | `q_glass` | obsidian | sculk → obsidian → **smooth_basalt** | smooth_basalt | deepslate |
| `creeping_downs` | ROLL | LOW_H | LOW_W | `q_flecked` | cobbled_deepslate | sculk → smooth_basalt → **blackstone** | smooth_basalt | deepslate |
| `vein_hollows` | ROLL | LOW_H | HIGH_W | `q_veined` | **sculk** | blackstone → smooth_basalt → **sculk** | blackstone | deepslate |
| `bloom_hollows` | ROLL | HIGH_H | LOW_W | `q_creep` | **sculk** | smooth_basalt → blackstone → **sculk** | **sculk** | deepslate |
| `shrieker_fields` | ROLL | HIGH_H | HIGH_W | `q_bloom` | **sculk** | blackstone → **sculk** | **sculk** | deepslate |
| `muted_flats` | FLAT | LOW_H | LOW_W | `q_bare` | deepslate | sculk → blackstone → **smooth_basalt** | smooth_basalt | deepslate |
| `still_barrens` | FLAT | LOW_H | HIGH_W | `q_veined` | **sculk** | blackstone → smooth_basalt → **sculk** | blackstone | deepslate |
| `sculk_sea` | FLAT | HIGH_H | LOW_W | `q_bloom` | **sculk** | blackstone → **sculk** | **sculk** | deepslate |
| **`the_quiet`** *(the heart)* | FLAT | HIGH_H | HIGH_W | `q_total` | **sculk** | **sculk** | **sculk** | deepslate |

**Features — deliberately almost nothing.**

The previous pass put a boulder and a pillar field in all twelve biomes and the
region read as a rubble pile. It is supposed to read as *silence with things
growing in it*, so the **props** are roughly a third of what they were and five
of the twelve biomes have no step-2 or step-4 feature at all. What is left is
rare enough that finding one means something. **The terrain does the talking
here, not the props.**

**Ground coverage is the opposite call.** Sculk patches and veins are *growth*,
not decoration, and thin growth just makes the region look like a quarry. The
three sculk passes and the vein pass are all back above their pre-diet values
(`bloom` is 3 charges x 420 at 45 % catalysts, veins are count 20 at 0.7 spread)
and **every** biome including the two threshold ones runs veins.

| Biome | s2 | s4 | s9 sculk |
|---|---|---|---|
| `whisper_spires` | — | `deepslate_needle` | `sculk/crust` |
| `sculkspine_peaks` | `sculk_boulder` | `quiet/spine` | `sculk/spread`, `sculk/veins` |
| `hushed_shelf` | — | — | `sculk/crust` |
| `resonant_crag` | — | `quiet/spine` | `sculk/crust`, `sculk/veins` |
| `creeping_downs` | — | — | `sculk/crust` |
| `vein_hollows` | `sculk_boulder` | — | `sculk/spread`, `sculk/veins` |
| `bloom_hollows` | — | — | `sculk/spread`, `sculk/veins` |
| `shrieker_fields` | — | `sculk/pillar` | `sculk/bloom`, `sculk/veins` |
| `muted_flats` | — | — | `sculk/crust` |
| `still_barrens` | `basalt_boulder` | — | `sculk/spread`, `sculk/veins` |
| `sculk_sea` | `quiet/crater` | — | `sculk/bloom`, `sculk/veins` |
| `the_quiet` | `quiet/crater` | `sculk/pillar` | `sculk/bloom`, `sculk/veins` |

**Ambience: the sound never stops.** Every one of the twelve biomes now carries
`minecraft:ambient.soul_sand_valley.loop` — the loop that used to be reserved
for the bloom stages. On top of it, `additions_sound` tick_chance and the
`warped_spore` particle rate both climb with infestation stage, and the cave
mood sound speeds up from one hit per 5 200 ticks at `q_bare` to one per 2 200
at `q_total`:

| stage | additions chance | spores | mood delay |
|---|---|---|---|
| `q_bare` | 0.0016 | 0.004 | 5 200 |
| `q_flecked` | 0.0030 | 0.006 | 4 800 |
| `q_glass` | 0.0045 | 0.008 | 4 400 |
| `q_veined` | 0.0060 | 0.010 | 4 000 |
| `q_creep` | 0.0085 | 0.013 | 3 400 |
| `q_bloom` | 0.0115 | 0.016 | 2 800 |
| `q_total` | 0.0150 | 0.020 | 2 200 |

Walking out of The Quiet is supposed to feel like surfacing. Sculk veins run in
all twelve biomes; what separates the threshold biomes (`whisper_spires`,
`muted_flats`, `hushed_shelf`, `creeping_downs`) is that their *surface* is
still mostly host rock, so the region announces itself with sound and veins
before the ground itself turns.

**Terrain here is the feature.** Four things, all in the density router:

1. **It sinks and swells.** Ground drops ~0.055 and low organic mounds push
   back out of it (`QUIET_SWELL`).
2. **Huge overheads.** `QUIET_SHELF` adds solidity inside one Y band only
   (78–146, peaking 98–120). Because it is *not* flat-cached it can do what
   `OFFSET` never can: bulge rock outward at canopy height and leave the ground
   under it undercut. Shelves, lips, roofs. The sculk veins crawl across the
   underside of them, which is the entire reason `sculk/veins` exists.
3. **Craters.** `minecraft:ore` will happily place **air**, which makes it the
   cheapest bowl-digger in the game — four overlapping blobs around one point,
   dropped 2–6 blocks below the surface. The walls expose raw host rock,
   because surface rules ran long before the crater did: the crater is a cut
   through the skin.
4. **Nothing is sharp.** Jaggedness is cancelled outright, `FACTOR` drops by
   1.4 (floored at 1.6, so it undercuts but stays connected). Still means still.

Deep strata are banded host rock from bedrock up — digging into The Quiet never
finds ordinary stone.

> **Wanted, needs NBT:** tentacles, arches, the big overhead set-pieces and the
> husk ruins. See `STRUCTURES.md §2`. A `block_column` cannot curve, so every
> tendril in the design doc is a structure, not a feature.

---

## Region 1 — Hollow Drifts

*Spawn region. Also the whole southern half of the world until The Quiet.*

**The countryside. The storm came through here and kept going.** Snow is thin
enough to see the world under it: dirt, stone and gravel show through in
patches, trees still stand, and a few of them still have leaves on. There is
almost no ice, there is no running water anywhere in this world, and there are
**no peaks and no mountains** — the height field is scaled down by 36 % across
the whole region and jaggedness is cancelled by 88 %, so PEAK-tier biomes are
tall hills, not summits. Hills and flats, with details punched into them.

Snow `light` · particle `snowflake` 0.0022 · ambient `WIND` · music
`snowy_slopes` (PEAK tiers get `frozen_peaks`) · mobs `COLD_M` (stray-heavy) ·
creatures `SNOW_C` (rabbit, polar bear, fox) · biome temp −0.75 … −0.67

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `frostcap_rise` | PEAK | FULL | LOW_W | `r1_crown` | stone | stone *(above y120)* → stone → gravel → **snow_block** | stone | stone |
| `windscoured_crags` | PEAK | FULL | HIGH_W | `r1_bare` | stone | stone → gravel → cobblestone → **snow_block** | stone | stone |
| `frost_shelf` | CRAG | LOW_H | FULL | `r1_ground` | stone | coarse_dirt → stone → powder_snow → **snow_block** | coarse_dirt | stone |
| `riven_stoneshelf` | CRAG | HIGH_H | FULL | `r1_bare` | stone | stone → gravel → cobblestone → **snow_block** | stone | stone |
| `drift_barrens` | ROLL | LOW_H | FULL | `r1_ground` | stone | coarse_dirt → stone → powder_snow → **snow_block** | coarse_dirt | stone |
| `rime_hollows` | ROLL | HIGH_H | LOW_W | `r1_ground` | stone | coarse_dirt → stone → powder_snow → **snow_block** | coarse_dirt | stone |
| `standing_pines` | ROLL | HIGH_H | HIGH_W | `r1_ground` | stone | coarse_dirt → stone → powder_snow → **snow_block** | coarse_dirt | stone |
| **`frozen_hollow`** *(spawn)* | FLAT | LOW_H | FULL | `r1_ground` | stone | coarse_dirt → stone → powder_snow → **snow_block** | coarse_dirt | stone |
| `snowveil_flats` | FLAT | HIGH_H | FULL | `r1_ground` | stone | coarse_dirt → stone → powder_snow → **snow_block** | coarse_dirt | stone |

**Features**

| Biome | s2 | s9 | s10 bare ground |
|---|---|---|---|
| `frostcap_rise` | `stone_boulder` | — | stone, gravel |
| `windscoured_crags` | `stone_boulder`, `cracked_boulder` | — | stone, gravel |
| `frost_shelf` | `stone_boulder` | `tree/dead_sparse`, `tree/dead_boughs` | dirt, stone |
| `riven_stoneshelf` | `cracked_boulder` | — *(carver: `surface_rift`)* | stone, gravel |
| `drift_barrens` | `frost_boulder` | `tree/dead_sparse`, `tree/dead_pinestand` | dirt |
| `rime_hollows` | — | `tree/dead_grove`, `tree/dead_boughs`, `tree/dead_snag` | dirt, gravel |
| `standing_pines` | — | **`tree/live_grove`**, **`tree/live_pines`**, `tree/dead_sparse`, `tree/dead_boughs` | dirt |
| `frozen_hollow` | — | `tree/dead_sparse`, `tree/dead_boughs` | dirt |
| `snowveil_flats` | `stone_boulder` | `tree/dead_sparse`, `tree/dead_pinestand` | gravel |

> `riven_stoneshelf` is **the stone biome**: bare stone and cobble under a light
> snow, and the only region-1 biome running the `surface_rift` carver, so the
> snow is split open by dry gorges that show the rock underneath.

> `standing_pines` is the only place in 40 000 blocks with living leaves on a
> tree. Do not add it anywhere else; it is the whole point of region 1 that it
> is *nearly* alive.

> Spawn resolves to `frozen_hollow` on flat ground on every seed — the
> `spawn_target` climate box in the noise settings is narrowed to it.

---

## Region 2 — Deadly Snowfields

**The storm found its stride.** Nothing alive is still standing: the only trees
are dead trunks, and they are rare. Snow is at normal depth — clearly deeper
than region 1 — and ice starts forming for real: sheets frozen into the ground,
blue-ice boulders, icicles under every overhang. Cliffs undercut, rifts open,
and there are hollows and holes people crawled into. It did not work.

Snow `normal` · particle `snowflake` 0.0065 · ambient `WIND` + additions 0.0055
· music `snowy_slopes` / `frozen_peaks` · mobs `COLD_M` · creatures `THIN_C`
(rabbit, fox) · biome temp −0.55 … −0.47

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `frozen_spires` | PEAK | FULL | LOW_W | `spire_ice` | blue_ice | stone → packed_ice → **snow_block** | packed_ice | stone |
| `glass_ridges` | PEAK | FULL | HIGH_W | `glass_ice` | blue_ice | blue_ice → **packed_ice** | packed_ice | stone |
| `shattered_shelf` | CRAG | LOW_H | FULL | `r2_crag` | stone | stone → packed_ice → **snow_block** | packed_ice | stone |
| `undercut_shelf` | CRAG | HIGH_H | FULL | `r2_crag` | stone | stone → packed_ice → **snow_block** | packed_ice | stone |
| `deadly_snowfield` | ROLL | LOW_H | FULL | `hard_snow` | packed_ice | blue_ice → powder_snow → **snow_block** | packed_ice | packed_ice |
| `crevasse_fields` | ROLL | HIGH_H | LOW_W | `crevasse` | packed_ice | packed_ice → **snow_block** | packed_ice | stone |
| `hollow_warrens` | ROLL | HIGH_H | HIGH_W | `r2_snow` | packed_ice | packed_ice → powder_snow → **snow_block** | snow_block | packed_ice |
| `whiteout_flats` | FLAT | LOW_H | FULL | `hard_snow` | packed_ice | blue_ice → powder_snow → **snow_block** | packed_ice | packed_ice |
| `bone_drifts` | FLAT | HIGH_H | FULL | `drift_snow` | packed_ice | packed_ice → **snow_block** | snow_block | packed_ice |

**Features**

| Biome | s2 | s4 | s9 | carver |
|---|---|---|---|---|
| `frozen_spires` | — | `rare_ice_spike`, `ice_needle`, `icicle` | — | — |
| `glass_ridges` | — | `ice_needle`, `icicle` | — | — |
| `shattered_shelf` | `frost_boulder`, `ice_sheet` | `icicle` | — | — |
| `undercut_shelf` | `ice_sheet` | `icicle` | — | `surface_rift` |
| `deadly_snowfield` | `frost_boulder` | — | `tree/dead_sparse`, `tree/dead_snag` | — |
| `crevasse_fields` | `ice_sheet` | — | — | — |
| `hollow_warrens` | `frost_boulder`, `ice_sheet` | `icicle` | — | `surface_rift` |
| `whiteout_flats` | — | `rare_ice_spike` | — | — |
| `bone_drifts` | `ice_boulder` | — | — | — |

> `hollow_warrens` is **the hiding biome** — rifts, undercut lips and iced-over
> holes. It is the intended home of the hidden-camp structures in
> `STRUCTURES.md §3`; the terrain is already shaped to hold them.

> `glass_ridges` is the only snowy biome whose default `top` is **not**
> snow_block — it is packed ice, so the drift layers sit on bare ice.

---

## Region 3 — Riven Wastes

**Deepslate arrives and ice starts standing up.** Ice spikes come out of the
ground in fields rather than one at a time, deepslate replaces stone as the
bedrock of the world, cliffs and overhangs get bigger, and the `surface_rift`
carver cuts gorges into hillsides — surface-level splits and ravines, never a
cave system. Nothing in this world goes deep underground and nothing is going
to. Towns start here: more settlement, bigger settlement, more *purposeful*
settlement than region 2, and more of it wrecked.

Snow `deep` · particle `snowflake` 0.012 · ambient `WIND` + additions 0.0095 ·
music `grove` / `frozen_peaks` · mobs `STORM_M` (husks, vindicators) ·
creatures `DEAD_C` · biome temp −0.35 … −0.27

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `slate_horns` | PEAK | FULL | LOW_W | `r3_slate` | deepslate | deepslate → cobbled_deepslate → **snow_block** | deepslate | deepslate |
| `glacier_horns` | PEAK | FULL | HIGH_W | `r3_ice` | blue_ice | packed_ice → deepslate → **snow_block** | packed_ice | deepslate |
| `riven_shelf` | CRAG | LOW_H | FULL | `r3_slate` | deepslate | deepslate → cobbled_deepslate → **snow_block** | deepslate | deepslate |
| `overhang_shelf` | CRAG | HIGH_H | FULL | `r3_snow` | cobbled_deepslate | packed_ice → deepslate → **snow_block** | snow_block | deepslate |
| `spike_barrens` | ROLL | LOW_H | FULL | `r3_snow` | cobbled_deepslate | packed_ice → deepslate → **snow_block** | snow_block | deepslate |
| `slate_hollows` | ROLL | HIGH_H | LOW_W | `r3_slate` | deepslate | deepslate → cobbled_deepslate → **snow_block** | deepslate | deepslate |
| `settled_hollows` | ROLL | HIGH_H | HIGH_W | `r3_snow` | cobbled_deepslate | packed_ice → deepslate → **snow_block** | snow_block | deepslate |
| `riven_flats` | FLAT | LOW_H | FULL | `r3_snow` | cobbled_deepslate | packed_ice → deepslate → **snow_block** | snow_block | deepslate |
| `frozen_commons` | FLAT | HIGH_H | FULL | `r3_snow` | cobbled_deepslate | packed_ice → deepslate → **snow_block** | snow_block | deepslate |

**Features**

| Biome | s2 | s4 | carver |
|---|---|---|---|
| `slate_horns` | `slate_boulder` | `deepslate_needle`, `icicle` | — |
| `glacier_horns` | — | `ice_spike_dense`, `ice_needle`, `icicle` | — |
| `riven_shelf` | `slate_boulder`, `rubble_slate` | `icicle` | `surface_rift` |
| `overhang_shelf` | `ice_sheet`, `rubble_slate` | `icicle` | `surface_rift` |
| `spike_barrens` | — | `ice_spike_dense` | — |
| `slate_hollows` | `slate_boulder`, `rubble_slate` | — | — |
| `settled_hollows` | `rubble_slate` | — | — |
| `riven_flats` | `ice_sheet` | `ice_spike_dense` | `surface_rift` |
| `frozen_commons` | `rubble_slate` | — | — |

> `slate_horns` and `riven_shelf` carry a deepslate core from y110 to y180 —
> the rock changed, not just its coat.

> `settled_hollows` and `frozen_commons` are the settlement biomes. Both are
> `HIGH_H`, both carry `rubble_slate`, and both are targets for the village and
> depot structures in `STRUCTURES.md §4`.

---

## Region 4 — Stormheart

**You are inside the thing that killed everything behind you.** The storm is
still here, standing on this region, and it has been pushing energy into the
ground for years. The rock is oxidised red — red terracotta and granite bands
from valley floor to summit, *inside* the mountain, not painted on. The
height field is scaled up 17 %, jaggedness up 55 %, and a low-frequency noise
bites long deep valleys out of the land. This is the most civilised region in
the world and the most comprehensively destroyed one.

Snow `deep` · particle `white_ash` 0.020 (grit, not ash — the storm is
shredding the ground) · ambient `WIND` + additions 0.016 · mood every 3 000
ticks · music `jagged_peaks` · mobs `STORM_M` · creatures `DEAD_C` · biome temp
−0.15 … −0.07

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `rustspine_peaks` | PEAK | FULL | LOW_W | `r4_rust` | red_terracotta | snow *(above y190)* → red_terracotta → granite → terracotta → **snow_block** | red_terracotta | deepslate |
| `scoured_horns` | PEAK | FULL | HIGH_W | `r4_valley` | granite | granite → deepslate → terracotta → **snow_block** | granite | deepslate |
| `rustfall_shelf` | CRAG | LOW_H | FULL | `r4_rust` | red_terracotta | *(as above)* | red_terracotta | deepslate |
| `storm_valley` | CRAG | HIGH_H | FULL | `r4_valley` | granite | granite → deepslate → terracotta → **snow_block** | granite | deepslate |
| `stormcut_barrens` | ROLL | LOW_H | FULL | `r4_waste` | deepslate | red_terracotta → gravel → **snow_block** | gravel | deepslate |
| `ruined_hollows` | ROLL | HIGH_H | LOW_W | `r4_town` | stone | gravel → cobblestone → **snow_block** | stone | deepslate |
| `razed_commons` | ROLL | HIGH_H | HIGH_W | `r4_town` | stone | gravel → cobblestone → **snow_block** | stone | deepslate |
| `stormheart_flats` | FLAT | LOW_H | FULL | `r4_waste` | deepslate | red_terracotta → gravel → **snow_block** | gravel | deepslate |
| `hollowed_township` | FLAT | HIGH_H | FULL | `r4_town` | stone | gravel → cobblestone → **snow_block** | stone | deepslate |

**Features**

| Biome | s2 | s4 | carver |
|---|---|---|---|
| `rustspine_peaks` | `rust_boulder` | `deepslate_needle`, `icicle` | — |
| `scoured_horns` | `granite_boulder` | `icicle` | — |
| `rustfall_shelf` | `rust_boulder`, `rubble_rust` | `icicle` | `surface_rift` |
| `storm_valley` | `granite_boulder`, `rubble_rust` | `icicle` | `surface_rift` |
| `stormcut_barrens` | `rubble_rust` | `ice_spike_dense` | — |
| `ruined_hollows` | `rubble_slate` | — | — |
| `razed_commons` | `rubble_slate`, `rubble_rust` | — | — |
| `stormheart_flats` | `rubble_rust` | — | `surface_rift` |
| `hollowed_township` | `rubble_slate` | — | — |

> `rustspine_peaks` and `rustfall_shelf` carry the **red core**: bands of
> red_terracotta / terracotta / granite from y100 to y195, chosen by noise.
> Cut into one of these mountains and it bleeds.

> `ruined_hollows`, `razed_commons` and `hollowed_township` are the town
> biomes — the `r4_town` palette is deliberately the most ordinary in the
> region (stone, cobble, gravel) so that what is *built* on it reads loudest.
> They are the target for the heaviest set-pieces in `STRUCTURES.md §5`.

---

## Region 4b — Hard Snowcliff

A narrow band, **38 500 → 40 000 north**, and the most important 1 500 blocks
in the world.

Three separate terms make this a wall instead of a line on the map. The first
version had only one of them and the result was a slope with taller green
mountains on the far side of it — which is exactly the failure this section now
documents so it does not happen twice.

1. **The land climbs to get here.** `PLATEAU` adds +0.28 (~36 blocks) ramped
   gradually across regions 3 and 4, so the snowcliff lip stands at roughly
   **y112 on the flats and y123 on the rolling ground**, with ice peaks behind
   it above y200. The cliff has something to fall *from*.
2. **The drop is 30 blocks wide, not 150.** `G_CLIFF` uses a width of 0.0012
   v-units — 30 blocks — with a ±300-block wobble on *where* it happens, and
   `CLIFF_EDGE` stiffens `FACTOR` by 6.0 across exactly that ramp. Offset says
   how far down; factor says how abruptly. Width is what separates a cliff from
   a hillside, and it is the number to change if this ever softens.
3. **Region 5 is flattened to 0.32 of its height.** This is the fix for the
   real problem: the green side's PEAK biomes used to stand ~50 blocks *taller*
   than the cliff looking down on them. Region 5 now tops out around **y84**,
   against a lip of y112–123.

Net: a **40–60 block face**, and green hills that stay below it. `CLIFF_LIP`
adds a Y-banded bulge (y112–124) on the ramp only, so the top of the wall leans
out over the drop and the face undercuts beneath it — the cliff dips toward
region 5 and cannot get into it.

You walk out of Stormheart, the snow is the heaviest it has ever been, the
ground has been climbing for 20 000 blocks, and then it stops and the green is
fifty blocks below your feet.

Snow `heavy` · particle `snowflake` 0.030 (blizzard) · ambient `WIND` +
additions 0.012 · music `frozen_peaks` · mobs `STORM_M` · creatures `DEAD_C` ·
biome temp −0.05 … −0.02

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `snowcliff_crown` | PEAK | FULL | FULL | `cliff_hard` | packed_ice | packed_ice → deepslate → **snow_block** | packed_ice | deepslate |
| `snowcliff_brink` | CRAG | FULL | FULL | `cliff_face` | blue_ice | deepslate → blue_ice → **snow_block** | packed_ice | deepslate |
| `snowcliff_terrace` | ROLL | FULL | FULL | `cliff_hard` | packed_ice | packed_ice → deepslate → **snow_block** | packed_ice | deepslate |
| `snowcliff_shelf` | FLAT | FULL | FULL | `cliff_hard` | packed_ice | packed_ice → deepslate → **snow_block** | packed_ice | deepslate |

**Features**

| Biome | s2 | s4 | carver |
|---|---|---|---|
| `snowcliff_crown` | `frost_boulder` | `ice_needle`, `icicle` | — |
| `snowcliff_brink` | — | `icicle` | `surface_rift` |
| `snowcliff_terrace` | `ice_sheet` | `icicle` | — |
| `snowcliff_shelf` | `frost_boulder`, `ice_sheet` | — | — |

> `snowcliff_crown` and `snowcliff_brink` are solid to their roots: a
> `cliff_core` rule bands packed ice, deepslate and stone from y80 to y175.
> The wall is not a facade.

---

## Region 5 — Verdant Reach

The payoff, 40 000 blocks compass north, and forty-odd blocks below the wall you
just came down.

**Deliberately the flattest region in the world** (height x0.32). Flats sit
around y59, its highest bluffs around y84, and the snowcliff lip behind you is
y112–123. Green fields read as *below*, which is the entire point of the last
1 500 blocks of the journey. If you raise this scale, raise `PLATEAU` or
`CLIFF_DROP` with it or the mountains here start looking over the wall again.

No snow · no particles · no ambient loop *(the silence is the point)* · music
`meadow` · mobs `WARM_M` · creatures `GREEN_C` (sheep, cow, pig, chicken,
horse) · biome temp 0.78 … 0.84

**The only region with grass anywhere in the world.**

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `emerald_bluffs` | PEAK | FULL | LOW_W | `green_rock` | stone | stone *(above y84)* → stone → **grass_block** | dirt | dirt |
| `verdant_crags` | PEAK | FULL | HIGH_W | `green_rock` | stone | stone *(above y84)* → stone → **grass_block** | dirt | dirt |
| `mossy_shelf` | CRAG | FULL | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `verdant_downs` | ROLL | LOW_H | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `sunlit_hollows` | ROLL | HIGH_H | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `verdant_plains` | FLAT | LOW_H | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `sunlit_meadow` | FLAT | HIGH_H | FULL | `green` | stone | **grass_block** | dirt | dirt |

**Features**

| Biome | s9 vegetal |
|---|---|
| `emerald_bluffs`, `verdant_crags`, `mossy_shelf` | `sparse_grass` |
| `verdant_downs`, `sunlit_hollows`, `verdant_plains`, `sunlit_meadow` | `grass_meadow`, `meadow_flowers` |

> The bare-rock cutoff is now y84, because the whole region sits low and flat.
> If you re-tune `CLIFF_DROP`, `PLATEAU` or region 5's height scale, re-tune
> this with them or the green bluffs lose their caps.
> These biomes still carry `has_precipitation: true` — at temp 0.78+ that is
> rain, not snow.

---

## Feature dictionary

What each id actually places. Configured + placed feature definitions are in
`gen3.py`; these are the effective behaviours.

### Boulders — step 2, `forest_rock`, rarity 1-in-24

| id | block | used by |
|---|---|---|
| `frost_boulder` | packed_ice | 6 (regions 1, 2, 4b) |
| `ice_boulder` | blue_ice | 1 (`bone_drifts`) |
| `stone_boulder` | stone | 4 (region 1) |
| `cracked_boulder` | cobblestone | 2 (`windscoured_crags`, `riven_stoneshelf`) |
| `slate_boulder` | deepslate | 3 (region 3) |
| `granite_boulder` | granite | 2 (region 4) |
| `rust_boulder` | red_terracotta | 2 (region 4) |
| `tuff_boulder` | tuff | *(defined, currently unused)* |
| `basalt_boulder` | smooth_basalt | 1 (`still_barrens`) |
| `sculk_boulder` | sculk | 2 (The Quiet) |

### Sheets and rubble — step 2, `disk`

| id | blocks | radius | rarity | used by |
|---|---|---|---|---|
| `ice_sheet` | packed_ice 3 / ice 2 / blue_ice 1 | 2–5, half-height 1 | 1-in-6 | 8 biomes, regions 2–4b |
| `rubble_slate` | cobbled_deepslate 3 / deepslate 2 / gravel 2 | 1–4, flat | 1-in-4 + noise count | 8 biomes, regions 3–4 |
| `rubble_rust` | red_terracotta 3 / terracotta 2 / granite 2 / gravel 1 | 1–4, flat | 1-in-4 + noise count | 5 biomes, region 4 |

Rubble is the cheapest "a town used to be here" signal in the game: a flat,
irregular plate of broken material with snow drifting over it.

### Spires, needles and icicles — step 4, `block_column`

| id | block | height | rarity | used by |
|---|---|---|---|---|
| `ice_needle` | packed_ice | 3–8 | 1-in-16 | `frozen_spires`, `glass_ridges`, `glacier_horns`, `snowcliff_crown` |
| `deepslate_needle` | deepslate | 4–12 | 1-in-14 | `slate_horns`, `rustspine_peaks`, `whisper_spires` |
| `basalt_needle` | basalt | 3–10 | 1-in-12 | *(defined, currently unused)* |
| `tuff_needle` | tuff | 3–9 | 1-in-10 | *(defined, currently unused)* |
| **`icicle`** | ice, hangs **down** 1–6 | count 24 | 16 biomes, regions 2–4b |
| `rare_ice_spike` | *(vanilla `minecraft:ice_spike`)* | — | 1-in-12 | `frozen_spires`, `whiteout_flats` |
| `ice_spike_dense` | same, clustered by noise | — | 1-in-4 + noise count | `glacier_horns`, `spike_barrens`, `riven_flats`, `stormcut_barrens` |
| `quiet/spine` | obsidian/smooth_basalt column 4–9 capped with 2–5 sculk | — | 1-in-22 | `sculkspine_peaks`, `resonant_crag` |
| `sculk/pillar` | sculk column 2–7 capped with a **sculk_shrieker** (`can_summon: false`) | — | 1-in-14 | `shrieker_fields`, `the_quiet` |

`icicle` is the single most valuable feature added in v5: it scans **up** from
inside the air for a ceiling, steps one block back down, and hangs ice off the
underside. It only ever appears where there is something over your head, so it
makes every overhang, cliff lip and rift wall read as one.

### Craters — step 2

| id | what | used by |
|---|---|---|
| `quiet/crater` | `minecraft:ore` placing **air**, size 60, four overlapping blobs offset 2–6 below the surface | `sculk_sea`, `the_quiet` |

### Trees — step 9

`minecraft:tree` refuses to generate unless the block under the trunk is
dirt-family, and this world's ground is snow. Every tree here is therefore
wrapped in a **`vegetation_patch`** that lays its own coarse_dirt first — which
also gives each trunk a believable thawed scar around it. Full explanation in
`WORLDGEN.md §2`.

**The shapes are vanilla's, unchanged.** `minecraft:spruce`, `minecraft:pine`,
`minecraft:fancy_oak` and `minecraft:mega_spruce` trunk and foliage placers,
copied exactly. A dead tree is the **same config with
`"foliage_provider": air`** — identical silhouette, nothing on the branches.

| id | tree | shape | rarity | used by |
|---|---|---|---|---|
| `tree/live_grove` | spruce, leaves | vanilla `spruce` | 1-in-4 + noise | `standing_pines` |
| `tree/live_pines` | pine, leaves | vanilla `pine` | 1-in-5 + noise | `standing_pines` |
| `tree/dead_sparse` | bare spruce | vanilla `spruce` | 1-in-5 + noise | 6 biomes (regions 1–2) |
| `tree/dead_grove` | bare spruce, count 2 | vanilla `spruce` | count 2 + noise | `rime_hollows` |
| `tree/dead_pinestand` | bare pine | vanilla `pine` | 1-in-6 + noise | `drift_barrens`, `snowveil_flats` |
| `tree/dead_boughs` | **bare branching tree** | vanilla `fancy_oak` | 1-in-6 + noise | 4 region-1 biomes |
| `tree/dead_snag` | **2x2 giant dead spruce** | vanilla `mega_spruce` | 1-in-11 + noise | `rime_hollows`, `deadly_snowfield` |

`fancy_oak` earns its place: `fancy_trunk_placer` is the only vanilla placer
that grows real branches, so stripped of leaves it reads as a bare tree rather
than a pole. `mega_spruce`'s `giant_trunk_placer` gives 13–21-block 2x2 snags —
the biggest dead thing in the world.

Living trees keep vanilla's own `alter_ground` decorator set to snow_block, so
they stand in a ring of snow instead of a ring of dirt.

### Other vegetal — step 9

| id | places | density | used by |
|---|---|---|---|
| `sparse_grass` | `minecraft:grass`, 6 tries | count 2 | 3 verdant biomes |
| `grass_meadow` | same feature, denser | count 5 | 4 verdant biomes |
| `meadow_flowers` | dandelion, poppy, azure_bluet, cornflower, oxeye_daisy | 1-in-3 | same 4 |
| `sculk/crust` | `sculk_patch` — 1 x 40, 3 rounds, 6 % catalysts | count 10 + noise | 5 Quiet biomes |
| `sculk/spread` | `sculk_patch` — 2 x 160, 5 rounds, 22 % catalysts | count 22 + noise | 4 |
| `sculk/bloom` | `sculk_patch` — 3 x 420, 7 rounds, 45 % catalysts | count 36 + noise | 3 |
| `sculk/veins` | `multiface_growth` — sculk_vein on floors, walls **and ceilings**, 0.7 spread | count 20 | **all 12** |

### Carvers

| id | what | used by |
|---|---|---|
| `surface_rift` | `minecraft:canyon` pinned to y95–205, thin (0–5), probability 0.018 | 10 biomes across regions 1–4b |

Surface-level gorges and splits only. There are **no cave systems in this
world** and there is not going to be one — the Y window is above the terrain
floor everywhere, so a rift that starts too low simply never intersects ground.

### Snow — step 10

Every snowy biome runs the same five-entry chain, differing only in profile:

```
minecraft:freeze_top_layer      one base layer, vanilla
frostline:snow/<p>/first        deepest — a higher block is directly adjacent
frostline:snow/<p>/second       medium  — nothing in ring 1, something in ring 2
frostline:snow/<p>/third        feather — nothing in rings 1-2, something in ring 3
frostline:snow/fix              catch-up pass for sheltered spots (environment_scan)
```

| profile | first | second | third | attempts/chunk | regions |
|---|---|---|---|---|---|
| `light` | 3–4 layers | 2–3 | 1 | 350 | 1 |
| `normal` | 5–6 | 3–5 | 2–3 | 800 | 2 |
| `deep` | 7 | 5–6 | 3–4 | 800 | 3, 4 |
| `heavy` | 7 | 6–7 | 4–5 | 1080 | 4b |

**Depth is the story.** The further north, the longer the storm stood there.
`light` is region 1 — snow you can still see the world through.

### Bare ground — step 10, *after* the snow

| id | ground | rarity | used by |
|---|---|---|---|
| `bare/dirt` | coarse_dirt 3 / dirt 1 | 1-in-5 + noise count | 5 region-1 biomes |
| `bare/stone` | stone 3 / cobblestone 1 | 1-in-6 + noise count | 4 region-1 biomes |
| `bare/gravel` | gravel 3 / coarse_dirt 1 | 1-in-8 + noise count | 5 region-1 biomes |

A `vegetation_patch` whose "vegetation" is **air**: it swaps the top block for
bare ground *and* deletes the snow layer standing on it. It has to run after the
drift passes or the drifts just bury it again. This is region 1's entire
identity in one feature — and it appears nowhere else in the world.

---

## Structures

**All structures are off by default.** `optional/structures/data/` holds biome
tags; copy it over `src/main/resources/data/` before building to enable.

Every vanilla structure set not listed below is overridden to empty — mineshafts,
ancient cities, ocean ruins, shipwrecks, monuments, buried treasure, swamp huts,
desert pyramids, jungle temples, woodland mansions, trail ruins and nether
complexes all generate nowhere. Strongholds keep a `concentric_rings` placement
with an empty structure list.

Placement now follows the story rather than covering everything:

| Structure | Which biomes | why |
|---|---|---|
| `igloo` | regions 1–2 (18 biomes) | shelters, where people were still trying |
| `village_snowy` | regions 3–4 (18 biomes) | civilisation is north |
| `pillager_outpost` | regions 2–4 (27 biomes) | what moved in afterwards |
| `village_plains` | region 5 only (7 biomes) | the intact world |
| `ruined_portal_standard` | all 59 | — |
| `ruined_portal_mountain` | every `PEAK` and `CRAG` biome (25) | — |

Everything Frostline actually *wants* — cabins, hidden camps, wrecked convoys,
tentacles, the cliff-edge outpost — is NBT, and NBT cannot be written by
`gen3.py`. It all lives in **`STRUCTURES.md`**, one section per region, with
build instructions and the exact JSON to drop in afterwards.

---

## Gaps — biomes with no decoration

These carry **only** a snow chain (or, in region 5, only grass):

| Region | bare biomes |
|---|---|
| 0 The Quiet | `hushed_shelf`, `creeping_downs`, `bloom_hollows`, `muted_flats` *(deliberate — see above)* |
| 1 Hollow Drifts | none |
| 2 Deadly Snowfields | none |
| 3 Riven Wastes | none |
| 4 Stormheart | none |
| 4b Hard Snowcliff | none |
| 5 Verdant Reach | all 7 — no boulders or spires anywhere in the region |

Other thin spots:

- `tuff_needle`, `basalt_needle` and `tuff_boulder` are defined but used by
  nothing since region 3 stopped being the Ashen Wastes. Either give them a
  home or delete them.
- The Verdant Reach still has no step-2 or step-4 features at all. It is the
  only region whose impact is pure colour.
- Region 4's town biomes have rubble and nothing built. That is the largest
  single gap in the world and `STRUCTURES.md §5` is the fix.

---

## Adding a biome — checklist

1. Add the row to `REGIONS` (or `QUIET_REGION`) in `gen3.py`:
   `(id, tier, humidity, weirdness, palette)`.
2. Make sure it does **not** duplicate another biome's tier + humidity +
   weirdness box inside the same region.
3. If the palette is new, add it to `PALETTES` — and for a Quiet palette, add a
   matching `QUIET_STAGE` entry or generation fails with a `KeyError`.
4. Optional decoration goes in `EXTRA` / `QUIET_EXTRA`, keyed `s2` / `s4` / `s9`,
   plus the non-step keys `bare=[...]` (region 1 style ground patches) and
   `carvers=RIFT`. Every feature you name must already exist in `MASTER` at that
   step, or `features()` raises at generate time.
5. Any new surface block must go in the `drift_ground` **and**
   `patch_replaceable` tags, or snow will ignore it and patches will refuse to
   chew through it.
6. Run `python gen3.py && python verify.py`, then `./gradlew build`, then make a
   **new world**.
7. Update this file.
