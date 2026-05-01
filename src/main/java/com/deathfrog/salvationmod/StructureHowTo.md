/give @s minecraft:structure_block

Use place one structure block just outside the structure to be saved.
Provide a name, and set it as a "save" block

Use a second structure block to mark the opposite corner.
Set it to corner mode and name it to match the first block. Name should be of the style <modname>:structure_name

On the "save" block, detect the structure, then save.  The save will be written to runs\client\saves\<World Name>\generated\<modname>\structures\<structure_name.nbt>

The structure being saved must include a jigsaw block. (/give @s minecraft:jigsaw)
The jigsaw block will be the effective placement point of the structure. For a structure with a "basement", you would then want your jigsaw box elevated from the bottom of the overall structure by the depth of the basement.

## Mob spawners in scanned structures

Configure spawners before running the structure-block scan. The scan saves the spawner block entity NBT, so the saved structure will keep the creature type, spawn count, spawn delay, range, and nearby-entity cap that were on the block at scan time.

Useful command template:

```mcfunction
/setblock ~ ~ ~ minecraft:spawner{SpawnData:{entity:{id:"salvation:voraxian_stinger"}},SpawnCount:3s,MinSpawnDelay:200s,MaxSpawnDelay:400s,MaxNearbyEntities:6s,RequiredPlayerRange:16s,SpawnRange:4s,Delay:20s}
```

Spawner fields to tune:

- `SpawnData.entity.id`: the entity id to spawn, such as `salvation:voraxian_stinger` or `minecraft:zombie`.
- `SpawnCount`: how many mobs the spawner attempts to create each cycle.
- `MinSpawnDelay` and `MaxSpawnDelay`: the random delay between spawn cycles, in ticks. There are 20 ticks per second, so `200s` to `400s` means roughly every 10 to 20 seconds.
- `MaxNearbyEntities`: the live-mob cap near the spawner. Keep this at least as high as the population you want nearby, otherwise the spawner will pause even if its delay has elapsed.
- `RequiredPlayerRange`: how close a player must be for the spawner to tick.
- `SpawnRange`: how far from the spawner mobs can appear.
- `Delay`: the initial countdown after the structure is placed or loaded. Use a small value for the first spawn to happen quickly.

Rule of thumb: `SpawnCount` controls the burst size, `MinSpawnDelay`/`MaxSpawnDelay` control frequency, and `MaxNearbyEntities` controls the maximum sustained number. For example, a spawner with `SpawnCount:4s`, `MinSpawnDelay:600s`, `MaxSpawnDelay:600s`, and `MaxNearbyEntities:8s` will try to add 4 mobs every 30 seconds until 8 nearby mobs are present.

After setting the NBT, look at the spawner in-game and confirm the creature spinning inside is correct before scanning the structure. If the spawner is already placed, update it with:

```mcfunction
/data merge block <x> <y> <z> {SpawnData:{entity:{id:"salvation:voraxian_stinger"}},SpawnCount:3s,MinSpawnDelay:200s,MaxSpawnDelay:400s,MaxNearbyEntities:6s,RequiredPlayerRange:16s,SpawnRange:4s,Delay:20s}
```

