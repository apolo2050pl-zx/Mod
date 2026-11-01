package com.luzviva.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dibuja en 3D (con el modelo real del bloque) cualquier objeto que emita luz y le da vida:
 *
 *  - LANTERN (faroles): cuelgan de una cadena. En la mano la cadena sale de tu mano y el
 *    farol se balancea segun tu movimiento. En el inventario / marco se mece solo.
 *  - FLAME (antorchas, hogueras, calabaza de Halloween): parpadeo de luz y de tamano + vaiven.
 *  - GLOW (glowstone, linterna marina, shroomlight...): pulso de luz y de tamano, leve balanceo.
 */
public class LightItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float DEG = (float) (Math.PI / 180.0);
    /** Tamano del brazo en 1a persona: unidades de modelo por pixel de skin (ajusta para brazo mas grande/pequeno). */
    private static final float ARM_PX = 2.5f / 16f;
    private static final Map<Block, AABB> BOUNDS = new HashMap<>();

    private enum Kind {
        LANTERN, FLAME, GLOW;

        static Kind of(BlockState state) {
            Block b = state.getBlock();
            if (b instanceof LanternBlock) {
                return LANTERN;
            }
            if (b instanceof BaseTorchBlock || b instanceof CampfireBlock || b instanceof CarvedPumpkinBlock) {
                return FLAME;
            }
            return GLOW;
        }
    }

    public LightItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModelSet());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf,
                             int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        Kind kind = Kind.of(state);
        float t = time();
        // Desfase por objeto para que no parpadeen todos a la vez.
        float phase = (BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode() & 1023) * 0.0137f;

        boolean hangs = kind == Kind.LANTERN
                && ctx != ItemDisplayContext.GROUND
                && ctx != ItemDisplayContext.HEAD
                && ctx != ItemDisplayContext.NONE;

        if (hangs) {
            renderHangingLantern(state, ctx, ps, buf, light, overlay, t, phase);
        } else {
            renderGeneric(state, kind, ctx, ps, buf, light, overlay, t, phase);
        }
    }

    // ------------------------------------------------------------------------------------
    //  FAROL COLGANDO DE UNA CADENA
    // ------------------------------------------------------------------------------------
    private void renderHangingLantern(BlockState lantern, ItemDisplayContext ctx, PoseStack ps,
                                      MultiBufferSource buf, int light, int overlay, float t, float phase) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher br = mc.getBlockRenderer();

        BlockState hanging = lantern.hasProperty(LanternBlock.HANGING)
                ? lantern.setValue(LanternBlock.HANGING, true) : lantern;
        BlockState chain = Blocks.CHAIN.defaultBlockState();

        // Brillo casi constante con un leve titileo.
        int level = 13 + Math.round(flicker(t * 0.6f, phase) * 2f);
        int packed = ctx == ItemDisplayContext.GUI ? light : boost(light, level);

        // Balanceo "de reposo" (siempre presente) ...
        float angF = 0.05f * Mth.sin(t * 1.9f + phase);
        float angR = 0.05f * Mth.sin(t * 1.3f + phase * 1.7f);
        float chainLen = 1f;   // largo de la cadena que cuelga (en bloques)
        int upperChain = 0;    // eslabones fijos POR ENCIMA de la mano

        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f); // origen = punto de agarre / centro del hueco

        if (isFirst(ctx)) {
            // El marco de primera persona esta alineado con la camara (+X der, +Y arriba, +Z hacia ti).
            float side = isLeft(ctx) ? -1f : 1f;
            ps.translate(-0.18f * side, 0.64f, 0f);   // sube el punto de agarre para que el farol quede a la vista
            ps.scale(0.4f, 0.4f, 0.4f);
            chainLen = 0.5f;
            if (mc.player != null) {
                // Brazo del jugador (con su skin) agarrando la parte alta de la cadena.
                ps.pushPose();
                ps.translate(0f, -0.12f, 0f);
                Vector3f dir = new Vector3f(0.5f * side, -0.65f, 0.57f).normalize(); // hacia el hombro (abajo-fuera de pantalla)
                ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), dir));
                ps.scale(ARM_PX, ARM_PX, ARM_PX);
                HandMesh.render(ps, buf, light, overlay, mc.player, isLeft(ctx), 12f);
                ps.popPose();

                SwingTracker.State s = SwingTracker.get(mc.player);
                angF += s.angF;
                angR += s.angR;
                chainLen = Math.max(0.3f, chainLen + s.stretch);
            }
        } else if (isThird(ctx)) {
            // Marco de la mano en 3a persona: +Y = adelante, +Z = hacia el hombro.
            // Girando 90 grados en X quedamos en un marco "camara": +X der, +Y arriba, +Z atras.
            ps.mulPose(Axis.XP.rotationDegrees(90f));
            ps.scale(0.3f, 0.3f, 0.3f);
            LivingEntity holder = SwingTracker.holder() != null ? SwingTracker.holder() : mc.player;
            if (holder != null) {
                SwingTracker.State s = SwingTracker.get(holder);
                angF += s.angF;
                angR += s.angR;
                chainLen = Math.max(0.35f, chainLen + s.stretch);
            }
        } else if (ctx == ItemDisplayContext.GUI) {
            ps.mulPose(new Quaternionf().rotationXYZ(30f * DEG, 225f * DEG, 0f));
            ps.scale(0.62f, 0.62f, 0.62f);
            ps.translate(0f, 0.72f, 0f); // centra el conjunto cadena+farol
            chainLen = 0.5f;             // cadena corta para que quepa en el hueco
            angF += 0.14f * Mth.sin(t * 2.2f + phase);
            angR += 0.10f * Mth.sin(t * 1.7f + phase + 1.3f);
        } else { // FIXED (marco de objetos)
            ps.scale(0.5f, 0.5f, 0.5f);
            ps.translate(0f, 0.72f, 0f);
            chainLen = 0.5f;
            angF += 0.12f * Mth.sin(t * 2.0f + phase);
            angR += 0.09f * Mth.sin(t * 1.5f + phase + 1.3f);
        }

        // Eslabones fijos por encima del agarre (no se balancean).
        for (int i = 0; i < upperChain; i++) {
            ps.pushPose();
            ps.translate(-0.5f, i, -0.5f);
            br.renderSingleBlock(chain, ps, buf, packed, overlay);
            ps.popPose();
        }

        // Todo lo que cuelga rota alrededor del punto de agarre (el pivote del pendulo).
        ps.mulPose(Axis.ZP.rotation(angR));
        ps.mulPose(Axis.XP.rotation(angF));

        // Cadena (de y=0 hacia abajo).
        ps.pushPose();
        ps.translate(-0.5f, -chainLen, -0.5f);
        ps.scale(1f, chainLen, 1f);
        br.renderSingleBlock(chain, ps, buf, packed, overlay);
        ps.popPose();

        // Farol colgante justo debajo de la cadena.
        ps.pushPose();
        ps.translate(-0.5f, -chainLen - 1f, -0.5f);
        br.renderSingleBlock(hanging, ps, buf, packed, overlay);
        ps.popPose();

        ps.popPose();
    }

    // ------------------------------------------------------------------------------------
    //  RESTO DE OBJETOS LUMINOSOS
    // ------------------------------------------------------------------------------------
    private void renderGeneric(BlockState state, Kind kind, ItemDisplayContext ctx, PoseStack ps,
                               MultiBufferSource buf, int light, int overlay, float t, float phase) {
        boolean flame = kind != Kind.GLOW;

        AABB b = bounds(state);
        float maxDim = (float) Math.max(b.getXsize(), Math.max(b.getYsize(), b.getZsize()));
        float fit = Mth.clamp(1f / Math.max(maxDim, 0.05f), 1f, 1.5f); // agranda objetos delgados (antorchas...)
        float cx = (float) ((b.minX + b.maxX) / 2.0);
        float cy = (float) ((b.minY + b.maxY) / 2.0);
        float cz = (float) ((b.minZ + b.maxZ) / 2.0);

        float f = flame ? flicker(t, phase) : 0.5f + 0.5f * Mth.sin(t * 1.6f + phase); // 0..1
        float depth = flame ? 4f : 2f;
        int level = Mth.clamp(state.getLightEmission() - Math.round((1f - f) * depth), 0, 15);
        int packed = ctx == ItemDisplayContext.GUI ? light : boost(light, level);
        float pulse = 1f + (f - 0.5f) * (flame ? 0.06f : 0.05f);
        float sway = (flame ? 0.05f : 0.02f) * Mth.sin(t * 2.3f + phase);

        ps.pushPose();
        ps.translate(0.5f, 0.5f, 0.5f);

        switch (ctx) {
            case GUI -> {
                ps.mulPose(new Quaternionf().rotationXYZ(30f * DEG, (225f + 10f * Mth.sin(t * 1.1f + phase)) * DEG, 0f));
                float s = 0.625f * fit;
                ps.scale(s, s, s);
            }
            case GROUND -> {
                ps.translate(0f, 0.19f, 0f);
                float s = 0.25f * Math.min(fit, 1.4f);
                ps.scale(s, s, s);
            }
            case FIXED -> {
                float s = 0.5f * Math.min(fit, 1.3f);
                ps.scale(s, s, s);
            }
            case HEAD -> {
                ps.translate(0f, 13f / 16f, 0f);
                ps.mulPose(Axis.YP.rotationDegrees(180f));
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                ps.mulPose(Axis.YP.rotationDegrees(isLeft(ctx) ? -45f : 45f));
                ps.mulPose(Axis.ZP.rotation(sway));
                float s = 0.4f * Math.min(fit, 1.3f);
                ps.scale(s, s, s);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                ps.translate(0f, 2.5f / 16f, 0f);
                ps.mulPose(new Quaternionf().rotationXYZ(75f * DEG, (isLeft(ctx) ? -45f : 45f) * DEG, 0f));
                ps.mulPose(Axis.ZP.rotation(sway));
                float s = 0.375f * Math.min(fit, 1.3f);
                ps.scale(s, s, s);
            }
            default -> {
            }
        }

        ps.scale(pulse, pulse, pulse);
        ps.translate(-cx, -cy, -cz); // centra la caja real del modelo

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ps, buf, packed, overlay);
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------
    //  Utilidades
    // ------------------------------------------------------------------------------------
    private static AABB bounds(BlockState state) {
        return BOUNDS.computeIfAbsent(state.getBlock(), blk -> {
            VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        });
    }

    private static float time() {
        return (float) ((System.nanoTime() / 1_000_000L) % 3_600_000L) / 1000f;
    }

    /** Ruido pseudo-aleatorio suave en [0,1] (suma de senos). */
    private static float flicker(float t, float p) {
        float v = 0.5f
                + 0.22f * Mth.sin(t * 11.3f + p)
                + 0.16f * Mth.sin(t * 19.7f + p * 2.1f)
                + 0.12f * Mth.sin(t * 37.1f + p * 3.3f);
        return Mth.clamp(v, 0f, 1f);
    }

    /** Sube la luz de bloque al menos a "level" (el objeto brilla aunque estes a oscuras). */
    private static int boost(int packed, int level) {
        return LightTexture.pack(Math.max(LightTexture.block(packed), level), LightTexture.sky(packed));
    }

    private static boolean isFirst(ItemDisplayContext c) {
        return c == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || c == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private static boolean isThird(ItemDisplayContext c) {
        return c == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || c == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static boolean isLeft(ItemDisplayContext c) {
        return c == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || c == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}
