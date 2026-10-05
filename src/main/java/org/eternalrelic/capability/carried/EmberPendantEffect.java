package org.eternalrelic.capability.carried;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.AttackDamage;

/**
 * 「余烬反扑」能力：带着余烬吊坠的人血量低于四成半时再挨一下，吊坠便在身周炸开一圈，
 * 把凑上来的敌人一起掀开、点着，同时给主人临时罩上一层抗火。
 *
 * <p>它<b>不替玩家挡伤害</b>：这一击照常挨，只是让围着打的人也要付一点代价——
 * 与回响之环（把整击挡下）和荆棘之誓（把掉的血扎回去）合起来，是本模组第三种「挨打时出手」。
 * 三者的分工可以这样记：<b>回响之环挡、荆棘之誓还、余烬吊坠炸</b>。</p>
 *
 * <p><b>为什么只有命悬一线时才炸</b>：这件遗物要的是「被围住快打死时的最后一次机会」，
 * 因此门槛是血量比例而不是挨打的次数。四成半这条线定的是「这一下再挨下去就要出事」，
 * 比三成留出更多余量——因为它同时还要替玩家挡掉随后的火。</p>
 *
 * <p><b>只炸敌对生物</b>：判断用原版自带的「敌对生物」标记（{@code Monster}），
 * 因此玩家、队友、宠物、村民、以及别的模组加进来的友好生物一律不受影响。
 * 这一点是制作者明确的取舍——一件在混战里会误伤队友的遗物，没人敢带。
 * 玩家自己也不在受害者之列，所以那 35 点伤害不会反过来把自己一起带走。</p>
 *
 * <p><b>它是第一件带耐久的遗物</b>：每炸一次耗一点，共 21 次；用尽之后不会消失，
 * 而是换成「黯淡的余烬吊坠」，在工作台上与附魔之瓶合成一次便可补回三分之一耐久
 * （见 {@link org.eternalrelic.recipe.EmberPendantRepairRecipe}）。这一点与回响之环的
 * 「碎裂」、守夜之瞳的「耗尽」同一条思路——<b>不让玩家白丢一件遗物</b>。</p>
 */
public final class EmberPendantEffect {

    /** 触发所需的血量比例：低于这个比例才算「命悬一线」。四成半。 */
    private static final float TRIGGER_HEALTH_RATIO = 0.45F;

    /** 炸开的半径（格）。以玩家为圆心，向上向下同样按这个半径算。 */
    private static final double RADIUS = 5.0D;

    /** 每个被炸到的敌人挨多少点伤害。35 点 = 17.5 颗心。 */
    private static final float DAMAGE = 35.0F;

    /** 被炸到的敌人会烧起来多少秒。 */
    private static final int BURN_SECONDS = 10;

    /** 炸开的同时给主人多少刻的抗火。200 刻 = 10 秒。 */
    private static final int FIRE_RESISTANCE_TICKS = 200;

    /** 掀开的力度。数越大推得越远、飞得越高。 */
    private static final double KNOCKBACK = 1.6D;

    /** 往上掀的分量，让人被炸得离地而不是贴着地面滑出去。 */
    private static final double KNOCKBACK_LIFT = 0.6D;

    /** 出手之后的冷却。700 刻 = 35 秒。 */
    private static final int COOLDOWN_TICKS = 700;

    /** 每隔多少刻清一次已经过期的冷却记录。1200 刻 = 1 分钟。 */
    private static final int CLEANUP_INTERVAL_TICKS = 1200;

    /**
     * 记录每位玩家何时可以再次引爆：玩家编号 → 冷却结束时的世界刻数。
     *
     * <p>与附魔兔脚同一套规矩：记在内存里、<b>不随下线清除</b>（否则退出重进就能立刻再炸一次）。
     * 已知代价是服务器重启会把这 35 秒放过去，可以接受。
     * 耐久则完全不同——它记在<b>吊坠自己身上</b>，丢掉、送人、放进箱子都跟着走。</p>
     */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private EmberPendantEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上挨打判定与冷却清理的回调。
     *
     * <p>用的是「伤害即将落下」这个判定点：游戏在 1.20.1 没有提供「伤害已经落下之后」的事件，
     * 而本能力对伤害本身毫无意见（既不加也不减），因此在落下之前动手与之后动手没有区别。</p>
     */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(EmberPendantEffect::allowDamage);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CLEANUP_INTERVAL_TICKS != 0) {
                return;
            }

            // 过期的记录与「没有这条记录」完全等价（判定那行比的是世界总刻数），
            // 清掉它只是别让这张表随着玩家来来去去一直涨
            long now = server.getOverworld().getTime();
            READY_AT.entrySet().removeIf(entry -> entry.getValue() <= now);
        });
    }

    /**
     * 在伤害即将落下时决定要不要引爆。
     *
     * <p>无论是否出手都返回 {@code true}：这件遗物从不让伤害落空。</p>
     *
     * @param entity 受伤的实体
     * @param source 伤害来源
     * @param amount 这一击本身的伤害数值（本能力不使用，但必须与事件签名一致）
     * @return 恒为 {@code true}，让伤害照常结算
     */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        if (player.isDead() || player.isInvulnerableTo(source)) {
            return true;
        }

        if (!AttackDamage.isAttack(source)) {
            return true;
        }

        // 刚挨过打的短时间里游戏本来就还会挡掉后续伤害（无敌帧）。这段时间内不引爆，
        // 免得把 35 秒冷却与一点耐久耗在一下根本没落下来的攻击上
        if (player.hurtTime > 0) {
            return true;
        }

        // 门槛按**这一击落下之前**的血量算：玩家此刻还剩多少血，才是「命悬一线」的实情
        if (player.getHealth() > player.getMaxHealth() * TRIGGER_HEALTH_RATIO) {
            return true;
        }

        // 取的是背包里那一块吊坠的**原件**：引爆之后要往它身上扣一点耐久。
        // 用 firstOf 而不是 inEffect，是因为要拿到可写入的那一格；
        // 这件遗物不可附着，因此两者在"带没带"上没有差别
        ItemStack pendant = CarriedStacks.firstOf(player, ModItems.EMBER_PENDANT);
        if (pendant.isEmpty()) {
            return true;
        }

        long now = player.getServerWorld().getTime();
        if (now < READY_AT.getOrDefault(player.getUuid(), 0L)) {
            return true;
        }

        READY_AT.put(player.getUuid(), now + COOLDOWN_TICKS);
        detonate(player);
        consume(pendant, player);
        return true;
    }

    /**
     * 在玩家身周炸开一圈：范围内的敌对生物各挨一记爆炸伤害、被点着、并被推离玩家；
     * 主人自己则得到一层临时抗火。
     *
     * <p>伤害交给原版结算（走爆炸这一种伤害类型），因此护甲、爆炸保护、抗性提升都会照常起作用——
     * 自己动手扣血会绕过这一整套，那是另一种做法，这里不需要。</p>
     *
     * @param player 引爆的玩家
     */
    private static void detonate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getPos();

        // 先用方盒子粗筛（原版按盒子取实体很快），再按实际距离筛一遍圆——
        // 盒子四个角比半径远出约四成，不补这一步会把角落里的敌人也炸到
        Box area = Box.of(center, RADIUS * 2.0D, RADIUS * 2.0D, RADIUS * 2.0D);

        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, area, EmberPendantEffect::isEnemy)) {
            double dx = victim.getX() - center.x;
            double dz = victim.getZ() - center.z;
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance > RADIUS) {
                continue;
            }

            victim.damage(world.getDamageSources().create(DamageTypes.EXPLOSION, player), DAMAGE);

            // 点着：爆炸本身只造成那一下伤害，火要单独点。已经烧着的会按这一次重新计时
            victim.setOnFireFor(BURN_SECONDS);

            // 掀开的方向是「离玩家越远越好」；除以距离是为了让近处的推力不至于大到离谱，
            // 再取一个下限，免得贴脸时除出一个巨大的数
            double push = KNOCKBACK / Math.max(distance, 1.0D);
            victim.addVelocity(dx * push, KNOCKBACK_LIFT, dz * push);
            victim.velocityModified = true;
        }

        // 给主人罩一层抗火：他自己不会被炸伤，但站在一圈火里仍旧会烧起来，
        // 这 10 秒正是让他从火里走出来的时间
        shieldFromFire(player);

        // 表现：一圈爆炸粒子加一记闷响。都用原版现成的，不新增贴图与音频资源
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5D, center.z,
                10, RADIUS * 0.4D, 0.3D, RADIUS * 0.4D, 0.0D);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * 给玩家一段抗火，**但不截短他自己那份更长的**。
     *
     * <p>与太阳 / 月亮纹章遇到的是同一个坑：直接给「10 秒抗火」会把玩家刚喝下去的那瓶
     * 八分钟抗火盖成十秒，等于遗物帮了倒忙。因此这里取「10 秒」与「原有剩余时间」中较长的
     * 那一个，等级也照原样保留。</p>
     *
     * @param player 引爆的玩家
     */
    private static void shieldFromFire(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        int duration = current == null ? FIRE_RESISTANCE_TICKS : Math.max(FIRE_RESISTANCE_TICKS, current.getDuration());
        int amplifier = current == null ? 0 : current.getAmplifier();

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, duration, amplifier));
    }

    /**
     * 扣掉一点耐久；扣到见底时把吊坠换成「黯淡」形态。
     *
     * <p><b>为什么不用原版的 {@code ItemStack#damage}</b>：那个方法在耐久耗尽时会把物品
     * <b>整个销毁</b>（工具用坏了就是那样）。这一件要的是「烧空了但还在」，
     * 因此自己数这一格，见底时换成另一个物品——回响之环碎裂走的也是这条路。</p>
     *
     * @param pendant 背包里那一块吊坠（原件）
     * @param player   携带它的玩家
     */
    private static void consume(ItemStack pendant, ServerPlayerEntity player) {
        int used = pendant.getDamage() + 1;

        if (used < pendant.getMaxDamage()) {
            pendant.setDamage(used);
            return;
        }

        replaceWith(player, pendant, ModItems.EMBER_PENDANT_DULL);

        player.sendMessage(Text.translatable("message.eternal_relic.ember_pendant_dull"), true);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * 把玩家身上的某一格换成另一样东西。
     *
     * <p>按<b>格子里的实例本身</b>去找，而不是按"第一件某某物品"去找：玩家身上可能同时带着
     * 两块吊坠（一块快烧空、一块是新的），按物品名找会替错那一块。传进来的
     * {@code original} 正是 {@link CarriedStacks#firstOf} 从背包里取出的那一个实例，
     * 因此这里比的是同一个对象。</p>
     *
     * @param player      目标玩家
     * @param original    要被换掉的那一格内容
     * @param replacement 换成什么
     */
    private static void replaceWith(ServerPlayerEntity player, ItemStack original, Item replacement) {
        PlayerInventory inventory = player.getInventory();

        if (replaceIn(inventory.main, original, replacement)) {
            return;
        }

        replaceIn(inventory.offHand, original, replacement);
    }

    /**
     * 在一组物品格里把指定的那一格换成另一样东西。
     *
     * @param slots       待查找的物品格
     * @param original    要被换掉的那一格内容
     * @param replacement 换成什么
     * @return 是否确实换掉了一格
     */
    private static boolean replaceIn(List<ItemStack> slots, ItemStack original, Item replacement) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == original) {
                slots.set(i, new ItemStack(replacement));
                return true;
            }
        }

        return false;
    }

    /**
     * @param entity 待判断的生物
     * @return 是不是「敌对生物」——用原版自己的标记判断，因此原版与别的模组的敌人都会认
     */
    private static boolean isEnemy(LivingEntity entity) {
        return entity.isAlive() && entity instanceof Monster;
    }
}
