package com.luzviva.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Dibuja un brazo (caja 4x12x4, o 3 de ancho en skins Alex) usando la skin REAL del jugador.
 * Origen del dibujo: extremo de la mano en (0,0,0); el brazo crece hacia +Y (hacia el hombro).
 * Unidades: pixeles de skin (el llamador escala el PoseStack).
 */
final class HandMesh {
    private static final float TEX = 64f;

    private HandMesh() {
    }

    static void render(PoseStack ps, MultiBufferSource buf, int light, int overlay,
                       AbstractClientPlayer player, boolean left, float lengthPx) {
        PlayerSkin skin = player.getSkin();
        boolean slim = skin.model() == PlayerSkin.Model.SLIM;
        float w = slim ? 3f : 4f, d = 4f, h = 12f;
        float u0 = left ? 32f : 40f;
        float v0 = left ? 48f : 16f;
        float len = Math.min(lengthPx, h);

        float x0 = -w / 2f, x1 = w / 2f, z0 = -d / 2f, z1 = d / 2f, L = len;
        float vb = v0 + d + h;   // v del extremo de la mano
        float vt = vb - len;     // v del extremo del hombro

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(skin.texture()));
        PoseStack.Pose pose = ps.last();

        // frontal (-Z)
        quad(vc, pose, light, overlay, x1, L, z0, -w, 0, 0, 0, -L, 0, u0 + d, vt, u0 + d + w, vb, 0, 0, -1);
        // trasera (+Z)
        quad(vc, pose, light, overlay, x0, L, z1, w, 0, 0, 0, -L, 0, u0 + d + w + d, vt, u0 + d + w + d + w, vb, 0, 0, 1);
        // lado exterior (+X)
        quad(vc, pose, light, overlay, x1, L, z1, 0, 0, -d, 0, -L, 0, u0, vt, u0 + d, vb, 1, 0, 0);
        // lado interior (-X)
        quad(vc, pose, light, overlay, x0, L, z0, 0, 0, d, 0, -L, 0, u0 + d + w, vt, u0 + d + w + d, vb, -1, 0, 0);
        // extremo del hombro (+Y)
        quad(vc, pose, light, overlay, x1, L, z1, -w, 0, 0, 0, 0, -d, u0 + d, v0, u0 + d + w, v0 + d, 0, 1, 0);
        // extremo de la mano (-Y)
        quad(vc, pose, light, overlay, x0, 0, z0, w, 0, 0, 0, 0, d, u0 + d + w, v0, u0 + d + w + w, v0 + d, 0, -1, 0);
    }

    /** Cuadrilatero: O, O+A, O+A+B, O+B con UV (ua0,va0) -> (ua1,va1). */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, int overlay,
                             float ox, float oy, float oz,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float ua0, float va0, float ua1, float va1,
                             float nx, float ny, float nz) {
        vertex(vc, pose, light, overlay, ox, oy, oz, ua0, va0, nx, ny, nz);
        vertex(vc, pose, light, overlay, ox + ax, oy + ay, oz + az, ua1, va0, nx, ny, nz);
        vertex(vc, pose, light, overlay, ox + ax + bx, oy + ay + by, oz + az + bz, ua1, va1, nx, ny, nz);
        vertex(vc, pose, light, overlay, ox + bx, oy + by, oz + bz, ua0, va1, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int light, int overlay,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u / TEX, v / TEX)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
