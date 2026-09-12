# Frostline — Structures, Set-Pieces and Sounds

**Everything in this file is work only you can do.** `gen3.py` can place a
boulder, carve a rift, bend terrain and grow sculk, but it cannot author an
`.nbt`, it cannot bend a tentacle around a corner, and it cannot ship an `.ogg`.
Every item below is something the design calls for that data alone cannot make.

For each one you get: what it is, which biome it belongs in, how big it should
be, exactly how to build it, and the exact JSON to paste afterwards. The biome
tags are **already generated** — you never have to list biomes by hand.

Read §0 and §1 once. After that the catalogue (§2–§6) is a checklist, and §7 is
sounds.

---

## 0. Where files go, and the one rule that will bite you

`python gen3.py` **wipes `src/main/resources/data/` on every run.** Anything you
hand-write in there is gone the next time the generator runs.

So there is a second tree that survives:

```
handmade/data/…      ← you write here. Copied over the generated tree at the
                       END of every gen3.py run, verbatim.
src/main/resources/data/…  ← generated output. NEVER edit.
```

The copy step is the last thing `gen3.py` does and it prints
`copied N hand-made file(s)`. If you do not see that line, your files are not in
the build.

### The five paths you will use

For a structure called `r1_cabin_intact` (Minecraft 1.20.1 / pack format 15):

| what | path under `handmade/` |
|---|---|
| the model | `data/frostline/structures/r1_cabin_intact/cabin_a.nbt` |
| the structure | `data/frostline/worldgen/structure/r1_cabin_intact.json` |
| the piece list | `data/frostline/worldgen/template_pool/r1_cabin_intact/start.json` |
| where/how often | `data/frostline/worldgen/structure_set/r1_cabin_intact.json` |
| decay rules | `data/frostline/worldgen/processor_list/weathered.json` |

Note `structures` (plural, no `worldgen/`) for the NBT — that folder name is
different from every other one and it is the single most common mistake.

The sixth path, the biome list, **already exists** and is regenerated every run:

```
data/frostline/tags/worldgen/biome/has_structure/r1_cabin_intact.json
```

To change which biomes a structure can appear in, edit `STRUCTURE_PLAN` in
`gen3.py` — not the tag file.

---

## 1. The universal recipe

### 1.1 Build it in-game

1. Creative world, superflat, `/gamerule doDaylightCycle false`.
2. Build the thing. **Build it snowless and undamaged**; decay is applied
   later by a processor so every copy in the world is damaged differently.
3. Place a **structure block** at the lowest-north-west corner of the build,
   set to `SAVE` mode.
4. Structure name: `frostline:r1_cabin_intact/cabin_a`.
5. Set size so the box covers the build **plus one empty layer of air on every
   side** except the bottom.
6. **Include entities:** off, unless the piece is supposed to ship item frames
   or armour stands.
7. Hit `DETECT`, then `SAVE`. The file lands in
   `run/saves/<world>/generated/frostline/structures/r1_cabin_intact/cabin_a.nbt`.
8. Move it to `handmade/data/frostline/structures/r1_cabin_intact/cabin_a.nbt`.

**The bottom layer matters.** Whatever block is on the bottom layer of the box
is what the structure stamps into the ground. Use the region's own ground
material (see each entry) or `minecraft:structure_void` if you want the terrain
left alone.

**`minecraft:structure_void`** is how you make non-rectangular shapes: any voxel
holding it is skipped at placement time. Ragged ruins, open roofs and irregular
footprints are all structure_void plus the `block_ignore` processor in §1.5.

**Jigsaw blocks** are only needed for multi-piece structures (§5.2, §6.1).
Single-piece structures need none.

### 1.2 The structure JSON

`handmade/data/frostline/worldgen/structure/r1_cabin_intact.json`:

```json
{
  "type": "minecraft:jigsaw",
  "biomes": "#frostline:has_structure/r1_cabin_intact",
  "step": "surface_structures",
  "terrain_adaptation": "beard_thin",
  "spawn_overrides": {},
  "start_pool": "frostline:r1_cabin_intact/start",
  "size": 1,
  "start_height": { "absolute": 0 },
  "project_start_to_heightmap": "WORLD_SURFACE_WG",
  "max_distance_from_center": 80,
  "use_expansion_hack": false
}
```

Field by field, because every one of these has a wrong setting that looks fine
until you fly 3 000 blocks:

| field | use |
|---|---|
| `biomes` | always `#frostline:has_structure/<id>` — the generated tag |
| `step` | `surface_structures` for anything above ground. Use `underground_structures` only for something genuinely buried (§4.2). |
| `terrain_adaptation` | `beard_thin` = terrain is pulled up to meet the base, lightly. `beard_box` = heavier, use for anything with a flat foundation. `bury` = the piece is sunk and terrain closes over it. `encapsulate` = wrapped in stone. `none` = the piece floats if the ground dips. **Snow slopes need beard_box on anything bigger than 9×9.** |
| `size` | 1 for single-piece. For jigsaw chains it is the maximum recursion depth, not a block count. |
| `project_start_to_heightmap` | `WORLD_SURFACE_WG` sits it on the surface. Omit it entirely for a structure placed at a fixed Y. |
| `max_distance_from_center` | the chunk-radius budget for jigsaw expansion, max 128. Leave at 80. |

### 1.3 The template pool

`handmade/data/frostline/worldgen/template_pool/r1_cabin_intact/start.json`:

```json
{
  "name": "frostline:r1_cabin_intact/start",
  "fallback": "minecraft:empty",
  "elements": [
    { "weight": 3, "element": {
        "element_type": "minecraft:single_pool_element",
        "location": "frostline:r1_cabin_intact/cabin_a",
        "processors": "frostline:weathered",
        "projection": "rigid" } },
    { "weight": 2, "element": {
        "element_type": "minecraft:single_pool_element",
        "location": "frostline:r1_cabin_intact/cabin_b",
        "processors": "frostline:weathered",
        "projection": "rigid" } }
  ]
}
```

`projection` is `rigid` (the piece keeps its shape and the ground is beard-ed to
it) or `terrain_matching` (the piece is draped over the heightmap — only for
flat, one-layer things like roads and walls).

**Variants are free.** Two or three `elements` in a pool costs you one build
each and removes 90 % of the "I have seen this before" feeling. Every entry in
the catalogue below says how many variants it wants.

### 1.4 The structure set — where and how often

`handmade/data/frostline/worldgen/structure_set/r1_cabin_intact.json`:

```json
{
  "structures": [ { "structure": "frostline:r1_cabin_intact", "weight": 1 } ],
  "placement": {
    "type": "minecraft:random_spread",
    "spacing": 28,
    "separation": 9,
    "salt": 774101,
    "spread_type": "linear"
  }
}
```

- `spacing` = average chunks between attempts. `separation` = minimum. Keep
  `separation` at roughly a third of `spacing` or placements clump.
- **`salt` is mandatory and must be unique per set.** A missing salt crashes
  world creation; a duplicated salt makes two structures try to occupy the same
  chunks forever. The catalogue below assigns every structure its own salt —
  use those numbers.
- Several structures can share one set with different `weight`s. That is how you
  make "one of these four things appears here", and it is cheaper than four sets
  competing for chunks.

### 1.5 Decay — the processor list you will use on nearly everything

`handmade/data/frostline/worldgen/processor_list/weathered.json`:

```json
{
  "processors": [
    { "processor_type": "minecraft:block_ignore",
      "blocks": [ { "Name": "minecraft:structure_void" } ] },
    { "processor_type": "minecraft:block_rot", "integrity": 0.86 },
    { "processor_type": "minecraft:rule", "rules": [
      { "input_predicate": { "predicate_type": "minecraft:random_block_match",
                             "block": "minecraft:cobblestone", "probability": 0.3 },
        "location_predicate": { "predicate_type": "minecraft:always_true" },
        "output_state": { "Name": "minecraft:mossy_cobblestone" } },
      { "input_predicate": { "predicate_type": "minecraft:random_block_match",
                             "block": "minecraft:stone_bricks", "probability": 0.35 },
        "location_predicate": { "predicate_type": "minecraft:always_true" },
        "output_state": { "Name": "minecraft:cracked_stone_bricks" } },
      { "input_predicate": { "predicate_type": "minecraft:random_block_match",
                             "block": "minecraft:glass_pane", "probability": 0.6 },
        "location_predicate": { "predicate_type": "minecraft:always_true" },
        "output_state": { "Name": "minecraft:air" } }
    ] }
  ]
}
```

`block_rot` with `integrity: 0.86` deletes 14 % of the blocks at random — one
build, endless ruins. Make **three** lists and reuse them everywhere:

| list | integrity | extra rules | for |
|---|---|---|---|
| `frostline:weathered` | 0.86 | moss/crack/glass as above | region 1–2, "still standing" |
| `frostline:ruined` | 0.62 | + planks→air 0.3, logs→stripped 0.5 | region 2–3, "mostly gone" |
| `frostline:razed` | 0.40 | + everything→air near the top | region 4, "the storm won" |

**Do not build three damaged versions of a house.** Build one clean house and
run it through three processor lists. That is the whole point.

### 1.6 Test loop

```
python gen3.py            (copies handmade/, prints the count)
./gradlew build
new world
/locate structure frostline:r1_cabin_intact
```

`/locate` failing with "could not find" means the biome tag, the structure set
or the salt is wrong — in that order of likelihood. A structure that locates but
is invisible means `terrain_adaptation` buried it.

---

## 2. Region 0 — The Quiet

The design brief for this region is **invasive, reforming, alien, but still this
planet**. Terrain now does the wide-scale work (craters, shelf overhangs, sinking
ground). What it cannot do is anything that curves, leans or arches, because
`block_column` only goes straight up and down. That is this section.

Material rule for everything here: **smooth_basalt, blackstone, deepslate,
obsidian, sculk, sculk_vein, sculk_catalyst, sculk_sensor, sculk_shrieker.**
Nothing else. The region's whole read is one closed material family and a single
brown block will undo it.

### 2.1 `q_tendril` — the small tentacle · salt `551001`

The signature piece. A sculk limb that comes out of the ground, curves, and
either re-enters the ground or ends in a blunt tip.

- **Biomes:** `vein_hollows`, `bloom_hollows`, `shrieker_fields`, `sculk_sea`,
  `the_quiet` *(tag already generated)*
- **Bounding box:** 9 × 12 × 9 max. Smaller is better — these should read as
  many, not as monuments.
- **Build:** start with a 3×3 sculk base. Rise 4–6 blocks, then **step
  sideways one block per two blocks of height** for the next 4 — a staircase of
  full blocks reads as a curve once it is sculk-textured. Taper: 3×3 → 2×2 →
  1×1 over the last 3 blocks. Coat the outer face in `sculk_vein` on every
  exposed side (hold the vein item and right-click each face; veins are
  multiface and stack).
- **Tip:** one `sculk_sensor`, or a `sculk_shrieker` with
  `can_summon: false`. Never `can_summon: true` — the Warden does not belong to
  this design.
- **Bottom layer:** sculk. Let it stamp.
- **Variants:** 4. Two leaning left, two right, one of them re-entering the
  ground in an arch.
- **Placement:** `spacing 14, separation 5`, `terrain_adaptation: beard_thin`,
  `projection: rigid`.
- **Processor:** none. These are alive, not ruined.

### 2.2 `q_tendril_great` — the huge tentacle · salt `551002`

Same idea at set-piece scale. One of these should be visible from a long way off
and should make the player walk toward it.

- **Biomes:** `shrieker_fields`, `sculk_sea`, `the_quiet`
- **Bounding box:** 24 × 40 × 24.
- **Build:** 7×7 sculk root mound, rising and narrowing to 3×3 by y20, leaning
  6–8 blocks off vertical over the full height. Core it with obsidian (it is
  invisible from outside but stops the silhouette reading as flat) and skin it
  in sculk. Sculk veins on every exposed face, heaviest at the root.
- **Detail that sells it:** three or four `sculk_catalyst` blocks embedded in
  the root mound, and one at the tip. They glow and pulse, so the limb looks
  like it is doing something.
- **Variants:** 2.
- **Placement:** `spacing 48, separation 20`, `terrain_adaptation: beard_box`.
- **Note:** if a great tendril lands on a `quiet/crater`, that is a gift, not a
  bug. Do not try to prevent it.

### 2.3 `q_arch` — the huge overhead · salt `551003`

The density function bulges rock outward between y98 and y120, which makes
natural shelves. This is the built version: a full arch you walk under.

- **Biomes:** `hushed_shelf`, `resonant_crag`, `sculkspine_peaks`,
  `whisper_spires`
- **Bounding box:** 32 × 24 × 16.
- **Build:** two deepslate/basalt legs 5×5, an arched span between them
  (build the arch as a 1-in-1 staircase for the first 4 blocks, then 1-in-2,
  then flat across the crown). The span should be at least 6 blocks thick at the
  crown so it reads as rock, not as a bridge.
- **Underside is the whole point:** cover the ceiling of the span in
  `sculk_vein`, hang 2–4 short sculk stalactites (a 1×1 column of sculk, 3–5
  long, capped with a sensor) from it, and leave one section of the ceiling as
  bare obsidian so it catches light.
- **Use structure_void generously** on the outer corners so the legs blend into
  whatever terrain they land on.
- **Variants:** 3 — one symmetric, one with a collapsed leg, one half-arch that
  cantilevers out and ends in air.
- **Placement:** `spacing 40, separation 16`, `terrain_adaptation: beard_box`,
  `projection: rigid`.

### 2.4 `q_husk` — the consumed ruin · salt `551004`

Environmental storytelling: something was here before The Quiet, and The Quiet
ate it. This is the **only** place in region 0 where a non-family material is
allowed, and only as a fragment.

- **Biomes:** `muted_flats`, `creeping_downs`, `vein_hollows`, `still_barrens`
- **Bounding box:** 14 × 8 × 14.
- **Build:** the corner of a stone-brick building — two walls meeting, three
  courses high, floor of stone bricks, one doorway. Then **destroy it with
  sculk**: replace 40–60 % of the stone with sculk, run veins up the standing
  walls, push a sculk mound through the floor from below, and leave the highest
  remaining course as cracked stone bricks.
- **Read:** the building is not ruined by weather, it is being *digested*.
  Nothing should look weather-worn; it should look swallowed.
- **Variants:** 3.
- **Processor:** `frostline:ruined` (integrity 0.62).
- **Placement:** `spacing 34, separation 12`, `terrain_adaptation: beard_thin`.

### 2.5 `q_maw` — the crater mouth · salt `551005`

The generator already digs craters (`quiet/crater`, four overlapping air blobs).
This is the thing that sits *in* one and makes it look deliberate.

- **Biomes:** `sculk_sea`, `the_quiet`
- **Bounding box:** 16 × 10 × 16, built **downward**: the structure block goes
  at the rim and the piece extends down.
- **Build:** a ring of raised sculk 2 blocks high, sloping inward to a 4×4 floor
  of solid sculk holding 3 `sculk_shrieker` (can_summon false) and 2
  `sculk_catalyst`. Line the bowl walls with veins.
- **`terrain_adaptation`: `none`** — this one must NOT be beard-ed or the game
  will fill your hole back in. Set `"start_height": {"absolute": 0}` with
  `project_start_to_heightmap: WORLD_SURFACE_WG` and build the piece so its top
  layer is at the rim.
- **Variants:** 2.
- **Placement:** `spacing 44, separation 18`.

---

## 3. Region 1 — Hollow Drifts

The countryside. Thin snow, exposed ground, standing trees. Buildings here are
the *least* damaged in the world — some are genuinely intact — because the storm
was weakest when it came through. That contrast is the point; do not damage
these as heavily as feels natural.

Materials: spruce logs and planks, cobblestone, stone, stone bricks, glass
panes, lanterns, wool, hay, barrels, campfires (unlit).

### 3.1 `r1_cabin_intact` — the house that survived · salt `774101`

- **Biomes:** `frost_shelf`, `rime_hollows`, `standing_pines`, `frozen_hollow`,
  `snowveil_flats`
- **Bounding box:** 11 × 9 × 11.
- **Build:** a one-room spruce cabin — log corners, plank walls, stone brick
  chimney, spruce stair roof with a 1-block overhang all round, glass panes in
  two windows, a door that still has a door in it. Inside: a bed, a crafting
  table, a barrel (loot table below), an **unlit** campfire in the hearth.
- **The detail that does the work:** put snow layers on the roof *in the build*.
  The generator's drift passes run at step 10 and will not reliably re-cover a
  structure placed at step 4, so roof snow must be baked in.
- **Bottom layer:** coarse_dirt — it matches the `bare/dirt` patches already
  scattered through region 1.
- **Loot:** `minecraft:chests/igloo_chest` is the closest vanilla fit. A custom
  table goes at `handmade/data/frostline/loot_tables/chests/r1_cabin.json` and
  is referenced by putting a barrel with `LootTable: "frostline:chests/r1_cabin"`
  in the build.
- **Variants:** 3 (cabin, cabin with lean-to, cabin with fenced yard).
- **Processor:** `frostline:weathered`.
- **Placement:** `spacing 30, separation 10`, `terrain_adaptation: beard_box`.

### 3.2 `r1_cabin_ruined` — the house that did not · salt `774102`

Same build, three walls and half a roof. Do **not** build this separately if you
are short on time: point a second template pool at the same NBT with the
`frostline:ruined` processor list and you get it for free.

- **Biomes:** the six wooded/rolling region-1 biomes.
- **Placement:** `spacing 22, separation 7` — ruins are commoner than survivors.

### 3.3 `r1_fallen_tree` — the trunk on the ground · salt `774103`

The generator grows standing dead trees (`tree/dead_sparse`) but a `block_column`
cannot lie down. Horizontal logs need NBT.

- **Biomes:** all six wooded region-1 biomes.
- **Bounding box:** 3 × 3 × 11.
- **Build:** a horizontal spruce log run (`axis: z`) 7–9 long, one end raised on
  a 2-block stump with a jagged break — use a stripped log and a trapdoor for
  splintering. Two or three logs scattered around it, one partially sunk.
  Coarse_dirt and a couple of snow layers over the middle of the trunk.
- **Variants:** 4, at different rotations. (Jigsaw rotates pieces for you, but
  varied *breaks* matter more than varied angles.)
- **Placement:** `spacing 12, separation 4`, `projection: terrain_matching`,
  `terrain_adaptation: none`.

### 3.4 `r1_woodpile` — small detail · salt `774104`

- **Bounding box:** 5 × 4 × 5. A stack of logs under a plank lean-to, an axe in
  a chopping block (use an item frame if you want the axe visible), a barrel.
- **Placement:** `spacing 16, separation 6`.

### 3.5 `r1_stonewall` — the field boundary · salt `774105`

Nothing says "people farmed here" faster than a wall that no longer encloses
anything.

- **Bounding box:** 3 × 3 × 16. A cobblestone wall two blocks high with three
  gaps, mossy in places, a wooden gate post at one end.
- **`projection: terrain_matching`** so it rides the hills instead of cutting
  through them. This is the one setting that makes or breaks the piece.
- **Placement:** `spacing 14, separation 5`.

### 3.6 `r1_marker` — the cairn · salt `774106`

- **Bounding box:** 3 × 5 × 3. A stack of cobble and stone slabs with a spruce
  sign. Put text on the sign: distances, names, warnings. **This is the cheapest
  storytelling in the whole project** — signs survive in NBT and players read
  every one of them.
- **Placement:** `spacing 10, separation 4`. Common on purpose.

---

## 4. Region 2 — Deadly Snowfields

People tried to survive here and the evidence is all shelter: holes, overhangs,
tents, farmhouses with the roof gone. Nothing is intact. No greenery at all —
not one leaf, not one grass block, not one green wool.

Materials: spruce, cobblestone, ice, packed ice, white wool, lanterns, iron
bars, barrels, campfires (unlit — a lit fire says someone is alive).

### 4.1 `r2_camp_hidden` — the camp under the overhang · salt `882201`

- **Biomes:** `hollow_warrens`, `undercut_shelf`, `shattered_shelf` — all three
  already have overhangs and rifts from the `surface_rift` carver and the icicle
  feature.
- **Bounding box:** 9 × 6 × 9.
- **Build:** a rock shelf of stone and packed ice overhanging a hollow. Under
  it: two white-wool bedrolls (wool slabs are not a thing — use white carpet on
  the floor), an unlit campfire, a barrel, a lantern hanging from the ceiling on
  a chain, and **skeleton remains only if you want them** — this project has
  been restrained about bodies so far and that restraint is working.
- **Sell the fear:** leave the entrance half-blocked by snow layers and a fallen
  boulder. The player should have to look twice to find the way in.
- **Variants:** 3.
- **Placement:** `spacing 26, separation 9`, `terrain_adaptation: beard_thin`.

### 4.2 `r2_burrow` — the hole in the ground · salt `882202`

- **Bounding box:** 7 × 9 × 7, built **downward**, `step: underground_structures`.
- **Build:** a shaft of packed ice and stone going down 6, opening into a 5×5
  chamber with a bed, a barrel, and a ladder. Cap it with a snow-layer lid so
  from above it is just a dimple in the drift.
- **`terrain_adaptation: bury`** — this one is supposed to be swallowed.
- **Placement:** `spacing 20, separation 7`.

### 4.3 `r2_farmhouse` — the roofless house · salt `882203`

- **Bounding box:** 15 × 10 × 13.
- **Build:** a two-room cobble-and-spruce farmhouse with the roof collapsed
  inward — build the roof, then break it and drop the pieces onto the floor
  inside. A fenced pen outside with the fence half-buried in drift. Snow layers
  **inside** the building, because the roof is open.
- **Processor:** `frostline:ruined`.
- **Variants:** 2.
- **Placement:** `spacing 32, separation 11`, `terrain_adaptation: beard_box`.

### 4.4 `r2_deadfall` — the fallen forest · salt `882204`

Region 2's trees are on the ground, not standing. Same technique as §3.3 but
bigger and more of it: 5–8 trunks lying roughly parallel, as if something pushed
them all one way. That directionality is the storytelling — the storm came from
the south.

- **Bounding box:** 13 × 4 × 13. **Variants:** 3. **Placement:**
  `spacing 15, separation 5`, `projection: terrain_matching`.

### 4.5 `r2_tent` — the last camp · salt `882205`

- **Bounding box:** 7 × 5 × 7. White/grey wool stretched over a spruce frame,
  one side torn open (structure_void), contents spilled into the snow — a
  barrel on its side, scattered items in item frames, an unlit campfire.
- **Placement:** `spacing 18, separation 6`.

---

## 5. Region 3 — Riven Wastes

Region 3 must read as **more civilised than region 2**: more settlement, bigger
settlement, and more purpose to it. Not a few huts — actual plans. Roads, walls,
a depot, a watchtower, a chapel. And then all of it broken, because the storm
stood here longer than it stood in region 2.

Materials: stone bricks, deepslate bricks, cobbled deepslate, spruce, iron bars,
lanterns, chains, polished deepslate. Deepslate is region 3's identity — use it
where region 1 would have used cobblestone.

### 5.1 `r3_watchtower` — the tower on the hill · salt `993301`

- **Biomes:** `slate_hollows`, `settled_hollows`, `riven_flats`,
  `frozen_commons`
- **Bounding box:** 9 × 22 × 9.
- **Build:** a square stone-brick and deepslate-brick tower, 4 floors, ladder
  or stair core, iron-bar windows, a beacon-less brazier at the top (campfire on
  a stone slab, unlit), and **the top two courses blown off** with the rubble
  lying at the base. Chains hanging from the broken edge.
- **Read:** someone was watching for the storm. They saw it.
- **Processor:** `frostline:ruined`. **Variants:** 2.
- **Placement:** `spacing 36, separation 13`, `terrain_adaptation: beard_box`.

### 5.2 `r3_settlement` — the multi-piece town · salt `993302`

**This is the one jigsaw structure worth the complexity.** Everything else in
this document is single-piece.

- **Biomes:** `settled_hollows`, `frozen_commons`
- **Structure JSON:** `size: 5`, `max_distance_from_center: 96`,
  `terrain_adaptation: beard_box`.
- **Pieces to build** (each its own NBT, each 11×11 or 16×16 footprint):
  1. `town_centre` — a paved square of stone bricks with a well, **four jigsaw
     blocks** facing out, one per side, with `pool: frostline:r3_settlement/street`.
  2. `street_straight`, `street_corner`, `street_end` — 11×5×11 paved strips
     with jigsaw blocks at their open ends, and side jigsaws pointing at
     `frostline:r3_settlement/building`.
  3. `building_house`, `building_barn`, `building_hall`, `building_ruin` — the
     buildings, each with one jigsaw on the street-facing side.
- **Jigsaw block setup** for each connection: `target` = the *name* of the
  partner jigsaw (use `frostline:street_in` / `frostline:street_out`),
  `pool` = the pool the partner comes from, `final state` =
  `minecraft:air`, `joint type` = `aligned` for streets, `rollable` for
  buildings.
- **Pools:** one JSON per pool (`start`, `street`, `building`), each listing its
  elements with weights. `fallback` for `street` should be
  `frostline:r3_settlement/street_end` so unfinished roads get capped instead of
  ending in a jigsaw block.
- **Processors:** `frostline:weathered` on streets, `frostline:ruined` on
  buildings, and make `building_ruin` its own already-broken build so the town
  has a range of damage rather than one uniform level.
- **Placement:** `spacing 40, separation 16`.

> Budget this at an evening's work. It is the single biggest visual upgrade
> available to the project, and it is the thing that makes region 3 *feel* like
> the approach to a civilisation rather than more of region 2.

### 5.3 `r3_depot` — the supply building · salt `993303`

- **Bounding box:** 17 × 9 × 13. A long stone-brick warehouse, wide doors, rows
  of barrels and chests inside, a loading platform outside with crates spilled
  into the snow. Half the roof intact, half gone. **Loot matters here** — this
  is the region where a player wants to find food and iron.
- **Placement:** `spacing 34, separation 12`.

### 5.4 `r3_bridge_broken` — the span over the rift · salt `993304`

`riven_shelf` and `overhang_shelf` run the `surface_rift` carver, so they have
gorges. A bridge that no longer crosses one is free storytelling.

- **Bounding box:** 7 × 8 × 24.
- **Build:** stone-brick approach ramps at both ends, deck extending 6–8 blocks
  from each side, **nothing in the middle**, with broken deck blocks and hanging
  chains at the break. Use structure_void for the entire central span so the
  piece places cleanly no matter how wide the rift actually is.
- **`projection: rigid`**, `terrain_adaptation: beard_thin`.
- **Placement:** `spacing 30, separation 11`.

### 5.5 `r3_chapel` — the last public building · salt `993305`

- **Bounding box:** 13 × 14 × 17. Tall, narrow, stone-brick and deepslate, a
  bell (use a bell block) still hanging, glass panes mostly gone, benches of
  spruce stairs, candles. Roof half collapsed. One wall completely open.
- **Read:** people gathered here. It did not help. Keep it dignified — no gore,
  no scattered bodies.
- **Placement:** `spacing 44, separation 16`.

---

## 6. Region 4 (Stormheart) and Region 4b (Hard Snowcliff)

Region 4 is **the most civilised and the most destroyed**, and the storm is
still standing on it. Every structure here should be bigger than its region-3
equivalent and in worse condition. The rock is red; use that — red terracotta,
terracotta, granite and deepslate are the region's materials, alongside the
concrete-grey of stone and cobble for anything built.

### 6.1 `r4_township` — the city block · salt `114401`

The region-3 settlement's bigger, deader sibling. Same jigsaw technique (§5.2),
different content.

- **Biomes:** `hollowed_township`, `razed_commons`
- **Pieces:** a paved plaza, four street types, and **six** building types —
  including a two-storey terrace, a market hall and two pieces that are nothing
  but foundations and rubble.
- **Every piece** runs `frostline:razed` (integrity 0.40). The town should read
  as flattened, with only the lower two courses of most walls left.
- **Detail that sells the storm:** all the damage should lean the same way.
  When you build each piece, decide the storm came from the south and collapse
  every wall northward. Consistency across pieces is what makes it read as one
  event instead of decay.
- **Placement:** `spacing 44, separation 18`, `size: 6`.

### 6.2 `r4_tower_block` — the collapsed apartment · salt `114402`

- **Bounding box:** 17 × 30 × 17. Four storeys of stone, concrete-grey wool and
  iron bars, sheared diagonally: the south face intact to the top, the north
  face gone from the second floor up, floors hanging and bent. Rubble spill
  reaching 10 blocks out.
- This is the tallest thing a player will see in the world apart from mountains.
  Make the silhouette count.
- **Variants:** 2. **Placement:** `spacing 38, separation 14`,
  `terrain_adaptation: beard_box`.

### 6.3 `r4_convoy` — the wrecked column · salt `114403`

- **Bounding box:** 9 × 6 × 26. Three or four "vehicles" — the vanilla-friendly
  version is a cart shape: spruce/oak plank body, iron bars, wheels of
  deepslate slabs, a chest in the back — strung out along a line, two on their
  sides, one nose-first in a snow drift. Scattered barrels and dropped cargo
  between them.
- **Read:** people were leaving, in a group, and they did not make it. This is
  the strongest single piece of environmental storytelling in the design.
- **`projection: terrain_matching`**, `terrain_adaptation: none`.
- **Placement:** `spacing 26, separation 10`.

### 6.4 `r4_bunker` — the thing that might have worked · salt `114404`

- **Bounding box:** 15 × 12 × 15, mostly below ground, `terrain_adaptation:
  bury`, `step: underground_structures`.
- **Build:** a reinforced concrete-grey box — stone bricks, iron blocks, iron
  doors, redstone lamps (unlit) — with a blast door half open and drifted over.
  Inside: bunks, barrels of supplies, a sealed inner room the player has to
  break into.
- **The best loot in the world should be here.** Region 4 is the hardest place
  and the player is carrying nothing.
- **Placement:** `spacing 48, separation 20`.

### 6.5 `r4_mast` — the radio mast · salt `114405`

- **Bounding box:** 11 × 34 × 11. A lattice tower of iron bars and chains on a
  stone base, guy-wires of chain running to four anchor blocks, the top third
  snapped off and lying beside it. One redstone lamp at the top that will never
  light again.
- **Biomes:** `rustspine_peaks`, `scoured_horns`, `stormheart_flats` — put it on
  the red mountains, where it is visible from the valleys.
- **Placement:** `spacing 52, separation 22`.

### 6.6 `rc_outpost` — the cliff-edge camp · salt `114406`

The snowcliff band is 1 500 blocks wide and ends in a 44-block wall. This is
what was built at the edge of it.

- **Biomes:** `snowcliff_shelf`, `snowcliff_terrace`
- **Bounding box:** 15 × 10 × 15. A stone-and-spruce shelter, a windbreak wall
  facing south, a lookout platform pointing **north over the drop**, a brazier,
  crates. Heavily iced: packed ice and blue ice built into the structure itself.
- **Placement:** `spacing 24, separation 9`.

### 6.7 `rc_crane` — the descent gear · salt `114407`

- **Biomes:** `snowcliff_crown`, `snowcliff_brink`
- **Bounding box:** 13 × 18 × 20. A timber-and-iron crane leaning out over the
  edge, a chain running down off the structure into open air (10–14 blocks of
  chain, ending in nothing), a winch of deepslate and iron blocks.
- **Read:** they were trying to get *down* to the green. Whether anyone made it
  is the question you leave unanswered.
- **Placement:** `spacing 30, separation 12`.

### 6.8 `rc_anchor` — the cable head · salt `114408`

- **Bounding box:** 7 × 6 × 7. Four iron blocks sunk into the cliff lip with
  chains running off the north face into the void, a plaque (sign) with a name
  and a date. Tiny piece, high impact, five minutes to build.
- **Placement:** `spacing 16, separation 6`.

---

## 7. Sounds — the ambience the mod cannot ship yet

Right now every snowy region uses `minecraft:ambient.basalt_deltas.loop` as a
stand-in wind bed and The Quiet uses
`minecraft:ambient.soul_sand_valley.loop`. They work, but they are recognisable
as Nether sounds to anyone who has played the game.

**This is the single highest-impact thing you can add after structures.**

### 7.1 What to record or source

Eight files, OGG Vorbis, mono, 44.1 kHz:

| file | length | content |
|---|---|---|
| `storm_loop.ogg` | 30–60 s, **seamlessly looping** | low wind bed, no transients |
| `storm_additions_1..3.ogg` | 2–5 s | a gust, a distant crack of ice, a snow-slide |
| `quiet_loop.ogg` | 30–60 s, seamless | sub-bass drone, barely-there pulse |
| `quiet_additions_1..3.ogg` | 2–6 s | a distant shriek, a wet click, a long breath |

Loops must be **true loops** — trim at a zero crossing or the seam will click
every 30 seconds forever, and players notice it within minutes.

### 7.2 Where the files go

```
src/main/resources/assets/frostline/sounds/ambient/storm_loop.ogg
src/main/resources/assets/frostline/sounds/ambient/storm_add_1.ogg
…
src/main/resources/assets/frostline/sounds.json
```

`assets/` is **not** wiped by `gen3.py` — only `data/` is. Sound files are safe
where they are.

`src/main/resources/assets/frostline/sounds.json`:

```json
{
  "ambient.storm.loop": {
    "category": "ambient",
    "sounds": [ { "name": "frostline:ambient/storm_loop", "stream": true } ]
  },
  "ambient.storm.additions": {
    "category": "ambient",
    "sounds": [
      "frostline:ambient/storm_add_1",
      "frostline:ambient/storm_add_2",
      "frostline:ambient/storm_add_3"
    ]
  },
  "ambient.quiet.loop": {
    "category": "ambient",
    "sounds": [ { "name": "frostline:ambient/quiet_loop", "stream": true } ]
  },
  "ambient.quiet.additions": {
    "category": "ambient",
    "sounds": [
      "frostline:ambient/quiet_add_1",
      "frostline:ambient/quiet_add_2",
      "frostline:ambient/quiet_add_3"
    ]
  }
}
```

`"stream": true` on the loops matters: without it the whole file is held in
memory and long ambience files are exactly what streaming is for.

### 7.3 Switching the biomes over — one line

In `gen3.py`:

```python
CUSTOM_SOUNDS = True
```

That is the entire change. `snd()` then emits an **inline sound event** —
`{"sound_id": "frostline:ambient.storm.loop"}` — instead of a vanilla id.

This works because a biome's `ambient_sound` and `additions_sound` are
`Holder<SoundEvent>` fields, and a holder codec accepts an inline definition as
well as a registry reference. A datapack cannot *add* to the sound registry, but
it can inline one, and the client resolves the id through `sounds.json`. **No
Java is required**, which keeps the prime directive in `WORLDGEN.md` intact.

If you ever see "Unknown sound event" in the log, the id in `sounds.json` and
the id in `snd()` disagree — they must match exactly, including the dots.

### 7.4 Per-region intensity, already wired

Flipping the flag inherits the intensity ramp that is already in place: additions
chance climbs 0.0055 → 0.0095 → 0.016 from region 2 to region 4, the cave mood
sound tightens from every 6 000 ticks to every 3 000, and The Quiet's seven
stages run 0.0016 → 0.015 with the mood at 2 200 in `the_quiet`. You do not need
to touch those numbers; record the files, flip the flag.

---

## 8. Priority order

If you only do some of this, do it in this order:

1. **§7 sounds.** Highest impact per hour of work, and the flag is already wired.
2. **§2.1 `q_tendril`.** The Quiet has no silhouette without it.
3. **§6.3 `r4_convoy`** and **§3.6 `r1_marker`.** Cheap, and they carry the
   story harder than anything else on the list.
4. **§5.2 `r3_settlement`.** The big one. Region 3's entire purpose.
5. **§6.1 `r4_township`.** The payoff of the whole northward run.
6. Everything else, in whatever order the building is fun.

Every item's biome tag already exists. Nothing in this list requires a line of
Java, and nothing requires touching `gen3.py` except the one boolean in §7.3.
