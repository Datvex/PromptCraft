package dev.promptcraft.structure;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

public record BlockSnapshot(BlockPos pos, BlockState state, NbtCompound nbt) {}