package com.luzviva.client;

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

    /**
     * Un objeto emite luz si es un BlockItem cuyo bloque (en su estado por defecto)
     * tiene nivel de luz > 0 y se dibuja con un modelo normal.
     * Los bloques con renderizado propio (cofre del Ender, conducto...) se dejan como en vanilla.
     */
    public static boolean isLightItem(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
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
