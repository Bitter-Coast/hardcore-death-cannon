package io.github.deathcannon;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SightingStore
{
    static final int LIMIT = 500;
    private static final int FORMAT = 0x48444301;
    private static final int SIZE = PlayerPortrait.SIZE;
    private static final Logger LOG = LoggerFactory.getLogger(SightingStore.class);
    private final Filepath file;

    SightingStore(Filepath directory)
    {
        file = directory.join("sightings.dat.gz");
    }

    Map<String, PlayerSighting> load()
    {
        Map<String, PlayerSighting> result = new LinkedHashMap<>();
        try (InputStream stream = file.openInputStream();
             DataInputStream in = new DataInputStream(new GZIPInputStream(stream)))
        {
            if (in.readInt() != FORMAT)
            {
                throw new IOException("Unknown sighting cache format");
            }
            int count = in.readInt();
            if (count < 0 || count > LIMIT)
            {
                throw new IOException("Invalid sighting cache size");
            }
            ByteBuffer packed = ByteBuffer.allocate(SIZE * SIZE * Integer.BYTES);
            int[] pixels = new int[SIZE * SIZE];
            for (int i = 0; i < count; i++)
            {
                String name = in.readUTF();
                Instant time = Instant.ofEpochMilli(in.readLong());
                int world = in.readInt();
                WorldPoint tile = in.readBoolean() ? new WorldPoint(in.readInt(), in.readInt(), in.readInt()) : null;
                in.readFully(packed.array());
                packed.asIntBuffer().get(pixels);
                BufferedImage portrait = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                portrait.setRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
                result.put(name, new PlayerSighting(portrait, time, world, tile));
            }
        }
        catch (NoSuchFileException ignored)
        {
            // First launch.
        }
        catch (IOException ex)
        {
            LOG.warn("Could not read the Hardcore Death Cannon sighting cache", ex);
        }
        return result;
    }

    static PlayerSighting newest(PlayerSighting first, PlayerSighting second)
    {
        return first.time.isAfter(second.time) ? first : second;
    }

    // The lock and merge preserve newer sightings when two clients use the same cache.
    boolean save(Map<String, PlayerSighting> snapshot)
    {
        synchronized (SightingStore.class)
        {
            try
            {
                file.getParent().createDirectories();
                Filepath lockFile = file.getParent().join("sightings.lock");
                try (FileChannel channel = lockFile.openFileChannel(StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock lock = channel.lock())
                {
                    Map<String, PlayerSighting> combined = load();
                    snapshot.forEach((name, sighting) -> combined.merge(name, sighting, SightingStore::newest));
                    write(combined);
                }
                return true;
            }
            catch (IOException ex)
            {
                LOG.warn("Could not save the Hardcore Death Cannon sighting cache", ex);
                return false;
            }
        }
    }

    private void write(Map<String, PlayerSighting> sightings) throws IOException
    {
        List<Map.Entry<String, PlayerSighting>> newest = sightings.entrySet().stream()
            .sorted(Map.Entry.<String, PlayerSighting>comparingByValue((a, b) -> b.time.compareTo(a.time)))
            .limit(LIMIT)
            .collect(Collectors.toList());
        Filepath temporary = file.getParent().createTempFile("sightings-", ".tmp");
        try
        {
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(temporary.openOutputStream())))
            {
                out.writeInt(FORMAT);
                out.writeInt(newest.size());
                ByteBuffer packed = ByteBuffer.allocate(SIZE * SIZE * Integer.BYTES);
                int[] pixels = new int[SIZE * SIZE];
                // Write oldest first so the in-memory eviction order is preserved after launch.
                for (int i = newest.size() - 1; i >= 0; i--)
                {
                    Map.Entry<String, PlayerSighting> entry = newest.get(i);
                    out.writeUTF(entry.getKey());
                    PlayerSighting sighting = entry.getValue();
                    out.writeLong(sighting.time.toEpochMilli());
                    out.writeInt(sighting.world);
                    WorldPoint tile = sighting.tile;
                    out.writeBoolean(tile != null);
                    if (tile != null)
                    {
                        out.writeInt(tile.getX());
                        out.writeInt(tile.getY());
                        out.writeInt(tile.getPlane());
                    }
                    sighting.portrait.getRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
                    packed.asIntBuffer().put(pixels);
                    out.write(packed.array());
                }
            }
            try
            {
                temporary.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException ex)
            {
                temporary.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            temporary.deleteIfExists();
        }
    }
}

