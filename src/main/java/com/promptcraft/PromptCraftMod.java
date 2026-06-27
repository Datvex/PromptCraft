package com.promptcraft;

import com.promptcraft.command.*;
import com.promptcraft.config.PromptCraftConfig;
import com.promptcraft.item.ModItems;
import com.promptcraft.session.SessionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptCraftMod implements ModInitializer {
    public static final String MOD_ID = "promptcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PromptCraftConfig.load();
        ModItems.register();
        SessionManager.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            new PromptCraftCommand().register(dispatcher);
            new PromptSettingsCommand().register(dispatcher);
            new PromptUndoCommand().register(dispatcher);
            new PromptBackCommand().register(dispatcher);
            new PromptNextCommand().register(dispatcher);
            new PromptEditCommand().register(dispatcher);
        });

        LOGGER.info("PromptCraft initialized.");
    }
}
