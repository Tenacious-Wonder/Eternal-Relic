package org.eternalrelic.capability.carried;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「剥皮」能力：带着剥皮小刀时，击杀<b>动物</b>有机会额外多掉一件。
 *
 * <p>它与 {@link HunterBadgeEffect}（猎人徽章：任何生物都可能多掉）是<b>同一件事的两个范围</b>：
 * 徽章不限对象，小刀只认动物。两者可以同时带着，各掷各的骰子，互不影响。</p>
 *
 * <p><b>什么叫「动物」</b>：判据是原版的 {@link AnimalEntity} —— 游戏里「<b>能繁殖的被动生物</b>」
 * 那一大类：牛（含蘑菇牛）、猪、羊、鸡、兔、马、驴、骡、骆驼、羊驼、狐狸、熊猫、北极熊、
 * 山羊、蜜蜂、美西螈、青蛙、海龟、炽足兽、嗅探兽都在内。</p>
 *
 * <p>⚠️ <b>能驯服的也在内</b>：狼、猫、豹猫、鹦鹉在游戏代码里正是从动物类继承下来的
 * （{@code TameableEntity extends AnimalEntity}），马与驴骡同理。因此<b>击杀自己的狗、猫、鹦鹉
 * 一样会多掉一件</b> —— 这是制作者 2026-10-06 明确选定的口径：一份判据管到底，不必维护名单。</p>
 *
 * <p><b>不在内</b>：村民与流浪商人、蝙蝠、鱿鱼、发光鱿鱼、海豚、铁傀儡与雪傀儡，以及全部敌对生物。</p>
 *
 * <p><b>别的模组的动物会被自动认出来</b>：想做一只「能繁殖」的动物，就必须继承原版的动物类
 * （否则拿不到喂养、繁殖、幼崽成长这些能力），因此只要它继承了 —— 哪怕在更深一层
 * （例如某模组的"僵尸牛"继承自牛）—— 这里不必改一行代码就认得它。
 * 反过来，模组若从更底层另起炉灶写动物、或者做的是敌对生物，不在内。</p>
 *
 * <p><b>多掉的是「掉落表给出的东西里随机挑一样、数量加一」</b>，与猎人徽章用的是同一段实现
 * （见 {@link HunterBadgeEffect#dropExtraItem}）：不是把掉落表重摇一遍，也不会凭空变出
 * 那只动物本来就不掉的东西。</p>
 */
public final class SkinningKnifeEffect {

    /** 每次击杀动物多掉一件的概率。 */
    private static final float EXTRA_DROP_CHANCE = 0.15F;

    private SkinningKnifeEffect() {
    }

    /**
     * 找出这次击杀的凶手 —— 前提是他带着剥皮小刀，而且死的是动物。
     *
     * @param source 这只生物致死的那次伤害
     * @param victim 死掉的那只生物
     * @return 带着小刀的击杀者；没有凶手、凶手不是玩家、死的不是动物、或者他没带小刀时返回 {@code null}
     */
    public static ServerPlayerEntity skinnerOf(DamageSource source, LivingEntity victim) {
        if (!(victim instanceof AnimalEntity)) {
            return null;
        }

        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return null;
        }

        return CarriedStacks.inEffect(player, ModItems.SKINNING_KNIFE) ? player : null;
    }

    /**
     * 掷一次骰子，决定这一只动物要不要多掉一件。
     *
     * @param skinner 带着小刀的击杀者
     * @return 命中时返回 {@code true}
     */
    public static boolean rollsExtra(ServerPlayerEntity skinner) {
        return skinner.getRandom().nextFloat() < EXTRA_DROP_CHANCE;
    }

    /**
     * 从这一次掉出来的东西里挑一样，数量多给一个。
     *
     * @param skinner  带着小刀的击杀者（提供随机源）
     * @param dropped  这一次掉落表交出来的每一样东西
     * @param consumer 游戏原本用来把掉落物放到地上的那一个动作
     */
    public static void dropExtraItem(ServerPlayerEntity skinner, List<ItemStack> dropped,
            Consumer<ItemStack> consumer) {
        HunterBadgeEffect.dropExtraItem(skinner, dropped, consumer);
    }
}
