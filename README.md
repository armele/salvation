
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

Corruption Stage Datapack Tuning
===========

Salvation now exposes the numeric corruption stage gameplay settings through datapacks. This lets modpack authors rebalance progression without editing the mod jar.

What Can Be Tuned
===========

Each corruption stage can override these values:

- `threshold`: the total progression value needed to enter the stage
- `loot_corruption_chance`: base chance for corruption-affected loot behavior
- `entity_spawn_chance`: base chance for corrupted spawn replacement logic
- `fight_back_corruption_chance`: base chance for a corruptable animal that is attacked convert to a corrupted entity and fight back
- `daily_raid_spawn_chance`: daily chance for an Exteritio raid check to succeed
- `decay_cooldown`: cooldown in ticks between chunk corruption decay or purification updates
- `blight_cooldown`: cooldown in ticks between blight spread attempts
- `transition_message_key`: optional translation key broadcast to all players when the stage is entered

What Is Not Datapacked
===========

The stage identities themselves are fixed:

- `stage_0_untriggered`
- `stage_1_normal`
- `stage_2_awakened`
- `stage_3_spreading`
- `stage_4_dangerous`
- `stage_5_critical`
- `stage_6_terminal`


Where To Put Your Override
===========

Create or edit a datapack and add a file at:

`data/<your_namespace>/salvation_corruption_stages/<file_name>.json`

Example:

`data/my_pack/salvation_corruption_stages/corruption_balance.json`

Salvation ships its built-in defaults here:

[`src/main/resources/data/salvation/salvation_corruption_stages/default.json`](c:/Programming/salvation/src/main/resources/data/salvation/salvation_corruption_stages/default.json)

Use that file as your starting template.

The built-in defaults keep `fight_back_corruption_chance` at `0.0` through stage 3, then raise it to `0.02` at stage 4, `0.04` at stage 5, and `0.08` at stage 6.

File Format
===========

The root object contains:

- `replace`: optional boolean
- `stages`: object keyed by the fixed stage ids listed above

Example:

```json
{
  "replace": false,
  "stages": {
    "stage_1_normal": {
      "threshold": 5000,
      "loot_corruption_chance": 0.03,
      "entity_spawn_chance": 0.06,
      "fight_back_corruption_chance": 0.0,
      "daily_raid_spawn_chance": 0.01,
      "decay_cooldown": 4800,
      "blight_cooldown": 14400,
      "transition_message_key": "message.salvation.corruption.stage_1_normal"
    },
    "stage_2_awakened": {
      "threshold": 12000,
      "loot_corruption_chance": 0.05,
      "entity_spawn_chance": 0.15,
      "fight_back_corruption_chance": 0.0,
      "daily_raid_spawn_chance": 0.03,
      "decay_cooldown": 6000,
      "blight_cooldown": 10800,
      "transition_message_key": "message.salvation.corruption.stage_2_awakened"
    }
  }
}
```

How `replace` Works
===========

- If `replace` is `false`, your file only overrides the stages you list. Any missing stages continue using Salvation's built-in defaults.
- If `replace` is `true`, your file replaces the entire built-in ruleset. In that mode, you should define all seven stages.

Validation Rules
===========

Salvation validates the loaded rules before using them:

- Every stage must exist after all files are merged
- `threshold` values must be strictly increasing from stage 0 through stage 6
- All chance values must be between `0.0` and `1.0`
- `decay_cooldown` and `blight_cooldown` must be positive integers

If validation fails, Salvation logs an error and falls back to the built-in defaults.

Recommended Workflow
===========

1. Copy the shipped default JSON into your datapack.
2. Change only the values you want to rebalance.
3. Keep thresholds in ascending order.
4. Test with `/reload` in a development environment or restart the game/server in a pack build.
5. Watch the latest log if your changes do not apply; invalid files will be rejected with an error.

Fight-Back Conversion Notes
===========

When `fight_back_corruption_chance` succeeds, the attacked corruptable animal starts a corruption conversion that completes in about 1 second instead of the normal 15-second corruption process. The converted mob also keeps the source animal's current health value so the triggering hit still matters.

Notes For Servers And Clients
===========

These stage rules are server-authoritative. Players do not need a separate client config for the gameplay changes to work.

At the moment, the client only receives the current stage ordinal for visual effects, not the full tuned ruleset. That is fine for current gameplay because the actual corruption logic runs on the server.

Furnace Designation For Modpacks
===========

Salvation now separates furnace compatibility into two datapack layers:

- block tags decide which machines count as corruption or purification sources
- furnace profiles describe how Salvation should read non-vanilla machines

This keeps the pack-author workflow simple:

1. Put the block in a Salvation furnace tag.
2. Add a furnace profile only if the machine is not a normal `AbstractFurnaceBlockEntity`.

Available Furnace Tags
===========

- `data/<your_namespace>/tags/block/corruption_furnaces.json`
- `data/<your_namespace>/tags/block/purification_furnaces.json`
- `data/<your_namespace>/tags/block/ignored_furnaces.json`

Salvation ships these defaults:

- `minecraft:furnace`
- `minecraft:blast_furnace`
- `minecraft:smoker`

as corruption furnaces, and:

- `salvation:purifying_furnace`

as a purification furnace.

If a block is placed in `ignored_furnaces`, Salvation will skip it even if it also appears in one of the other furnace tags.

When You Need A Furnace Profile
===========

You only need a profile when the machine is not readable as a vanilla furnace. Typical examples:

- FE-powered furnaces
- machines that expose inventory through item handlers
- custom furnaces whose input, output, or fuel slots are not the vanilla `0/2/1` layout

Put profiles in:

`data/<your_namespace>/salvation_furnace_profiles/<file_name>.json`

Built-in example:

[`src/main/resources/data/salvation/salvation_furnace_profiles/purifying_furnace.json`](c:/Programming/salvation/src/main/resources/data/salvation/salvation_furnace_profiles/purifying_furnace.json)

Profile Fields
===========

- `adapter`: `abstract_furnace` or `item_handler`
- `designation`: `corruption` or `purification`
- `blocks`: list of block ids this profile applies to
- `block_tags`: optional list of block tag ids this profile applies to
- `input_slots`: required for `item_handler`
- `output_slots`: required for `item_handler`
- `fuel_slots`: optional for `item_handler`
- `activity_blockstate_property`: optional boolean blockstate property name such as `lit` or `working`
- `recipe_type`: optional recipe type id, defaults to `minecraft:smelting` for `item_handler`
- `cook_time`: fallback cook time in ticks when Salvation cannot resolve a recipe
- `corruption_multiplier`: scales the normal corruption caused by cooking on this machine
- `machine_corruption_per_1000`: extra corruption per 1000 cook-time-equivalent points, useful for power-based machines
- `machine_purification_per_1000`: extra purification per 1000 cook-time-equivalent points

Example: FE Furnace
===========

```json
{
  "adapter": "item_handler",
  "designation": "corruption",
  "blocks": [
    "mysticalagriculture:furnace"
  ],
  "input_slots": [0],
  "output_slots": [1],
  "fuel_slots": [],
  "activity_blockstate_property": "working",
  "recipe_type": "minecraft:smelting",
  "cook_time": 200,
  "machine_corruption_per_1000": 2.0
}
```

In that example:

- the block must also be added to `corruption_furnaces`
- Salvation will watch slot `0` as input and slot `1` as output
- the machine contributes corruption even with no burnable fuel item present

Recommended Workflow
===========

1. Start by tagging the block as a corruption or purification furnace.
2. Test it in-game before adding a profile. Vanilla-style furnaces may already work.
3. Add a profile if the machine uses item handlers or custom slot layouts.
4. Use `/reload` in development, or restart the server in a pack build.
5. Check the latest log if a profile fails to load. Invalid profile files are skipped with an error.
