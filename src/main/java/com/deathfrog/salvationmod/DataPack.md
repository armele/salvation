Pack Maker notes:
#Environmental Sources
Key resources to progress the storyline are biome-driven. To add a biome as a candidate to host these resources, give it the "salvation:thin_spot_biomes" tag.

#Item Corruption
To make an item subject to corruption, give it the "salvation:corruptable_items" tag.
Designate what takes its place by creating a tag "salvation:corrupted_<original_item>".
In that tag, put one or more corrupted items that may take the place of the original item.

If the corrupted item is not already in the "salvation:corrupted_items" list, give it one of these tags, depending on rarity:
salvation:corrupted_items_common
salvation:corrupted_items_uncommon
salvation:corrupted_items_rare
salvation:corrupted_items_epic

#Corruption Progression
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