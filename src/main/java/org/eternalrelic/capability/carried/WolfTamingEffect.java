package org.eternalrelic.capability.carried;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.registry.ModItems;

/**
 * 「喂骨头更容易认主」能力：带着可怕狼牙吊坠去驯服狼时，成功的机会被抬高一截。
 *
 * <p>原版驯服一次狼是<b>三分之一</b>的机会（掷一次三面的骰子，掷出 0 才算认主），
 * 因此平均要喂掉三根骨头。带着吊坠时改成<b>六分之五</b>——只有掷出六分之一的那一面才失败，
 * 平均一根多一点就能认主。</p>
 *
 * <p>这里只管「这一次算不算成功」，掷骰子与判定都交给
 * {@link org.eternalrelic.mixin.WolfTamingMixin}：它替原版掷出那个用来比较的数字，
 * 使得原版那句「等于 0 就算驯服」的判定结果正好落在我们想要的概率上，
 * 而驯服成功之后的动作（认主、坐下、冒爱心）仍旧全部由原版执行，一处也没有复制。</p>
 */
public final class WolfTamingEffect {

    /** 带着吊坠时判定失败的面数：六面里只有一面失败，也就是六分之五成功。 */
    private static final int FAILURE_CHANCE_DENOMINATOR = 6;

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
     * 替原版掷一次驯服判定的骰子。
     *
     * @param random 这只狼自己的随机源
     * @return 这一次是否算驯服成功
     */
    public static boolean tames(Random random) {
        return random.nextInt(FAILURE_CHANCE_DENOMINATOR) != 0;
    }
}
