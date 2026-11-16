package io.github.deathcannon;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;

final class PlayerPortrait
{
    static final int SIZE = 82;
    private static final double YAW = Math.toRadians(18);
    private static final double PITCH = Math.toRadians(9);

    private PlayerPortrait()
    {
    }

    // Draw a small, flat-shaded snapshot without changing the game's model or rasterizer.
    static BufferedImage render(Model model)
    {
        return render(model, SIZE, 1, 1);
    }

    /**
     * Draws a model into a square image. NPC definitions stretch their models with width and
     * height scales (128 = 100%), which are applied here as fractions.
     */
    static BufferedImage render(Model model, int size, double widthScale, double heightScale)
    {
        if (model == null || model.getVerticesCount() == 0 || model.getFaceCount() == 0 || size <= 8
            || widthScale <= 0 || heightScale <= 0)
        {
            return null;
        }

        int vertexCount = model.getVerticesCount();
        float[] x = model.getVerticesX();
        float[] y = model.getVerticesY();
        float[] z = model.getVerticesZ();
        double[] horizontal = new double[vertexCount];
        double[] vertical = new double[vertexCount];
        double[] depth = new double[vertexCount];
        double left = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < vertexCount; i++)
        {
            double vx = x[i] * widthScale;
            double vy = y[i] * heightScale;
            double vz = z[i] * widthScale;
            double forward = vz * Math.cos(YAW) - vx * Math.sin(YAW);
            horizontal[i] = vx * Math.cos(YAW) + vz * Math.sin(YAW);
            vertical[i] = vy * Math.cos(PITCH) - forward * Math.sin(PITCH);
            depth[i] = forward * Math.cos(PITCH) + vy * Math.sin(PITCH);
            left = Math.min(left, horizontal[i]);
            right = Math.max(right, horizontal[i]);
            top = Math.min(top, vertical[i]);
            bottom = Math.max(bottom, vertical[i]);
        }

        double extent = Math.max(right - left, bottom - top);
        if (extent <= 0)
        {
            return null;
        }
        double scale = (size - Math.max(4, size / 10)) / extent;
        int[] px = new int[vertexCount];
        int[] py = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++)
        {
            px[i] = (int) Math.round(size / 2.0 + (horizontal[i] - (left + right) / 2) * scale);
            py[i] = (int) Math.round(size / 2.0 + (vertical[i] - (top + bottom) / 2) * scale);
        }

        int[] a = model.getFaceIndices1();
        int[] b = model.getFaceIndices2();
        int[] c = model.getFaceIndices3();
        int[] colors = model.getFaceColors1();
        int[] second = model.getFaceColors2();
        int[] third = model.getFaceColors3();
        byte[] transparency = model.getFaceTransparencies();
        short[] textures = model.getFaceTextures();
        List<Integer> faces = new ArrayList<>(model.getFaceCount());
        for (int face = 0; face < model.getFaceCount(); face++)
        {
            if (third[face] != -2 && (transparency == null || Byte.toUnsignedInt(transparency[face]) < 245))
            {
                faces.add(face);
            }
        }
        if (faces.isEmpty())
        {
            return null;
        }

        faces.sort(Comparator.<Integer>comparingDouble(
            face -> depth[a[face]] + depth[b[face]] + depth[c[face]]).reversed());
        BufferedImage portrait = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = portrait.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] faceX = new int[3];
            int[] faceY = new int[3];
            for (int face : faces)
            {
                faceX[0] = px[a[face]];
                faceX[1] = px[b[face]];
                faceX[2] = px[c[face]];
                faceY[0] = py[a[face]];
                faceY[1] = py[b[face]];
                faceY[2] = py[c[face]];
                int alpha = transparency == null ? 255 : 255 - Byte.toUnsignedInt(transparency[face]);
                boolean textured = textures != null && textures[face] != -1;
                g.setColor(faceColor(colors[face], second[face], third[face], textured, alpha));
                g.fillPolygon(faceX, faceY, 3);
            }
        }
        finally
        {
            g.dispose();
        }
        return portrait;
    }

    private static Color faceColor(int first, int second, int third, boolean textured, int alpha)
    {
        int light = first & 127;
        if (third >= 0)
        {
            light = (light + (second & 127) + (third & 127)) / 3;
        }
        double luminance = (light + 0.5) / 128;
        double hue = (JagexColor.unpackHue((short) first) + 0.5) / 64;
        double saturation = textured ? 0 : (JagexColor.unpackSaturation((short) first) + 0.5) / 8;
        double amplitude = saturation * Math.min(luminance, 1 - luminance);
        return new Color(channel(0, hue, luminance, amplitude), channel(8, hue, luminance, amplitude),
            channel(4, hue, luminance, amplitude), alpha);
    }

    private static int channel(int offset, double hue, double luminance, double amplitude)
    {
        double k = (offset + hue * 12) % 12;
        double value = luminance - amplitude * Math.max(-1, Math.min(1, Math.min(k - 3, 9 - k)));
        return (int) Math.round(255 * value);
    }
}
