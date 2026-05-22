package com.deathfrog.salvationmod.client;

import java.time.Duration;

import com.minecolonies.core.client.render.worldevent.HighlightManager;
import com.minecolonies.core.client.render.worldevent.highlightmanager.TimedBoxRenderData;
import com.deathfrog.salvationmod.client.map.BeaconMapWaypointBridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side beacon highlight rendering hooks.
 */
@OnlyIn(Dist.CLIENT)
public final class BeaconHighlighter
{
    private static final String HIGHLIGHT_KEY = "salvationBeaconLocation";
    public static final Duration HIGHLIGHT_DURATION = Duration.ofSeconds(60);
    private static final int BEACON_HIGHLIGHT_COLOR = 0xff55ddff;

    private BeaconHighlighter()
    {
    }

    /**
     * Highlight one beacon block and briefly describe its position relative to the player.
     *
     * @param beaconPos beacon core position
     */
    public static void highlight(final BlockPos beaconPos)
    {
        if (beaconPos == null)
        {
            return;
        }

        HighlightManager.clearHighlightsForKey(HIGHLIGHT_KEY);
        HighlightManager.addHighlight(
            HIGHLIGHT_KEY,
            beaconPos.toShortString(),
            new TimedBoxRenderData(beaconPos)
                .setDuration(HIGHLIGHT_DURATION)
                .setColor(BEACON_HIGHLIGHT_COLOR)
                .addText("Purification beacon"));

        showRelativePosition(beaconPos);
        BeaconMapWaypointBridge.showBeaconWaypoint(beaconPos);
    }

    /**
     * Show the actionbar message near the middle bottom of the screen.
     *
     * @param beaconPos beacon core position
     */
    @SuppressWarnings("null")
    private static void showRelativePosition(final BlockPos beaconPos)
    {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
        {
            return;
        }

        player.displayClientMessage(Component.literal(relativeDescription(player.blockPosition(), beaconPos)), true);
    }

    /**
     * Build a concise position description relative to the player.
     *
     * @param playerPos player block position
     * @param beaconPos target beacon position
     * @return relative direction, distance, height, and exact position
     */
    private static String relativeDescription(final BlockPos playerPos, final BlockPos beaconPos)
    {
        final int dx = beaconPos.getX() - playerPos.getX();
        final int dy = beaconPos.getY() - playerPos.getY();
        final int dz = beaconPos.getZ() - playerPos.getZ();
        final int horizontalDistance = (int) Math.round(Math.hypot(dx, dz));

        return "Beacon highlighted. " + horizontalInstruction(horizontalDistance, dx, dz)
            + ", " + verticalDirection(dy)
            + ". Position: " + beaconPos.toShortString();
    }

    /**
     * Build a horizontal navigation instruction from Minecraft X/Z offsets.
     *
     * @param horizontalDistance distance on the X/Z plane
     * @param dx east-west offset
     * @param dz north-south offset
     * @return horizontal navigation instruction
     */
    private static String horizontalInstruction(final int horizontalDistance, final int dx, final int dz)
    {
        if (dx == 0 && dz == 0)
        {
            return "You are horizontally aligned";
        }

        final String northSouth = dz < 0 ? "north" : dz > 0 ? "south" : "";
        final String eastWest = dx > 0 ? "east" : dx < 0 ? "west" : "";
        return "Head " + northSouth + eastWest + " " + horizontalDistance + " blocks";
    }

    /**
     * Resolve Y offset into readable height language.
     *
     * @param dy vertical offset
     * @return vertical direction from player
     */
    private static String verticalDirection(final int dy)
    {
        if (dy > 0)
        {
            return "up " + dy + " blocks";
        }
        if (dy < 0)
        {
            return "down " + -dy + " blocks";
        }
        return "same elevation";
    }
}
