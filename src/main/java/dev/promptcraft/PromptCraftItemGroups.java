package dev.promptcraft;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class PromptCraftItemGroups {
    public static final ItemGroup PROMPTCRAFT_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(PromptCraftItems.SELECTION_BRUSH))
            .displayName(Text.translatable("itemGroup.promptcraft.promptcraft"))
            .entries((context, entries) -> entries.add(PromptCraftItems.SELECTION_BRUSH))
            .build();

    private PromptCraftItemGroups() {
    }

    public static void register() {
        Registry.register(
                Registries.ITEM_GROUP,
                new Identifier(PromptCraftMod.MOD_ID, "promptcraft"),
                PROMPTCRAFT_GROUP
        );
    }
}
