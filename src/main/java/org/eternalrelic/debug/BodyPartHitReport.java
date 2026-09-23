package org.eternalrelic.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import org.eternalrelic.bodypart.BodyPart;
import org.eternalrelic.bodypart.BodyPartHit;
import org.eternalrelic.bodypart.BodyPartHits;
import org.eternalrelic.bodypart.ProjectileBodyPartHit;

/**
 * <b>临时调试用</b>：把「这一击打中哪儿、实际扣了多少」打在受击者的聊天栏里。
 *
 * <p>部位判定与护肩减伤本来都是后台计算，游戏里什么都不显示，所以需要这么一段「看得见」的输出，
 * 才能核对判定准不准、肩甲到底挡下了几点。<b>它不是正式功能</b>——不再需要时，把本文件连同
 * {@link org.eternalrelic.EternalRelic#onInitialize()} 里那一行调用一起删掉即可，
 * 删完不会影响任何遗物的效果（它只读、不参与结算）。</p>
 *
 * <p><b>为什么分两步才凑得出一行</b>：打中哪儿在伤害结算<b>之前</b>就知道（近战按站位算、
 * 弹射物算命中点），而扣了多少要到结算<b>之中</b>才算得出来，中间隔着几行游戏代码。
 * 所以判定时先在这里留一张便条，等到 {@link org.eternalrelic.mixin.PlayerDamageMixin}
 * 把伤害减完，再喊一声把几个数凑成一行发出去。</p>
 *
 * <p><b>这张便条与护肩减伤用的那张是两套，刻意不共用</b>（另一张见 {@code bodypart.RecentBodyPartHit}）：
 * 调试代码要能整段删干净，不该与正式逻辑缠在一起。时效的规矩与那张相同——只认<b>同一刻</b>
 * 记下的便条，因为「判定发生了、伤害却没落下」的情况是有的，那种便条不能拿去凑数。</p>
 *
 * <p>输出长这样（方括号里是打中的部位，后面是这一击的账）：</p>
 *
 * <pre>
 * [受击] 左肩 (近战) · 3.9 → 1.4（肩甲 -2.5）
 * [受击] 头部 (弹射物 / 离脚底 1.42 格 / 身高 1.80) · 3.9
 * </pre>
 *
 * <p>没有肩甲护着（或没打中肩膀）时，只报一个数字，不出现箭头与括号。</p>
 */
public final class BodyPartHitReport {

    /** 玩家 → 刚记下的那张便条。 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private BodyPartHitReport() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上「打中哪儿」的订阅。
     */
    public static void register() {
        BodyPartHits.register(BodyPartHitReport::remember);
    }

    /**
     * 记下这一击打在哪个部位，盖掉这位玩家上一张便条。
     *
     * @param hit 一次命中
     */
    private static void remember(BodyPartHit hit) {
        PlayerEntity player = hit.player();
        PENDING.put(player.getUuid(), new Pending(hit, player.getWorld().getTime()));
    }

    /**
     * 把这次挨打的账报出来。由 {@link org.eternalrelic.mixin.PlayerDamageMixin} 在减完伤时调用。
     *
     * <p>没有便条（这一击没有部位可言，例如爆炸与摔落）、或便条已经过期时什么都不做——
     * 这条输出只负责「有部位的攻击」，不扩大到别的伤害上去。</p>
     *
     * @param player     挨打的玩家
     * @param original   原版算完之后、肩甲出手之前，这一击本来要扣多少
     * @param afterGuard 肩甲减完之后，这一击要扣多少
     * @param blocked    肩甲实际挡下的点数（夹到 0 之后的结果，因此可能与登记值不同）
     */
    public static void report(PlayerEntity player, float original, float afterGuard, float blocked) {
        Pending pending = PENDING.remove(player.getUuid());

        if (pending == null || player.getWorld().getTime() != pending.tick()) {
            return;
        }

        BodyPartHit hit = pending.hit();

        String damage = blocked > 0.0F
                ? String.format("%.1f §7→ §f%.1f §7（肩甲 -%.1f）", original, afterGuard, blocked)
                : String.format("%.1f", original);

        player.sendMessage(Text.literal(String.format("§e[受击] §f%s §7%s §7· %s",
                partName(hit.part()), detail(hit), damage)), false);
    }

    /**
     * 报出「这一箭被肩甲弹开了」。
     *
     * <p>被弹开的箭不会造成伤害，也就走不到伤害结算那一步，所以这一条不能等 {@link #report}
     * 来发——必须在这里当场报出来。不报的话，弹开在游戏里就完全看不见了：玩家只会觉得
     * "这一箭好像没射中"，而看不出是肩甲挡的。</p>
     *
     * <p>便条在这里就用不上了：这一箭不会有伤害结算来取它，留着也只会在下一刻过期。</p>
     *
     * @param hit 这次命中的记录
     */
    public static void reportDeflected(BodyPartHit hit) {
        PlayerEntity player = hit.player();
        PENDING.remove(player.getUuid());

        player.sendMessage(Text.literal(String.format("§e[受击] §f%s §7%s §7· §b箭被弹开了",
                partName(hit.part()), detail(hit))), false);
    }

    /**
     * 补充信息：弹射物报出打在离脚底多高，近战则只标一下来源
     * （近战没有命中点，部位是按站位算出来的，报不出高度）。
     *
     * @param hit 一次命中
     * @return 附加在部位后面的那一小段说明
     */
    private static String detail(BodyPartHit hit) {
        if (hit instanceof ProjectileBodyPartHit projectileHit) {
            return String.format("(弹射物 / 离脚底 %.2f 格 / 身高 %.2f)",
                    projectileHit.hitPos().y - hit.player().getY(),
                    hit.player().getHeight());
        }

        return "(近战)";
    }

    /**
     * @param part 部位
     * @return 部位的中文名
     */
    private static String partName(BodyPart part) {
        return switch (part) {
            case HEAD -> "头部";
            case LEFT_SHOULDER -> "左肩";
            case RIGHT_SHOULDER -> "右肩";
            case CHEST -> "正胸";
            case ABDOMEN -> "腹部";
            case BACK -> "后背";
            case LEGS -> "腿部";
        };
    }

    /**
     * 一张便条：这一击的完整命中记录、以及哪一刻记的。
     *
     * @param hit  这次命中的记录（含打在哪儿、是谁射的等）
     * @param tick 记下便条时的世界刻数
     */
    private record Pending(BodyPartHit hit, long tick) {
    }
}
