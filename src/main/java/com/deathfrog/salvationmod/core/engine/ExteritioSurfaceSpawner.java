package com.deathfrog.salvationmod.core.engine;

import java.util.List;
import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.ModEntityTypes;
import com.deathfrog.salvationmod.SalvationMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Keeps Exteritio's surface feeling inhabited even when caves consume most of the monster cap.
 * This intentionally spawns only in sky-visible locations near players and ignores light.
 */
public final class ExteritioSurfaceSpawner
{
    private static final int TICK_INTERVAL = 40;
    private static final int ATTEMPTS_PER_PLAYER = 2;
    private static final int MIN_SPAWN_DISTANCE = 24;
    private static final int MAX_SPAWN_DISTANCE = 56;
    private static final int LOCAL_CHECK_RADIUS = 72;
    private static final int LOCAL_TARGET_MINIONS = 8;
    private static final int MAX_GROUP_SPAWNS_PER_TICK = 4;

    private ExteritioSurfaceSpawner()
    {
    }

    /**
     * Attempts to spawn Exteritio entities near all players the players in the given level.
     * This runs every {@link #TICK_INTERVAL} ticks and will spawn up to {@link #MAX_GROUP_SPAWNS_PER_TICK} groups per tick.
     * If a player is in spectator mode, or if there are at least {@link #LOCAL_TARGET_MINIONS} Voraxian entities nearby,
     * the player will be skipped.
     * For each player, up to {@link #ATTEMPTS_PER_PLAYER} spawn attempts will be made.
     * @param level The level to spawn entities in.
     */
    public static void tick(final @Nonnull ServerLevel level)
    {
        if ((level.getGameTime() % TICK_INTERVAL) != 0L || level.players().isEmpty())
        {
            return;
        }

        int spawnedGroups = 0;
        for (final ServerPlayer player : level.players())
        {
            if (spawnedGroups >= MAX_GROUP_SPAWNS_PER_TICK)
            {
                return;
            }

            BlockPos playerPos = player.blockPosition();


            if (playerPos == null || player.isSpectator() || nearbyVoraxianCount(level, playerPos) >= LOCAL_TARGET_MINIONS)
            {
                continue;
            }

            for (int attempt = 0; attempt < ATTEMPTS_PER_PLAYER && spawnedGroups < MAX_GROUP_SPAWNS_PER_TICK; attempt++)
            {
                if (trySpawnNearPlayer(level, player))
                {
                    spawnedGroups++;
                    break;
                }
            }
        }
    }

    /**
     * Attempts to spawn a voraxian mob near the given player.
     * Spawns a voraxian mob at a random position within a circle centered at the player,
     * with a radius of between MIN_SPAWN_DISTANCE and MAX_SPAWN_DISTANCE blocks.
     * The mob will be spawned at the surface level of the world, and will be set to persist even after being removed from the world.
     * If the entity has a target set, the mob will be set to target that entity.
     * A sound effect will be played when the mob is spawned.
     * @param level the server level to spawn the mob in
     * @param player the server player to spawn the mob near
     * @return whether or not the mob was spawned successfully
     */
    @SuppressWarnings("deprecation")
    private static boolean trySpawnNearPlayer(final ServerLevel level, final ServerPlayer player)
    {
        final RandomSource random = level.random;
        final float angle = random.nextFloat() * Mth.TWO_PI;
        final int radius = Mth.nextInt(random, MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE);
        final int x = Mth.floor(player.getX() + Mth.cos(angle) * radius);
        final int z = Mth.floor(player.getZ() + Mth.sin(angle) * radius);
        final int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        final BlockPos groundPos = new BlockPos(x, height - 1, z);

        if (!isValidSurfaceGround(level, groundPos))
        {
            return false;
        }

        final EntityType<? extends Mob> type = pickVoraxianType(random);
        final Mob mob = type.create(level);
        if (mob == null)
        {
            return false;
        }

        final BlockPos spawnPos = spawnPosForType(type, groundPos, random);
        final float yaw = random.nextFloat() * 360.0F;
        mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, yaw, 0.0F);

        if (!level.noCollision(mob) || !level.getWorldBorder().isWithinBounds(spawnPos))
        {
            return false;
        }

        DifficultyInstance difficulty = level.getCurrentDifficultyAt(spawnPos);

        if (difficulty == null)
        {
            return false;
        }

        mob.finalizeSpawn(level, difficulty, MobSpawnType.NATURAL, null);
        final boolean added = level.addFreshEntity(mob);

        if (added)
        {
            SalvationMod.LOGGER.debug("Spawned surface {} in Exteritio at {}", type, spawnPos);
        }

        return added;
    }

    /**
     * Randomly selects a Voraxian entity type to spawn based on the given random source.
     * 
     * The selection probability is as follows:
     * - Voraxian Stinger: 60%
     * - Voraxian Maw: 25%
     * - Voraxian Observer: 15%
     * 
     * @param random the random source to use for selection
     * @return the randomly selected Voraxian entity type
     */
    private static EntityType<? extends Mob> pickVoraxianType(final RandomSource random)
    {
        final int roll = random.nextInt(100);
        if (roll < 60)
        {
            return ModEntityTypes.VORAXIAN_STINGER.get();
        }

        if (roll < 85)
        {
            return ModEntityTypes.VORAXIAN_MAW.get();
        }

        return ModEntityTypes.VORAXIAN_OBSERVER.get();
    }

    private static BlockPos spawnPosForType(final EntityType<? extends Mob> type, final BlockPos groundPos, final @Nonnull RandomSource random)
    {
        if (type == ModEntityTypes.VORAXIAN_STINGER.get())
        {
            return groundPos.above();
        }

        final int yOffset = type == ModEntityTypes.VORAXIAN_MAW.get()
            ? Mth.nextInt(random, 2, 4)
            : Mth.nextInt(random, 4, 7);
        return groundPos.above(yOffset);
    }

    /**
     * Checks if the given block position is a valid surface ground position.
     * 
     * A valid surface ground position is defined as a position that is:
     * - above the minimum build height of the level
     * - solid (not air)
     * - not a liquid
     * - not a leaf block
     * - has a clear line of sight to the sky
     * 
     * @param level the level to check in
     * @param groundPos the block position to check
     * @return true if the position is a valid surface ground position, false otherwise
     */
    @SuppressWarnings("null")
    private static boolean isValidSurfaceGround(final Level level, final BlockPos groundPos)
    {
        if (groundPos.getY() < level.getMinBuildHeight())
        {
            return false;
        }

        final BlockState ground = level.getBlockState(groundPos);
        final BlockState spawnState = level.getBlockState(groundPos.above());

        if (ground.isAir() || !spawnState.isAir() || !ground.getFluidState().isEmpty() || ground.getBlock() instanceof LeavesBlock)
        {
            return false;
        }

        return level.canSeeSky(groundPos.above());
    }

    /**
     * Counts the number of living Voraxian mobs within a certain radius of the given block position.
     * The radius is defined as LOCAL_CHECK_RADIUS blocks in the x and z axes, and extends up to 32 blocks in the y-axis.
     * The mobs must be alive and have a clear line of sight to the sky to be counted.
     * 
     * @param level the level to check in
     * @param center the block position to check around
     * @return the number of living Voraxian mobs within the given radius
     */
    private static int nearbyVoraxianCount(final ServerLevel level, final @Nonnull BlockPos center)
    {
        final AABB box = new AABB(center).inflate(LOCAL_CHECK_RADIUS, 32.0D, LOCAL_CHECK_RADIUS);

        if (box == null)
        {
            return 0;
        }

        @SuppressWarnings("null")
        final List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, mob ->
            mob != null
                && mob.isAlive()
                && SalvationManager.isVoraxian(mob.getType())
                && level.canSeeSky(mob.blockPosition()));
                
        return mobs.size();
    }
}
