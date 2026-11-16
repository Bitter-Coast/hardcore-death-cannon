package io.github.deathcannon;

import java.awt.image.BufferedImage;

/**
 * A crop of the client's own scene map ({@code Client.drawInstanceMap}, the image RuneLite's
 * Instance Map shows) around one tile. The client draws 4 pixels per tile, north up, inside a
 * 160 pixel margin for extended scenes, so scene tile (x, y) starts at pixel
 * (160 + 4x, 160 + 4(103 - y)), the same transform RuneLite uses for its player dot.
 */
final class SceneMap
{
    static final int TILE = 4;
    static final int MARGIN = 160;
    static final int SCENE = 104;
    static final int COLUMNS = 51;
    static final int ROWS = 28;

    final BufferedImage image;
    /** Top-left pixel of the marked tile inside {@link #image}. */
    final int markerX;
    final int markerY;

    private SceneMap(BufferedImage image, int markerX, int markerY)
    {
        this.image = image;
        this.markerX = markerX;
        this.markerY = markerY;
    }

    /** Crops the map around a scene tile, or returns null when the tile is outside the drawn scene. */
    static SceneMap crop(int[] pixels, int width, int height, int sceneX, int sceneY)
    {
        int size = MARGIN * 2 + SCENE * TILE;
        if (pixels == null || width < size || height < size || pixels.length < width * height
            || sceneX < 0 || sceneY < 0 || sceneX >= SCENE || sceneY >= SCENE)
        {
            return null;
        }
        int tileX = MARGIN + sceneX * TILE;
        int tileY = MARGIN + (SCENE - 1 - sceneY) * TILE;
        int w = COLUMNS * TILE;
        int h = ROWS * TILE;
        // Keep the crop inside the loaded scene; the marker moves off-centre near its edge.
        int left = Math.max(MARGIN, Math.min(MARGIN + SCENE * TILE - w, tileX + TILE / 2 - w / 2));
        int top = Math.max(MARGIN, Math.min(MARGIN + SCENE * TILE - h, tileY + TILE / 2 - h / 2));
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, w, h, pixels, top * width + left, width);
        return new SceneMap(image, tileX - left, tileY - top);
    }
}
