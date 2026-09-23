package org.eternalrelic.bodypart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.PlayerEntity;

/**
 * <h1>「这一击打中哪儿」的临时便条</h1>
 *
 * <p>部位判定与伤害结算不是同一段代码：判定发生在伤害落下<b>之前</b>——弹射物是在撞上玩家的那一刻
 * （见 {@link BodyPartResolver}），近战是在挨打事件里按站位算（见 {@link MeleeBodyPartDetector}）；
 * 而「按部位少掉几点伤害」只能等到伤害真正结算时才做得了。两者之间隔着几行游戏自己的代码，
 * 没有参数可以递，于是在这里放一张极短的便条：判定时记下打在哪儿，结算时取走。</p>
 *
 * <p><b>便条只管当刻。</b>「判定发生了、伤害却没落下」的事是有的——雪球砸在身上不掉血、
 * 盾牌把整击挡下、玩家正好处在无敌帧里。那时候便条会留在桌上，若不管它，就变成
 * 「下一次挨打被莫名其妙减掉几点」。两道闸门挡住这种情况：取走便条时<b>立刻删掉</b>，
 * 并且只认<b>同一刻</b>记下的那一张（见 {@link #consume}）。</p>
 *
 * <p>每位玩家最多留一张便条，新的判定直接盖掉旧的，因此不会越积越多；
 * 取走的动作发生在每一次伤害结算里，所以正常游戏过程中这张桌子基本都是空的。</p>
 */
public final class RecentBodyPartHit {

    /** 玩家 → 刚记下的那张便条。 */
    private static final Map<UUID, Note> NOTES = new HashMap<>();

    private RecentBodyPartHit() {
    }

    /**
     * 记下这一击打在哪个部位，盖掉这位玩家上一张便条。
     *
     * <p>由部位判定的出口（{@link BodyPartHits}）在每次命中时调用，玩法代码不需要自己调。</p>
     *
     * @param player 被命中的玩家
     * @param part   打中的部位
     */
    public static void remember(PlayerEntity player, BodyPart part) {
        NOTES.put(player.getUuid(), new Note(part, player.getWorld().getTime()));
    }

    /**
     * 取走这位玩家刚记下的部位，并立刻删掉。
     *
     * <p>只有<b>同一刻</b>记下的才算数：更早的便条说明那一击并没有真的落下，
     * 这时候要把位置让给这一次真正生效的伤害。</p>
     *
     * @param player 挨打的玩家
     * @return 这一击打中的部位；没有有效便条时返回 {@code null}
     */
    public static BodyPart consume(PlayerEntity player) {
        Note note = NOTES.remove(player.getUuid());

        if (note == null) {
            return null;
        }

        return player.getWorld().getTime() == note.tick() ? note.part() : null;
    }

    /**
     * 一张便条：打在哪儿、哪一刻记的。
     *
     * @param part 打中的部位
     * @param tick 记下这张便条时的世界刻数
     */
    private record Note(BodyPart part, long tick) {
    }
}
