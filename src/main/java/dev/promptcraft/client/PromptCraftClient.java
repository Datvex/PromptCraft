package dev.promptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.promptcraft.PromptCraftMod;
import dev.promptcraft.client.gui.PromptCraftSettingsScreen;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.network.PromptCraftNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PromptCraftClient implements ClientModInitializer {
    private static BlockPos firstPos = null;
    private static BlockPos secondPos = null;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.SELECTION_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            boolean hasFirst = buf.readBoolean();
            BlockPos first = hasFirst ? buf.readBlockPos() : null;
            boolean hasSecond = buf.readBoolean();
            BlockPos second = hasSecond ? buf.readBlockPos() : null;
            client.execute(() -> { firstPos = first; secondPos = second; });
        });

        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.OPEN_GUI_PACKET, (client, handler, buf, responseSender) -> {
            String apiKey = buf.readString();
            String model = buf.readString();
            boolean showPreview = buf.readBoolean();
            client.execute(() -> client.setScreen(new PromptCraftSettingsScreen(apiKey, model, showPreview)));
        });

        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            
            // Reload config silently if needed to check preview status
            boolean showPreview = PromptCraftConfigManager.get().showSelectionPreview;

            BlockPos pos1 = firstPos;
            BlockPos pos2 = secondPos;

            // Dynamic preview logic
            if (showPreview && pos1 != null && pos2 == null) {
                HitResult hit = client.crosshairTarget;
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    pos2 = ((BlockHitResult) hit).getBlockPos();
                } else {
                    pos2 = pos1;
                }
            }

            if (pos1 == null || pos2 == null) return;

            Vec3d cameraPos = context.camera().getPos();
            Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            // Inclusive bounds: +1 to Max coordinates to fully enclose the blocks
            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
            int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

            Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ).expand(0.005).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            // Draw filled inner box
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            drawFilledBox(matrix, buffer, box, 22/255f, 91/255f, 212/255f, 0.4f);
            tessellator.draw();

            // Thicker outline
            RenderSystem.lineWidth(5.0f);
            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            WorldRenderer.drawBox(context.matrixStack(), buffer, box, 1.0f, 1.0f, 1.0f, 1.0f);
            tessellator.draw();

            RenderSystem.lineWidth(1.0f); // Reset
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        });
    }

    private void drawFilledBox(Matrix4f matrix, BufferBuilder buffer, Box box, float r, float g, float b, float a) {
        float minX = (float) box.minX; float minY = (float) box.minY; float minZ = (float) box.minZ;
        float maxX = (float) box.maxX; float maxY = (float) box.maxY; float maxZ = (float) box.maxZ;
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next(); buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next(); buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next(); buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next(); buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next(); buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next(); buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next(); buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next(); buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a).next(); buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a).next();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a).next(); buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a).next(); buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a).next();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a).next(); buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a).next();
    }
}
