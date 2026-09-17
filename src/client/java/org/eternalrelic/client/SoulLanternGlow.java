package org.eternalrelic.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;

import org.eternalrelic.capability.carried.SoulLanternEffect;
import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.SoulBurstParticleEffect;

/**
 * 引魂燃灯的蓝光 —— 灯里攒着魂火时，携带者身上会浮起一圈青蓝色的星点。
 *
 * <p><b>它只是"看起来在发光"，并不真的照亮地面。</b>游戏里的光是<b>方块</b>的属性，
 * 生物身上没有"发光"这个概念，原版没给实体留这个口子。想在身上做出光的观感只能靠粒子；
 * 真要照亮四周，就得依赖动态光源模组、或者往脚下偷偷塞隐形光源方块——后者是在改世界数据，
 * 代价太大，因此走了纯粒子这条路。</p>
 *
 * <p><b>星点数就是魂火数</b>：灯里攒了几缕，身上就同时浮着几颗，最多十三颗。于是灯真的像
 * "燃着"——攒到魂火才亮起来、倾泻出去就散掉，玩家不看物品格子也能从身上读出状态。
 * 做法是按粒子的寿命换算每刻该补几颗（见 {@link #AURA_LIFETIME_TICKS}），而不是一股脑地撒：
 * 撒得再多也只是一片糊，而"几缕魂火就几点光"是玩家能一眼数出来的。</p>
 *
 * <p><b>光晕只在自己屏幕上</b>：粒子由客户端生成，多人游戏里其他玩家看不到它。
 * 换来的是一点网络开销都没有——若走服务端广播，每秒几十个粒子包并不划算。</p>
 */
public final class SoulLanternGlow {

    /**
     * 一颗粒子的寿命（刻）。这个数<b>必须与星点粒子自己的寿命一致</b>——
     * 它决定"每刻补几颗才能维持住 N 颗同时存在"。
     *
     * <p>{@code SoulBurstParticle} 的寿命固定为「扩散 5 刻 + 停驻 6 刻」，改那边记得同步改这里。</p>
     */
    private static final float AURA_LIFETIME_TICKS = 11.0F;

    /** 星点离身体中轴多远：薄薄一圈贴着身子，而不是飘在远处。 */
    private static final double GLOW_RADIUS_MIN = 0.28D;
    private static final double GLOW_RADIUS_SPREAD = 0.26D;

    /** 星点从脚踝到胸口的高度范围。 */
    private static final double GLOW_HEIGHT_MIN = 0.15D;
    private static final double GLOW_HEIGHT_SPREAD = 1.00D;

    /** 每颗星点从出生点往上飘多远。出生点与圆心重合，因此它一律朝上走。 */
    private static final float AURA_RISE = 0.18F;

    /** 星点的体型倍率。比魂火被收进灯时胸口炸开的那一圈更细。 */
    private static final float AURA_SCALE = 0.6F;

    /** 攒下来的"该补几颗"零头。不足一颗时留到下一刻，免得魂火少时被取整抹平。 */
    private static float pendingParticles;

    private SoulLanternGlow() {
    }

    /**
     * 由客户端入口调用，挂上逐刻的检查。
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SoulLanternGlow::tick);
    }

    /**
     * 每刻按魂火数补上若干颗星点，使同时存在的数量刚好等于魂火数。
     *
     * @param client 客户端实例
     */
    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) {
            // 退出世界或读档途中：攒到一半的零头不该留到下一次进世界
            pendingParticles = 0.0F;
            return;
        }

        int souls = carriedSouls(player);
        if (souls <= 0) {
            // 灯里空了就彻底熄掉，省下那些粒子
            pendingParticles = 0.0F;
            return;
        }

        // 寿命 11 刻 = 同时存在的颗数 × (1/11) 颗每刻，因此这样补正好维持「魂火数」颗
        pendingParticles += souls / AURA_LIFETIME_TICKS;

        int count = (int) pendingParticles;
        pendingParticles -= count;

        for (int i = 0; i < count; i++) {
            spawnGlow(world, player);
        }
    }

    /**
     * 在身体周围浮起一颗星点。
     *
     * <p>出生点同时当作它自己的圆心，于是方向退化成"正上方"——每颗都从身上缓缓上浮，
     * 而不是朝四面八方炸开。</p>
     *
     * @param world  所在世界
     * @param player 携带灯的玩家
     */
    private static void spawnGlow(ClientWorld world, ClientPlayerEntity player) {
        Random random = world.getRandom();

        // 极坐标取点：落点均匀地围在身子周围，而不是总聚在某一侧
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = GLOW_RADIUS_MIN + random.nextDouble() * GLOW_RADIUS_SPREAD;

        double x = player.getX() + Math.cos(angle) * distance;
        double z = player.getZ() + Math.sin(angle) * distance;
        double y = player.getY() + GLOW_HEIGHT_MIN + random.nextDouble() * GLOW_HEIGHT_SPREAD;

        world.addParticle(
                new SoulBurstParticleEffect(x, y, z, AURA_RISE, AURA_SCALE),
                x, y, z,
                0.0D, 0.0D, 0.0D);
    }

    /**
     * 读出玩家携带的灯里攒了多少魂火。
     *
     * <p>携带多盏时取魂火最多的那一盏：灯各自记账，身上浮起来的应当是那盏最旺的。</p>
     *
     * @param player 目标玩家
     * @return 魂火数；没有携带灯、或灯是空的时候返回 0
     */
    private static int carriedSouls(ClientPlayerEntity player) {
        int most = 0;

        for (ItemStack stack : player.getInventory().main) {
            most = Math.max(most, soulsOf(stack));
        }

        for (ItemStack stack : player.getInventory().offHand) {
            most = Math.max(most, soulsOf(stack));
        }

        return most;
    }

    /**
     * @param stack 待检查的物品堆
     * @return 这一格若是引魂燃灯则返回它攒的魂火数，否则返回 0
     */
    private static int soulsOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(ModItems.SOUL_LANTERN)) {
            return 0;
        }

        return SoulLanternEffect.soulsOf(stack);
    }
}
