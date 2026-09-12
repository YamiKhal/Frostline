# handmade/

Hand-authored data that must survive `python gen3.py`.

`gen3.py` wipes `src/main/resources/data/` on every run — it is generated
output. Everything under `handmade/data/` is copied over that tree **after**
generation, verbatim, and the run prints `copied N hand-made file(s)`.

So: NBT structures, template pools, structure JSON, structure sets, processor
lists and loot tables go here. Nothing else does.

```
handmade/data/frostline/structures/<id>/<piece>.nbt      the models
handmade/data/frostline/worldgen/structure/<id>.json
handmade/data/frostline/worldgen/template_pool/<id>/<pool>.json
handmade/data/frostline/worldgen/structure_set/<id>.json
handmade/data/frostline/worldgen/processor_list/<name>.json
handmade/data/frostline/loot_tables/chests/<name>.json
```

Biome tags are NOT hand-written: `gen3.py` generates one per planned structure
at `data/frostline/tags/worldgen/biome/has_structure/<id>.json` from the
`STRUCTURE_PLAN` table. Edit that table, not the tag.

Full build instructions, per structure: `../STRUCTURES.md`.
