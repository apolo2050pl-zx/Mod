package com.luzviva.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/**
 * Envuelve el modelo original del objeto para que Minecraft use nuestro renderer 3D
 * en lugar del sprite plano / modelo estatico, sin necesidad de un JSON por objeto.
 */
public class LightBakedModel extends BakedModelWrapper<BakedModel> {
    public LightBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true; // iluminacion 3D en el inventario
    }

    /**
     * NO aplicamos las transformaciones del JSON original (un sprite plano no sirve para
     * un modelo 3D). Devolvemos "this" para conservar isCustomRenderer(); el renderer
     * coloca el objeto por su cuenta segun el contexto (mano, GUI, suelo...).
     */
    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        return this;
    }
}
