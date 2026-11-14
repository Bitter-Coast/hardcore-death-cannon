package io.github.deathcannon;

import java.awt.image.BufferedImage;
import java.time.Instant;
import net.runelite.api.coords.WorldPoint;

final class PlayerSighting
{
    final BufferedImage portrait;
    final Instant time;
    final int world;
    final WorldPoint tile;

    PlayerSighting(BufferedImage portrait, Instant time, int world, WorldPoint tile)
    {
        this.portrait = portrait;
        this.time = time;
        this.world = world;
        this.tile = tile;
    }

    String displayLocation()
    {
        return WorldMapLabels.nearest(tile);
    }
}
