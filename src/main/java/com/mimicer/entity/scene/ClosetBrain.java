package com.mimicer.entity.scene;

import com.mimicer.config.ClosetConfig;
import com.mimicer.hide.ClosetIndex;
import com.mimicer.hide.HideManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuándo busca un monstruo en los armarios: (1) va directo al que vio entrar a su víctima, (2) tras perder de
 * vista a una presa revisa los armarios cercanos, (3) de vez en cuando (probabilidad configurable) revisa uno
 * aunque no haya visto entrar a nadie. Cada 10 ticks como mucho, sin recorrer bloques (usa {@link ClosetIndex}).
 */
public final class ClosetBrain {
    private final SceneHost host;
    private LivingEntity prevTarget;
    private long searchUntil;
    private int checks;
    private long nextRoll = -1;
    private final Map<Long, Long> checked = new HashMap<>();

    public ClosetBrain(SceneHost host) {
        this.host = host;
    }

    public void tick() {
        Mob mob = host.mob();
        if (!(mob.level() instanceof ServerLevel level) || mob.tickCount % 10 != 3 || !ClosetConfig.ENABLED.get()) {
            return;
        }
        long now = level.getGameTime();
        LivingEntity target = mob.getTarget();
        if (prevTarget != null && target == null && prevTarget.isAlive()
                && (prevTarget instanceof Player || prevTarget instanceof AbstractVillager)) {
            searchUntil = now + ClosetConfig.LOST_TARGET_SEARCH_SECONDS.get() * 20L;
            checks = 0;
        }
        prevTarget = target;
        if (target != null || host.isBusyWithScene() || !host.sceneAllowed()) {
            return;
        }
        // 1. Recuerda dónde se escondió (lo vio entrar).
        HideManager.Hidden seen = HideManager.findSeenBy(level, mob.getUUID(), mob.position(), 64.0, false);
        if (seen != null && seen.closet() && host.requestClosetCheck(seen.bed())) {
            return;
        }
        // 2. Perdió de vista a la presa: revisa armarios cercanos, uno tras otro.
        if (now < searchUntil && checks < ClosetConfig.MAX_CHECKS_PER_SEARCH.get()) {
            BlockPos c = nextUnchecked(level, now, false);
            if (c != null && host.requestClosetCheck(c)) {
                mark(c, now);
                checks++;
                return;
            }
        }
        // 3. Revisión al azar.
        if (nextRoll < 0) {
            nextRoll = now + mob.getRandom().nextInt(400);
        }
        if (now >= nextRoll) {
            nextRoll = now + ClosetConfig.CHECK_INTERVAL_SECONDS.get() * 20L + mob.getRandom().nextInt(200);
            if (mob.getRandom().nextDouble() < ClosetConfig.RANDOM_CHECK_CHANCE.get()) {
                BlockPos c = nextUnchecked(level, now, true);
                if (c != null && host.requestClosetCheck(c)) {
                    mark(c, now);
                }
            }
        }
    }

    private void mark(BlockPos c, long now) {
        checked.put(c.asLong(), now + 1800);
        if (checked.size() > 64) {
            checked.values().removeIf(t -> t < now);
        }
    }

    private BlockPos nextUnchecked(ServerLevel level, long now, boolean random) {
        List<BlockPos> list = ClosetIndex.near(level, host.mob().blockPosition(),
                ClosetConfig.SEARCH_RANGE.get(), 4);
        list.removeIf(p -> checked.getOrDefault(p.asLong(), 0L) > now || ClosetIndex.isBusy(level, p));
        if (list.isEmpty()) {
            return null;
        }
        return random ? list.get(host.mob().getRandom().nextInt(list.size())) : list.get(0);
    }
}
