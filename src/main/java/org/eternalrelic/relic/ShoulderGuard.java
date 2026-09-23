package org.eternalrelic.relic;

import java.util.Set;

import org.eternalrelic.bodypart.BodyPart;

/**
 * 一件肩甲的「护哪一侧、这一击少掉几点、能不能弹开」—— 肩甲表（{@code registry/ShoulderGuards}）里的一行。
 *
 * <p>它回答三件事：这一击打在某个部位上时，这件肩甲管不管；管的话，这一击少掉几点伤害；
 * 以及远程打在这个部位上时，有没有机会把这一击整个弹开。</p>
 *
 * <p><b>为什么和属性加成分开成两份配置</b>：{@link RelicEffect} 管的是「一直挂在玩家身上的数值」
 * （盔甲韧性就登记在那一边），而这一份管的是「某一击打中肩膀时减掉的那几点」与「某一箭能不能崩回去」——
 * 一个长期挂着，一个只在挨打的那一刻算一次，结算的时机完全不同，因此各走各的表。</p>
 *
 * <p><b>减的是原版算完之后的数字</b>：护甲与保护附魔都结算完毕，这里才从剩下的伤害里扣掉
 * {@link #reduction} 点。扣到 0 为止，不会倒扣成加血——这一点由实际使用它的地方保证
 * （见 {@code capability.attached.ShoulderGuardEffect}）。</p>
 *
 * <p><b>弹开与减伤是两件事</b>：弹开是「这一箭整个不算」，连伤害带插箭一起拦下；
 * 减伤只是「这一击少掉几点」。一件肩甲可以只有前者（{@code deflectChance} 为 0 就是不会弹），
 * 也可以两样都有。</p>
 *
 * @param guardedParts  这件肩甲护住的身体部位（一只肩甲护一侧，一套两侧都护）
 * @param reduction     这一击少掉几点伤害
 * @param deflectChance 远程打中护着的部位时，有多大概率把这一击整个弹开；0 表示不会弹
 */
public record ShoulderGuard(Set<BodyPart> guardedParts, float reduction, float deflectChance) {

    /**
     * 收下登记的部位并复制一份存起来，让这份清单此后只读。
     */
    public ShoulderGuard {
        guardedParts = Set.copyOf(guardedParts);
    }

    /**
     * @param part 这一击打中的部位
     * @return 这一击是否落在本肩甲护着的部位上
     */
    public boolean guards(BodyPart part) {
        return this.guardedParts.contains(part);
    }

    /**
     * @return 这件肩甲是否有可能弹开远程攻击
     */
    public boolean canDeflect() {
        return this.deflectChance > 0.0F;
    }
}
