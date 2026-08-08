package net.runelite.client.plugins.microbot.quinnmain;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

/**
 * RuneLite/Microbot plugin entry point for the Quinn Main port.
 *
 * <p>This is the Microbot equivalent of OSRS-Main's {@code QuinnMain extends AbstractScript}: RuneLite
 * discovers the plugin via {@link PluginDescriptor}, the user enables it in the plugin list, and
 * {@link #startUp()} kicks off {@link QuinnMainScript}'s scheduled loop.
 *
 * <p>Build: drop this whole {@code quinnmain} package into a Microbot fork under
 * {@code runelite-client/src/main/java/net/runelite/client/plugins/microbot/} and build the client.
 */
@PluginDescriptor(
        name = PluginConstants.DEFAULT + "Quinn Main",
        description = "All-skills weighted trainer + combat + quests + money (DreamBot port).",
        tags = {"skilling", "combat", "quests", "money", "quinn"},
        enabledByDefault = false
)
public class QuinnMainPlugin extends Plugin {

    @Inject private QuinnMainConfig config;
    private final QuinnMainScript script = new QuinnMainScript();

    @Provides
    QuinnMainConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(QuinnMainConfig.class);
    }

    @Override
    protected void startUp() {
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
    }
}
