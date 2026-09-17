package org.eternalrelic.item;

import net.minecraft.item.Item;

/**
 * 回响之环 —— 携带在背包中时替玩家挡下攻击的遗物。
 *
 * <p>它本身不带任何行为，价值全部来自「携带在背包中」这一条件：由
 * {@link org.eternalrelic.capability.carried.DamageWardEffect} 在攻击落下前出手，
 * 把这一击挡下、或化出金心分摊，并让环身碎裂。名称、品阶与效果说明由语言文件和遗物界面承担，
 * 提示框里只留一句「按左 Shift 详细查看」。</p>
 *
 * <p>碎裂的形态（{@code echo_ring_drained}）用的是同一个类：它同样没有行为，
 * 只是名称不同，而名称由各自的翻译键决定。</p>
 */
public class EchoRingItem extends Item {

    public EchoRingItem(Settings settings) {
        super(settings);
    }
}
