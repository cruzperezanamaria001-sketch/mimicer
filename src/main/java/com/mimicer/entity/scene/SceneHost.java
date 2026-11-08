package com.mimicer.entity.scene;

import com.mimicer.entity.CorpseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Monstruo que puede alimentarse de cadáveres y sacar víctimas de debajo de las camas (The Mimic y The Void;
 * The Glitch queda fuera). Los inicios de escena viajan en datos sincronizados (tick de inicio), así que todos
 * los clientes calculan la misma fase de la animación a partir de la hora del mundo.
 */
public interface SceneHost {
    PathfinderMob mob();

    int getFeedStart();

    void setFeedStart(int gameTime);

    int getExtractStart();

    void setExtractStart(int gameTime);

    /** Agacha (o levanta) al monstruo físicamente al empezar o terminar una escena. */
    void scenePosture(boolean crouched);

    /** ¿Puede ahora mismo participar en una escena? (el Mímico solo en forma monstruosa) */
    boolean sceneAllowed();

    void beginFeeding(CorpseEntity corpse);

    /** Pide sacar a una víctima escondida; false si ya está ocupado con otra o la víctima ya la lleva otro. */
    boolean requestExtraction(LivingEntity victim, BlockPos bed);

    /** Modo de la escena de extracción en curso (ver {@link SceneTiming#MODE_BED}). */
    int getExtractMode();

    void setExtractMode(int mode);

    /** Pide revisar un armario (posición inferior); si hay alguien dentro lo sacará. */
    boolean requestClosetCheck(BlockPos closetLower);

    /** Cerebro de búsqueda de armarios del monstruo (memoria, búsqueda tras perder la presa, revisiones al azar). */
    default void tickClosetSearch() {}

    /** La víctima ya está fuera de la cama: aquí empieza el ataque. */
    void onExtracted(LivingEntity victim);

    /** No pudo llegar hasta la cama. */
    void onExtractFailed(LivingEntity victim);

    /** Hay una escena en marcha (incluido el camino hacia el cadáver o la cama). */
    boolean isBusyWithScene();

    default boolean isFeeding() {
        return getFeedStart() >= 0;
    }

    default boolean isExtracting() {
        return getExtractStart() >= 0;
    }

    default float feedTime(float partial) {
        int s = getFeedStart();
        return s < 0 ? -1.0F : (float) (mob().level().getGameTime() - s) + partial;
    }

    default float extractTime(float partial) {
        int s = getExtractStart();
        return s < 0 ? -1.0F : (float) (mob().level().getGameTime() - s) + partial;
    }
}
