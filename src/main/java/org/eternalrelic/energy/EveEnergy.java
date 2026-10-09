package org.eternalrelic.energy;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.eternalrelic.network.EveEnergyNetwork;

/**
 * <h1>EVE 能量</h1>
 *
 * <p>玩家身上的一份「奇术余量」：<b>基础 100 点，平均每秒自然回复 1.2 点</b>
 * （制作者 2026-10-06 定，起初是每秒 3 点，当天按手感降了下来）。
 * 它目前只做一件事 —— 供给屏幕左下那根能量条显示；将来要用能量的东西
 * （技能、遗物效果）从这里 {@link #spend 扣}。</p>
 *
 * <h2>数值存在哪</h2>
 * <p>存在<b>记分板</b>上的一张无名次表里（{@code eternal_relic.eve}）。选它有两个理由：
 * 一是记分板天生就是"给玩家存一个整数"的东西，写进去就跟着存档走；
 * 二是它<b>不影响任何玩法</b> —— 若像"学会了没有"那样借用某个属性，
 * 能量会在 0~100 之间不停变，挂在幸运或护甲上都会当场把玩家变成怪物。</p>
 *
 * <p>那张表<b>不设显示位置</b>，因此不会出现在侧边栏上（玩家看不到它，
 * 只在 {@code /scoreboard objectives list} 里能查到名字）。</p>
 *
 * <h2>新玩家一上来是多少</h2>
 * <p>记分板上<b>查不到记录</b>时按满值（{@value #MAX}）处理 —— 刚进游戏的玩家、
 * 以及第一次装这个模组的老存档，都会直接拿到满格能量，而不是从 0 开始等回复。</p>
 *
 * <h2>客户端怎么知道</h2>
 * <p>数值一变就发一趟包（见 {@link EveEnergyNetwork}）：每秒的回复、以及任何消耗。
 * 玩家上线时另外补发一次。HUD 只负责画，不做任何计算。</p>
 */
public final class EveEnergy {

    /** 能量上限。 */
    public static final int MAX = 100;

    /** 每次回复多少点。 */
    private static final int REGEN_AMOUNT = 3;

    /**
     * 回复的间隔。<b>50 刻 = 2.5 秒</b>，因此平均<b>每秒 1.2 点</b>
     * （制作者 2026-10-06 定：起初是每秒 3 点，当天按手感降了下来）。
     *
     * <p><b>为什么不直接写"每秒 1.2 点"</b>：记分板只能存整数，1.2 写不进去。
     * 换算成「每 2.5 秒 +{@value #REGEN_AMOUNT}」之后，平均值正是 1.2/秒，而每一次都是一个整数。</p>
     */
    private static final int REGEN_INTERVAL_TICKS = 50;

    /** 吃一个普通金苹果补多少 EVE（制作者 2026-10-06 定）。 */
    private static final int GOLDEN_APPLE_RESTORE = 50;

    /** 吃一个附魔金苹果补多少 EVE —— 正好补满。 */
    private static final int ENCHANTED_GOLDEN_APPLE_RESTORE = 100;

    /**
     * 记分板上那张表的名字。
     *
     * <p>带 {@code eternal_relic.} 前缀，是为了在 {@code /scoreboard objectives list} 里
     * 一眼认出它是本模组的、而不是玩家自己建的。</p>
     */
    private static final String OBJECTIVE_NAME = "eternal_relic.eve";

    private EveEnergy() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，
     * 挂上「每秒回复」与「上线补发一次」。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EveEnergy::regenerate);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                EveEnergyNetwork.sendTo(handler.getPlayer(), get(handler.getPlayer())));
    }

    /**
     * 这位玩家此刻有多少能量。
     *
     * @param player 目标玩家
     * @return 当前能量点数（0 ~ {@link #MAX}）；记分板上还没有记录时按满值算
     */
    public static int get(ServerPlayerEntity player) {
        Scoreboard scoreboard = player.server.getScoreboard();
        ScoreboardObjective objective = objectiveOf(player.server);

        if (!scoreboard.playerHasObjective(player.getEntityName(), objective)) {
            return MAX;
        }

        return scoreboard.getPlayerScore(player.getEntityName(), objective).getScore();
    }

    /**
     * 把能量设成一个值（会夹在 0 ~ {@link #MAX} 之间），并立刻同步给客户端。
     *
     * @param player 目标玩家
     * @param value  新的能量点数
     */
    public static void set(ServerPlayerEntity player, int value) {
        int clamped = Math.max(0, Math.min(MAX, value));

        Scoreboard scoreboard = player.server.getScoreboard();
        ScoreboardPlayerScore score = scoreboard.getPlayerScore(player.getEntityName(), objectiveOf(player.server));

        score.setScore(clamped);
        scoreboard.updateScore(score);

        EveEnergyNetwork.sendTo(player, clamped);
    }

    /**
     * 花掉一些能量 —— 留给以后要用能量的东西。
     *
     * <p><b>不够就不扣、也不做任何事</b>，由调用方自己决定怎么提示玩家；
     * 这里若"扣成负数"或"扣一半"，都会让"能量不够"这个判断变得没法解释。</p>
     *
     * @param player 目标玩家
     * @param amount 想花掉的点数
     * @return 扣成功返回 {@code true}；能量不足返回 {@code false}（此时一点都没扣）
     */
    public static boolean spend(ServerPlayerEntity player, int amount) {
        int current = get(player);

        if (amount <= 0 || current < amount) {
            return false;
        }

        set(player, current - amount);
        return true;
    }

    /**
     * 吃下金苹果时补能量（制作者 2026-10-06 定）：普通金苹果 <b>+{@value #GOLDEN_APPLE_RESTORE}</b>、
     * 附魔金苹果 <b>+{@value #ENCHANTED_GOLDEN_APPLE_RESTORE}</b>（正好补满）。
     *
     * <p>由 {@code mixin/PlayerEntityMixin} 在玩家进食那一刻调用 —— 游戏没有"玩家吃了什么"的事件，
     * 只能挂在 {@code PlayerEntity#eatFood} 上（守夜之瞳的取下也挂在那同一处）。</p>
     *
     * <p>补的时候封顶 {@link #MAX}，所以附魔金苹果就是"直接补满"，多出来的部分不会浪费成负数或溢出。</p>
     *
     * @param player    吃下苹果的玩家
     * @param enchanted 吃的是不是附魔金苹果
     */
    public static void restoreFromGoldenApple(ServerPlayerEntity player, boolean enchanted) {
        restore(player, enchanted ? ENCHANTED_GOLDEN_APPLE_RESTORE : GOLDEN_APPLE_RESTORE);
    }

    /**
     * 补能量，封顶 {@link #MAX}。
     *
     * @param player 目标玩家
     * @param amount 补多少点
     */
    public static void restore(ServerPlayerEntity player, int amount) {
        set(player, get(player) + amount);
    }

    /**
     * 每秒给所有在线玩家回一点能量，满了就不再往上加。
     *
     * @param server 服务器
     */
    private static void regenerate(MinecraftServer server) {
        if (server.getTicks() % REGEN_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            int current = get(player);

            if (current >= MAX) {
                continue;
            }

            set(player, Math.min(MAX, current + REGEN_AMOUNT));
        }
    }

    /**
     * 取出（必要时新建）那张记分板表。
     *
     * @param server 服务器
     * @return 记录 EVE 能量的那张表
     */
    private static ScoreboardObjective objectiveOf(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective existing = scoreboard.getNullableObjective(OBJECTIVE_NAME);

        if (existing != null) {
            return existing;
        }

        return scoreboard.addObjective(OBJECTIVE_NAME, ScoreboardCriterion.DUMMY,
                Text.literal("EVE"), ScoreboardCriterion.RenderType.INTEGER);
    }
}
