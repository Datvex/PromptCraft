package dev.promptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GhostRenderer {
    private static final int MAX_DETAILED_BLOCKS = 20000;
    private static final float GHOST_ALPHA = 0.6f;

    private GhostRenderer() {}

    public static void render(WorldRenderContext context) {
        if (!GhostPreviewState.isActive()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Map<BlockPos, BlockState> blocks = GhostPreviewState.getFlattenedRotatedBlocks();
        if (blocks.isEmpty()) return;

        BlockPos anchor = GhostPreviewState.getCurrentAnchor();
        Vec3d cameraPos = context.camera().getPos();

        // Рисуем только видимую оболочку: пропускаем воздух и блоки, окружённые
        // со всех 6 сторон. Это убирает "кашу" из наложенных полупрозрачных
        // граней и сильно разгружает GPU на больших структурах.
        List<Map.Entry<BlockPos, BlockState>> visible = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            if (e.getValue().isAir()) continue;
            if (isEnclosed(e.getKey(), blocks)) continue;
            visible.add(e);
        }
        if (visible.isEmpty()) return;

        if (visible.size() > MAX_DETAILED_BLOCKS) {
            renderSimplifiedBox(context, blocks, anchor, cameraPos);
        } else {
            renderDetailed(client, context, visible, anchor, cameraPos);
        }
    }

    private static boolean isEnclosed(BlockPos p, Map<BlockPos, BlockState> blocks) {
        return present(blocks, p.up()) && present(blocks, p.down())
            && present(blocks, p.north()) && present(blocks, p.south())
            && present(blocks, p.east()) && present(blocks, p.west());
    }

    private static boolean present(Map<BlockPos, BlockState> blocks, BlockPos p) {
        BlockState s = blocks.get(p);
        return s != null && !s.isAir();
    }

    private static void renderDetailed(MinecraftClient client, WorldRenderContext context,
                                       List<Map.Entry<BlockPos, BlockState>> visible,
                                       BlockPos anchor, Vec3d cameraPos) {
        MatrixStack matrices = context.matrixStack();
        BlockRenderManager renderManager = client.getBlockRenderManager();
        int light = LightmapTextureManager.pack(15, 15);

        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumerProvider ghostProvider = new GhostVertexConsumerProvider(immediate, GHOST_ALPHA);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull(); // <- ключевой фикс: отсекаем задние грани

        for (Map.Entry<BlockPos, BlockState> entry : visible) {
            BlockState state = entry.getValue();
            BlockPos local = entry.getKey();

            matrices.push();
            matrices.translate(
                anchor.getX() + local.getX() - cameraPos.x,
                anchor.getY() + local.getY() - cameraPos.y,
                anchor.getZ() + local.getZ() - cameraPos.z
            );
            renderManager.renderBlockAsEntity(state, matrices, ghostProvider, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }

        immediate.draw(RenderLayer.getTranslucent());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderSimplifiedBox(WorldRenderContext context, Map<BlockPos, BlockState> blocks, BlockPos anchor, Vec3d cameraPos) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos p : blocks.keySet()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }

        Box box = new Box(
            anchor.getX() + minX, anchor.getY() + minY, anchor.getZ() + minZ,
            anchor.getX() + maxX + 1, anchor.getY() + maxY + 1, anchor.getZ() + maxZ + 1
        ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        var tessellator = net.minecraft.client.render.Tessellator.getInstance();
        var buffer = tessellator.getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);

        buffer.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS, net.minecraft.client.render.VertexFormats.POSITION_COLOR);

        float r = 1.0f, g = 1.0f, b = 1.0f, a = 0.25f;
        float minXf = (float) box.minX, minYf = (float) box.minY, minZf = (float) box.minZ;
        float maxXf = (float) box.maxX, maxYf = (float) box.maxY, maxZf = (float) box.maxZ;

        buffer.vertex(matrix, minXf, minYf, minZf).color(r, g, b, a).next();
        buffer.vertex(matrix, maxXf, minYf, minZf).color(r, g, b, a).next();
        buffer.vertex(matrix, maxXf, minYf, maxZf).color(r, g, b, a).next();
        buffer.vertex(matrix, minXf, minYf, maxZf).color(r, g, b, a).next();

        buffer.vertex(matrix, minXf, maxYf, maxZf).color(r, g, b, a).next();
        buffer.vertex(matrix, maxXf, maxYf, maxZf).color(r, g, b, a).next();
        buffer.vertex(matrix, maxXf, maxYf, minZf).color(r, g, b, a).next();
        buffer.vertex(matrix, minXf, maxYf, minZf).color(r, g, b, a).next();

        tessellator.draw();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}