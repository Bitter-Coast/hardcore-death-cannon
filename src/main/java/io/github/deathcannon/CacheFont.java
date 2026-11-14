package io.github.deathcannon;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

final class CacheFont
{
    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final int CELL = 24;
    private final BufferedImage mask;
    private final byte[] metrics;
    private final Map<Color, BufferedImage> colors = new HashMap<>();

    CacheFont(String name)
    {
        mask = DeathCannonOverlay.load("font-" + name + ".png");
        try (InputStream stream = CacheFont.class.getResourceAsStream("font-" + name + ".bin"))
        {
            if (stream == null)
            {
                throw new IOException("Missing font metrics");
            }
            metrics = stream.readAllBytes();
            if (metrics.length != 257 + 256 * 4)
            {
                throw new IOException("Invalid font metrics");
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not load cache font " + name, ex);
        }
    }

    int width(String text)
    {
        return width(text.getBytes(CP1252));
    }

    private int width(byte[] text)
    {
        int width = 0;
        for (byte code : text)
        {
            width += Byte.toUnsignedInt(metrics[Byte.toUnsignedInt(code)]);
        }
        return width;
    }

    void draw(Graphics2D graphics, Color color, String value, int x, int y, double scale, int maxWidth,
        boolean centered)
    {
        byte[] text = value.getBytes(CP1252);
        int width = width(text);
        if (width == 0)
        {
            return;
        }
        double size = Math.min(scale, (double) maxWidth / width);
        BufferedImage atlas = colors.computeIfAbsent(color, this::tint);
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.translate(centered ? Math.round(x - width * size / 2) : x, y);
            g.scale(size, size);
            int pen = 0;
            int ascent = Byte.toUnsignedInt(metrics[256]);
            for (byte code : text)
            {
                int id = Byte.toUnsignedInt(code);
                int offset = 257 + id * 4;
                int dx = pen + Byte.toUnsignedInt(metrics[offset]);
                int dy = Byte.toUnsignedInt(metrics[offset + 1]) - ascent;
                int w = Byte.toUnsignedInt(metrics[offset + 2]);
                int h = Byte.toUnsignedInt(metrics[offset + 3]);
                int sx = id % 16 * CELL;
                int sy = id / 16 * CELL;
                g.drawImage(atlas, dx, dy, dx + w, dy + h, sx, sy, sx + w, sy + h, null);
                pen += Byte.toUnsignedInt(metrics[id]);
            }
        }
        finally
        {
            g.dispose();
        }
    }

    private BufferedImage tint(Color color)
    {
        BufferedImage tinted = new BufferedImage(mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tinted.createGraphics();
        try
        {
            g.drawImage(mask, 0, 0, null);
            g.setComposite(AlphaComposite.SrcIn);
            g.setColor(color);
            g.fillRect(0, 0, tinted.getWidth(), tinted.getHeight());
        }
        finally
        {
            g.dispose();
        }
        return tinted;
    }
}
