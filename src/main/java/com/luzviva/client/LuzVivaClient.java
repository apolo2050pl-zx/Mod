package com.luzviva.client;

import com.luzviva.LuzViva;
import java.util.Map;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = LuzViva.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LuzVivaClient {
    private static LightItemRenderer renderer;

    private LuzVivaClient() {
    }

    /** Asigna nuestro renderer a TODOS los objetos que emiten luz (vanilla y de otros mods). */
    @SubscribeEvent
    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions extensions = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new LightItemRenderer();
                }
                return renderer;
            }
        };

        for (Item item : BuiltInRegistries.ITEM) {
            if (LightItems.isLightItem(item)) {
                event.registerItem(extensions, item);
            }
        }
    }

    /** Marca los modelos de esos objetos como "renderer propio". */
    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!LightItems.isLightItem(item)) {
                continue;
            }
            ModelResourceLocation key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
            BakedModel original = models.get(key);
            if (original != null && !original.isCustomRenderer()) {
                models.put(key, new LightBakedModel(original));
            }
        }
    }
}
