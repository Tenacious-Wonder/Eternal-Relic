package org.eternalrelic.capability.worn;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.relic.NightwatchEye;

/**
 * 「装入生效」能力：把守夜之瞳装进玩家的眼窝，并让代价一直挂在玩家身上。
 *
 * <p>目前这一层承载的是守夜之瞳这一族遗物：装入一只眼，就从生命上限里扣掉一颗心。</p>
 *
 * <p><b>扣减用「加法」而不是「按比例」</b>：加法在属性结算顺序里排在最前，先于一切按百分比
 * 放大的加成（例如奥塔的枝叶的 +12%）。因此玩家失去的是实打实的底数，枝叶也放大不了这份损失，
 * 正是需求里说的「优先计算」。</p>
 *
 * <p><b>「戴着哪只眼」记在哪里</b>：不另设一份名单，而是直接以「生命上限上有没有这条扣减」
 * 为准。这样状态与代价天然一致，不会出现「记录说戴着、血却已经还回去」的错位。
 * 退出再进时，玩家的属性会被游戏自己存进存档，状态因此自动延续；只有死亡重生与穿越维度时
 * 玩家会被换成一个新的个体、属性不跟着走，才需要 {@link ServerPlayerEvents#COPY_FROM}
 * 把这条扣减重新挂上去。</p>
 */
public final class WornRelicEffect {

    /** 装入一只眼要付出的代价：一颗心（游戏里 2 点生命）。 */
    private static final double HEALTH_COST = 2.0D;

    private WornRelicEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上重生与穿越维度时的搬运回调。
     *
     * <p>游戏把玩家换成一个新个体时，只搬运背包、血量这类数据，属性不跟着走。
     * 这里把旧玩家身上的眼睛重新装到新玩家身上，避免「眼睛还装着、血量却白白还回来」。</p>
     */
    public static void register() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            for (NightwatchEye eye : NightwatchEye.values()) {
                if (isWorn(oldPlayer, eye)) {
                    apply(newPlayer, eye);
                }
            }
        });
    }

    /**
     * 把一只守夜之瞳装进玩家的眼窝。
     *
     * @param player 装入的玩家
     * @param eye    装入的是哪只眼
     * @return 是否装入成功；已经戴着同一只眼、或不在服务端时返回 {@code false}
     */
    public static boolean wear(PlayerEntity player, NightwatchEye eye) {
        if (player.getWorld().isClient || isWorn(player, eye)) {
            return false;
        }

        apply(player, eye);
        return true;
    }

    /**
     * @param player 待检查的玩家
     * @param eye    待检查的眼
     * @return 该玩家是否已经戴着这只眼
     */
    public static boolean isWorn(PlayerEntity player, NightwatchEye eye) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        return maxHealth != null && maxHealth.getModifier(modifierIdOf(eye)) != null;
    }

    /**
     * 把一只眼的代价挂到玩家的生命上限上。
     *
     * @param player 目标玩家
     * @param eye    装入的眼
     */
    private static void apply(PlayerEntity player, NightwatchEye eye) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth == null || maxHealth.getModifier(modifierIdOf(eye)) != null) {
            return;
        }

        maxHealth.addPersistentModifier(new EntityAttributeModifier(
                modifierIdOf(eye),
                "eternal_relic:nightwatch_eye_" + eye.id(),
                -HEALTH_COST,
                EntityAttributeModifier.Operation.ADDITION));

        clampHealth(player);
    }

    /**
     * 生命上限降低后，把当前血量收回到新的上限之内。
     *
     * <p>例如满血十颗心的玩家装入一只眼后，上限降到九颗心，当前血量随之收到九颗心，
     * 不会留下比上限还高的数值。</p>
     *
     * @param player 目标玩家
     */
    private static void clampHealth(PlayerEntity player) {
        float maxHealth = player.getMaxHealth();
        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    /**
     * 由一只眼派生它挂在生命上限上的固定标识。
     *
     * <p>标识必须固定：同一只眼每次都要挂到同一条记录上，否则反复装入会不断堆积扣减。
     * 这里由眼的短名稳定派生，而不是另写一串常量，避免两处信息各写一遍。</p>
     *
     * @param eye 守夜之瞳的某一只眼
     * @return 该只眼对应的属性扣减标识
     */
    private static UUID modifierIdOf(NightwatchEye eye) {
        return UUID.nameUUIDFromBytes(
                ("eternal_relic:nightwatch_eye_" + eye.id()).getBytes(StandardCharsets.UTF_8));
    }
}
