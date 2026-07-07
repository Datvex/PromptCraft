package dev.promptcraft.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

public class GhostVertexConsumerProvider implements VertexConsumerProvider {
    private final VertexConsumerProvider.Immediate delegate;
    private final float alpha;

    public GhostVertexConsumerProvider(VertexConsumerProvider.Immediate delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        // Принудительно рисуем всё через translucent-слой, чтобы блендинг альфы работал
        // независимо от того, к какому слою (solid/cutout/translucent) относится блок в реальности.
        VertexConsumer real = delegate.getBuffer(RenderLayer.getTranslucent());
        return new AlphaOverrideVertexConsumer(real, alpha);
    }
}