package dev.promptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.promptcraft.PromptCraftMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class PromptCraftClient implements ClientModInitializer {
    public static final Identifier SELECTION_SYNC_PACKET = new Identifier(PromptCraftMod.MOD_ID, "selection_sync");

    private static BlockPos firstPos = null;
    private static BlockPos secondPos = null;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SELECTION_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            boolean hasFirst = buf.readBoolean();
            BlockPos first = hasFirst ? buf.readBlockPos() : null;
            boolean hasSecond = buf.readBoolean();
            BlockPos second = hasSecond ? buf.readBlockPos() : null;

            client.execute(() -> {
                firstPos = first;
                secondPos = second;
            });
        });

        WorldRenderEvents.LAST.register(context -> {
            if (firstPos == null && secondPos == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            Vec3d cameraPos = context.camera().getPos();
            Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            BlockPos pos1 = firstPos != null ? firstPos : secondPos;
            BlockPos pos2 = secondPos != null ? secondPos : firstPos;
            Box box = new Box(pos1, pos2).expand(0.01).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            // Draw filled inner box (#165bd4 with alpha)
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            drawFilledBox(matrix, buffer, box, 22/255f, 91/255f, 212/255f, 0.4f);
            tessellator.draw();

            // Draw white outline
            RenderSystem.lineWidth(2.0f);
            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            WorldRenderer.drawBox(context.matrixStack(), buffer, box, 1.0f, 1.0f, 1.0f, 1.0f);
            tessellator.draw();

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
