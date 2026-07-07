package dev.promptcraft.client;

import net.minecraft.client.render.VertexConsumer;

/** Оборачивает VertexConsumer, принудительно подменяя альфа-канал на фиксированную прозрачность. */
public class AlphaOverrideVertexConsumer implements VertexConsumer {
    private final VertexConsumer parent;
    private final int forcedAlpha;

    public AlphaOverrideVertexConsumer(VertexConsumer parent, float alpha) {
        this.parent = parent;
        this.forcedAlpha = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        parent.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        parent.color(red, green, blue, forcedAlpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        parent.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        parent.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        parent.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        parent.normal(x, y, z);
        return this;
    }

    @Override
    public void next() {
        parent.next();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {
        parent.fixedColor(red, green, blue, forcedAlpha);
    }

    @Override
    public void unfixColor() {
        parent.unfixColor();
    }
}