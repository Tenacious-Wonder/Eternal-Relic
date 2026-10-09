package org.eternalrelic.capability.carried;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.RelicAttachment;

/**
 * 「动能」能力：<b>附在武器或工具上</b>时，连续命中同一个目标 3 次，第 4 次命中造成 1.5 倍伤害。
 *
 * <p>这是模组里第一件管"攻击"而不是"挨打"的遗物。此前的遗物几乎都在管防御、赶路、
 * 掉落与信息，平砍这一侧只有"近战 +0.5"这种静态加成；动能器柄把"打"本身变成一件有节奏的事。</p>
 *
 * <h2>★ 只认附着（制作者 2026-10-07 定）</h2>
 * <p>它<b>放在背包里不算</b>，必须真的附在某件武器或工具上，而且<b>要握在手里</b>才算数 ——
 * 因此 {@code registry/ModRelics} 里用的是 {@code defineAttachmentOnly}，
 * 能附到哪几类东西上则由 {@code registry/AttachableRelics} 限定（武器与工具两类）。</p>
 * <p>这与"纹章一律放背包就生效"那条口径<b>不冲突</b>：那条管的是纹章，这一件是器柄 ——
 * 器柄记的是"同一件东西连打一个目标"的节奏，附在别处或放在包里都没有意义。</p>
 *
 * <h2>怎么算连击</h2>
 * <p>每名玩家记一份「正在打谁、已经连中几下、上一次是什么时候」。三种情况会断连，
 * 断了就从这一次重新数：</p>
 * <ul>
 *   <li><b>换了目标</b> —— 打 A 三下再打 B，B 那里从头数；</li>
 *   <li><b>断了太久</b> —— 超过 {@value #COMBO_TIMEOUT_TICKS} 刻（3 秒）没再打中同一个目标；</li>
 *   <li><b>手里换了东西</b> —— 把附器柄的剑收回包里、换一把没附的，记录顺手清掉。</li>
 * </ul>
 *
 * <h2>为什么要动游戏内部代码</h2>
 * <p>游戏给模组的事件里，"这一击打出多少伤害"是<b>只读</b>的：{@code ALLOW_DAMAGE} 只能
 * 选择放行或整击取消，没有"把它改成 1.5 倍"这个档位。因此这一件在
 * {@link org.eternalrelic.mixin.KineticHiltMixin} 里拦在伤害结算的入口上改那个数字。</p>
 *
 * <p><b>连击记在服务器内存里，不写存档。</b>它的寿命只有几秒，退游戏、换维度、重连都等于
 * "什么都没发生"；写进存档反而要额外操心清理。</p>
 */
public final class KineticHiltEffect {

    /** 要连中几下才强化。 */
    private static final int HITS_REQUIRED = 3;

    /** 强化那一击的倍率：1.5 倍（也就是 +50%）。 */
    private static final float FINISHER_MULTIPLIER = 1.5F;

    /** 多久没打中同一个目标就算断连。60 刻 = 3 秒。 */
    private static final int COMBO_TIMEOUT_TICKS = 60;

    /** 表里超过这么久没动的记录会被顺手清掉（刻）。 */
    private static final long KEEP_TICKS = 600L;

    /** 玩家编号 → 他此刻的连击。 */
    private static final Map<UUID, Combo> COMBOS = new HashMap<>();

    private KineticHiltEffect() {
    }

    /**
     * 这一击要不要强化 —— 由 {@code mixin/KineticHiltMixin} 在伤害结算开头调用。
     *
     * <p>顺带在这里推进连击：每调用一次算"又打中了一下"。因此这个方法<b>既改伤害、又记账</b>，
     * 两件事必须一起做（分开就会漏记或多记）。</p>
     *
     * @param source 这一击的来源（用它认出动手的玩家）
     * @param victim 挨打的那个生物
     * @param amount 这一击原本的伤害
     * @return 应当实际使用的伤害；不该强化时原样返回
     */
    public static float amplify(DamageSource source, LivingEntity victim, float amount) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player) || victim == player) {
            return amount;
        }

        // 手里那件东西上没附器柄：顺手把他的记录清掉 —— 换一件武器就得重新数
        if (!wields(player)) {
            COMBOS.remove(player.getUuid());
            return amount;
        }

        long now = player.server.getOverworld().getTime();

        // 顺手清掉早就过期的记录，免得这张表随玩家人数一直长下去
        COMBOS.entrySet().removeIf(entry -> now - entry.getValue().lastTick() > KEEP_TICKS);

        Combo combo = COMBOS.get(player.getUuid());
        boolean sameTarget = combo != null && combo.target().equals(victim.getUuid());
        boolean inTime = sameTarget && now - combo.lastTick() <= COMBO_TIMEOUT_TICKS;

        if (!inTime) {
            // 换了目标、或者断得太久：从这一击重新数
            COMBOS.put(player.getUuid(), new Combo(victim.getUuid(), 1, now));
            return amount;
        }

        int hits = combo.hits() + 1;

        if (hits > HITS_REQUIRED) {
            // 第 4 击：强化，然后从头数起
            COMBOS.put(player.getUuid(), new Combo(victim.getUuid(), 1, now));
            return amount * FINISHER_MULTIPLIER;
        }

        COMBOS.put(player.getUuid(), new Combo(victim.getUuid(), hits, now));
        return amount;
    }

    /**
     * 这位玩家此刻手里握着的武器或工具上，是不是附着了动能器柄。
     *
     * <p><b>只认附着</b>（制作者 2026-10-07 定）：放在背包里不算，必须让它真的长在某件东西上，
     * 而且此刻正握在手里。至于"能附到哪几类东西上"，由 {@code registry/AttachableRelics}
     * 那一行限定（武器与工具两类），因此这里只看主手那件东西上有没有它。</p>
     *
     * @param player 目标玩家
     * @return 握着附有器柄的武器或工具时为 {@code true}
     */
    private static boolean wields(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();

        return !held.isEmpty() && RelicAttachment.isAttached(held, ModItems.KINETIC_HILT);
    }

    /**
     * 一份连击记录。
     *
     * @param target   正在打的那个目标
     * @param hits     已经连中几下
     * @param lastTick 上一次打中是在哪一刻
     */
    private record Combo(UUID target, int hits, long lastTick) {
    }
}
