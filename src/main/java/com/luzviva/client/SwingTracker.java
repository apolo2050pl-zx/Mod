package com.luzviva.client;

import com.luzviva.LuzViva;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Pendulo amortiguado por entidad. El farol reacciona a:
 *  - caminar / correr / frenar / strafe        (inclinacion y aceleracion horizontal)
 *  - girar la camara                            (se queda "atras" al girar)
 *  - saltar / caer / aterrizar                  (la cadena se estira y rebota como un muelle)
 *
 * Convencion de angulos (marco "camara": +X derecha, +Y arriba, -Z adelante):
 *  angF > 0 : el farol se desplaza hacia adelante
 *  angR > 0 : el farol se desplaza hacia la derecha
 *  stretch  : alargamiento de la cadena (en bloques de modelo), puede ser negativo
 */
@EventBusSubscriber(modid = LuzViva.MODID, value = Dist.CLIENT)
public final class SwingTracker {
    // ---- Ajustes de la fisica ----
    private static final float OMEGA2 = 18f;       // rigidez del pendulo
    private static final float DAMPING = 1.6f;
    private static final float LEAN = 0.04f;       // inclinacion en regimen (rad por bloque/s)
    private static final float ACCEL_GAIN = 0.55f;
    private static final float YAW_LEAN = 0.045f;  // reaccion al giro de camara (rad por rad/s)
    private static final float MAX_ANGLE = 1.1f;
    private static final float STRETCH_K = 70f;    // rigidez del muelle vertical
    private static final float STRETCH_C = 5f;
    private static final float STRETCH_GAIN = 0.25f;
    private static final float MAX_STRETCH = 0.3f;
    private static final float RAD = (float) (Math.PI / 180.0);

    public static final class State {
        public float angF, angR, velF, velR;
        public float stretch, stretchVel;
        float smoothF, smoothR, smoothYaw, smoothVy, lastYaw;
        boolean hasYaw;
        long last;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static long lastCleanup;
    private static LivingEntity current;

    private SwingTracker() {
    }

    /** Entidad que se esta renderizando ahora mismo (null en primera persona). */
    public static LivingEntity holder() {
        return current;
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        current = event.getEntity();
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        current = null;
    }

    /** Avanza (como mucho una vez por frame) y devuelve el estado del pendulo. */
    public static State get(LivingEntity entity) {
        final long now = System.nanoTime();
        State s = STATES.computeIfAbsent(entity.getId(), id -> new State());
        if (s.last == 0L) {
            s.last = now;
            return s;
        }
        float dt = (now - s.last) / 1.0e9f;
        if (dt < 0.0005f) {
            return s; // ya actualizado en este frame
        }
        s.last = now;
        step(entity, s, Math.min(dt, 0.05f));

        if (now - lastCleanup > 10_000_000_000L) {
            lastCleanup = now;
            STATES.values().removeIf(v -> now - v.last > 5_000_000_000L);
        }
        return s;
    }

    private static void step(LivingEntity e, State s, float dt) {
        Minecraft mc = Minecraft.getInstance();
        float yawDeg = e == mc.player ? e.getYRot() : e.yBodyRot;
        float yaw = yawDeg * RAD;

        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // adelante (yaw 0 = +Z)
        float rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);  // derecha

        float vx = (float) (e.getX() - e.xo) * 20f;
        float vz = (float) (e.getZ() - e.zo) * 20f;
        float vf = vx * fx + vz * fz;
        float vr = vx * rx + vz * rz;
        float vy = (float) (e.getY() - e.yo) * 20f;

        float k = 1f - (float) Math.exp(-dt / 0.07f);
        float nf = s.smoothF + (vf - s.smoothF) * k;
        float nr = s.smoothR + (vr - s.smoothR) * k;
        float nvy = s.smoothVy + (vy - s.smoothVy) * k;
        float af = Mth.clamp((nf - s.smoothF) / dt, -40f, 40f);
        float ar = Mth.clamp((nr - s.smoothR) / dt, -40f, 40f);
        float ay = Mth.clamp((nvy - s.smoothVy) / dt, -60f, 60f);
        s.smoothF = nf;
        s.smoothR = nr;
        s.smoothVy = nvy;

        // Velocidad de giro de la camara (rad/s). Girar a la derecha = yaw creciente.
        float rawYaw = s.hasYaw ? Mth.clamp(Mth.wrapDegrees(yawDeg - s.lastYaw) * RAD / dt, -10f, 10f) : 0f;
        s.lastYaw = yawDeg;
        s.hasYaw = true;
        s.smoothYaw += (rawYaw - s.smoothYaw) * k;

        float eqF = -nf * LEAN;                          // al avanzar, el farol se queda atras
        float eqR = -nr * LEAN - s.smoothYaw * YAW_LEAN; // y tambien al girar

        int sub = 2;
        float h = dt / sub;
        for (int i = 0; i < sub; i++) {
            s.velF += (-OMEGA2 * (s.angF - eqF) - DAMPING * s.velF - af * ACCEL_GAIN) * h;
            s.velR += (-OMEGA2 * (s.angR - eqR) - DAMPING * s.velR - ar * ACCEL_GAIN) * h;
            s.angF = Mth.clamp(s.angF + s.velF * h, -MAX_ANGLE, MAX_ANGLE);
            s.angR = Mth.clamp(s.angR + s.velR * h, -MAX_ANGLE, MAX_ANGLE);

            // Muelle vertical: al acelerar hacia arriba la cadena se estira, al frenar rebota.
            s.stretchVel += (-STRETCH_K * s.stretch - STRETCH_C * s.stretchVel + ay * STRETCH_GAIN) * h;
            s.stretch = Mth.clamp(s.stretch + s.stretchVel * h, -MAX_STRETCH, MAX_STRETCH);
        }
    }
}
