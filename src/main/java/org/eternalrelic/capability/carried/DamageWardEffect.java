package org.eternalrelic.capability.carried;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.registry.ModRelics;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.DamageWard;
import org.eternalrelic.relic.EchoRingShardParticleEffect;
import org.eternalrelic.relic.RelicDefinition;

/**
 * 「守护」能力：携带守护遗物的玩家每次被攻击时，由遗物替他出手一次 ——
 * 伤害不超过门槛的整击挡下，更重的攻击改为化出金心替他分摊 —— 随后遗物碎裂冷却。
 *
 * <p>这一层只认遗物表里的数据，不认具体是哪件遗物：哪些遗物能守护、门槛多少、
 * 出手之后碎裂成什么、冷却多久，全部写在 {@link DamageWard} 里，
 * 因此新增一件守护遗物只需登记，不必改动这里。</p>
 *
 * <p><b>重击为什么改用金心</b>：游戏提供的事件只能选择让这一击生效或者整个取消，
 * 没有「改成一半」这种档位；若自己动手扣血，又会绕过护甲、击退、无敌时间这一整套结算。
 * 当场给玩家若干点吸收（金心）就省事得多——吸收在伤害结算的最后一步替玩家挡掉一部分，
 * 其余流程一步不差地照常走完，玩家还能从多出来的一排金心上直接看出遗物刚刚出了手。</p>
 *
 * <p><b>什么样的伤害才算「被攻击」</b>：有人打的（近战、弓箭、有主的爆炸）都算；
 * 此外，没有攻击者但同属「炸过来、砸下来」的物理伤害也算——红石引爆的 TNT 与末影水晶
 * 就没有攻击者，铁砧、落石与钟乳石则是靠砸而不是靠谁打。由状态效果造成的伤害
 * （中毒、凋零、瞬间伤害药水、龙息），以及守卫者光束、幻术师尖牙这类魔法弹道
 * ——它们走的是 {@code indirectMagic}、还带着攻击者，同样不算——都不触发；
 * 摔落、岩浆、仙人掌、虚空这类伤害也不例外。</p>
 *
 * <p><b>两处已知的副作用，都不至于出乱子，但改动这里时要知道</b>：
 * 一是给出去的金心若没被这一击用完就会留在身上（吸收本来就是会留存的），
 * 因此穿着强甲挨重击时偶尔能剩几颗当缓冲；
 * 二是游戏在玩家刚受伤的极短时间内会自行挡掉后续伤害，而本能力在伤害落下之前就已判定，
 * 所以那段时间里仍可能被消耗掉一块——代价只是一次冷却，不会让玩家少挡一次真正的致命伤。</p>
 */
public final class DamageWardEffect {

    /** 每隔多少刻核对一次碎裂的遗物是否已经恢复。5 刻约为 0.25 秒。 */
    private static final int RECOVER_INTERVAL_TICKS = 5;

    /** 碎裂形态上记录「何时恢复」的标签名。 */
    private static final String READY_AT_KEY = "ReadyAt";

    /** 碎裂形态 → 它恢复后的原物品。挂上能力时由遗物表填好。 */
    private static final Map<Item, Item> ORIGINAL_FORMS = new HashMap<>();

    /** 碎裂时迸出的碎屑颗数下限与随机增量。 */
    private static final int SHARD_COUNT_MIN = 24;
    private static final int SHARD_COUNT_SPREAD = 9;

    /** 碎屑的出生点离球心多近；客户端据此定出它向外扩散的方向。 */
    private static final double SHARD_SPAWN_OFFSET = 0.05D;

    /** 碎屑向外扩散的距离；每颗会在此基础上上下浮动，实际落在 2~3 格之间。 */
    private static final float SHARD_SPREAD_RADIUS = 2.5F;

    private DamageWardEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用。
     *
     * <p>先按遗物表建好「碎裂形态 → 原物品」的对照，再挂上挡伤害的回调与逐刻核对恢复的回调。</p>
     */
    public static void register() {
        // 这份对照表必须在遗物表登记完成之后建立，因此本方法要排在 ModItems.register() 之后
        for (RelicDefinition definition : ModRelics.all()) {
            DamageWard ward = definition.ward();
            if (ward != null) {
                ORIGINAL_FORMS.put(ward.drainedForm(), definition.item());
            }
        }

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(DamageWardEffect::allowDamage);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % RECOVER_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                recover(player);
            }
        });
    }

    /**
     * 决定一次即将落下的伤害要不要放行。
     *
     * <p>只有「确实被攻击」且「身上带着尚未碎裂的守护遗物」时才出手；
     * 其余情况一律放行，不消耗遗物。</p>
     *
     * @param entity 受伤的实体
     * @param source 伤害来源
     * @param amount 这一击本身的伤害数值
     * @return {@code true} 表示让伤害照常结算；{@code false} 表示这一击由遗物出手处理
     */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }

        if (player.isDead() || player.isInvulnerableTo(source)) {
            return true;
        }

        if (!isAttack(source)) {
            return true;
        }

        RelicDefinition relic = wardRelicCarriedBy(player);
        if (relic == null) {
            return true;
        }

        // 提示文字里报的是「这件遗物自己的名字」，因此需要连物品一起拿到
        DamageWard ward = relic.ward();
        Text relicName = relic.item().getName();

        shatter(player, ward.cooldownTicks());
        playWardEffect(player);

        if (amount <= ward.blockThreshold()) {
            player.sendMessage(Text.translatable("message.eternal_relic.ward_blocked", relicName), false);
            return false;
        }

        // 挡不干净的重击：当场化出金心，让吸收在结算的最后一步替玩家分摊。
        // 吸收若没被这一击用完会留在身上（游戏本就如此），因此穿强甲时可能剩几颗
        player.setAbsorptionAmount(player.getAbsorptionAmount() + ward.absorptionFor(amount));
        player.sendMessage(Text.translatable("message.eternal_relic.ward_gilded", relicName), false);
        return true;
    }

    /**
     * 播放遗物出手时的听视觉表现：一记碎裂声，加上一圈向外炸开的白色碎屑。
     *
     * <p>声音由服务端从玩家身上广播，因此附近的玩家也听得到；
     * 碎屑则逐颗生成，每颗的出生方向都不同，客户端才知道各自该往哪散。</p>
     *
     * @param player 受守护的玩家
     */
    private static void playWardEffect(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        world.playSound(
                null,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                ModSounds.ECHO_RING_WARD,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F);

        spawnShards(player, world);
    }

    /**
     * 以玩家胸口为球心撒出一圈向外扩散的碎屑。
     *
     * <p>球面上的方向按「方位角随意取、俯仰角按 cos 均匀取」来抽，否则碎屑会往头顶与脚下堆，
     * 看上去变成上下各一串，而不是从身上向四面八方炸开。出生点紧贴球心，
     * 客户端拿「出生点相对球心的指向」当作每颗碎屑自己的扩散方向。</p>
     *
     * @param player 受守护的玩家
     * @param world  玩家所在的服务端世界
     */
    private static void spawnShards(ServerPlayerEntity player, ServerWorld world) {
        double chestX = player.getX();
        double chestY = player.getEyeY() - 0.35D;
        double chestZ = player.getZ();
        Random random = player.getRandom();

        int count = SHARD_COUNT_MIN + random.nextInt(SHARD_COUNT_SPREAD);

        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double cosPhi = random.nextDouble() * 2.0D - 1.0D;
            double sinPhi = Math.sqrt(Math.max(0.0D, 1.0D - cosPhi * cosPhi));

            double x = chestX + sinPhi * Math.cos(theta) * SHARD_SPAWN_OFFSET;
            double y = chestY + cosPhi * SHARD_SPAWN_OFFSET;
            double z = chestZ + sinPhi * Math.sin(theta) * SHARD_SPAWN_OFFSET;

            // 一次只生成一颗：每颗的出生方向都不同，没法合并成一次批量生成
            world.spawnParticles(
                    new EchoRingShardParticleEffect(chestX, chestY, chestZ, SHARD_SPREAD_RADIUS),
                    x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 判断这次伤害算不算「被攻击」。
     *
     * <p>先排除由状态效果造成的伤害类型，以及守卫者光束、幻术师尖牙这类走
     * {@code indirectMagic} 的魔法弹道——这一步不能省：女巫灌下的伤害药水、守卫者的光束
     * 同样带着施法者，只看有没有攻击者会把它们误当成普通攻击。</p>
     *
     * <p>剩下的分两类都算：一类是有人打的（近战、弓箭、有主的爆炸）；
     * 另一类是没有主、但同属「炸过来、砸下来」的物理伤害。后者必须单独列出来，
     * 否则红石引爆的 TNT 会因为查不到攻击者而被放行。</p>
     *
     * @param source 伤害来源
     * @return 是否算一次攻击
     */
    private static boolean isAttack(DamageSource source) {
        if (source.isOf(DamageTypes.MAGIC)
                || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.WITHER)
                || source.isOf(DamageTypes.DRAGON_BREATH)) {
            return false;
        }

        if (source.getAttacker() != null) {
            return true;
        }

        return source.isIn(DamageTypeTags.IS_EXPLOSION)
                || source.isOf(DamageTypes.UNATTRIBUTED_FIREBALL)
                || source.isOf(DamageTypes.FALLING_ANVIL)
                || source.isOf(DamageTypes.FALLING_BLOCK)
                || source.isOf(DamageTypes.FALLING_STALACTITE);
    }

    /**
     * 找出玩家携带的、可以出手的守护遗物。
     *
     * <p>主背包与副手都会计入——玩家把遗物换到副手时它应当继续管用。
     * 携带多件时取最先遇到的那一件出手，其余的留待下次。</p>
     *
     * <p>返回整条遗物定义而不只是那份配置：出手时的提示文字要报出**这件遗物自己的名字**。
     * 把名字写死成某一件，第二件守护遗物上线时玩家就会看到错误的名字。</p>
     *
     * @param player 目标玩家
     * @return 可以出手的那件守护遗物；没有携带时返回 {@code null}
     */
    private static RelicDefinition wardRelicCarriedBy(ServerPlayerEntity player) {
        for (ItemStack stack : carriedStacks(player)) {
            RelicDefinition definition = ModRelics.definitionOf(stack.getItem());
            if (definition != null && definition.hasDamageWard()) {
                return definition;
            }
        }

        return null;
    }

    /**
     * 收集玩家身上会参与判定的物品格。
     *
     * <p>返回的是一份只读用的副本：这里只拿来查看，改动背包请直接操作
     * {@link PlayerInventory} 的字段，否则改的只是副本。</p>
     *
     * @param player 目标玩家
     * @return 主背包与副手的物品堆
     */
    private static List<ItemStack> carriedStacks(PlayerEntity player) {
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().main);
        stacks.addAll(player.getInventory().offHand);
        return stacks;
    }

    /**
     * 让一件守护遗物碎裂：换成碎裂形态，并在它身上记下何时恢复。
     *
     * @param player        受守护的玩家
     * @param cooldownTicks 需要经过多少刻才恢复
     */
    private static void shatter(ServerPlayerEntity player, int cooldownTicks) {
        // 用世界总刻数计时：它一直向前走，也不受 /time set 改动影响，
        // 因此跨维度与重登都不会让冷却长短错乱
        long readyAt = player.getServerWorld().getTime() + cooldownTicks;
        PlayerInventory inventory = player.getInventory();

        if (shatterIn(inventory.main, readyAt)) {
            return;
        }

        shatterIn(inventory.offHand, readyAt);
    }

    /**
     * 在一组物品格里找出第一件守护遗物并让它碎裂。
     *
     * @param slots   待检查的物品格
     * @param readyAt 恢复时刻
     * @return 是否确实让一件遗物碎裂了
     */
    private static boolean shatterIn(List<ItemStack> slots, long readyAt) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }

            RelicDefinition definition = ModRelics.definitionOf(stack.getItem());
            if (definition == null || !definition.hasDamageWard()) {
                continue;
            }

            ItemStack drained = new ItemStack(definition.ward().drainedForm());
            drained.getOrCreateNbt().putLong(READY_AT_KEY, readyAt);
            slots.set(i, drained);
            return true;
        }

        return false;
    }

    /**
     * 核对一名玩家背包里的碎裂遗物，到点的换回原样。
     *
     * <p>恢复时刻存在物品自己身上，因此退出重进、跨维度、死亡重生都不会让它错乱。
     * 冷却同样只在玩家带着它时才推进：放进箱子里的碎裂遗物要等拿回背包才会恢复。</p>
     *
     * @param player 目标玩家
     */
    private static void recover(ServerPlayerEntity player) {
        long now = player.getServerWorld().getTime();
        PlayerInventory inventory = player.getInventory();

        recoverIn(inventory.main, now);
        recoverIn(inventory.offHand, now);
    }

    /**
     * 在一组物品格里把已经到点的碎裂遗物换回原样。
     *
     * @param slots 待检查的物品格
     * @param now   当前时刻
     */
    private static void recoverIn(List<ItemStack> slots, long now) {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }

            Item original = ORIGINAL_FORMS.get(stack.getItem());
            if (original == null) {
                continue;
            }

            NbtCompound nbt = stack.getNbt();
            if (nbt != null && nbt.contains(READY_AT_KEY) && now < nbt.getLong(READY_AT_KEY)) {
                continue;
            }

            slots.set(i, new ItemStack(original));
        }
    }
}
