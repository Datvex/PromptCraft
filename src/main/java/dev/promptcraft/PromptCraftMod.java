package dev.promptcraft;

import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.task.TaskManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptCraftMod implements ModInitializer {
    public static final String MOD_ID = "promptcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PromptCraft...");
        PromptCraftConfigManager.load();
        PromptCraftItems.register();
        PromptCraftItemGroups.register();
        PromptCraftCommands.register();
        TaskManager.init();
        LOGGER.info("PromptCraft initialized.");
    }
}
