package org.eternalrelic.relic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.registry.ModItems;

/**
 * 「物品保全」能力：钉了永恒纹章的物品不会被销毁，掉进虚空还会被送回主人身边。
 *
 * <p><b>游戏里其实只有三条销毁路径</b>（查自 1.20.1 的物品实体源码），因此这里也只对应三件事：</p>
 *
 * <ul>
 *   <li><b>火烧与岩浆</b>——游戏在点火之前会先问一句「这件东西怕不怕火」，怕才点、不怕就跳过，
 *       所以只要回答「不怕」，两条一起解决（见 {@code ItemEntityMixin#isFireImmune}）；</li>
 *   <li><b>爆炸与仙人掌刺</b>——两者都走物品实体的同一个「受伤」入口，扣的是物品那 5 点血，
 *       扣完才消失（见 {@code ItemEntityMixin#damage}）；</li>
 *   <li><b>虚空</b>——掉到世界底部以下 64 格时被删除，由本类的 {@link #rescueFromVoid} 接住。</li>
 * </ul>
 *
 * <p><b>为什么「受伤」那一处是整类免疫，而不是逐条列出爆炸与仙人掌</b>：逐条列伤害类型意味着
 * 漏掉一条就是一个洞，而掉落物本来就不吃玩家的攻击（{@code ItemEntity#isAttackable()} 为
 * {@code false}），能被伤到的只有环境——因此整类免疫不会挡住任何正常玩法，却不会再漏。</p>
 *
 * <p><b>关于「会不会变成拿不回来的幽灵实体」</b>：只阻止删除是不够的，物品会继续往世界底下
 * 无限下坠，玩家既看不见也捞不回。所以虚空这一条做的是<b>捞回来</b>：优先送回主人身边，
 * 主人不在（离线、或物品不是玩家丢的）则送回世界出生点上方。</p>
 */
public final class ItemPreservation {

    private ItemPreservation() {
    }

    /**
     * 这件物品是否受保全：它身上钉着永恒纹章。
     *
     * <p><b>它必须便宜</b>：物品实体的每刻逻辑会问到它三次（防火判断、受伤判断、年龄检查），
     * 而「掉进虚空」那一处更是对<b>全世界每个实体</b>每刻都会走到一次判断。
     * 之所以不必额外做缓存：绝大多数物品身上压根没有附着数据，读取会在「有没有那个标签」这一步
     * 就返回，不会去解析物品编号；真正带纹章的掉落物数量很少，剩下的开销可以忽略。</p>
     *
     * @param stack 待检查的物品
     * @return 钉着纹章时返回 {@code true}
     */
    public static boolean protects(ItemStack stack) {
        return RelicAttachment.isAttached(stack, ModItems.ETERNAL_EMBLEM);
    }

    /**
     * 把一件掉进虚空的受保物品捞回来。
     *
     * <p>只在服务端动手：位置由服务端说了算，客户端照着同步即可。</p>
     *
     * <p>落点选主人身上而不是「虚空上方的某个位置」，是因为物品本来就是从主人那里掉下去的，
     * 送回原处最容易被找到；主人不在线时退回世界出生点，至少是一个玩家到得了的地方。</p>
     *
     * @param entity 掉进虚空的物品实体
     * @return 是否真的捞了回来（不是受保物品时为 {@code false}，由游戏按原样删除）
     */
    public static boolean rescueFromVoid(ItemEntity entity) {
        if (!protects(entity.getStack()) || !(entity.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        Entity owner = entity.getOwner();
        double x;
        double y;
        double z;

        if (owner != null) {
            x = owner.getX();
            y = owner.getY() + 1.0D;
            z = owner.getZ();
        } else {
            BlockPos spawn = world.getSpawnPos();
            x = spawn.getX() + 0.5D;
            y = spawn.getY() + 1.0D;
            z = spawn.getZ() + 0.5D;
        }

        entity.refreshPositionAndAngles(x, y, z, entity.getYaw(), entity.getPitch());
        entity.setVelocity(Vec3d.ZERO);
        return true;
    }
}
