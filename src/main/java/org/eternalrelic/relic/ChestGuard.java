package org.eternalrelic.relic;

import java.util.Set;

import org.twcore.api.bodypart.BodyPart;

/**
 * 一件「缝在胸甲上的护具」的配置 —— 胸甲护具表（{@code registry/ChestGuards}）里的一行。
 *
 * <p><b>肩甲、胸甲片、斗篷做的都是同一件事</b>：挨打时若这一击落在它护着的那一块上，就少掉几点伤害；
 * 若来的是远程，还有机会被整个弹开。因此它们共用这一份配置与同一张表，差别只在登记的那几个数上
 * （护哪儿、减多少、弹不弹、挡不挡魔法）。此前肩甲与胸甲片各有一套一模一样的账，
 * 加第三类时合并成了这一套（见交接文档）。</p>
 *
 * <p><b>护哪些部位是明写的</b>：肩甲护左右两侧肩膀，胸甲片护正胸，斗篷护后背。
 * 部位取自 TW Core 的 {@link BodyPart}，判定那一侧会如实按它比较。</p>
 *
 * <p><b>减的是原版算完之后的数字</b>：护甲与保护附魔都结算完毕，这里才从剩下的伤害里扣掉
 * {@link #reduction} 点。按部位的那一条<b>可以扣到 0</b>（挡下轻击本来就是护具的本事），
 * 而 {@link #magicReduction} 那一条在结算时<b>至少留 1 点</b>——理由见
 * {@link org.eternalrelic.capability.attached.ChestGuardEffect}。</p>
 *
 * <p><b>弹开与减伤是两件事</b>：弹开是「这一箭整个不算」，连伤害带插箭一起拦下；
 * 减伤只是「这一击少掉几点」。一件护具可以只有前者（{@code deflectChance} 为 0 就是不会弹），
 * 也可以两样都有。</p>
 *
 * <p><b>魔法那一条不看部位</b>：药水、凋零、龙息这类伤害是从身体内部发作的，游戏不会产生
 * 「打中哪儿」的消息，因此 {@link #magicReduction} 只在「缝着没有」这一层判断。</p>
 *
 * @param guardedParts   它护住的身体部位
 * @param reduction      这一击少掉几点伤害
 * @param deflectChance  远程打中护着的部位时，有多大概率把这一击整个弹开；0 表示不会弹
 * @param magicReduction 替玩家挡下的魔法伤害点数；0 表示不挡
 */
public record ChestGuard(Set<BodyPart> guardedParts, float reduction, float deflectChance,
        float magicReduction) {

    /**
     * 收下登记的部位并复制一份存起来，让这份清单此后只读。
     */
    public ChestGuard {
        guardedParts = Set.copyOf(guardedParts);
    }

    /**
     * @param part 这一击打中的部位
     * @return 这一击是否落在本护具护着的部位上
     */
    public boolean guards(BodyPart part) {
        return this.guardedParts.contains(part);
    }

    /**
     * @return 这件护具是否有可能弹开远程攻击
     */
    public boolean canDeflect() {
        return this.deflectChance > 0.0F;
    }
}
