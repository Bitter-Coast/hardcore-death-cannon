package io.github.deathcannon;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class DeathCannonLauncher
{
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DeathCannonPlugin.class);
        RuneLite.main(args);
    }
}
