# Frostline — Biome Dictionary

Every region, every biome, and exactly what generates in it. Generated from
`gen3.py`'s tables — if you change the generator, regenerate this by hand or it
goes stale.

Companion docs: `CONTROLS.md` (line numbers to change things),
`WORLDGEN.md` (techniques), `README.md` (design overview).

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

**Features** are listed by generation step: `s2` local modifications (boulders),
`s4` surface structures (spires, needles, pillars), `s9` vegetal decoration,
`s10` top layer (snow). Steps not listed are empty in every biome.

---

## Region index

| # | Region | Distance from spawn | temperature band | Snow | Biomes |
|---|---|---|---|---|---|
| 1 | **Hollow Drifts** *(spawn)* | 0 north — **and the entire south to 5 000** | −1.00 … −0.55 | `deep` | 7 |
| 2 | **Deadly Snowfields** | 11 250 north | −0.55 … −0.20 | `normal` | 7 |
| 3 | **Ashen Wastes** | 20 000 north | −0.20 … 0.15 | `normal` | 7 |
| 4 | **Thawing Reach** | 28 750 north | 0.15 … 0.60 | `thin` | 7 |
| 5 | **Verdant Reach** | **40 000 north** | 0.60 … 1.00 | none | 7 |
| 6 | **The Quiet** | **5 000 south, forever** | *(continentalness 0.60…1.00)* | none | 12 |

**47 biomes total.** Regions 1–5 are contiguous bands on `temperature`, so you
physically cannot reach region 4 without crossing 2 and 3. The Quiet rides a
second axis and is pinned out of existence everywhere north of spawn.

---

## Region 1 — Hollow Drifts

*Spawn region. Also the whole southern half of the world until The Quiet.*

Snow `deep` · particle `snowflake` · music `snowy_slopes` (PEAK tiers get
`frozen_peaks`) · mobs `COLD_M` (stray-heavy) · creatures `SNOW_C` (rabbit,
polar bear, fox) · biome temp −0.75 … −0.69

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `hollow_spires` | PEAK | FULL | LOW_W | `spire_ice` | blue_ice | stone → packed_ice → **snow_block** | packed_ice | stone |
| `windscoured_crags` | PEAK | FULL | HIGH_W | `scoured` | stone | stone → powder_snow → **snow_block** | packed_ice | stone |
| `frost_shelf` | CRAG | FULL | FULL | `shelf` | stone | stone → **snow_block** | packed_ice | stone |
| `drift_barrens` | ROLL | LOW_H | FULL | `drift_snow` | packed_ice | packed_ice → **snow_block** | snow_block | packed_ice |
| `rime_hollows` | ROLL | HIGH_H | FULL | `deep_snow` | packed_ice | powder_snow → **snow_block** | snow_block | packed_ice |
| **`frozen_hollow`** *(spawn)* | FLAT | LOW_H | FULL | `deep_snow` | packed_ice | powder_snow → **snow_block** | snow_block | packed_ice |
| `snowveil_flats` | FLAT | HIGH_H | FULL | `drift_snow` | packed_ice | packed_ice → **snow_block** | snow_block | packed_ice |

**Features**

| Biome | s2 boulders | s4 structures | s10 snow |
|---|---|---|---|
| `hollow_spires` | — | `rare_ice_spike`, `ice_needle` | deep |
| `windscoured_crags` | — | — | deep |
| `frost_shelf` | `frost_boulder` | — | deep |
| `drift_barrens` | `frost_boulder` | — | deep |
| `rime_hollows` | — | — | deep |
| `frozen_hollow` | — | — | deep |
| `snowveil_flats` | — | — | deep |

> Spawn resolves to `frozen_hollow` on flat ground on every seed — the
> `spawn_target` climate box in the noise settings is narrowed to it.

---

## Region 2 — Deadly Snowfields

Snow `normal` · particle `snowflake` · music `snowy_slopes` / `frozen_peaks` ·
mobs `COLD_M` · creatures `DEAD_C` (rabbit only, weight 2) · biome temp
−0.55 … −0.49

Harder, glassier ice than region 1. Blue ice starts appearing as a surface block
rather than only on cliffs.

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `frozen_spires` | PEAK | FULL | LOW_W | `spire_ice` | blue_ice | stone → packed_ice → **snow_block** | packed_ice | stone |
| `glass_ridges` | PEAK | FULL | HIGH_W | `glass_ice` | blue_ice | blue_ice → **packed_ice** | packed_ice | stone |
| `shattered_shelf` | CRAG | FULL | FULL | `crevasse` | packed_ice | packed_ice → **snow_block** | packed_ice | stone |
| `deadly_snowfield` | ROLL | LOW_H | FULL | `hard_snow` | packed_ice | blue_ice → powder_snow → **snow_block** | packed_ice | packed_ice |
| `crevasse_fields` | ROLL | HIGH_H | FULL | `crevasse` | packed_ice | packed_ice → **snow_block** | packed_ice | stone |
| `whiteout_flats` | FLAT | LOW_H | FULL | `hard_snow` | packed_ice | blue_ice → powder_snow → **snow_block** | packed_ice | packed_ice |
| `bone_drifts` | FLAT | HIGH_H | FULL | `drift_snow` | packed_ice | packed_ice → **snow_block** | snow_block | packed_ice |

**Features**

| Biome | s2 boulders | s4 structures | s10 snow |
|---|---|---|---|
| `frozen_spires` | — | `rare_ice_spike`, `ice_needle` | normal |
| `glass_ridges` | — | `ice_needle` | normal |
| `shattered_shelf` | `frost_boulder` | — | normal |
| `deadly_snowfield` | — | — | normal |
| `crevasse_fields` | — | — | normal |
| `whiteout_flats` | — | `rare_ice_spike` | normal |
| `bone_drifts` | `frost_boulder` | — | normal |

> `glass_ridges` is the only snowy biome whose default `top` is **not**
> snow_block — it is packed ice, so the drift layers sit on bare ice.

---

## Region 3 — Ashen Wastes

Snow `normal` · particle `white_ash` · music `grove` / `frozen_peaks` ·
mobs `COLD_M` · creatures `DEAD_C` · biome temp −0.25 … −0.19

Ash falling on snow. Netherrack and blackstone show up as *terrain*, not
decoration — `cinder_ridges` carries a netherrack/blackstone core from y72 to
y140.

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `cinder_ridges` | PEAK | FULL | LOW_W | `cinder` | blackstone | snow_block *(above y150)* → netherrack → basalt → blackstone → gravel → **snow_block** | netherrack → blackstone | blackstone |
| `soot_spires` | PEAK | FULL | HIGH_W | `soot` | blackstone | basalt → blackstone → **snow_block** | blackstone | deepslate |
| `slaghelm` | CRAG | FULL | FULL | `soot` | blackstone | basalt → blackstone → **snow_block** | blackstone | deepslate |
| `ashen_snowfield` | ROLL | LOW_H | FULL | `ash_snow` | tuff | gravel → coarse_dirt → **snow_block** | gravel | stone |
| `ember_hollows` | ROLL | HIGH_H | FULL | `charred` | tuff | coarse_dirt → gravel → **snow_block** | coarse_dirt | stone |
| `ashfall_flats` | FLAT | LOW_H | FULL | `ash_snow` | tuff | gravel → coarse_dirt → **snow_block** | gravel | stone |
| `charred_barrens` | FLAT | HIGH_H | FULL | `charred` | tuff | coarse_dirt → gravel → **snow_block** | coarse_dirt | stone |

**Features**

| Biome | s2 boulders | s4 structures | s10 snow |
|---|---|---|---|
| `cinder_ridges` | `cinder_boulder` | `basalt_needle` | normal |
| `soot_spires` | `basalt_boulder` | `basalt_needle` | normal |
| `slaghelm` | `cinder_boulder` | — | normal |
| `ashen_snowfield` | — | — | normal |
| `ember_hollows` | — | — | normal |
| `ashfall_flats` | — | — | normal |
| `charred_barrens` | `basalt_boulder` | — | normal |

---

## Region 4 — Thawing Reach

Snow `thin` · particle `snowflake` · music `jagged_peaks` · mobs `WARM_M` ·
creatures `DEAD_C` · biome temp 0.04 … 0.10

The snow running out. Grey rock, tuff, deepslate, thinning cover — deliberately
**not** a meadow. `slate_peaks` carries a raised deepslate line from y96 to y152.

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `slate_peaks` | PEAK | FULL | LOW_W | `grey_rock` | deepslate | snow_block *(above y140)* → deepslate → tuff → **snow_block** | deepslate | deepslate |
| `tuff_spires` | PEAK | FULL | HIGH_W | `meltstone` | tuff | tuff → andesite → **snow_block** | tuff | deepslate |
| `grey_shelf` | CRAG | FULL | FULL | `pale` | stone | gravel → stone → **snow_block** | stone | deepslate |
| `slushfields` | ROLL | LOW_H | FULL | `pale` | stone | gravel → stone → **snow_block** | stone | deepslate |
| `meltstone_hollows` | ROLL | HIGH_H | FULL | `meltstone` | tuff | tuff → andesite → **snow_block** | tuff | deepslate |
| `pale_flats` | FLAT | LOW_H | FULL | `pale` | stone | gravel → stone → **snow_block** | stone | deepslate |
| `gravel_barrens` | FLAT | HIGH_H | FULL | `ash_snow` | tuff | gravel → coarse_dirt → **snow_block** | gravel | stone |

**Features**

| Biome | s2 boulders | s4 structures | s10 snow |
|---|---|---|---|
| `slate_peaks` | `slate_boulder` | `deepslate_needle` | thin |
| `tuff_spires` | `tuff_boulder` | `tuff_needle` | thin |
| `grey_shelf` | `slate_boulder` | — | thin |
| `slushfields` | — | — | thin |
| `meltstone_hollows` | — | — | thin |
| `pale_flats` | — | — | thin |
| `gravel_barrens` | `tuff_boulder` | — | thin |

> `gravel_barrens` reuses region 3's `ash_snow` palette — it is the visual bridge
> back toward the Ashen Wastes.

---

## Region 5 — Verdant Reach

The payoff, 40 000 blocks compass north.

No snow · no particles · music `meadow` · mobs `WARM_M` · creatures `GREEN_C`
(sheep, cow, pig, chicken, horse) · biome temp 0.78 … 0.84

**The only region with grass anywhere in the world.**

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `emerald_bluffs` | PEAK | FULL | LOW_W | `green_rock` | stone | stone *(above y150)* → stone → **grass_block** | dirt | dirt |
| `verdant_crags` | PEAK | FULL | HIGH_W | `green_rock` | stone | stone *(above y150)* → stone → **grass_block** | dirt | dirt |
| `mossy_shelf` | CRAG | FULL | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `verdant_downs` | ROLL | LOW_H | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `sunlit_hollows` | ROLL | HIGH_H | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `verdant_plains` | FLAT | LOW_H | FULL | `green` | stone | **grass_block** | dirt | dirt |
| `sunlit_meadow` | FLAT | HIGH_H | FULL | `green` | stone | **grass_block** | dirt | dirt |

**Features**

| Biome | s9 vegetal |
|---|---|
| `emerald_bluffs` | `sparse_grass` |
| `verdant_crags` | `sparse_grass` |
| `mossy_shelf` | `sparse_grass` |
| `verdant_downs` | `grass_meadow`, `meadow_flowers` |
| `sunlit_hollows` | `grass_meadow`, `meadow_flowers` |
| `verdant_plains` | `grass_meadow`, `meadow_flowers` |
| `sunlit_meadow` | `grass_meadow`, `meadow_flowers` |

> No boulders, no needles, no snow. This region has the thinnest feature set in
> the world and currently relies entirely on the colour change for impact.
> These biomes still carry `has_precipitation: true` — at temp 0.78+ that is
> rain, not snow.

---

## Region 6 — The Quiet

5 000 blocks compass south, and it never ends. Reached on `continentalness`, not
`temperature`, so it is a hard overlay rather than a step in the sequence.

No snow (`has_precipitation: false`) · particle `warped_spore` · music
`deep_dark` · mobs `QUIET_M` (silverfish-led) · no creatures · biome temp
0.72 … 0.83

**One material family, graded.** Host rock is smooth_basalt / blackstone /
deepslate / obsidian; `sculk` is the growth. The seven palettes differ **only**
in how low the `frostline:infest` noise threshold sits before sculk wins:

| palette | sculk when infest ≥ | reads as |
|---|---|---|
| `q_bare` | 0.82 | bare host rock, occasional patch |
| `q_flecked` | 0.62 | flecked |
| `q_glass` | 0.45 | obsidian shelves, some sculk |
| `q_veined` | 0.40 | visibly taken |
| `q_creep` | 0.18 | more sculk than rock |
| `q_bloom` | −0.05 | overtaken, sculk *under*-layer |
| `q_total` | −0.35 | solid sculk |

`humidity` is repurposed here as **infestation stage** — `LOW_H` sparse,
`HIGH_H` overtaken. `weirdness` gives variety within a stage.

| Biome | tier | hum | wd | palette | steep | top | under | deep |
|---|---|---|---|---|---|---|---|---|
| `whisper_spires` | PEAK | LOW_H | FULL | `q_bare` | deepslate | sculk → blackstone → **smooth_basalt** | smooth_basalt | deepslate |
| `sculkspine_peaks` | PEAK | HIGH_H | FULL | `q_veined` | deepslate | sculk → blackstone → **smooth_basalt** | blackstone | deepslate |
| `hushed_shelf` | CRAG | LOW_H | FULL | `q_flecked` | cobbled_deepslate | sculk → smooth_basalt → **blackstone** | smooth_basalt | deepslate |
| `resonant_crag` | CRAG | HIGH_H | FULL | `q_glass` | obsidian | sculk → obsidian → **smooth_basalt** | smooth_basalt | deepslate |
| `creeping_downs` | ROLL | LOW_H | LOW_W | `q_flecked` | cobbled_deepslate | sculk → smooth_basalt → **blackstone** | smooth_basalt | deepslate |
| `vein_hollows` | ROLL | LOW_H | HIGH_W | `q_veined` | deepslate | sculk → blackstone → **smooth_basalt** | blackstone | deepslate |
| `bloom_hollows` | ROLL | HIGH_H | LOW_W | `q_creep` | smooth_basalt | sculk → blackstone → **smooth_basalt** | smooth_basalt | deepslate |
| `shrieker_fields` | ROLL | HIGH_H | HIGH_W | `q_bloom` | blackstone | sculk → smooth_basalt → **blackstone** | **sculk** | deepslate |
| `muted_flats` | FLAT | LOW_H | LOW_W | `q_bare` | deepslate | sculk → blackstone → **smooth_basalt** | smooth_basalt | deepslate |
| `still_barrens` | FLAT | LOW_H | HIGH_W | `q_veined` | deepslate | sculk → blackstone → **smooth_basalt** | blackstone | deepslate |
| `sculk_sea` | FLAT | HIGH_H | LOW_W | `q_bloom` | blackstone | sculk → smooth_basalt → **blackstone** | **sculk** | deepslate |
| **`the_quiet`** *(the heart)* | FLAT | HIGH_H | HIGH_W | `q_total` | deepslate | sculk → **smooth_basalt** | **sculk** | deepslate |

**Features**

| Biome | s2 boulders | s4 structures | s9 sculk |
|---|---|---|---|
| `whisper_spires` | `slate_boulder` | `deepslate_needle` | `sculk/crust` |
| `sculkspine_peaks` | `sculk_boulder` | `deepslate_needle`, `sculk/pillar` | `sculk/spread`, `sculk/veins` |
| `hushed_shelf` | `basalt_boulder` | — | `sculk/crust`, `sculk/veins` |
| `resonant_crag` | `basalt_boulder` | `basalt_needle` | `sculk/crust`, `sculk/veins` |
| `creeping_downs` | `basalt_boulder` | — | `sculk/crust`, `sculk/veins` |
| `vein_hollows` | `sculk_boulder` | — | `sculk/spread`, `sculk/veins` |
| `bloom_hollows` | `sculk_boulder` | `sculk/pillar` | `sculk/spread`, `sculk/veins` |
| `shrieker_fields` | — | `sculk/pillar` | `sculk/bloom`, `sculk/veins` |
| `muted_flats` | `slate_boulder` | — | `sculk/crust` |
| `still_barrens` | `basalt_boulder` | — | `sculk/spread`, `sculk/veins` |
| `sculk_sea` | `sculk_boulder` | `sculk/pillar` | `sculk/bloom`, `sculk/veins` |
| `the_quiet` | `sculk_boulder` | `sculk/pillar` | `sculk/bloom`, `sculk/veins` |

**Ambience escalates with stage:** only `q_creep` / `q_bloom` / `q_total`
biomes carry `minecraft:ambient.soul_sand_valley.loop`. `whisper_spires` and
`muted_flats` (`q_bare`) are the only Quiet biomes with **no particles and no
sculk veins** — they are the threshold, meant to be walked through before the
region announces itself.

**Terrain here is different too:** ground sinks ~0.055 and low organic mounds
swell back out of it, `FACTOR` drops by 1.4 (floored at 1.6, so it undercuts but
stays connected), and jaggedness is cancelled outright. Deep strata are banded
host rock from bedrock up — digging into The Quiet never finds ordinary stone.

---

## Feature dictionary

What each id actually places. Configured + placed feature definitions are in
`gen3.py`; these are the effective behaviours.

### Boulders — step 2, `forest_rock`, rarity 1-in-24

| id | block | used by |
|---|---|---|
| `frost_boulder` | packed_ice | 4 biomes (regions 1–2) |
| `slate_boulder` | deepslate | 4 (`slate_peaks`, `grey_shelf`, `whisper_spires`, `muted_flats`) |
| `cinder_boulder` | blackstone | 2 (`cinder_ridges`, `slaghelm`) |
| `tuff_boulder` | tuff | 2 (`tuff_spires`, `gravel_barrens`) |
| `basalt_boulder` | smooth_basalt | 6 (region 3 + The Quiet) |
| `sculk_boulder` | sculk | 5 (The Quiet only) |

### Spires and needles — step 4, `block_column`

| id | block | height | rarity | used by |
|---|---|---|---|---|
| `ice_needle` | packed_ice | 3–8 | 1-in-16 | `hollow_spires`, `frozen_spires`, `glass_ridges` |
| `tuff_needle` | tuff | 3–9 | 1-in-10 | `tuff_spires` **only** |
| `basalt_needle` | basalt | 3–10 | 1-in-12 | `cinder_ridges`, `soot_spires`, `resonant_crag` |
| `deepslate_needle` | deepslate | 4–12 | 1-in-14 | `slate_peaks`, `whisper_spires`, `sculkspine_peaks` |
| `rare_ice_spike` | *(vanilla `minecraft:ice_spike`)* | — | 1-in-12 | `hollow_spires`, `frozen_spires`, `whiteout_flats` |
| `sculk/pillar` | sculk column 2–7 capped with a **sculk_shrieker** (`can_summon: false`) | — | 1-in-6 | 5 Quiet biomes |

### Vegetal — step 9

| id | places | density | used by |
|---|---|---|---|
| `sparse_grass` | `minecraft:grass`, 6 tries | count 2 | `emerald_bluffs`, `verdant_crags`, `mossy_shelf` |
| `grass_meadow` | same feature, denser | count 5 | 4 flat/rolling verdant biomes |
| `meadow_flowers` | dandelion, poppy, azure_bluet, cornflower, oxeye_daisy | 1-in-3 | same 4 |
| `sculk/crust` | `sculk_patch` — 30 per charge, 2 growth rounds, 5 % catalysts | count 12 | 5 Quiet biomes |
| `sculk/spread` | `sculk_patch` — 120 per charge, 4 rounds, 18 % catalysts | count 24 | 4 |
| `sculk/bloom` | `sculk_patch` — 2 charges × 400, 7 rounds, 42 % catalysts | count 40 | 3 |
| `sculk/veins` | `multiface_growth` — sculk_vein on floors, walls **and ceilings**, 0.6 spread | count 24 | 10 |

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
| `deep` | 7 layers | 5–6 | 3–4 | 800 | 1 |
| `normal` | 6–7 | 4–5 | 2–3 | 800 | 2, 3 |
| `thin` | 3–5 | 2–3 | 1–2 | 240 | 4 |

28 of 47 biomes carry a snow chain. Regions 5 and 6 carry none.
Full explanation in `WORLDGEN.md §1`.

---

## Structures

**All structures are off by default.** `optional/structures/data/` holds biome
tags; copy it over `src/main/resources/data/` before building to enable.

Every vanilla structure set not listed below is overridden to empty — mineshafts,
ancient cities, ocean ruins, shipwrecks, monuments, buried treasure, swamp huts,
desert pyramids, jungle temples, woodland mansions, trail ruins and nether
complexes all generate nowhere. Strongholds keep a `concentric_rings` placement
with an empty structure list.

| Structure | Biome tag | Which biomes |
|---|---|---|
| `igloo` | snowy | all of regions 1–4 (28 biomes) |
| `village_snowy` | snowy | same 28 |
| `pillager_outpost` | snowy | same 28 |
| `village_plains` | green | region 5 only (7 biomes) |
| `ruined_portal_standard` | all | all 47 |
| `ruined_portal_mountain` | mountains | every `PEAK` and `CRAG` biome, all six regions (18 biomes) |

"Snowy" here means everything except The Quiet and the Verdant Reach.

---

## Gaps — biomes with no decoration

These carry **only** a snow chain (or, in region 5, only grass). If you are
looking for somewhere to put new content, start here:

| Region | bare biomes |
|---|---|
| 1 Hollow Drifts | `windscoured_crags`, `rime_hollows`, `frozen_hollow` *(spawn!)*, `snowveil_flats` |
| 2 Deadly Snowfields | `deadly_snowfield`, `crevasse_fields` |
| 3 Ashen Wastes | `ashen_snowfield`, `ember_hollows`, `ashfall_flats` |
| 4 Thawing Reach | `slushfields`, `meltstone_hollows`, `pale_flats` |
| 5 Verdant Reach | all 7 — no boulders or spires anywhere in the region |
| 6 The Quiet | none — every Quiet biome has at least a boulder or a pillar |

Other thin spots:

- `tuff_needle` is used by exactly one biome (`tuff_spires`).
- The Verdant Reach has no step-2 or step-4 features at all.
- Planned: custom leafless dead trees, which would land in `MASTER[9]` and fill
  most of the region 1–4 gaps above. `minecraft:tree` will **not** work — it
  requires dirt-family ground and silently refuses on snow blocks. Use
  `minecraft:block_column` (the `*_needle` features are the pattern) or a custom
  feature.

---

## Adding a biome — checklist

1. Add the row to `REGIONS` (or `QUIET_REGION`) in `gen3.py`:
   `(id, tier, humidity, weirdness, palette)`.
2. Make sure it does **not** duplicate another biome's tier + humidity +
   weirdness box inside the same region.
3. If the palette is new, add it to `PALETTES` — and for a Quiet palette, add a
   matching `QUIET_STAGE` entry or generation fails with a `KeyError`.
4. Optional decoration goes in `EXTRA` / `QUIET_EXTRA`, keyed `s2` / `s4` / `s9`.
   Every feature you name must already exist in `MASTER` at that step, or
   `features()` raises at generate time.
5. Run `python gen3.py && python verify.py`, then `./gradlew build`, then make a
   **new world**.
6. Update this file.
