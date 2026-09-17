package org.eternalrelic.capability.carried;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.network.SoulFlameNetwork;
import org.eternalrelic.registry.ModItems;
import org.eternalrelic.registry.ModParticleTypes;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.SoulWispParticleEffect;

/**
 * 引魂燃灯的「引魂」能力：携带期间每击杀一只生物攒一缕魂火，按 G 键一次倾泻出去。
 *
 * <p><b>魂火记在灯自己身上</b>（物品数据），不在服务器内存里另立玩家名单。这样把灯放进箱子、
 * 带过维度、死亡重生都不会错位，也不必在玩家进出时做搬运。</p>
 *
 * <p><b>释放分两层</b>：先是瞬间的一记冲击，范围内所有生物按魂火数受伤；随后在原地留下一片
 * 魂火，持续灼烧<b>除玩家以外</b>的生物——两层共用同一道过滤器，因此所有玩家（含放灯的人与
 * 队友）都不在受害者之列。这是有意为之的取舍：这套能力是<b>打怪不打人</b>的，砸进人堆里放
 * 也不会误伤同伴。魂火越多，冲击越重、范围越大、留得也越久——攒满再放与随手就放，
 * 是完全不同的两件事。</p>
 *
 * <p>灯只需被携带（主背包或副手）即可攒魂，不必装入身体，也没有代价。</p>
 */
public final class SoulLanternEffect {

    /** 魂火上限。攒满之后不再增长，直到释放。 */
    public static final int MAX_SOULS = 13;

    /** 每缕魂火带来的瞬时伤害。 */
    private static final float DAMAGE_PER_SOUL = 3.0F;

    /** 释放范围：一缕时与攒满时的半径（格）。中间按魂火数线性过渡。 */
    private static final double MIN_RADIUS = 2.0D;
    private static final double MAX_RADIUS = 10.0D;

    /** 每缕魂火让残留魂火多存在多少刻。0.45 秒 = 9 刻。 */
    private static final int ZONE_TICKS_PER_SOUL = 9;

    /**
     * 残留魂火每秒造成的伤害，按目标最大生命值的比例计算。
     *
     * <p>用比例而不是固定点数，是为了让它在强弱悬殊的目标之间都保持同样的威胁：
     * 一群小怪与一头首领站在同一片魂火里，掉血的速度是同一回事。</p>
     */
    private static final float ZONE_DAMAGE_RATIO = 0.04F;

    /** 残留魂火的结算间隔：每 20 刻（一秒）扣一次。 */
    private static final int ZONE_INTERVAL_TICKS = 20;

    /** 释放后的冷却刻数。1 分钟 = 1200 刻。 */
    private static final int COOLDOWN_TICKS = 1200;

    /** 魂火数在物品数据里的键名。 */
    public static final String SOULS_KEY = "Souls";

    /** 冷却结束时刻在物品数据里的键名（世界总刻数）。 */
    public static final String COOLDOWN_KEY = "CooldownEnd";

    /** 场上现存的魂火区域。 */
    private static final List<SoulFlame> FLAMES = new ArrayList<>();

    private SoulLanternEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用。
     *
     * <p>挂上两件事：击杀时攒魂，以及逐刻推进场上残留的魂火。</p>
     */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(SoulLanternEffect::onDeath);
        ServerTickEvents.END_SERVER_TICK.register(SoulLanternEffect::tickFlames);
    }

    // ==================== 攒魂 ====================

    /**
     * 玩家击杀生物时，给他携带的灯添一缕魂火。
     *
     * <p>冷却期间不攒——刚倾泻过一次，灯需要时间重新聚拢魂火。</p>
     *
     * @param entity 被击杀的生物
     * @param source 致死伤害的来源
     */
    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player) || entity == player) {
            return;
        }

        ItemStack lantern = carriedLantern(player);
        if (lantern == null) {
            return;
        }

        if (isCoolingDown(lantern, player.getServerWorld().getTime())) {
            return;
        }

        int souls = soulsOf(lantern);
        if (souls >= MAX_SOULS) {
            return;
        }

        setSouls(lantern, souls + 1);
        spawnWisp(player, entity);
    }

    /**
     * 从尸体上放出一缕飞向玩家的魂火。
     *
     * <p>魂火本身是客户端粒子，服务端只负责告诉它「追谁」——目标用实体编号传递，
     * 客户端据此每刻重新定位，因此玩家边走边打也能看着魂火追上来。</p>
     *
     * <p>生物与玩家不在同一维度时不放：跨维度的魂火既追不过去，也看不见。</p>
     *
     * @param player 收集魂火的玩家
     * @param corpse 刚倒下的生物
     */
    private static void spawnWisp(ServerPlayerEntity player, LivingEntity corpse) {
        if (corpse.getWorld() != player.getServerWorld()) {
            return;
        }

        player.getServerWorld().spawnParticles(
                new SoulWispParticleEffect(player.getId()),
                corpse.getX(),
                corpse.getY() + corpse.getHeight() * 0.5D,
                corpse.getZ(),
                1,
                0.1D, 0.1D, 0.1D,
                0.0D);
    }

    // ==================== 释放 ====================

    /**
     * 倾泻全部魂火：范围内生物挨一记冲击，原地再留下一片灼烧的魂火。
     *
     * <p>冲击这一下里已经并进了「落地灼烧」的那一跳，因此挨打的人一次就吃满两部分伤害；
     * 之后残留的魂火再从一秒后开始按秒结算。</p>
     *
     * <p>放完即进入一分钟冷却，期间既不能再放，也攒不到新的魂火。</p>
     *
     * @param player 释放的玩家
     * @return 是否确实释放了（没携带灯、一缕魂火都没有、或仍在冷却中时返回 {@code false}）
     */
    public static boolean cast(ServerPlayerEntity player) {
        ItemStack lantern = carriedLantern(player);
        if (lantern == null) {
            return false;
        }

        ServerWorld world = player.getServerWorld();
        if (isCoolingDown(lantern, world.getTime())) {
            return false;
        }

        int souls = soulsOf(lantern);
        if (souls <= 0) {
            return false;
        }

        setSouls(lantern, 0);
        lantern.getOrCreateNbt().putLong(COOLDOWN_KEY, world.getTime() + COOLDOWN_TICKS);

        Vec3d center = player.getPos();
        double radius = radiusFor(souls);

        // 第一层：瞬间冲击。所有玩家都不在受害者之列（含队友）——这套能力打怪不打人。
        //
        // 这一击里顺带把「落地灼烧」的那一下也算了进去：两者如果分成两次打，
        // 后打的那次会被游戏的受伤冷却按「新伤害是否更大」的规则吃掉一部分甚至全部，
        // 白白少一截。合成一次打出去，伤害一分不少。
        Box area = Box.of(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, area, SoulLanternEffect::isHostileTarget)) {
            if (victim.squaredDistanceTo(center) > radius * radius) {
                continue;
            }

            float impact = souls * DAMAGE_PER_SOUL + victim.getMaxHealth() * ZONE_DAMAGE_RATIO;
            victim.damage(world.getDamageSources().create(DamageTypes.MAGIC), impact);
        }

        // 第二层：原地留下魂火，魂火越多烧得越久。
        // 落地那一下已经并在上面了，因此这里的第一次按秒结算从一秒后开始
        long now = world.getTime();
        SoulFlame flame = new SoulFlame(world, center, radius,
                now + (long) souls * ZONE_TICKS_PER_SOUL,
                now + ZONE_INTERVAL_TICKS);

        FLAMES.add(flame);

        // 顺带告诉附近的客户端：这里落了一片魂火、还要烧这么多刻、范围有多大——
        // 它们据此播放低语，并在范围内随机位置冒出几声呻吟
        SoulFlameNetwork.broadcastFlame(world, center, souls * ZONE_TICKS_PER_SOUL, radius);

        // 由服务端广播：周围的玩家也听得到这一记释放
        world.playSound(null, center.x, center.y, center.z,
                ModSounds.SOUL_CAST, SoundCategory.PLAYERS, 1.0F, 1.0F);

        return true;
    }

    /**
     * @param souls 本次释放的魂火数
     * @return 对应的作用半径；一缕时最小，攒满时最大，中间线性过渡
     */
    private static double radiusFor(int souls) {
        double ratio = (double) (souls - 1) / (MAX_SOULS - 1);
        return MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * Math.max(0.0D, Math.min(1.0D, ratio));
    }

    /**
     * 魂火只烧玩家以外的活物——所有玩家都不在受害者之列（含队友）。
     *
     * @param entity 待判定的实体
     * @return 是否属于会被灼烧的对象
     */
    private static boolean isHostileTarget(LivingEntity entity) {
        return entity.isAlive() && !(entity instanceof PlayerEntity);
    }

    // ==================== 残留魂火 ====================

    /**
     * 逐刻推进场上的魂火：每秒灼烧一次区域内的生物，时间到了就熄灭。
     *
     * @param server 服务端实例
     */
    private static void tickFlames(MinecraftServer server) {
        if (FLAMES.isEmpty()) {
            return;
        }

        Iterator<SoulFlame> iterator = FLAMES.iterator();
        while (iterator.hasNext()) {
            SoulFlame flame = iterator.next();

            if (flame.world().getTime() >= flame.endTick()) {
                iterator.remove();
                continue;
            }

            spawnAmbientFlames(flame);

            if (flame.world().getTime() < flame.nextDamageTick()) {
                continue;
            }

            flame.setNextDamageTick(flame.nextDamageTick() + ZONE_INTERVAL_TICKS);
            damageIn(flame);
        }
    }

    /**
     * 对魂火范围内的生物结算一次灼烧。
     *
     * <p>伤害按各自最大生命值的比例算，因此强弱目标掉血的"比例"一致。所有玩家都不在受害者之列——
     * 放了灯的人与队友站在里面同样不受伤。</p>
     *
     * @param flame 待结算的区域
     */
    private static void damageIn(SoulFlame flame) {
        Vec3d center = flame.center();
        double radiusSquared = flame.radius() * flame.radius();

        Box area = Box.of(center, flame.radius() * 2.0D, flame.radius() * 2.0D, flame.radius() * 2.0D);
        for (LivingEntity victim : flame.world().getEntitiesByClass(
                LivingEntity.class, area, SoulLanternEffect::isHostileTarget)) {
            if (victim.squaredDistanceTo(center) > radiusSquared) {
                continue;
            }

            victim.damage(flame.world().getDamageSources().create(DamageTypes.MAGIC),
                    victim.getMaxHealth() * ZONE_DAMAGE_RATIO);
        }
    }

    // ---- 魂火的表现参数。纯观感，不影响任何伤害数值，可放心调 ----

    /** 每刻贴着地面烧起的火舌数量。 */
    private static final int AMBIENT_FLAME_COUNT = 8;

    /** 每刻往上窜的火星数量。给地面那层火一点高度，免得看着像贴地的贴纸。 */
    private static final int AMBIENT_SPARK_COUNT = 3;

    /** 每刻从地里冒出的鬼魂数量。 */
    private static final int AMBIENT_RISE_COUNT = 2;

    /** 每刻"地里忽然涌出一群鬼魂"的概率。均匀的毛毛雨反而假，偶尔来一波才像活的。 */
    private static final float AMBIENT_SURGE_CHANCE = 0.08F;

    /** 一次涌出时多冒几只。 */
    private static final int AMBIENT_SURGE_COUNT = 3;

    /**
     * 在魂火范围内撒出火舌、火星与从地里升起的鬼魂，让人一眼看出这块地还在烧。
     *
     * <p>分成三层是有讲究的：贴地的火舌负责交代"范围有多大"，往上窜的火星负责交代
     * "这里有多少能量"，而从地里升起的鬼魂负责交代"这片火里有东西"。只有第一层时，
     * 那片火看起来像一张贴在地上的发光贴纸。</p>
     *
     * <p>撒点范围都比实际半径略收一些：粒子自己还会往外飘一点，正好把边界补圆。</p>
     *
     * @param flame 待表现的区域
     */
    private static void spawnAmbientFlames(SoulFlame flame) {
        ServerWorld world = flame.world();
        Vec3d center = flame.center();
        double radius = flame.radius();
        Random random = world.getRandom();

        // 第一层：贴地烧起来的火舌
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y + 0.12D, center.z,
                AMBIENT_FLAME_COUNT,
                radius * 0.60D, 0.08D, radius * 0.60D,
                0.02D);

        // 第二层：往上窜的火星，给这片火一点高度
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.x, center.y + 0.35D, center.z,
                AMBIENT_SPARK_COUNT,
                radius * 0.45D, 0.25D, radius * 0.45D,
                0.05D);

        // 第三层：从地里升起、缓缓往上飘的鬼魂
        world.spawnParticles(ModParticleTypes.soulRise(),
                center.x, center.y + 0.05D, center.z,
                AMBIENT_RISE_COUNT,
                radius * 0.65D, 0.02D, radius * 0.65D,
                0.0D);

        // 偶尔来一波：地里忽然涌出一群，比均匀地一直冒更像回事
        if (random.nextFloat() < AMBIENT_SURGE_CHANCE) {
            world.spawnParticles(ModParticleTypes.soulRise(),
                    center.x, center.y + 0.05D, center.z,
                    AMBIENT_SURGE_COUNT,
                    radius * 0.30D, 0.02D, radius * 0.30D,
                    0.0D);
        }
    }

    // ==================== 携带与物品数据 ====================

    /**
     * 找出玩家携带的引魂燃灯。
     *
     * <p>主背包与副手都算——灯拿在手上、放在背包里同样管用。携带多件时取最先遇到的那一件。</p>
     *
     * @param player 目标玩家
     * @return 灯的物品堆；没有携带时返回 {@code null}
     */
    private static ItemStack carriedLantern(PlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (isLantern(stack)) {
                return stack;
            }
        }

        for (ItemStack stack : player.getInventory().offHand) {
            if (isLantern(stack)) {
                return stack;
            }
        }

        return null;
    }

    /**
     * 判断一件物品是不是引魂燃灯。
     *
     * <p>灯是这套能力唯一的触发物，因此这个判定就是攒魂与释放两道流程的入口条件。</p>
     *
     * @param stack 待判定的物品堆
     * @return 是否为引魂燃灯
     */
    private static boolean isLantern(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(ModItems.SOUL_LANTERN);
    }

    /**
     * 读出灯里攒下的魂火数。
     *
     * @param stack 灯的物品堆
     * @return 魂火数；没有记录时为 0
     */
    public static int soulsOf(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getInt(SOULS_KEY);
    }

    /**
     * 判断灯是否还在冷却中。
     *
     * <p>冷却记的是「结束时刻」而不是「剩余刻数」，因此不受读取频率影响，
     * 世界时间本身一路向前，跨维度与重登都不会算错。</p>
     *
     * @param stack 灯的物品堆
     * @param now   当前的世界总刻数
     * @return 是否仍在冷却
     */
    public static boolean isCoolingDown(ItemStack stack, long now) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(COOLDOWN_KEY) && now < nbt.getLong(COOLDOWN_KEY);
    }

    /**
     * @param stack 灯的物品堆
     * @param now   当前的世界总刻数
     * @return 冷却还剩多少刻；不在冷却中时为 0
     */
    public static long cooldownRemainingTicks(ItemStack stack, long now) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(COOLDOWN_KEY)) {
            return 0L;
        }

        return Math.max(0L, nbt.getLong(COOLDOWN_KEY) - now);
    }

    /**
     * 写入魂火数。
     *
     * @param stack  灯的物品堆
     * @param souls  新的魂火数
     */
    private static void setSouls(ItemStack stack, int souls) {
        stack.getOrCreateNbt().putInt(SOULS_KEY, Math.max(0, Math.min(MAX_SOULS, souls)));
    }

    /**
     * 一片仍在燃烧的魂火。
     *
     * <p>「下次结算时刻」是可变的：每次烧完就往后推一秒，因此结算节奏与区域何时生成无关，
     * 也不受服务器卡顿造成的刻数跳变影响。</p>
     */
    private static final class SoulFlame {

        private final ServerWorld world;
        private final Vec3d center;
        private final double radius;
        private final long endTick;
        private long nextDamageTick;

        /**
         * @param world          魂火所在的服务端世界
         * @param center         魂火中心
         * @param radius         作用半径（格）
         * @param endTick        这片火什么时候熄灭（世界总刻数）
         * @param nextDamageTick 下一次按秒结算是什么时候（世界总刻数）；首跳已并进释放时的冲击，
         *                       因此它比 {@code endTick} 近，且此后每结算一次就往后推一秒
         */
        SoulFlame(ServerWorld world, Vec3d center, double radius, long endTick, long nextDamageTick) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.endTick = endTick;
            this.nextDamageTick = nextDamageTick;
        }

        ServerWorld world() {
            return this.world;
        }

        Vec3d center() {
            return this.center;
        }

        double radius() {
            return this.radius;
        }

        long endTick() {
            return this.endTick;
        }

        long nextDamageTick() {
            return this.nextDamageTick;
        }

        void setNextDamageTick(long tick) {
            this.nextDamageTick = tick;
        }
    }
}
