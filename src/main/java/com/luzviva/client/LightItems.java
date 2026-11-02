package com.luzviva.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Decide que objetos cuentan como "objeto que genera luz". */
public final class LightItems {
    private LightItems() {
    }

    public static boolean isLightItem(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return false;
        }
        // Solo objetos de Minecraft vanilla: otros mods ya registran su propio renderer
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }
        Block block = blockItem.getBlock();
        if (block == Blocks.AIR || block == Blocks.LIGHT) {
            return false;
        }
        BlockState state = block.defaultBlockState();
        return state.getLightEmission() > 0 && state.getRenderShape() == RenderShape.MODEL;
    }
}
