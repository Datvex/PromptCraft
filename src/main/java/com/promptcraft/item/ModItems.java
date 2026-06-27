package com.promptcraft.item;

import com.promptcraft.PromptCraftMod;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item PROMPT_BRUSH = new PromptBrushItem(new FabricItemSettings().maxCount(1));

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier(PromptCraftMod.MOD_ID, "prompt_brush"), PROMPT_BRUSH);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(PROMPT_BRUSH);
        });
    }
}
