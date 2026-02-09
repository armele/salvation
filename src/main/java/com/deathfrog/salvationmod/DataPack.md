Pack Maker notes:
#Environmental Sources
Key resources to progress the storyline are biome-driven. To add a biome as a candidate to host these resources, give it the "salvation:thin_spot_biomes" tag.

# Item Corruption
To make an item subject to corruption, give it the "salvation:corruptable_items" tag.
Designate what takes its place by creating a tag "salvation:corrupted_<original_item>".
In that tag, put one or more corrupted items that may take the place of the original item.

If the corrupted item is not already in the "salvation:corrupted_items" list, give it one of these tags, depending on rarity:
salvation:corrupted_items_common
salvation:corrupted_items_uncommon
salvation:corrupted_items_rare
salvation:corrupted_items_epic

# Corruption Progression
To designate breaking a block as an act that progresses corruption, give it one of these tags:
salvation:corruption_block_minor
salvation:corruption_block_major
salvation:corruption_block_extreme

To designate killing an entity as an act that progresses or counteracts corruption, give it one of these tags:
salvation:corruption_kill_minor
salvation:corruption_kill_major
salvation:corruption_kill_extreme
salvation:purification_kill_minor
salvation:purification_kill_major
salvation:purification_kill_extreme

# Curing mobs
Salvation supports data-driven cure mappings via datapacks.

Datapacks may define or override which entity a corrupted mob converts into when cured by adding JSON files under:

data/<namespace>/salvation_cure_mappings/

Each JSON file in this folder is automatically loaded when datapacks are applied or reloaded (/reload).
Mappings are merged across all loaded datapacks unless explicitly replaced.

This allows modpack and datapack authors to:

Change which vanilla (or modded) entity a corrupted mob cures into

Add cure mappings for corrupted entities added by other mods

Override Salvation’s default cure behavior without code changes

# Setting Initial Corruption Levels for Custom Dimensions

Salvation allows modpack and datapack authors to control the starting corruption stage of individual dimensions. This is done entirely through datapack tags—no code changes are required.

This system is intended for worldbuilders who want:

Safe “starter” dimensions

Mid-game danger zones

Late-game or endgame corrupted realms

How it works (high level)

Each dimension has its own saved corruption data.

When a dimension is first loaded for the first time, Salvation checks whether it belongs to one of several dimension tags.

If a match is found, the dimension’s initial corruption level is set accordingly.

This initialization happens once per dimension and is saved permanently.

Dimension tags

Salvation looks for tags on the dimension registry (minecraft:dimension, internally LevelStem).

You define these tags in your datapack under:

data/<namespace>/tags/dimension/

Each tag represents a starting corruption stage.

Example tags
Tag name	Intended meaning
corruption_stage0	Uncorrupted / safe
corruption_stage3	Spreading corruption
corruption_stage6	Terminal corruption

Avoid having dimensions in multiple corruption stage tags. If you do, it will apply the furthest progression to the dimension.