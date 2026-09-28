package org.eternalrelic.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import org.eternalrelic.registry.ModItems;

/**
 * 牧羊人铃铛的第一人称摇铃动作 —— 平时纹丝不动，摇响的那半秒里晃两下。
 *
 * <p><b>为什么平时不动、只在摇的时候动</b>：这只铃铛不是挂在身上的饰品，而是一件「拿起来摇」的
 * 家什。一直自己晃会显得它没被握住；改成摇了才晃之后，动作与声音落在同一刻，玩家一眼就知道
 * 那是自己摇出来的。</p>
 *
 * <p><b>节奏照着铃声来</b>：把那一记铃音的响度画成图，能看出它是两记明显的撞击——
 * 第一记在 0.03 秒、第二记在 0.33 秒（相隔 0.30 秒），之后是长长的余韵。
 * 因此摆动取<b>半个来回 = 0.3 秒</b>：铃铛从一侧摆到另一侧，两次摆到极值时各撞响一次，
 * 与音频对齐；摆完再用 0.25 秒缓缓回到中位，像手腕停下来那样。</p>
 *
 * <p><b>角度怎么算</b>：{@code 最大角 × cos(2πt / 0.6)}。取 cos 而不是 sin，是为了让动画
 * <b>一开头就在最大角</b>（对应音效开头那一记撞击），而不是从零慢慢摆上去——否则第一记响
 * 会落在一个还没开始动的铃铛上。</p>
 *
 * <p><b>它只在第一人称生效</b>：第三人称与别人眼里的姿态由模型自己的 {@code display} 决定，
 * 那一边不去插手。</p>
 *
 * <p><b>摆动是左右的</b>：绕模型的前后轴（Z 轴）转，因此从玩家眼里看就是铃铛以黑环顶端为支点
 * 左右倾摆——这支环像是被捏在手里，铃身在下头荡。</p>
 */
@Environment(EnvType.CLIENT)
public final class ShepherdBellSwing {

    /** 摆动一个完整来回的时长（秒）。两记撞响各占半程，因此撞响间隔是它的一半：0.30 秒。 */
    private static final float PERIOD_SECONDS = 0.6F;

    /** 摆动持续多久（秒）—— 半个来回，正好两记撞响。 */
    private static final float SWING_SECONDS = 0.3F;

    /** 摆完之后用多久回到中位（秒）。 */
    private static final float SETTLE_SECONDS = 0.25F;

    /** 摆到最边上时的角度（度）。比挂在身上那种轻晃大得多——这是「摇」，不是「晃」。 */
    private static final float MAX_DEGREES = 20.0F;

    /** 整个动作的总时长（毫秒）。 */
    private static final long TOTAL_MS = (long) ((SWING_SECONDS + SETTLE_SECONDS) * 1000.0F);

    /** 这一次摇铃是从哪一刻开始的（毫秒）。还没摇过时是一个很早的值，因此角度恒为 0。 */
    private static long startedAtMs = Long.MIN_VALUE;

    private ShepherdBellSwing() {
    }

    /**
     * 由 {@link org.eternalrelic.client.EternalRelicClient} 调用，挂上「右键铃铛就摇一下」这件事。
     *
     * <p><b>冷却必须在这里自己判一次，别指望原版替我们挡。</b>Fabric 这个「使用物品」口子挂得
     * 比原版的物品冷却检查<b>更早</b>——原版 {@code interactItem} 的顺序是「先发移动包
     * （Fabric 就挂在这一句之前）→ 稍后才判物品还在不在冷却里 → 再调用物品自己的 use」。
     * 因此冷却期间按右键，本回调照样会被叫到，只是服务端那边把这一击拒了：
     * <b>铃声不响，铃铛却照样晃</b>。这正是当初漏掉那一句时的症状。</p>
     */
    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient
                    && player.getStackInHand(hand).isOf(ModItems.SHEPHERD_BELL)
                    && !player.getItemCooldownManager().isCoolingDown(ModItems.SHEPHERD_BELL)) {
                trigger();
            }

            // 返回「不处理」，让原版照常把这件物品用下去
            return TypedActionResult.pass(ItemStack.EMPTY);
        });
    }

    /**
     * 从现在起摇一下。
     */
    public static void trigger() {
        startedAtMs = Util.getMeasuringTimeMs();
    }

    /**
     * @return 此刻铃铛该摆到的角度（度）。不在摇铃的那半秒里时返回 0 —— 也就是纹丝不动
     */
    public static float currentDegrees() {
        long elapsed = Util.getMeasuringTimeMs() - startedAtMs;
        if (elapsed < 0 || elapsed >= TOTAL_MS) {
            return 0.0F;
        }

        float seconds = elapsed / 1000.0F;

        if (seconds <= SWING_SECONDS) {
            // 从最大角摆向另一侧：开头与结尾都正好落在极值上，也就是两记撞响的位置
            return MAX_DEGREES * MathHelper.cos((float) (2.0 * Math.PI) * seconds / PERIOD_SECONDS);
        }

        // 收尾：从另一侧的极值缓缓回到中位。cos 在这两端的速度都是 0，因此接得上摆动那一截，
        // 也不会让铃铛「啪」地弹回原位
        float settle = (seconds - SWING_SECONDS) / SETTLE_SECONDS;
        return -MAX_DEGREES * MathHelper.cos(settle * (float) Math.PI / 2.0F);
    }
}
