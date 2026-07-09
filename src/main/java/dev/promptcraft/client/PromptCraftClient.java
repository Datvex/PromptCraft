package dev.promptcraft.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.promptcraft.client.gui.PlacementConfirmScreen;
import dev.promptcraft.client.gui.PromptCraftSettingsScreen;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftLang;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.structure.PromptCraftStructure;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class PromptCraftClient implements ClientModInitializer {
    private static BlockPos firstPos = null;
    private static BlockPos secondPos = null;

    private static boolean scrollHookInstalled = false;

    private static KeyBinding rotateGhostKey;
    private static KeyBinding confirmPlacementKey;

    @Override
    public void onInitializeClient() {
        KeyBinding openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.promptcraft.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.promptcraft"
        ));

        rotateGhostKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.promptcraft.rotate_ghost",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.promptcraft"
        ));

        confirmPlacementKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.promptcraft.confirm_placement",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                "category.promptcraft"
        ));

        // При заходе в мир подхватываем язык интерфейса Minecraft: русский -> ru,
        // любой другой -> en. Одна проверка на join, нагрузки нет.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String mcLang = client.getLanguageManager().getLanguage();
            String detected = (mcLang != null && mcLang.toLowerCase().startsWith("ru")) ? "ru" : "en";
            if (!detected.equals(PromptCraftConfigManager.get().language)) {
                PromptCraftConfigManager.get().language = detected;
                PromptCraftConfigManager.save();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            installScrollHookIfNeeded(client);

            while (openMenuKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    ClientPlayNetworking.send(PromptCraftNetworking.REQUEST_OPEN_GUI_PACKET, PacketByteBufs.create());
                }
            }

            while (rotateGhostKey.wasPressed()) {
                if (GhostPreviewState.isActive()) {
                    GhostPreviewState.rotate();
                }
            }

            while (confirmPlacementKey.wasPressed()) {
                if (GhostPreviewState.isActive() && client.currentScreen == null) {
                    BlockPos anchor = GhostPreviewState.getCurrentAnchor();
                    int rotation = GhostPreviewState.getRotationSteps();
                    client.setScreen(new PlacementConfirmScreen(anchor, rotation));
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

        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.START_FREE_PLACEMENT_PACKET, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                try {
                    PromptCraftStructure structure = new Gson().fromJson(json, PromptCraftStructure.class);
                    GhostPreviewState.start(structure);
                } catch (Exception ignored) {
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.CANCEL_FREE_PLACEMENT_PACKET, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                GhostPreviewState.cancel();
                if (client.currentScreen instanceof PlacementConfirmScreen) {
                    client.setScreen(null);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(PromptCraftNetworking.BUILD_PROGRESS_PACKET, (client, handler, buf, responseSender) -> {
            int percent = buf.readInt();
            boolean active = buf.readBoolean();
            client.execute(() -> {
                if (active) {
                    BuildProgressState.update(percent);
                } else if (percent >= 100) {
                    BuildProgressState.complete();
                } else {
                    BuildProgressState.hide();
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
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!GhostPreviewState.isActive()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen != null) return;

            String keyName = rotateGhostKey.getBoundKeyLocalizedText().getString();
            String hint = PromptCraftLang.t("Press ", "Нажмите ")
                + keyName
                + PromptCraftLang.t(" to rotate the structure", ", чтобы повернуть структуру");

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int textWidth = client.textRenderer.getWidth(hint);
            int x = (screenWidth - textWidth) / 2;
            int y = screenHeight - 40;

            drawContext.drawTextWithShadow(client.textRenderer, hint, x, y, 0xFFFFFF);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!BuildProgressState.isVisible()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen != null) return;

            int percent = BuildProgressState.getPercent();
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int barWidth = 160;
            int barHeight = 9;
            int barX = (screenWidth - barWidth) / 2;
            int barY = screenHeight - 52;

            String hex = PromptCraftConfigManager.get().themeColor.replace("#", "");
            int rgb = 0x17b95f;
            try { rgb = Integer.parseInt(hex, 16); } catch (Exception ignored) {}
            int fillColor = 0xFF000000 | rgb;

            String label = PromptCraftLang.t("Building structure", "Строительство структуры");
            drawContext.drawTextWithShadow(client.textRenderer, label, barX, barY - 12, 0xFFFFFFFF);

            // рамка + фон трека
            drawContext.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
            drawContext.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF2B2B2B);

            // заполнение
            int fillW = Math.max(0, Math.min(barWidth, (int) (barWidth * (percent / 100.0f))));
            drawContext.fill(barX, barY, barX + fillW, barY + barHeight, fillColor);

            // проценты справа
            String pct = percent + "%";
            drawContext.drawTextWithShadow(client.textRenderer, pct, barX + barWidth + 6, barY + 1, 0xFFFFFFFF);
        });

        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            GhostRenderer.render(context);

            boolean showPreview = PromptCraftConfigManager.get().showSelectionPreview;
            if (!showPreview) return;

            BlockPos pos1 = firstPos;
            BlockPos pos2 = secondPos;

            if (pos1 != null && pos2 == null) {
                boolean holdingBrush = client.player.getMainHandStack().isOf(dev.promptcraft.PromptCraftItems.SELECTION_BRUSH) ||
                                       client.player.getOffHandStack().isOf(dev.promptcraft.PromptCraftItems.SELECTION_BRUSH);

                if (!holdingBrush) return;

                HitResult hit = client.crosshairTarget;
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    pos2 = ((BlockHitResult) hit).getBlockPos();
                } else {
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

    private static void installScrollHookIfNeeded(MinecraftClient client) {
        if (scrollHookInstalled || client.getWindow() == null) return;
        long handle = client.getWindow().getHandle();

        // Чейнимся к прежнему колбэку Minecraft, чтобы вне режима предпросмотра
        // колесо работало штатно (хотбар и т.д.).
        org.lwjgl.glfw.GLFWScrollCallbackI[] previous = new org.lwjgl.glfw.GLFWScrollCallbackI[1];
        org.lwjgl.glfw.GLFWScrollCallback ours = org.lwjgl.glfw.GLFWScrollCallback.create((win, dx, dy) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (GhostPreviewState.isActive() && mc.currentScreen == null) {
                GhostPreviewState.addDistance(dy * 2.0); // вверх = дальше, вниз = ближе
                return; // не листаем хотбар во время позиционирования
            }
            if (previous[0] != null) previous[0].invoke(win, dx, dy);
        });

        previous[0] = org.lwjgl.glfw.GLFW.glfwSetScrollCallback(handle, ours);
        scrollHookInstalled = true;
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