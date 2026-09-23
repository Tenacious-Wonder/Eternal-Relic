package org.eternalrelic.capability.carried;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.DayNightEmblems;

/**
 * 「时段加护」能力：白天或夜晚，给带着纹章的人挂上两条状态效果。
 *
 * <p>与另外几类能力的区别：{@code CarriedRelicEffect} 改的是玩家属性，
 * {@code DamageWardEffect} 管的是挨打那一下，{@code EnchantedRabbitFootEffect}
 * 是挨打时给一段速度；而这里<b>既不改属性也不看事件</b>，只按「世界现在是白天还是夜晚」
 * 持续给两条状态效果，时段一过就停手、效果自行到期。</p>
 *
 * <p><b>带在身上就生效</b>：放主背包、副手，或者缝在<b>正穿着 / 正拿着</b>的装备与盾牌上，都算
 * （见 {@link CarriedStacks#inEffect}）——与勇气纹章、斑驳的铜甲片同一条口径。
 * 给哪两条效果、管哪一段，全部登记在 {@link DayNightEmblems} 里，本类不认具体是哪枚纹章。</p>
 *
 * <p><b>效果怎么给</b>：</p>
 * <ul>
 *   <li>平时只给 <b>I 级</b>（{@link #BASE_AMPLIFIER}），每 {@link #DURATION_TICKS} 刻续一次；</li>
 *   <li>玩家自己弄到了更强的效果时，<b>在它之上送一级</b>——但只送这一下，用完就没了；</li>
 *   <li>我们这份时长固定 {@link #DURATION_TICKS} 刻，<b>不跟着玩家原有的剩余时长走</b>，
 *       所以玩家自己的强力增益几时结束就几时结束，纹章不会替它续命。</li>
 * </ul>
 *
 * <p>⚠️ <b>为什么必须记一张「送到过哪一级」的表</b>：这类能力每隔一小段时间就要续一次期，
 * 否则效果会断；而若每次都按「有就再抬一级」来算，等级会每秒涨一级、瞬间失控。
 * 因此每（效果）都记下<b>我们送出去过的那一级</b>（{@link #GRANTED_PEAK}），
 * 只有确认玩家自己拿到了更高的效果时才再送一级。改动 {@link #grant} 之前先读那段。</p>
 *
 * <p>⚠️ <b>另有一件事同样必须做</b>：上面那张表只活在内存里，而玩家一退出世界，
 * 游戏会把他的整份数据（<b>含身上的状态效果</b>）存下来，下次进世界原样还回来
 * （单人世界走的是原版 {@code PlayerManager.loadPlayerData} 里「主机玩家」那一条）。
 * 少了 {@link #claimExisting 进世界时的那次认领}，纹章就会把自己上一轮留下的效果
 * 当成「玩家自己弄到的更强效果」，于是再抬一级——<b>每进出一次世界涨一级，没有上限</b>。</p>
 */
public final class DayNightEmblemEffect {

    /** 每隔多少刻核对并续期一次。20 刻 = 1 秒。 */
    private static final int CHECK_INTERVAL_TICKS = 20;

    /**
     * 每次续期给到的持续时间。240 刻 = 12 秒。
     *
     * <p>必须明显长于 {@link #CHECK_INTERVAL_TICKS}，否则效果会在两次核对之间断掉；
     * 也要保持在 200 刻以上——原版在剩余时间不足 10 秒时会让状态图标闪烁。</p>
     */
    private static final int DURATION_TICKS = 240;

    /** 身上本来没有这条效果时给到的等级。0 在游戏里写作「I 级」。 */
    private static final int BASE_AMPLIFIER = 0;

    /**
     * 记录每位玩家身上「本能力曾经给到过的最高一级」：玩家编号 → （效果 → 等级）。
     *
     * <p>它不是「此刻该给几级」，而是「上一轮送出去的那一级」，只用来认人：
     * 玩家当前的等级比它更高，说明玩家自己变强了，可以再送一级；
     * 不比它高，就老老实实只维持自己那一份。玩家身上回落到只剩基准等级时这张记录归零
     * （见 {@link #grant}）。</p>
     */
    private static final Map<UUID, Map<StatusEffect, Integer>> GRANTED_PEAK = new HashMap<>();

    /**
     * 刚进入世界、还等着「认领身上已有等级」的玩家（见 {@link #claimExisting}）。
     *
     * <p>进世界时先登记在这里，等下一次核对才真正去认领——那一刻玩家数据已经恢复完毕、
     * 背包也已经在手上，不会读到一半的状态。</p>
     */
    private static final Set<UUID> PENDING_CLAIM = new HashSet<>();

    private DayNightEmblemEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上按刻核对的回调。
     *
     * <p>每 {@link #CHECK_INTERVAL_TICKS} 刻核对一次：满足条件就把两条效果续上，
     * 不满足（纹章收进箱子了、或者时段过去了）就<b>不再续期</b>——效果会在十几秒内自行到期。
     * 刻意不主动移除：玩家身上那条效果很可能是他自己喝药水得来的，
     * 一把抹掉会把他的药水也一起抹掉。</p>
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                refresh(player);
            }
        });

        // 刚进入世界的人先登记一笔：等下一次核对时，把他身上已经有的等级认领下来
        // （理由见 claimExisting）。不在这一刻直接认领，是因为玩家数据与背包要到
        // 进世界之后才完全就位，早一步去读可能只读到一半
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PENDING_CLAIM.add(handler.getPlayer().getUuid()));

        // 记录按玩家存；人走了就清掉，避免这两张表随着玩家来来去去一直涨。
        // 注意清掉的只是「内存里那本账」——真正要紧的等级已经写进玩家数据，
        // 下次进来由 claimExisting 重新认领
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GRANTED_PEAK.remove(handler.getPlayer().getUuid());
            PENDING_CLAIM.remove(handler.getPlayer().getUuid());
        });
    }

    /**
     * 给一名玩家续上他此刻该有的时段效果。
     *
     * <p>玩家刚进世界后的第一次核对，会先把他身上已经有的等级认领下来
     * （见 {@link #claimExisting}），这一步必须在续期之前做。</p>
     *
     * @param player 目标玩家
     */
    private static void refresh(ServerPlayerEntity player) {
        if (PENDING_CLAIM.remove(player.getUuid())) {
            claimExisting(player);
        }

        for (Map.Entry<Item, DayNightEmblems.Blessing> entry : DayNightEmblems.all().entrySet()) {
            DayNightEmblems.Blessing blessing = entry.getValue();

            // 带在身上就算：背包里放着本体，或缝在正穿着 / 正拿着的装备与盾牌上（副手的盾牌算）
            if (!CarriedStacks.inEffect(player, entry.getKey())) {
                continue;
            }

            if (!blessing.time().matches(player.getServerWorld())) {
                continue;
            }

            for (StatusEffect effect : blessing.effects()) {
                grant(player, effect);
            }
        }
    }

    /**
     * 认领玩家身上已经有的时段效果：把它们记成「本能力已经给到这一级」。
     *
     * <p>⚠️ <b>为什么非有这一步不可</b>：玩家退出世界时，游戏会把他身上那份时段效果
     * 连同其它数据一起存下来；下次进世界又原样还给他（单人世界走的是原版
     * {@code PlayerManager.loadPlayerData} 里「主机玩家」那一条）。而 {@link #GRANTED_PEAK}
     * 这本账只活在内存里，人一走就空了——于是续期时会看到「身上已经有生命恢复，
     * 但账上没记」，按规矩当成玩家自己弄到的增益，<b>再抬一级</b>。
     * 每进出一次世界涨一级，几趟下来就是生命恢复 IV、速度 IV。</p>
     *
     * <p>认领之后，同样的等级在账上有了着落，后续核对不会再送一级；
     * 而且这一份效果该几时结束就几时结束，纹章不会替它续命。</p>
     *
     * <p><b>只认领「进世界那一刻就已经在身上」的效果</b>：玩家进世界之后自己喝药水、
     * 或者站到信标旁边拿到的更强效果，仍然按原设计在原有等级上再送一级。</p>
     *
     * @param player 刚进入世界的玩家
     */
    private static void claimExisting(ServerPlayerEntity player) {
        Map<StatusEffect, Integer> peaks = GRANTED_PEAK.computeIfAbsent(player.getUuid(), id -> new HashMap<>());

        for (Map.Entry<Item, DayNightEmblems.Blessing> entry : DayNightEmblems.all().entrySet()) {
            if (!CarriedStacks.inEffect(player, entry.getKey())) {
                continue;
            }

            for (StatusEffect effect : entry.getValue().effects()) {
                StatusEffectInstance current = player.getStatusEffect(effect);
                if (current != null) {
                    peaks.put(effect, current.getAmplifier());
                }
            }
        }
    }

    /**
     * 给玩家续上「我们自己那一份」效果：固定 {@link #BASE_AMPLIFIER} 级，固定 {@link #DURATION_TICKS} 刻。
     *
     * <p><b>三条规矩，改动之前先读</b>：</p>
     * <ol>
     *   <li><b>平时只给基准等级。</b>玩家自己弄来的那一份比基准高时，我们给的低等级顶不掉它
     *       （原版的效果只升不降），玩家照旧享受自己那一份；我们这一份则排队等着，
     *       等它走完再接手。因此纹章永远不会把玩家的临时增益锁死在自己身上。</li>
     *   <li><b>只有玩家自己变强时才送一级，而且只送这一下。</b>送到哪儿由 {@link #GRANTED_PEAK} 记着：
     *       送出去的那一级用完就没了，不会一轮接一轮续下去。</li>
     *   <li><b>时长永远只给固定的一份，绝不跟着玩家原有的剩余时长走。</b>这一点是关键——
     *       原版只在「新给的这一份更长」时才改动时长，所以固定给 {@link #DURATION_TICKS}
     *       既能给自己续期，又<b>绝不会把玩家的效果越续越长</b>。这里曾经取的是
     *       「自己这份与玩家剩余时长里较长的那个」，后果是：一瓶三十秒的强力药水，
     *       只要戴上纹章就会被永久续下去，玩家再也不用管它什么时候结束。</li>
     * </ol>
     *
     * @param player 目标玩家
     * @param effect 要续的那条效果
     */
    private static void grant(ServerPlayerEntity player, StatusEffect effect) {
        Map<StatusEffect, Integer> peaks = GRANTED_PEAK.computeIfAbsent(player.getUuid(), id -> new HashMap<>());
        StatusEffectInstance current = player.getStatusEffect(effect);

        int peak = peaks.getOrDefault(effect, BASE_AMPLIFIER);
        int currentAmplifier = current == null ? -1 : current.getAmplifier();

        int amplifier;

        if (currentAmplifier > peak) {
            // 玩家自己弄到了比我们送过的还高的一级 → 送他一级，并记下送到哪儿为止
            amplifier = currentAmplifier + 1;
            peak = amplifier;
        } else {
            // 其余情况都只维持自己那一份
            amplifier = BASE_AMPLIFIER;

            // 玩家身上已经回落到只剩基准等级，说明先前送出去的那一级走完了，记录随之归零
            if (currentAmplifier <= BASE_AMPLIFIER) {
                peak = BASE_AMPLIFIER;
            }
        }

        player.addStatusEffect(new StatusEffectInstance(effect, DURATION_TICKS, amplifier));
        peaks.put(effect, peak);
    }
}
