package io.github.deathcannon;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CannonAudio
{
    private static final Logger LOG = LoggerFactory.getLogger(CannonAudio.class);
    private static final int RATE = 22050;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task ->
    {
        Thread thread = new Thread(task, "Hardcore Death Cannon audio");
        thread.setDaemon(true);
        return thread;
    });

    private Clip clip;
    private final short[][] cues = new short[4][];

    void play(int volume, boolean cannonEnabled, boolean musicEnabled)
    {
        worker.execute(() ->
        {
            closeClip();
            if (volume <= 0 || (!cannonEnabled && !musicEnabled))
            {
                return;
            }

            int mix = (cannonEnabled ? 1 : 0) | (musicEnabled ? 2 : 0);
            if (cues[mix] == null)
            {
                cues[mix] = synthesize(cannonEnabled, musicEnabled);
            }
            short[] cue = cues[mix];
            byte[] pcm = new byte[cue.length * 2];
            for (int i = 0; i < cue.length; i++)
            {
                int sample = cue[i] * Math.min(volume, 100) / 100;
                pcm[2 * i] = (byte) sample;
                pcm[2 * i + 1] = (byte) (sample >> 8);
            }
            try
            {
                clip = AudioSystem.getClip();
                clip.open(FORMAT, pcm, 0, pcm.length);
                clip.start();
            }
            catch (LineUnavailableException | IllegalArgumentException ex)
            {
                closeClip();
                LOG.debug("Could not play the Hardcore Death Cannon cue", ex);
            }
        });
    }

    void stop()
    {
        worker.execute(this::closeClip);
    }

    void close()
    {
        stop();
        worker.shutdown();
    }

    private void closeClip()
    {
        if (clip != null)
        {
            clip.close();
            clip = null;
        }
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
