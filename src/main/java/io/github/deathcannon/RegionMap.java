package io.github.deathcannon;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A broad map of the region a death broadcast names. The maps are Jagex's own Leagues area-menu sprites
 * (cache sprites 8059 to 8070, RuneLite's {@code SpriteID.LEAGUE_6_MAP_*}), whose areas carry the same names
 * in cache enum 3412. Each overlay is stored on the sprites' shared 468 x 214 canvas, so it lines up with
 * the empty world map. The map shows the whole region, never a tile.
 */
final class RegionMap
{
    /** Broadcast region names, lower case, to their bundled sprite. */
    private static final Map<String, String> FILES = Map.ofEntries(
        Map.entry("misthalin", "misthalin"),
        Map.entry("karamja", "karamja"),
        Map.entry("asgarnia", "asgarnia"),
        Map.entry("kandarin", "kandarin"),
        Map.entry("morytania", "morytania"),
        Map.entry("kharidian desert", "desert"),
        Map.entry("tirannwn", "tirannwn"),
        Map.entry("fremennik province", "fremennik"),
        Map.entry("wilderness", "wilderness"),
        Map.entry("kourend & kebos", "kourend"),
        Map.entry("varlamore", "varlamore"));
    /** Canvas pixels kept around the region's main landmass. */
    private static final int PAD = 10;
    private static final Map<String, BufferedImage> CROPS = new ConcurrentHashMap<>();

    private RegionMap()
    {
    }

    /**
     * The region highlighted on the world map, cropped around its largest landmass to the given aspect,
     * or null when the region is absent or has no bundled map.
     */
    static BufferedImage of(String region, int aspectWidth, int aspectHeight)
    {
        String file = region == null ? null : FILES.get(region.toLowerCase(Locale.ROOT));
        return file == null ? null
            : CROPS.computeIfAbsent(file + '@' + aspectWidth + 'x' + aspectHeight, k -> crop(file, aspectWidth, aspectHeight));
    }

    private static BufferedImage crop(String file, int aspectWidth, int aspectHeight)
    {
        BufferedImage base = DeathCannonOverlay.load("regions/empty.png");
        BufferedImage area = DeathCannonOverlay.load("regions/" + file + ".png");
        int[] box = largestLandmass(area);
        int width = box[2] - box[0] + 2 * PAD;
        int height = box[3] - box[1] + 2 * PAD;
        // Widen the short side so the crop fills the view without stretching.
        if (width * aspectHeight < height * aspectWidth)
        {
            width = (int) Math.ceil((double) height * aspectWidth / aspectHeight);
        }
        else
        {
            height = (int) Math.ceil((double) width * aspectHeight / aspectWidth);
        }
        width = Math.min(width, base.getWidth());
        height = Math.min(height, base.getHeight());
        int left = Math.max(0, Math.min(base.getWidth() - width, (box[0] + box[2]) / 2 - width / 2));
        int top = Math.max(0, Math.min(base.getHeight() - height, (box[1] + box[3]) / 2 - height / 2));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, -left, -top, null);
        g.drawImage(area, -left, -top, null);
        g.dispose();
        return out;
    }

    /**
     * The bounds {minX, minY, maxX, maxY} of the largest connected opaque area. Some regions include far
     * islands, which would otherwise shrink the main landmass to a few pixels.
     */
    static int[] largestLandmass(BufferedImage image)
    {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[] seen = new boolean[w * h];
        int[] best = {0, 0, w, h};
        int bestSize = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < w * h; start++)
        {
            if (seen[start] || image.getRGB(start % w, start / w) >>> 24 == 0)
            {
                continue;
            }
            int[] box = {w, h, 0, 0};
            int size = 0;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty())
            {
                int p = queue.poll();
                int x = p % w;
                int y = p / w;
                size++;
                box[0] = Math.min(box[0], x);
                box[1] = Math.min(box[1], y);
                box[2] = Math.max(box[2], x + 1);
                box[3] = Math.max(box[3], y + 1);
                for (int dy = -1; dy <= 1; dy++)
                {
                    for (int dx = -1; dx <= 1; dx++)
                    {
                        int nx = x + dx;
                        int ny = y + dy;
                        int n = ny * w + nx;
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[n] && image.getRGB(nx, ny) >>> 24 != 0)
                        {
                            seen[n] = true;
                            queue.add(n);
                        }
                    }
                }
            }
            if (size > bestSize)
            {
                bestSize = size;
                best = box;
            }
        }
        return best;
    }
}
