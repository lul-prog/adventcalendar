package com.adventcalendar.client;

import com.adventcalendar.entity.AdventNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer del NPC con PlayerModel, para que las skins de jugador
 * (64x64, brazos/piernas de jugador) se vean correctamente.
 */
public class AdventNpcRenderer extends LivingEntityRenderer<AdventNpcEntity, PlayerModel<AdventNpcEntity>> {
    public AdventNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(AdventNpcEntity entity) {
        return SkinManager.getSkinTexture(entity.getSkin());
    }
}
