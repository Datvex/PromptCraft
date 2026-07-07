package dev.promptcraft.client;

import dev.promptcraft.structure.PromptCraftStructure;
import dev.promptcraft.structure.StructureFlattener;
import dev.promptcraft.structure.StructureRotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public final class GhostPreviewState {
    private static volatile boolean active = false;
    private static PromptCraftStructure originalStructure;
    private static int rotationSteps = 0;

    private static Map<BlockPos, BlockState> cachedFlatBlocks;
    private static long cacheVersion = -1;
    private static long currentVersion = 0;

    // Кэш геометрии текущего поворота (чтобы не сканировать все блоки каждый кадр).
    private static int cachedCenterLocalX = 0;
    private static int cachedCenterLocalZ = 0;
    private static int cachedMinY = 0;

    private GhostPreviewState() {}

    public static void start(PromptCraftStructure structure) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || structure == null) return;
        originalStructure = structure;
        rotationSteps = 0;
        cachedFlatBlocks = null;
        currentVersion++;
        active = true;
    }

    public static void cancel() {
        active = false;
        originalStructure = null;
        cachedFlatBlocks = null;
    }

    public static boolean isActive() { return active; }

    public static void rotate() {
        if (!active) return;
        rotationSteps = (rotationSteps + 1) % 4;
        currentVersion++;
    }

    public static int getRotationSteps() { return rotationSteps; }

    /**
     * Мировые координаты локального угла (0,0,0) структуры.
     * Структура центрируется по горизонтали на блоке, куда смотрит игрок,
     * и ставится основанием на него. Если игрок смотрит в пустоту -
     * проецируем на несколько блоков вперёд по направлению взгляда.
     */
    public static BlockPos getCurrentAnchor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return BlockPos.ORIGIN;

        getFlattenedRotatedBlocks(); // гарантируем актуальный кэш bounds
        BlockPos target = resolveTargetBlock(client);

        return new BlockPos(
            target.getX() - cachedCenterLocalX,
            target.getY() - cachedMinY,
            target.getZ() - cachedCenterLocalZ
        );
    }

    private static BlockPos resolveTargetBlock(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hit;
            return bhr.getBlockPos().up(); // ставим на верх блока, на который смотрим
        }
        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f);
        double distance = 8.0;
        Vec3d projected = eye.add(look.x * distance, look.y * distance, look.z * distance);
        return BlockPos.ofFloored(projected);
    }

    public static Map<BlockPos, BlockState> getFlattenedRotatedBlocks() {
        if (originalStructure == null) return Map.of();
        if (cachedFlatBlocks == null || cacheVersion != currentVersion) {
            PromptCraftStructure rotated = StructureRotationUtil.rotate(originalStructure, rotationSteps);
            cachedFlatBlocks = StructureFlattener.flatten(rotated);
            cacheVersion = currentVersion;
            recomputeBounds();
        }
        return cachedFlatBlocks;
    }

    private static void recomputeBounds() {
        if (cachedFlatBlocks == null || cachedFlatBlocks.isEmpty()) {
            cachedCenterLocalX = cachedCenterLocalZ = cachedMinY = 0;
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cachedFlatBlocks.keySet()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        cachedCenterLocalX = (minX + maxX) / 2;
        cachedCenterLocalZ = (minZ + maxZ) / 2;
        cachedMinY = minY;
    }
}