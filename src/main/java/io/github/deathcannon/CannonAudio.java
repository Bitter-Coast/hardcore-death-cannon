package io.github.deathcannon;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.runelite.client.audio.AudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CannonAudio
{
    private static final Logger LOG = LoggerFactory.getLogger(CannonAudio.class);
    private static final int RATE = 22050;
    private static final int BYTES_PER_SAMPLE = Short.BYTES;

    private final AudioPlayer player;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task ->
    {
        Thread thread = new Thread(task, "Hardcore Death Cannon audio");
        thread.setDaemon(true);
        return thread;
    });
    private final short[][] cues = new short[4][];

    CannonAudio(AudioPlayer player)
    {
        this.player = player;
    }

    void play(int volume, boolean cannonEnabled, boolean musicEnabled, int popupSeconds)
    {
        if (volume <= 0 || (!cannonEnabled && !musicEnabled))
        {
            return;
        }

        worker.execute(() ->
        {
            int mix = (cannonEnabled ? 1 : 0) | (musicEnabled ? 2 : 0);
            if (cues[mix] == null)
            {
                cues[mix] = synthesize(cannonEnabled, musicEnabled);
            }
            byte[] wav = encodeWave(cues[mix], volume, popupSeconds);
            if (Thread.currentThread().isInterrupted())
            {
                return;
            }
            try
            {
                player.play(new ByteArrayInputStream(wav), 0);
            }
            catch (Exception ex)
            {
                LOG.debug("Could not play the Hardcore Death Cannon cue", ex);
            }
        });
    }

    void close()
    {
        worker.shutdownNow();
    }

    static byte[] encodeWave(short[] samples, int volume, int popupSeconds)
    {
        int sampleCount = Math.min(samples.length, RATE * popupSeconds);
        int dataSize = sampleCount * BYTES_PER_SAMPLE;
        ByteBuffer wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[] {'R', 'I', 'F', 'F'}).putInt(36 + dataSize);
        wav.put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '}).putInt(16);
        wav.putShort((short) 1).putShort((short) 1); // PCM, mono
        wav.putInt(RATE).putInt(RATE * BYTES_PER_SAMPLE);
        wav.putShort((short) BYTES_PER_SAMPLE).putShort((short) (8 * BYTES_PER_SAMPLE));
        wav.put(new byte[] {'d', 'a', 't', 'a'}).putInt(dataSize);
        for (int i = 0; i < sampleCount; i++)
        {
            wav.putShort((short) (samples[i] * Math.min(volume, 100) / 100));
        }
        return wav.array();
    }

    static short[] synthesize(boolean cannonEnabled, boolean musicEnabled)
    {
        short[] samples = new short[RATE * 33 / 10];
        Random random = new Random(2907);
        double rumble = 0;
        for (int i = 0; i < samples.length; i++)
        {
            double t = (double) i / RATE;
            double noise = random.nextDouble() * 2 - 1;
            rumble = 0.88 * rumble + 0.12 * noise;
            double cannon = 0.49 * Math.sin(2 * Math.PI * (42 * t + 3 * (1 - Math.exp(-t * 16))))
                * Math.exp(-t * 7) + 0.30 * rumble * Math.exp(-t * 5)
                + 0.29 * noise * Math.exp(-t * 65);
            double music = note(t - 0.34, 146.83, 0.66, 0.18)
                + note(t - 0.78, 174.61, 0.58, 0.16)
                + note(t - 1.12, 220.00, 0.68, 0.17)
                + note(t - 1.57, 207.65, 0.53, 0.16)
                + note(t - 1.92, 146.83, 1.34, 0.19)
                + note(t - 1.92, 110.00, 1.30, 0.10)
                + note(t - 1.92, 174.61, 1.25, 0.10);
            double mix = (cannonEnabled ? cannon : 0) + (musicEnabled ? music : 0);
            samples[i] = (short) Math.round(Math.tanh(mix) * Short.MAX_VALUE);
        }
        return samples;
    }

    private static double note(double time, double frequency, double length, double gain)
    {
        if (time <= 0 || time >= length)
        {
            return 0;
        }

        double envelope = Math.min(1, time / 0.048) * Math.pow(1 - time / length, 1.4);
        double phase = 2 * Math.PI * frequency * time;
        return gain * envelope * (Math.sin(phase) + 0.27 * Math.sin(2 * phase) + 0.12 * Math.sin(3 * phase));
    }
}
