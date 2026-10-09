package org.eternalrelic.skill;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.energy.EveEnergy;

/**
 * 「破阵」—— 举着盾蓄力，松手朝准星方向冲出去，沿途撞伤并撞开所有目标。
 *
 * <p><b>怎么发动的</b>：客户端在「举着盾 + 按住左键」期间每两刻报一次蓄了多久，
 * 松手时再报一次「放开了」（见 {@code network/ShieldRushNetwork} 与
 * {@code client/ShieldRushCharge}）。服务端在这里校验并出手 ——
 * <b>有没有学会、冷却好没好、是不是真举着盾，全由服务端说了算</b>。</p>
 *
 * <h2>几处刻意的选择</h2>
 *
 * <ul>
 *   <li><b>冲刺期间要求一直举着盾</b>：一放下盾（松开右键）冲刺立刻结束。
 *       这不只是为了合理 —— 制作者要的「冲刺时正前方仍有盾牌效果」正是靠举盾实现的：
 *       原版 {@code blockedByShield} 的条件就是「正在举盾 + 伤害来自正面」，
 *       所以只要冲刺不打断举盾、并把身体朝向锁到冲刺方向，
 *       <b>格挡正面近战与弹开正面弓箭就自动成立，不必额外写一行判定</b>。</li>
 *   <li><b>朝准星，而不是朝移动方向</b>：方向取自玩家按下那一刻的视角（{@code getRotationVec}），
 *       之后锁定不再跟随。同时把身体 yaw 也转过去，免得「人朝前冲、盾朝旁边」。</li>
 *   <li><b>同一个目标只撞一次</b>：逐刻扫描会有很多重复命中，
 *       因此每次冲刺自带一张「这次撞过谁」的表（与余烬吊坠炸一圈时同一套做法）。</li>
 *   <li><b>速度逐刻设置并逐刻同步</b>：服务端改速度不会自动同步给客户端，
 *       不像二段跳只改一次、这里每刻都在改，因此每刻都要发一次速度包，
 *       否则客户端会一路橡皮筋。</li>
 * </ul>
 *
 * <p><b>状态都在服务器内存里</b>（正在冲谁、各自还剩几刻、冷却到什么时候）：
 * 冲刺只活在一瞬间，下线或重启等于「什么都没发生」，没有需要搬运的东西。</p>
 */
public final class ShieldRushEffect {

    /** 轻点一下至少冲多远（格）。 */
    private static final double MIN_DISTANCE = 4.0D;

    /** 蓄满冲多远（格）。 */
    private static final double MAX_DISTANCE = 15.0D;

    /** 蓄满要多久。<b>30 刻 = 1.5 秒</b>（制作者 2026-10-06 定）。 */
    private static final int FULL_CHARGE_TICKS = 30;

    /** 冲刺速度（格/刻）。因此「冲几格」就等于「冲几刻」。 */
    private static final double RUSH_SPEED = 1.0D;

    /**
     * 蓄力冲刺的伤害<b>看"已经冲出去多远"</b>，而不是看蓄了多久（制作者 2026-10-07 定）。
     *
     * <p>贴脸撞到只有 {@link #MIN_RUSH_DAMAGE} 点，一路冲到最远（{@link #MAX_DISTANCE} 格）才到
     * {@link #MAX_RUSH_DAMAGE} 点，中间按距离直线插值。于是：<b>只有蓄满力、又真的冲到底，才撞得出满伤</b>；
     * 站在近处的人天然吃得少，所以**不需要再按"第几个撞到"做递减**。</p>
     *
     * <p>⚠️ 撞人时至少已经冲出去 1 格，因此实战里最低那一档会略高于地板值（约 10.8）。</p>
     */
    private static final float MIN_RUSH_DAMAGE = 10.0F;

    /** 冲到最远时的伤害（主手盾）。 */
    private static final float MAX_RUSH_DAMAGE = 22.5F;

    /** 副手拿盾时的起步伤害 —— 与主手同一套算法，只是整体轻一档。 */
    private static final float MIN_OFFHAND_DAMAGE = 6.0F;

    /** 副手盾冲到最远时的伤害。 */
    private static final float MAX_OFFHAND_DAMAGE = 9.0F;

    /**
     * 伤害从起步涨到满值所对应的冲刺距离。
     *
     * <p>取"能冲的最远距离"（也就是蓄满力那一次的长度），因此<b>只有冲满全程才吃到满伤</b>。</p>
     */
    private static final double DAMAGE_SCALE_DISTANCE = MAX_DISTANCE;

    /**
     * 点按与蓄力的分界：<b>不大于这个刻数算点按</b>（走小冲刺）。
     *
     * <p>4 刻 = <b>0.2 秒</b>（制作者 2026-10-06 定）—— 短按一下往前窜三格，
     * 按住不放才进入蓄力。</p>
     */
    private static final int MINI_CHARGE_TICKS = 4;

    /** 小冲刺冲多远（格）。制作者 2026-10-07 从 3 格改成 <b>2 格</b>。 */
    private static final double MINI_DISTANCE = 2.0D;

    /** 小冲刺撞一下多少伤害 —— 固定值，与盾拿在哪只手无关（盾牌 5 点 × 2）。 */
    private static final float MINI_DAMAGE = 10.0F;

    /** 小冲刺花掉多少 EVE。制作者 2026-10-07 从 10 点提到 <b>15 点</b>。 */
    private static final int MINI_EVE_COST = 15;

    /**
     * 蓄力冲刺的冷却。<b>100 刻 = 5 秒</b>（制作者 2026-10-06 从 15 秒压到 5 秒）。
     *
     * <p>⚠️ <b>真正的瓶颈在能量，不在冷却</b>：能量平均每秒回 1.2 点，而蓄满一次要
     * {@link #MAX_EVE_COST} 点 —— 约 <b>42 秒</b>才攒得回来，5 秒冷却早就走完了。
     * 冷却、耗能、回复速率这三个数是一起起作用的，动其中一个都要回头看一眼另外两个。</p>
     */
    private static final int COOLDOWN_TICKS = 100;

    /**
     * 蓄力冲刺花掉多少 EVE：<b>最低 {@value #MIN_EVE_COST}、随蓄力涨到最高 {@value #MAX_EVE_COST}</b>
     * （制作者 2026-10-07 定；起初是固定 30 点）。
     *
     * <p>蓄得越久，冲得越远、撞得越狠，也越费能量 —— 三者是同一个蓄力进度。</p>
     *
     * <p><b>不够就冲不出去</b>：不扣能量、也不进冷却（免得白等），只在屏幕上提示一句。</p>
     */
    private static final int MIN_EVE_COST = 30;

    /** 蓄满时花掉多少 EVE。 */
    private static final int MAX_EVE_COST = 50;

    /**
     * 撞人判定在玩家碰撞箱外<b>水平</b>再放多少（格）。
     *
     * <p>玩家碰撞箱本身宽 0.6 格，因此 1.0 相当于<b>左右各多出一格</b>（合计 2.6 格宽）——
     * 冲过人群时几乎不会漏掉谁。原本是 0.6，制作者 2026-10-06 要求"加大一点，撞起来更爽"。</p>
     */
    private static final double HIT_PADDING = 1.0D;

    /**
     * 撞人判定在玩家碰撞箱外<b>竖直</b>再放多少（格）。
     *
     * <p>竖直刻意放得比水平窄：冲刺是"横扫过去"，头顶上飞过的蝙蝠、脚下蹦跶的小东西
     * 不该跟着一起挨撞。</p>
     */
    private static final double HIT_PADDING_VERTICAL = 0.4D;

    /** 被撞开的力量。 */
    private static final double KNOCKBACK = 0.8D;

    /** 蓄力时每隔几刻响一声。 */
    private static final int CHARGE_SOUND_INTERVAL_TICKS = 10;

    /** 正在冲刺的人：玩家编号 → 这一次冲刺。 */
    private static final Map<UUID, Rush> RUSHING = new HashMap<>();

    /** 冷却结束时刻：玩家编号 → 那一刻的世界刻数。 */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private ShieldRushEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上冲刺的逐刻推进。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ShieldRushEffect::advance);
    }

    /**
     * 受理客户端报来的那一下。
     *
     * @param player      报信的玩家
     * @param chargeTicks 已蓄力的刻数
     * @param released    {@code true} 表示松手冲刺；{@code false} 表示还在蓄力
     */
    public static void onClientReport(ServerPlayerEntity player, int chargeTicks, boolean released) {
        if (!ShieldRushSkill.hasLearned(player)) {
            return;
        }

        if (!released) {
            // 蓄力途中：正在冲的时候不必再给聚风的表现
            if (!RUSHING.containsKey(player.getUuid())) {
                showCharging(player, chargeTicks);
            }

            return;
        }

        start(player, chargeTicks);
    }

    /**
     * 冲出去。
     *
     * @param player      冲刺的玩家
     * @param chargeTicks 蓄了多少刻（决定冲多远）
     */
    private static void start(ServerPlayerEntity player, int chargeTicks) {
        if (RUSHING.containsKey(player.getUuid()) || !isHoldingShield(player)) {
            return;
        }

        long now = player.server.getOverworld().getTime();

        // 点按 = 小冲刺，按住 = 蓄力冲刺 —— 两者只差一个"按了多久"（制作者 2026-10-06 定）
        boolean mini = chargeTicks <= MINI_CHARGE_TICKS;

        // 冷却只管蓄力冲刺：小冲刺不占冷却，靠它自己那 15 点能量限速
        if (!mini && READY_AT.getOrDefault(player.getUuid(), 0L) > now) {
            player.sendMessage(Text.translatable("message.eternal_relic.shield_rush.cooling")
                    .formatted(Formatting.GRAY), true);
            return;
        }

        // 蓄力进度要先算出来：**耗能、距离、伤害三样都跟着它走**（蓄得越久，越远越狠也越费）
        double ratio = chargeRatio(chargeTicks);
        int cost = mini ? MINI_EVE_COST : eveCostFor(ratio);

        // EVE 能量不够就冲不出去：不扣、也不进冷却，只提示一句。
        // 放在冷却检查之后、正式开始之前 —— 扣能量的那一步自己会把新数值同步给 HUD
        if (!EveEnergy.spend(player, cost)) {
            player.sendMessage(Text.translatable("message.eternal_relic.shield_rush.no_energy")
                    .formatted(Formatting.GRAY), true);
            return;
        }

        double distance = mini
                ? MINI_DISTANCE
                : MIN_DISTANCE + (MAX_DISTANCE - MIN_DISTANCE) * ratio;

        Vec3d look = player.getRotationVec(1.0F);
        Vec3d direction = new Vec3d(look.x, 0.0D, look.z).normalize();

        // 伤害不在这一刻定死：它跟着"冲出去多远"走，所以每次撞人时现算（见 damageFor）
        RUSHING.put(player.getUuid(), new Rush(direction, mini, (int) Math.round(distance)));

        // 只有蓄力冲刺进冷却
        if (!mini) {
            READY_AT.put(player.getUuid(), now + COOLDOWN_TICKS);
        }

        // 把身体转到冲刺方向上：盾牌只护正面，人朝前冲、盾也得朝前
        player.setYaw((float) (MathHelper.atan2(direction.z, direction.x) * 180.0D / Math.PI) - 90.0F);
        player.velocityModified = true;

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.8F);
    }

    /**
     * 推进所有正在冲刺的人：推着走、撞人、撒尾迹、数剩余刻数。
     *
     * @param server 服务器
     */
    private static void advance(MinecraftServer server) {
        if (RUSHING.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Rush>> iterator = RUSHING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Rush> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Rush rush = entry.getValue();

            // 人没了、或者中途把盾放下了：冲刺立刻结束
            if (player == null || player.isDead() || !isHoldingShield(player)) {
                iterator.remove();
                continue;
            }

            Vec3d velocity = player.getVelocity();
            player.setVelocity(rush.direction.x * RUSH_SPEED, velocity.y, rush.direction.z * RUSH_SPEED);
            player.velocityModified = true;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

            // 先把"这一格已经冲出去了"记上，再撞人 —— 撞到时算伤害用的正是这个数
            rush.traveled++;

            hitNearby(player, rush);
            showTrail(player);

            rush.ticksLeft--;

            if (rush.ticksLeft <= 0) {
                iterator.remove();
            }
        }
    }

    /**
     * 撞开并撞伤身边的目标。同一次冲刺里，同一个目标只挨一下。
     *
     * @param player 冲刺的玩家
     * @param rush   这一次冲刺（带着「已经撞过谁」的表）
     */
    private static void hitNearby(ServerPlayerEntity player, Rush rush) {
        Box area = player.getBoundingBox().expand(HIT_PADDING, HIT_PADDING_VERTICAL, HIT_PADDING);
        DamageSource source = player.getDamageSources().playerAttack(player);

        for (LivingEntity target : player.getWorld()
                .getEntitiesByClass(LivingEntity.class, area, LivingEntity::isAlive)) {

            if (target == player || isFriendly(player, target) || !rush.alreadyHit.add(target.getUuid())) {
                continue;
            }

            target.damage(source, damageFor(player, rush));
            target.takeKnockback(KNOCKBACK, rush.direction.x, rush.direction.z);
        }
    }

    /**
     * 这一撞要不要放过对方 —— <b>自己人与自己人不撞</b>（制作者 2026-10-06 定）。
     *
     * <p>两类放过：<b>自己的宠物</b>（认你为主的狗、猫、鹦鹉、马驴骡骆驼都在内，判据是原版的
     * {@link Tameable} 接口）与<b>联机时的队友</b>（走原版自己的队伍判定
     * {@code isTeammate}，因此"谁算队友"完全跟着游戏里的队伍走，不必另立名单）。</p>
     *
     * <p>村民、路人、野生动物一律照撞 —— 那是"沿途撞开所有目标"的本意。</p>
     *
     * @param player 冲刺的玩家
     * @param target 被判定到的那个目标
     * @return 应当放过对方时返回 {@code true}
     */
    private static boolean isFriendly(ServerPlayerEntity player, LivingEntity target) {
        if (target instanceof Tameable tameable && player.getUuid().equals(tameable.getOwnerUuid())) {
            return true;
        }

        return player.isTeammate(target);
    }

    /**
     * @param player 目标玩家
     * @return 他此刻是不是举着一面盾
     */
    private static boolean isHoldingShield(ServerPlayerEntity player) {
        return player.isUsingItem() && player.getActiveItem().isOf(Items.SHIELD);
    }

    /**
     * 这一撞算多少伤害 —— <b>看已经冲出去多远</b>（制作者 2026-10-07 定）。
     *
     * <p><b>主手拿盾</b>：{@link #MIN_RUSH_DAMAGE}（贴脸）→ {@link #MAX_RUSH_DAMAGE}（冲到底），
     * 按"已冲距离 ÷ {@link #DAMAGE_SCALE_DISTANCE}"直线插值。</p>
     *
     * <p><b>副手拿盾</b>：{@link #MIN_OFFHAND_DAMAGE} → {@link #MAX_OFFHAND_DAMAGE}，同一套算法、
     * 整体轻一档 —— 副手盾本就是"顺手用的那面盾"。</p>
     *
     * <p><b>小冲刺不走这套</b>：它只冲两格，按距离算永远只有地板值，所以直接给固定的
     * {@link #MINI_DAMAGE}。</p>
     *
     * @param player 冲刺的玩家（用它现在手里拿的是哪只手的盾）
     * @param rush   这一次冲刺（里面有"已经冲出去几格"）
     * @return 这一撞的伤害
     */
    private static float damageFor(ServerPlayerEntity player, Rush rush) {
        if (rush.mini) {
            return MINI_DAMAGE;
        }

        double ratio = Math.min(1.0D, rush.traveled / DAMAGE_SCALE_DISTANCE);

        if (player.getMainHandStack().isOf(Items.SHIELD)) {
            return (float) (MIN_RUSH_DAMAGE + (MAX_RUSH_DAMAGE - MIN_RUSH_DAMAGE) * ratio);
        }

        return (float) (MIN_OFFHAND_DAMAGE + (MAX_OFFHAND_DAMAGE - MIN_OFFHAND_DAMAGE) * ratio);
    }

    /**
     * 蓄力进度：0 = 刚过点按阈值，1 = 蓄满。
     *
     * <p>冲多远与主手盾撞多狠都按它插值，于是"蓄得越久，冲得越远、撞得越狠"是同一个进度。</p>
     *
     * @param chargeTicks 蓄了多少刻
     * @return 0~1 之间的进度
     */
    private static double chargeRatio(int chargeTicks) {
        int span = FULL_CHARGE_TICKS - MINI_CHARGE_TICKS;
        double raw = (chargeTicks - MINI_CHARGE_TICKS) / (double) span;

        return Math.max(0.0D, Math.min(1.0D, raw));
    }

    /**
     * 蓄力冲刺花掉多少 EVE：按蓄力进度在 {@link #MIN_EVE_COST} ~ {@link #MAX_EVE_COST} 之间取整
     * （制作者 2026-10-07 定：最低 30、蓄满 50）。
     *
     * @param ratio 蓄力进度（0 = 刚过点按阈值，1 = 蓄满）
     * @return 这一次要扣的能量点数
     */
    private static int eveCostFor(double ratio) {
        return (int) Math.round(MIN_EVE_COST + (MAX_EVE_COST - MIN_EVE_COST) * ratio);
    }

    /**
     * 蓄力时的表现：脚边聚起风，越蓄越密，每十刻响一声，音调跟着往上走。
     *
     * @param player      蓄力的玩家
     * @param chargeTicks 已蓄力的刻数
     */
    private static void showCharging(ServerPlayerEntity player, int chargeTicks) {
        ServerWorld world = player.getServerWorld();
        double ratio = Math.min(chargeTicks, FULL_CHARGE_TICKS) / (double) FULL_CHARGE_TICKS;

        world.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.2D, player.getZ(),
                1 + (int) (ratio * 3.0D), 0.35D, 0.05D, 0.35D, 0.01D);

        if (chargeTicks % CHARGE_SOUND_INTERVAL_TICKS == 0) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS,
                    0.7F, 0.6F + (float) ratio);
        }
    }

    /**
     * 冲刺途中身后拖出的风迹。
     *
     * @param player 冲刺的玩家
     */
    private static void showTrail(ServerPlayerEntity player) {
        player.getServerWorld().spawnParticles(ParticleTypes.CLOUD,
                player.getX() - player.getVelocity().x, player.getY() + 0.4D,
                player.getZ() - player.getVelocity().z,
                2, 0.2D, 0.2D, 0.2D, 0.01D);
    }

    /** 一次冲刺的全部状态。 */
    private static final class Rush {

        /** 冲刺方向（水平，已归一化）。 */
        private final Vec3d direction;

        /** 这一趟是不是点按出来的<b>小冲刺</b> —— 小冲刺的伤害是固定值，不按距离算。 */
        private final boolean mini;

        /** 还剩几刻。按「冲几格就几刻」算。 */
        private int ticksLeft;

        /**
         * 已经冲出去几格 —— 从 1 开始（第一刻就撞到人时算冲了 1 格）。
         *
         * <p><b>伤害就是按它算的</b>（见 {@link ShieldRushEffect#damageFor}）：冲得越远、撞得越狠。
         * 冲刺速度取 1 格/刻，因此它每刻 +1，正好等于"已经冲过的格数"。</p>
         */
        private int traveled;

        /** 这一次已经撞过谁 —— 同一个目标只挨一下。 */
        private final Set<UUID> alreadyHit = new HashSet<>();

        private Rush(Vec3d direction, boolean mini, int ticksLeft) {
            this.direction = direction;
            this.mini = mini;
            this.ticksLeft = ticksLeft;
        }
    }
}
