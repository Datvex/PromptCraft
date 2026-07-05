package dev.promptcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.promptcraft.client.gui.PromptCraftSettingsScreen;
import dev.promptcraft.config.PromptCraftConfig;
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
        // Регистрация кнопки K (свободна в ваниле)
        net.minecraft.client.option.KeyBinding openMenuKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(new net.minecraft.client.option.KeyBinding(
                "key.promptcraft.open_menu",
                net.minecraft.client.util.InputUtil.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_K,
                "category.promptcraft"
        ));

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PromptCraftNetworking.REQUEST_OPEN_GUI_PACKET, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.SELECTION_SYNC_PACKET, (client, handler, buf, responseSender) -> {
            boolean hasFirst = buf.readBoolean();
            BlockPos first = hasFirst ? buf.readBlockPos() : null;
            boolean hasSecond = buf.readBoolean();
            BlockPos second = hasSecond ? buf.readBlockPos() : null;
            client.execute(() -> { firstPos = first; secondPos = second; });
        });

        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.AI_STREAM_PACKET, (client, handler, buf, responseSender) -> {
            String eventType = buf.readString();
            String payload = buf.readString();
            client.execute(() -> {
                switch (eventType) {
                    case "start" -> AiStreamState.reset();
                    case "reasoning" -> AiStreamState.append(payload);
                    case "done" -> AiStreamState.finish();
                    case "error" -> AiStreamState.fail(payload);
                    case "cancelled" -> AiStreamState.cancelled();
                    default -> {}
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.OPEN_GUI_PACKET, (client, handler, buf, responseSender) -> {
            String provider = buf.readString();
            
            int keyCount = buf.readInt();
            java.util.Map<String, String> apiKeys = new java.util.HashMap<>();
            for (int i = 0; i < keyCount; i++) {
                apiKeys.put(buf.readString(), buf.readString());
            }
            
            String model = buf.readString();
            boolean showPreview = buf.readBoolean();
            String language = buf.readString();
            String themeColor = buf.readString();
            boolean thickOutline = buf.readBoolean();
            float fillOpacity = buf.readFloat();
            boolean outlineThroughBlocks = buf.readBoolean();

            boolean selectionLimitEnabled = buf.readBoolean();
            int maxSelectionWidth = buf.readInt();
            int maxSelectionHeight = buf.readInt();
            int maxSelectionDepth = buf.readInt();

            client.execute(() -> client.setScreen(
                    new PromptCraftSettingsScreen(
                            provider,
                            apiKeys,
                            model,
                            showPreview,
                            language,
                            themeColor,
                            thickOutline,
                            fillOpacity,
                            outlineThroughBlocks,
                            selectionLimitEnabled,
                            maxSelectionWidth,
                            maxSelectionHeight,
                            maxSelectionDepth
                    )
            ));
        });        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            
            boolean showPreview = PromptCraftConfigManager.get().showSelectionPreview;
            if (!showPreview) return; // Если предпросмотр вообще выключен в настройках

            BlockPos pos1 = firstPos;
            BlockPos pos2 = secondPos;

            // Если выделена только первая точка
            if (pos1 != null && pos2 == null) {
                // Проверяем, держит ли игрок нашу кисть в главной или левой руке
                boolean holdingBrush = client.player.getMainHandStack().isOf(dev.promptcraft.PromptCraftItems.SELECTION_BRUSH) || 
                                       client.player.getOffHandStack().isOf(dev.promptcraft.PromptCraftItems.SELECTION_BRUSH);
                
                // Если кисти в руках нет — прерываем отрисовку
                if (!holdingBrush) return;

                HitResult hit = client.crosshairTarget;
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    pos2 = ((BlockHitResult) hit).getBlockPos();
                } else {
                    // Безопасный фоллбэк: проецируем на 5 блоков вперёд, если смотрим в воздух
                    Vec3d eyePos = client.player.getCameraPosVec(context.tickDelta());
                    Vec3d lookVec = client.player.getRotationVec(context.tickDelta());
                    pos2 = BlockPos.ofFloored(eyePos.add(lookVec.multiply(5.0D)));
                }
            }

            if (pos1 == null || pos2 == null) return;

            String hex = PromptCraftConfigManager.get().themeColor.replace("#", "");
            int color = 0x17b95f;
            try { color = Integer.parseInt(hex, 16); } catch (Exception ignored) {}
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            Vec3d cameraPos = context.camera().getPos();
            Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            int minX = Math.min(pos1.getX(), pos2.getX());
            int minY = Math.min(pos1.getY(), pos2.getY());
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
            int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

            double fillEpsilon = 0.025D;
            double outlineEpsilon = 0.018D;

            Box fillBox = new Box(
                    minX - fillEpsilon,
                    minY + 0.006D,
                    minZ - fillEpsilon,
                    maxX + fillEpsilon,
                    maxY + fillEpsilon,
                    maxZ + fillEpsilon
            ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            Box outlineBox = new Box(
                    minX - outlineEpsilon,
                    minY + 0.012D,
                    minZ - outlineEpsilon,
                    maxX + outlineEpsilon,
                    maxY + outlineEpsilon,
                    maxZ + outlineEpsilon
            ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            PromptCraftConfig config = PromptCraftConfigManager.get();

            float fillOpacity = Math.max(0.0f, Math.min(1.0f, config.selectionFillOpacity));

            if (fillOpacity > 0.0f) {
                buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                drawFilledBox(matrix, buffer, fillBox, r, g, b, fillOpacity);
                tessellator.draw();
            }

            boolean outlineThroughBlocks = config.selectionOutlineThroughBlocks;

            if (outlineThroughBlocks) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            float outlineThickness = config.thickSelectionOutline ? 0.035f : 0.008f;
            drawThickOutline(matrix, buffer, outlineBox, outlineThickness, r, g, b, 1.0f);

            tessellator.draw();

            RenderSystem.enableDepthTest();

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
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

    private void drawThickOutline(Matrix4f matrix, BufferBuilder buffer, Box box, float t, float r, float g, float b, float a) {
        float x1 = (float)box.minX, y1 = (float)box.minY, z1 = (float)box.minZ;
        float x2 = (float)box.maxX, y2 = (float)box.maxY, z2 = (float)box.maxZ;
        drawFilledBox(matrix, buffer, new Box(x1-t, y1-t, z1-t, x2+t, y1+t, z1+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x1-t, y1-t, z2-t, x2+t, y1+t, z2+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x1-t, y1-t, z1-t, x1+t, y1+t, z2+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x2-t, y1-t, z1-t, x2+t, y1+t, z2+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x1-t, y2-t, z1-t, x2+t, y2+t, z1+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x1-t, y2-t, z2-t, x2+t, y2+t, z2+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x1-t, y2-t, z1-t, x1+t, y2+t, z2+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x2-t, y2-t, z1-t, x2+t, y2+t, z2+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x1-t, y1-t, z1-t, x1+t, y2+t, z1+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x2-t, y1-t, z1-t, x2+t, y2+t, z1+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x1-t, y1-t, z2-t, x1+t, y2+t, z2+t), r, g, b, a);
        drawFilledBox(matrix, buffer, new Box(x2-t, y1-t, z2-t, x2+t, y2+t, z2+t), r, g, b, a);
    }
}
