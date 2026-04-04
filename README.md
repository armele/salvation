
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
