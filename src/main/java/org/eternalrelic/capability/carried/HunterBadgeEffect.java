package org.eternalrelic.capability.carried;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「猎人收获」能力：带着猎人徽章时，击杀生物有机会额外多掉一件战利品。
 *
 * <p><b>它是本模组第一件「改变掉落结果」的遗物</b>：生物掉什么是游戏照自己的掉落表算出来的，
 * 模组没有可以监听的事件，只能守在「跑掉落表」那一步（见
 * {@link org.eternalrelic.mixin.LivingEntityMixin}）。</p>
 *
 * <p><b>多掉的是「一样东西的一个」，而不是把掉落表再摇一遍。</b>掉落表是一样一样交出来的，
 * 因此先由 {@link Collector} 把这一次掉出来的每一样都记下来，再从里面随机挑一样、
 * 数量多给一个。掉落表什么都没给（例如这头牛本来就不掉东西）时，这一次就作罢。</p>
 *
 * <p><b>只有玩家击杀才算</b>：凶手取自伤害来源，怪物互相残杀、摔死、烧死都没有凶手，
 * 一律不触发。带多枚徽章也只算 5%。</p>
 */
public final class HunterBadgeEffect {

    /** 每次击杀多掉一件的概率。 */
    private static final float EXTRA_DROP_CHANCE = 0.05F;

    private HunterBadgeEffect() {
    }

    /**
     * 找出这次击杀的凶手——前提是他带着猎人徽章。
     *
     * @param source 这只生物致死的那次伤害
     * @return 带着徽章的击杀者；没有凶手、凶手不是玩家、或者凶手没带徽章时返回 {@code null}
     */
    public static ServerPlayerEntity hunterOf(DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return null;
        }

        return CarriedStacks.inEffect(player, ModItems.HUNTER_BADGE) ? player : null;
    }

    /**
     * 掷一次骰子，决定这一只生物要不要多掉一件。
     *
     * @param hunter 带着徽章的击杀者
     * @return 命中时返回 {@code true}
     */
    public static boolean rollsExtra(ServerPlayerEntity hunter) {
        return hunter.getRandom().nextFloat() < EXTRA_DROP_CHANCE;
    }

    /**
     * 从这一次掉出来的东西里挑一样，数量多给一个。
     *
     * <p>挑中的那一样只给一个、不改它原本的数量；原本那份已经由游戏照常掉在地上了。</p>
     *
     * @param hunter   带着徽章的击杀者（提供随机源）
     * @param dropped  这一次掉落表交出来的每一样东西
     * @param consumer 游戏原本用来把掉落物放到地上的那一个动作
     */
    public static void dropExtraItem(ServerPlayerEntity hunter, List<ItemStack> dropped,
            Consumer<ItemStack> consumer) {
        if (dropped.isEmpty()) {
            return;
        }

        ItemStack picked = dropped.get(hunter.getRandom().nextInt(dropped.size()));
        consumer.accept(picked.copyWithCount(1));
    }

    /**
     * 掉落表的记账本——把游戏给出的每一样东西先抄一份记下，再原样交给游戏去落地。
     *
     * <p>作用是让 {@link #dropExtraItem} 事后有清单可挑。抄下来的是副本：
     * 交出去的那一份会被游戏装进掉落物实体，之后还会被改动（合并、消失），
     * 直接留着原件会在挑选时拿到已经变了的东西。</p>
     */
    public static final class Collector implements Consumer<ItemStack> {

        /** 游戏原本的落地动作。 */
        private final Consumer<ItemStack> target;

        /** 这一次掉落表交出来的每一样东西（副本）。 */
        private final List<ItemStack> collected = new ArrayList<>();

        /**
         * @param target 游戏原本的落地动作，每一样东西都要原样转交给它
         */
        public Collector(Consumer<ItemStack> target) {
            this.target = target;
        }

        @Override
        public void accept(ItemStack stack) {
            this.collected.add(stack.copy());
            this.target.accept(stack);
        }

        /**
         * @return 这一次掉落表交出来的每一样东西
         */
        public List<ItemStack> collected() {
            return this.collected;
        }
    }
}
