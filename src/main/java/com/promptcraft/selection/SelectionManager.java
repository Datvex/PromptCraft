package com.promptcraft.selection;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private static final Map<UUID, BlockPos> firstPoints = new HashMap<>();
    private static final Map<UUID, BlockPos> secondPoints = new HashMap<>();

    public static void setFirstPoint(PlayerEntity player, BlockPos pos) {
        firstPoints.put(player.getUuid(), pos.toImmutable());
        player.sendMessage(Text.literal("§b[PromptCraft] §aFirst point set: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
    }

    public static void setSecondPoint(PlayerEntity player, BlockPos pos) {
        secondPoints.put(player.getUuid(), pos.toImmutable());
        player.sendMessage(Text.literal("§b[PromptCraft] §aSecond point set: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
    }

    public static BlockPos getFirstPoint(PlayerEntity player) {
        return firstPoints.get(player.getUuid());
    }

    public static BlockPos getSecondPoint(PlayerEntity player) {
        return secondPoints.get(player.getUuid());
    }

    public static boolean hasCompleteSelection(PlayerEntity player) {
        return firstPoints.containsKey(player.getUuid()) && secondPoints.containsKey(player.getUuid());
    }

    public static Box getSelectionBox(PlayerEntity player) {
        BlockPos p1 = firstPoints.get(player.getUuid());
        BlockPos p2 = secondPoints.get(player.getUuid());
        if (p1 == null || p2 == null) return null;
        return new Box(
            Math.min(p1.getX(), p2.getX()),
            Math.min(p1.getY(), p2.getY()),
            Math.min(p1.getZ(), p2.getZ()),
            Math.max(p1.getX(), p2.getX()) + 1,
            Math.max(p1.getY(), p2.getY()) + 1,
            Math.max(p1.getZ(), p2.getZ()) + 1
        );
    }

    public static Vec3i getSelectionSize(PlayerEntity player) {
        BlockPos p1 = firstPoints.get(player.getUuid());
        BlockPos p2 = secondPoints.get(player.getUuid());
        if (p1 == null || p2 == null) return null;
        return new Vec3i(
            Math.abs(p1.getX() - p2.getX()) + 1,
            Math.abs(p1.getY() - p2.getY()) + 1,
            Math.abs(p1.getZ() - p2.getZ()) + 1
        );
    }

    public static BlockPos getSelectionMin(PlayerEntity player) {
        BlockPos p1 = firstPoints.get(player.getUuid());
        BlockPos p2 = secondPoints.get(player.getUuid());
        if (p1 == null || p2 == null) return null;
        return new BlockPos(
            Math.min(p1.getX(), p2.getX()),
            Math.min(p1.getY(), p2.getY()),
            Math.min(p1.getZ(), p2.getZ())
        );
    }

    public static void clearSelection(PlayerEntity player) {
        firstPoints.remove(player.getUuid());
        secondPoints.remove(player.getUuid());
    }
}
