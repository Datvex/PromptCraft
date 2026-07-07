package dev.promptcraft.client;

import dev.promptcraft.structure.PromptCraftStructure;
import dev.promptcraft.structure.StructureFlattener;
import dev.promptcraft.structure.StructureRotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public final class GhostPreviewState {
    private static final double MIN_DISTANCE = 2.0;
    private static final double MAX_DISTANCE = 160.0;

    private static volatile boolean active = false;
    private static PromptCraftStructure originalStructure;
    private static int rotationSteps = 0;

    private static Map<BlockPos, BlockState> cachedFlatBlocks;
    private static long cacheVersion = -1;
    private static long currentVersion = 0;

    private static int cachedCenterLocalX = 0;
    private static int cachedCenterLocalZ = 0;
    private static int cachedMinY = 0;
    private static int cachedHalfX = 0;
    private static int cachedHalfZ = 0;

    private static volatile double previewDistance = 8.0;

    private GhostPreviewState() {}

    public static void start(PromptCraftStructure structure) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || structure == null) return;
        originalStructure = structure;
        rotationSteps = 0;
        cachedFlatBlocks = null;
        currentVersion++;
        active = true;
        getFlattenedRotatedBlocks();
        previewDistance = clampDistance(Math.max(cachedHalfX, cachedHalfZ) + 8.0);
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

    /** Приблизить/отдалить предпросмотр колесиком. delta>0 = ближе к игроку. */
    public static void addDistance(double delta) {
        if (!active) return;
        previewDistance = clampDistance(previewDistance + delta);
    }

    private static double clampDistance(double d) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, d));
    }

    /**
     * Локальный угол (0,0,0) структуры. Точка проецируется вдоль взгляда игрока
     * на дистанцию previewDistance -> следует по горизонтали и высоте, колесо зумит.
     */
    public static BlockPos getCurrentAnchor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return BlockPos.ORIGIN;

        getFlattenedRotatedBlocks();

        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0f);
        Vec3d point = eye.add(look.multiply(previewDistance));
        BlockPos target = BlockPos.ofFloored(point);

        return new BlockPos(
            target.getX() - cachedCenterLocalX,
            target.getY() - cachedMinY,
            target.getZ() - cachedCenterLocalZ
        );
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
            cachedHalfX = cachedHalfZ = 0;
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
        cachedHalfX = (maxX - minX) / 2;
        cachedHalfZ = (maxZ - minZ) / 2;
    }
}