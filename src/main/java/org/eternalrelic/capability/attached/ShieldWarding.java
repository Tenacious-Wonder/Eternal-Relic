package org.eternalrelic.capability.attached;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.bodypart.BodyPart;

/**
 * <h1>盾牌侧翼防护</h1>
 *
 * <p><b>拿在手上的盾，护的是自己那一侧</b>：主手的盾护右边、副手的盾护左边，
 * 从护着那一侧打来的攻击各少掉 {@value #REDUCTION} 点，远程还有
 * {@code 65%} 的概率被整个弹开。<b>爆头不算</b> —— 制作者的原话是「免疫头部以下的伤害」。</p>
 *
 * <h2>举起来反而不护侧面</h2>
 * <p>这条是刻意的：<b>举盾 = 专心正面</b>（原版自己的格挡就在那一刻生效：正面近战照挡、
 * 正面弓箭照弹），<b>放下盾 = 侧身护翼</b>。两者互斥，既不重复叠加，玩家也一眼能懂 ——
 * 举起来是"我要顶住正面"，放下是"我侧着身子护住翼侧"。因此这里第一件事就是
 * 看 {@link PlayerEntity#isBlocking()}，举着盾时整条侧翼防护不生效。</p>
 *
 * <h2>左右是按"从哪边来"算的，不是按"打中哪块"</h2>
 * <p>身上的八个部位里<b>只有两肩与两腿分左右</b>（正胸、腹、后背都不分），
 * 若按"打中哪块"来判断，右边胸口上的攻击就永远护不到。因此这里改用
 * <b>攻击的来源方位</b>：把「玩家朝向」与「来源方位」都压到水平面上求二维叉积，
 * 正负即左右。近战取攻击者的位置，远程取<b>箭飞来的反方向</b>（箭命中时已经贴在身上，
 * 位置不再说明问题，速度才说明问题）。</p>
 *
 * <h2>耐久按原版的规矩磨</h2>
 * <p>与原版盾牌格挡同一套：<b>伤害不足 3 点不磨盾</b>，够 3 点就按伤害取整地扣
 * （见 {@link #MIN_DAMAGE_TO_WEAR}）。因此挡大锤掉得快、挡小拳几乎不疼。</p>
 *
 * <h2>挡下时会报一声</h2>
 * <p>挡下与弹开都会在<b>屏幕上方</b>闪一句提示（不是聊天栏，免得刷屏）。
 * 挨打是高频事件，所以同一名玩家 {@value #NOTICE_INTERVAL_TICKS} 刻内只提示一次
 * —— 被一群怪围殴时不会满屏都是这句话。</p>
 */
public final class ShieldWarding {

    /** 护着那一侧时，每次少掉的伤害点数。 */
    private static final float REDUCTION = 3.0F;

    /** 远程打中护着那一侧时，把这一箭整个弹开的概率。 */
    private static final float DEFLECT_CHANCE = 0.65F;

    /**
     * 原版的规矩：伤害不到这个数就不磨盾。
     *
     * <p>照抄 {@code PlayerEntity#damageShield} 的口径，免得拿盾的人被一群小怪蹭到就报废一面盾。</p>
     */
    private static final float MIN_DAMAGE_TO_WEAR = 3.0F;

    /** 两次提示之间至少隔多少刻。10 刻 = 0.5 秒。 */
    private static final int NOTICE_INTERVAL_TICKS = 10;

    /** 表里超过这么久没更新的记录会被顺手清掉（刻）。 */
    private static final long NOTICE_KEEP_TICKS = 200L;

    /** 方位短到可以忽略时的门槛（向量长度平方）。 */
    private static final double DIRECTION_EPSILON = 1.0E-6D;

    /** 上一次给谁提示过、在什么时刻：玩家编号 → 世界刻数。 */
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    private ShieldWarding() {
    }

    /**
     * 这一击会不会被手上的盾挡下一部分。
     *
     * <p>只在服务端调用（伤害结算那一处已经滤掉了客户端）。</p>
     *
     * @param player 挨打的玩家
     * @param part   这一击打中的部位；没有有效便条时为 {@code null}
     * @param source 这一击的来源（用来算它从哪一侧来）
     * @param amount 原版刚算完的伤害（用来决定磨多少耐久）
     * @return 这一击应当少掉的点数；护不到、或者是爆头时返回 0
     */
    public static float blockFor(PlayerEntity player, BodyPart part, DamageSource source, float amount) {
        // 快速出口：这几种情况根本轮不到侧翼防护 —— 爆头不护、举着盾时专心正面、
        // 或者压根没有"打中哪儿"的便条。除了省一点计算，更要紧的是：
        // 算"这一击从哪边来"要读伤害来源的位置，而有些伤害根本没有位置（中毒、凋零），
        // 能不算就不算（第一版就是在那儿崩的）。
        if (part == null || part == BodyPart.HEAD || player.isBlocking()) {
            return 0.0F;
        }

        ItemStack shield = guardingShield(player, part, sourceDirection(player, source));

        if (shield.isEmpty()) {
            return 0.0F;
        }

        wear(shield, player, amount);
        notice(player, "message.eternal_relic.shield_blocked");

        return REDUCTION;
    }

    /**
     * 这一箭会不会被手上的盾整个弹开。
     *
     * <p>与 {@link ChestGuardEffect#deflects} 是同一件事的两种来源：那边是缝在胸甲上的护具，
     * 这边是拿在手上的盾。两边各自判、各掷各的骰子。</p>
     *
     * <p><b>本方法不消费那张「打中哪儿」的便条</b>：弹开只意味着这一箭整个不算，
     * 而便条还要留给伤害结算去算减伤（万一没弹开）。</p>
     *
     * @param player     挨这一箭的玩家
     * @param part       这一箭打中的部位
     * @param projectile 那一支箭（用它飞来的方向判断左右）
     * @return 这一箭是否被弹开
     */
    public static boolean deflects(PlayerEntity player, BodyPart part, ProjectileEntity projectile) {
        Vec3d incoming = projectile.getVelocity().multiply(-1.0D);

        if (guardingShield(player, part, incoming).isEmpty()) {
            return false;
        }

        if (player.getRandom().nextFloat() >= DEFLECT_CHANCE) {
            return false;
        }

        notice(player, "message.eternal_relic.shield_deflected");
        return true;
    }

    /**
     * 在屏幕上方闪一句提示（同一个人短时间内只提示一次）。
     *
     * @param player 挨打的那位
     * @param key    语言键
     */
    private static void notice(PlayerEntity player, String key) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        long now = serverPlayer.server.getOverworld().getTime();

        // 顺手清掉早就过期的记录，免得这张表随玩家人数一直长下去
        LAST_NOTICE.entrySet().removeIf(entry -> now - entry.getValue() > NOTICE_KEEP_TICKS);

        Long last = LAST_NOTICE.get(serverPlayer.getUuid());

        if (last != null && now - last < NOTICE_INTERVAL_TICKS) {
            return;
        }

        LAST_NOTICE.put(serverPlayer.getUuid(), now);
        serverPlayer.sendMessage(Text.translatable(key).formatted(Formatting.GRAY), true);
    }

    /**
     * 找出此刻「护着这一侧」的那面盾。
     *
     * <p>依次排除三种情况：<b>爆头</b>（头部以下才护）、<b>正举着盾</b>（那一刻专心正面）、
     * 以及<b>方位不明</b>（例如摔落、虚空这种没有来源方向的伤害）。</p>
     *
     * @param player    挨打的玩家
     * @param part      打中的部位
     * @param direction <b>来源方位</b>：从玩家指向攻击来源的那个方向（不必归一化）
     * @return 护着这一侧的那面盾；护不到时返回空堆
     */
    private static ItemStack guardingShield(PlayerEntity player, BodyPart part, Vec3d direction) {
        if (part == null || part == BodyPart.HEAD) {
            return ItemStack.EMPTY;
        }

        // 举着盾时不护侧面：那一刻是"专心正面"，交给原版自己的格挡
        if (player.isBlocking()) {
            return ItemStack.EMPTY;
        }

        Vec3d flat = new Vec3d(direction.x, 0.0D, direction.z);

        if (flat.lengthSquared() < DIRECTION_EPSILON) {
            return ItemStack.EMPTY;
        }

        flat = flat.normalize();

        Vec3d facing = player.getRotationVec(1.0F);
        Vec3d flatFacing = new Vec3d(facing.x, 0.0D, facing.z).normalize();

        // 二维叉积的 y 分量：正 = 来源在玩家的左手边，负 = 右手边。
        // 拿"朝北、敌人在西"验一下：facing = (0,0,−1)、来源 = (−1,0,0)，
        // 叉积 = (−1)×(−1) − 0×0 = +1 → 判为左侧，与游戏里"西就是朝北时的左手边"一致。
        boolean fromLeft = flatFacing.z * flat.x - flatFacing.x * flat.z > 0.0D;

        if (fromLeft) {
            // 左手边的攻击，由副手的盾接
            ItemStack off = player.getOffHandStack();
            return off.isOf(Items.SHIELD) ? off : ItemStack.EMPTY;
        }

        // 右手边的攻击，由主手的盾接
        ItemStack main = player.getMainHandStack();
        return main.isOf(Items.SHIELD) ? main : ItemStack.EMPTY;
    }

    /**
     * 算出这一击「从哪儿来」。
     *
     * <p>近战取攻击者或伤害来源的位置；远程取<b>弹射物速度的反方向</b> ——
     * 箭命中玩家时已经贴在身上，位置不再说明它是从哪边飞来的，速度才说明。</p>
     *
     * @param player 挨打的玩家
     * @param source 这一击的来源
     * @return 从玩家指向来源的方向；查不出来时返回零向量
     */
    private static Vec3d sourceDirection(PlayerEntity player, DamageSource source) {
        Entity direct = source.getSource();

        if (direct instanceof ProjectileEntity projectile) {
            Vec3d velocity = projectile.getVelocity();

            if (velocity.horizontalLengthSquared() > DIRECTION_EPSILON) {
                return velocity.multiply(-1.0D);
            }
        }

        Vec3d position = source.getPosition();

        // ⚠️ 有些伤害**根本没有位置** —— 中毒、凋零这类从体内发作的效果，
        // 它们的 DamageSource.getPosition() 返回的是 **null**，而不是零向量。
        // 第一版直接拿它去减坐标，结果就是"玩家吃下蜘蛛眼（中毒）之后当场把服务器打崩"：
        // 中毒每跳一次伤害都会走到这里，于是每一跳抛一次空指针。
        // 返回零向量之后，下面会判成"方位不明"→ 不挡，这正是对的：中毒不该被盾牌挡住。
        if (position == null) {
            return Vec3d.ZERO;
        }

        return position.subtract(player.getPos());
    }

    /**
     * 磨盾牌 —— 与原版盾牌格挡同一套规矩：伤害不足 3 点不磨，够了就按伤害取整地扣。
     *
     * @param shield 护着这一侧的那面盾
     * @param player 持盾的玩家
     * @param amount 这一击原本的伤害
     */
    private static void wear(ItemStack shield, PlayerEntity player, float amount) {
        if (amount < MIN_DAMAGE_TO_WEAR) {
            return;
        }

        // 盾在哪只手上：原版是照"正举着的那只手"去清的（它只处理举盾格挡），
        // 而这一条**只在没举盾时**生效，所以得自己认这只手 —— 认错了，盾牌耐久耗尽时
        // 会清错格子（手上还留着那面盾，或者把另一只手的东西弄没）。
        Hand hand = player.getStackInHand(Hand.MAIN_HAND) == shield ? Hand.MAIN_HAND : Hand.OFF_HAND;

        if (player instanceof ServerPlayerEntity serverPlayer) {
            shield.damage((int) amount, serverPlayer, broken -> broken.sendToolBreakStatus(hand));
        }
    }
}
