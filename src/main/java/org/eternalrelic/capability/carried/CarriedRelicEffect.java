package org.eternalrelic.capability.carried;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

import org.eternalrelic.registry.ModRelics;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.AotaPulseParticleEffect;
import org.eternalrelic.relic.RelicDefinition;
import org.eternalrelic.relic.RelicEffect;

/**
 * 「携带生效」能力：遗物放在背包里时，把它的属性加成挂到玩家身上。
 *
 * <p>这一层只认遗物表里的数据，不认具体是哪件遗物：把 {@link ModRelics} 里登记的
 * 携带效果逐件应用到玩家身上，因此新增属性类遗物只需登记，不必改动这里。</p>
 *
 * <p><b>为什么用「按最终总量放大」这种运算</b>：它先把基础值与其它模组提供的加成相加，
 * 最后才乘以本遗物的倍率。其它模组增加同一属性的手段依然完整生效，本模组也不去和它们
 * 争抢「加固定值」的位置，因此不会互相覆盖。</p>
 */
public final class CarriedRelicEffect {

    /** 每隔多少刻核对一次背包内容。5 刻约为 0.25 秒，玩家察觉不到延迟。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 记录每位玩家当前已生效的携带件数：玩家编号 → （遗物编号 → 件数）。 */
    private static final Map<UUID, Map<UUID, Integer>> APPLIED_COUNTS = new HashMap<>();

    /** 只带一件遗物时，搏动声的音量。 */
    private static final float PULSE_MIN_VOLUME = 1.0F;

    /** 携带量达到 {@link #PULSE_FULL_COUNT} 件时，搏动声的音量。 */
    private static final float PULSE_MAX_VOLUME = 1.4F;

    /** 携带到这么多件时搏动声最响；再多也不再变响，免得一堆遗物叠出一声炸响。 */
    private static final float PULSE_FULL_COUNT = 5.0F;

    private CarriedRelicEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上核对背包的回调。
     *
     * <p>每个核对周期清点一次玩家携带的遗物，数量变化时才重算属性。这样无论遗物是捡起、
     * 拖动、放进箱子还是丢在地上，都被同一套逻辑覆盖，不依赖某一处特定的背包写入入口。</p>
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyFor(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> applyFor(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                APPLIED_COUNTS.remove(handler.getPlayer().getUuid()));
    }

    /**
     * 核对一名玩家携带的遗物，只在件数确实变化时才更新属性。
     *
     * <p>每个核对周期都会走一遍这里，但件数没变时立即返回，
     * 因此频繁的背包操作不会反复重算属性。</p>
     *
     * @param player 目标玩家
     */
    private static void applyFor(ServerPlayerEntity player) {
        Map<UUID, Integer> carried = countRelics(player);

        if (carried.equals(appliedCountsOf(player))) {
            return;
        }

        recalculate(player, carried);
        APPLIED_COUNTS.put(player.getUuid(), carried);
    }

    /**
     * 重新挂载玩家身上全部遗物的携带效果。
     *
     * @param player  目标玩家
     * @param carried 当前携带的遗物及件数
     */
    private static void recalculate(ServerPlayerEntity player, Map<UUID, Integer> carried) {
        Map<UUID, Integer> applied = appliedCountsOf(player);

        for (RelicDefinition definition : ModRelics.all()) {
            RelicEffect effect = definition.effect();
            if (effect == null) {
                continue;
            }

            UUID modifierId = modifierIdOf(definition);
            int previous = applied.getOrDefault(modifierId, 0);
            int current = carried.getOrDefault(modifierId, 0);

            if (previous == current) {
                continue;
            }

            double oldModifier = previous <= 0 ? 0.0D : effect.valueFor(previous);
            double newModifier = current <= 0 ? 0.0D : effect.valueFor(current);

            EntityAttributeInstance attribute = player.getAttributeInstance(effect.attribute().attribute());
            if (attribute == null) {
                continue;
            }

            attribute.removeModifier(modifierId);

            if (current > 0) {
                attribute.addPersistentModifier(new EntityAttributeModifier(
                        modifierId,
                        "eternal_relic:" + definition.id().getPath(),
                        newModifier,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
            }

            if (effect.attribute().attribute() == EntityAttributes.GENERIC_MAX_HEALTH) {
                keepHealthRatio(player, oldModifier, newModifier);
            }

            if (previous <= 0 && current > 0) {
                playBirthEffect(player, current);
            }
        }
    }

    /**
     * 统计玩家携带的遗物及件数。
     *
     * <p>主背包与副手都会被计入——玩家把遗物换到副手时效果应当继续生效，
     * 反过来只要离开了这些位置，效果也应当立刻撤销。</p>
     *
     * @param player 目标玩家
     * @return 遗物编号 → 携带件数；没有携带任何遗物时为空表
     */
    private static Map<UUID, Integer> countRelics(PlayerEntity player) {
        Map<UUID, Integer> carried = new HashMap<>();

        for (ItemStack stack : player.getInventory().main) {
            collectRelic(stack, carried);
        }

        for (ItemStack stack : player.getInventory().offHand) {
            collectRelic(stack, carried);
        }

        return carried;
    }

    /**
     * 把一格物品计入携带统计。
     *
     * @param stack   待检查的物品堆
     * @param carried 统计结果，就地累加
     */
    private static void collectRelic(ItemStack stack, Map<UUID, Integer> carried) {
        if (stack.isEmpty()) {
            return;
        }

        RelicDefinition definition = ModRelics.definitionOf(stack.getItem());
        if (definition == null || !definition.hasCarriedEffect()) {
            return;
        }

        carried.merge(modifierIdOf(definition), stack.getCount(), Integer::sum);
    }

    /**
     * @param player 目标玩家
     * @return 该玩家当前已生效的携带件数；没有记录时为空表
     */
    private static Map<UUID, Integer> appliedCountsOf(ServerPlayerEntity player) {
        return APPLIED_COUNTS.getOrDefault(player.getUuid(), Map.of());
    }

    /**
     * 生命上限变化后，按变化前的血量比例重新折算当前血量。
     *
     * <p>折算结果不允许超过新的生命上限，因此撤销携带效果时当前血量会平滑回落到上限之内，
     * 不会留下比上限还高的数值。</p>
     *
     * @param player      目标玩家
     * @param oldModifier 变更前的加成倍率
     * @param newModifier 变更后的加成倍率
     */
    private static void keepHealthRatio(ServerPlayerEntity player, double oldModifier, double newModifier) {
        double oldMax = player.getMaxHealth() / (1.0D + oldModifier);
        double newMax = oldMax * (1.0D + newModifier);

        if (oldMax <= 0.0D || newMax <= 0.0D) {
            return;
        }

        float scaled = (float) (player.getHealth() * (newMax / oldMax));
        player.setHealth(Math.max(0.0F, Math.min(scaled, (float) newMax)));
    }

    /**
     * 由一个遗物派生它挂在属性上的固定标识。
     *
     * <p>标识必须固定：同一个遗物每次都要挂到同一条记录上，否则反复添加会不断堆积。
     * 这里用遗物编号稳定派生，而不是在代码里另写一串常量，避免两处信息各写一遍。</p>
     *
     * @param definition 遗物定义
     * @return 该遗物的属性加成标识
     */
    private static UUID modifierIdOf(RelicDefinition definition) {
        return UUID.nameUUIDFromBytes(definition.id().toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 在玩家刚开始携带遗物时播放一次「诞生」表现：搏动声与涌出后收敛的粒子。
     *
     * <p>只在这一刻发生一次：放进背包时响一次，拿出后再放回来才会再次响起，
     * 携带期间既不重复播放声音，也不持续冒粒子。声音与粒子都从玩家身上发出，
     * 因此附近的玩家既能听到、也能看到。</p>
     *
     * @param player 刚开始携带遗物的玩家
     * @param count  当前携带件数。只影响声音大小：带得越多略响一些，粒子数不随它变化
     */
    private static void playBirthEffect(ServerPlayerEntity player, int count) {
        float volume = PULSE_MIN_VOLUME
                + Math.min(1.0F, count / PULSE_FULL_COUNT) * (PULSE_MAX_VOLUME - PULSE_MIN_VOLUME);
        ServerWorld world = player.getServerWorld();

        // 由服务端广播：玩家自己与附近玩家都能听到（实测这条路径可靠）
        world.playSound(
                null,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                ModSounds.AOTA_PULSE,
                SoundCategory.PLAYERS,
                volume,
                1.0F);

        // 胸口位置作为球心：眼球高度略往下一点
        double chestX = player.getX();
        double chestY = player.getEyeY() - 0.35D;
        double chestZ = player.getZ();

        int total = 36 + player.getRandom().nextInt(12);
        int farCount = total / 3;
        int nearCount = total - farCount;

        // 第一批：扩散到三格，走完两轮涨落
        world.spawnParticles(
                new AotaPulseParticleEffect(chestX, chestY, chestZ, 3.0F, 1.0F),
                chestX, chestY, chestZ,
                farCount,
                0.35D, 0.35D, 0.35D,
                0.0D);

        // 第二批：只扩散到一格半，同样走两轮
        world.spawnParticles(
                new AotaPulseParticleEffect(chestX, chestY, chestZ, 1.5F, 1.0F),
                chestX, chestY, chestZ,
                nearCount,
                0.30D, 0.30D, 0.30D,
                0.0D);
    }
}
