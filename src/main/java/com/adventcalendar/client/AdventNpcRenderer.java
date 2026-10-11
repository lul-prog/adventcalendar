package com.adventcalendar.client;

import com.adventcalendar.entity.AdventNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Advent NPC with the player model so custom player skins work, including the
 * slim (Alex style) model. The model is swapped on the fly depending on the synced
 * {@code SkinModel} of the entity.
 */
public class AdventNpcRenderer extends LivingEntityRenderer<AdventNpcEntity, PlayerModel<AdventNpcEntity>> {
    private final PlayerModel<AdventNpcEntity> classic;
    private final PlayerModel<AdventNpcEntity> slim;

    public AdventNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.classic = this.model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(AdventNpcEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        this.model = entity.isSlim() ? this.slim : this.classic;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AdventNpcEntity entity) {
        ResourceLocation custom = SkinManager.textureFor(entity.getSkin());
        if (custom != null) {
            return custom;
        }
        return entity.isSlim() ? SkinManager.SLIM_ALEX : SkinManager.WIDE_STEVE;
    }
}
