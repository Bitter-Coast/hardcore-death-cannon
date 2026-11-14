package io.github.deathcannon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

final class WorldMapLabels
{
    private static final List<Label> LABELS = load();

    private WorldMapLabels()
    {
    }

    static String nearest(WorldPoint tile)
    {
        if (tile == null)
        {
            return "Unknown location";
        }

        Label best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Label label : LABELS)
        {
            if (tile.getPlane() < label.plane || tile.getPlane() >= label.plane + label.planes)
            {
                continue;
            }
            double distance = Math.hypot(tile.getX() - label.x, tile.getY() - label.y);
            int radius = label.scale == 0 ? 72 : label.scale == 1 ? 144 : 250;
            // Prefer towns and named areas over nearby river labels and minor landmarks.
            double score = distance + (label.scale == 1 ? -35 : label.scale >= 2 ? 75 : 0);
            if (distance <= radius && score < bestScore)
            {
                best = label;
                bestScore = score;
            }
        }
        return best == null ? "Unknown location" : best.name;
    }

    private static List<Label> load()
    {
        List<Label> labels = new ArrayList<>();
        try (InputStream stream = WorldMapLabels.class.getResourceAsStream("world-map-labels.tsv"))
        {
            if (stream == null)
            {
                throw new IOException("Missing world map labels");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    String[] fields = line.split("\t", 6);
                    labels.add(new Label(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                        Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]),
                        fields[5].replace("<br>", " ")));
                }
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not load cached world map labels", ex);
        }
        return labels;
    }

    private static final class Label
    {
        private final int x;
        private final int y;
        private final int plane;
        private final int planes;
        private final int scale;
        private final String name;

        private Label(int x, int y, int plane, int planes, int scale, String name)
        {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.planes = planes;
            this.scale = scale;
            this.name = name;
        }
    }
}
