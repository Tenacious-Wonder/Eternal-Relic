package org.eternalrelic.capability.carried;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.registry.ModItems;

/**
 * 「喂骨头更容易认主」能力：带着可怕狼牙吊坠去驯服狼时，成功的机会被抬高一截。
 *
 * <p>原版驯服一次狼是<b>三分之一</b>的机会（掷一次三面的骰子，掷出 0 才算认主），
 * 因此平均要喂掉三根骨头。带着吊坠时改成<b>六分之五</b>——只有六分之一的机会失败，
 * 平均一根多一点就能认主。</p>
 *
 * <p><b>六分之五是怎么折算出来的</b>：原版判成功的那一份（三分之一）原样保留；
 * 原版判失败的那一份（三分之二）里，再以四分之三的比例改判成功。
 * 合计 1/3 + 2/3 × 3/4 = 5/6。</p>
 *
 * <p><b>为什么是折算而不是自己另掷一次骰子</b>：替换掉原版那次掷骰是占位式的做法，
 * 两个模组都想改这里就会在启动时撞崩；在掷出的数上折算则可以叠加。
 * 掷骰、判定、以及认主之后的动作（认主、坐下、冒爱心、扣掉一根骨头）仍旧全部由原版执行，
 * 一处也没有复制（见设计决策 56）。</p>
 */
public final class WolfTamingEffect {

    /** 原版判定里「掷出这个数就算驯服成功」。 */
    private static final int SUCCESS_VALUE = 0;

    /** 带着吊坠时，原版判失败的那些里再改判成功的比例：四面里三面改判，也就是四分之三。 */
    private static final int OVERTURN_FACES = 4;

    private WolfTamingEffect() {
    }

    /**
     * 这位玩家喂骨头时，驯服概率是否被吊坠抬高。
     *
     * @param player 正在喂骨头的玩家；狼被别人用别的方式交互时可能拿不到，故允许为 {@code null}
     * @return 是否按抬高后的概率来判定
     */
    public static boolean easesTaming(PlayerEntity player) {
        return player != null && CarriedStacks.inEffect(player, ModItems.DREADFUL_WOLF_FANG_PENDANT);
    }

    /**
     * 把原版这一次掷骰的结果，折算成带着吊坠时该有的结果。
     *
     * <p>原版已经判成功的保持成功 —— 「掷出 0」这件事本身不被改写；
     * 只有在原版即将判失败、且喂骨头的人确实带着吊坠时，才按 {@link #OVERTURN_FACES} 的比例改判。</p>
     *
     * @param player 正在喂骨头的玩家，可能为 {@code null}
     * @param rolled 原版掷出的那个数
     * @param random 这只狼自己的随机源，用来决定这一次要不要改判
     * @return 交给原版判定的数：{@code 0} 会让它认为驯服成功，其余则失败
     */
    public static int eased(PlayerEntity player, int rolled, Random random) {
        if (rolled == SUCCESS_VALUE || !easesTaming(player)) {
            return rolled;
        }

        return random.nextInt(OVERTURN_FACES) != 0 ? SUCCESS_VALUE : rolled;
    }
}
