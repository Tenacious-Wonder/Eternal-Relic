package org.eternalrelic.skill;

import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 「破阵」技能的学习状态 —— 这位玩家有没有学会盾牌冲刺。
 *
 * <p><b>记在哪</b>：玩家自己的<b>幸运属性</b>上挂一条<b>值为 0</b> 的修饰符，当作一张便利贴。
 * 项目此前没有「玩家数据」这一类东西，而属性修饰符本来就会跟着玩家存档走 ——
 * 退出重进、死亡重生、跨维度都还在，重启服务器也不会丢（远行绑腿记「到期时刻」用的也是这条路）。</p>
 *
 * <p><b>为什么挂在幸运上</b>：值是 0，因此对钓鱼与掉落表<b>没有任何影响</b>；
 * 而原版提示框在列属性时用的是固定文案（"+X 攻击力"这种），<b>不会把修饰符的名字画出来</b>，
 * 所以玩家看不到这张便利贴。选它只是随便挑一个"多 0 点也不会有人察觉"的属性。</p>
 *
 * <p>⚠️ <b>值必须是 0</b>：它不是加成，是标记。哪天有人想"顺手改成 1"，
 * 就会变成一件白送幸运的隐形遗物。</p>
 */
public final class ShieldRushSkill {

    /**
     * 标记用的 UUID。
     *
     * <p>固定值，不能每次启动重新随机 —— 那样重启之后就认不出旧存档里的标记了。</p>
     */
    private static final UUID LEARNED_MARKER_ID = UUID.fromString("7c4f1a92-3b6d-4e58-9f21-0d8a5c6b7e30");

    /** 标记的名字。玩家看不到，只在排查存档时用来一眼认出它。 */
    private static final String MARKER_NAME = "eternal_relic:shield_rush";

    /** 标记的值 —— 必须是 0。 */
    private static final double NO_EFFECT = 0.0D;

    private ShieldRushSkill() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，
     * 挂上「玩家被换成新个体时，把学会的标记搬过去」这一步。
     *
     * <p><b>为什么非有不可</b>：游戏在<b>死亡重生与穿越维度</b>时会把玩家换成一个新的个体，
     * 而它只搬运背包、血量这类数据 —— <b>属性不跟着走</b>，而"学会了破阵"恰恰是记在属性上的
     * 一张便利贴（见本类开头）。少了这一步，玩家死一次就再也冲不出去，
     * 而且看起来完全像是技能坏了。</p>
     *
     * <p>守夜之瞳的生命上限扣减踩的是同一个坑，做法也一样（见
     * {@link org.eternalrelic.capability.worn.WornRelicEffect#register()}）。</p>
     */
    public static void register() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (hasLearned(oldPlayer)) {
                learn(newPlayer);
            }
        });
    }

    /**
     * 这位玩家学会冲刺了没有。
     *
     * @param player 目标玩家
     * @return 学会时返回 {@code true}
     */
    public static boolean hasLearned(ServerPlayerEntity player) {
        return markerOf(player) != null;
    }

    /**
     * 把冲刺教给这位玩家。已经会了就什么都不做。
     *
     * @param player 目标玩家
     */
    public static void learn(ServerPlayerEntity player) {
        EntityAttributeInstance luck = player.getAttributeInstance(EntityAttributes.GENERIC_LUCK);

        if (luck == null || markerOf(player) != null) {
            return;
        }

        luck.addPersistentModifier(new EntityAttributeModifier(LEARNED_MARKER_ID, MARKER_NAME,
                NO_EFFECT, EntityAttributeModifier.Operation.ADDITION));
    }

    /**
     * 找出那张便利贴。
     *
     * @param player 目标玩家
     * @return 标记本身；没学会时返回 {@code null}
     */
    private static EntityAttributeModifier markerOf(ServerPlayerEntity player) {
        EntityAttributeInstance luck = player.getAttributeInstance(EntityAttributes.GENERIC_LUCK);

        if (luck == null) {
            return null;
        }

        for (EntityAttributeModifier modifier : luck.getModifiers()) {
            if (modifier.getId().equals(LEARNED_MARKER_ID)) {
                return modifier;
            }
        }

        return null;
    }
}
