package org.eternalrelic.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 戴克森应急制氧球 —— 拿在手上右键，换来五分钟的水下呼吸。
 *
 * <p><b>它借的是原版「水下呼吸」效果</b>，因此氧气条的表现在游戏各处都完全一致
 * （水下不消耗氧气、界面上的气泡图标、与其它来源的水下呼吸互相叠加的规矩），
 * 本模组一行都不用自己写。</p>
 *
 * <p><b>为什么是「给一段效果」而不是「让氧气永远不掉」</b>：制作者要的是"应急"，
 * 五分钟够潜下去办一件事，回到水面后它就该失效；而效果一过、冷却还没走完的那五分钟空窗，
 * 正是"应急"两个字的分量所在。</p>
 *
 * <p>冷却交给游戏的物品冷却（图标上那圈遮罩、联机同步都由游戏代管），
 * 与牧羊人铃铛是同一套做法。</p>
 */
public class DaiksonOxygenOrbItem extends Item {

    /**
     * 记在物品自己身上的「生效到什么时候」（世界时间）。
     *
     * <p>客户端画图标时读的就是它：还没到点就画"展开"的那张，到点自动画回球
     * （见 {@code client/DaiksonOxygenOrbModel}）。</p>
     */
    public static final String ACTIVE_UNTIL_KEY = "EternalRelicOxygenUntil";

    /** 一次给多久的水下呼吸：6000 刻 = 5 分钟。 */
    private static final int BREATH_TICKS = 6000;

    /** 用完之后要等多久才能再用：12000 刻 = 10 分钟（效果与冷却之间留出 5 分钟空窗）。 */
    private static final int COOLDOWN_TICKS = 12000;

    public DaiksonOxygenOrbItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient()) {
            // showParticles 关掉：否则接下来的五分钟里玩家会一直冒泡泡（与守夜之瞳的夜视同一个口径）。
            // showIcon 留着，玩家能从状态栏知道自己还剩多久
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING,
                    BREATH_TICKS, 0, false, false, true));

            // 启动就是启动：这段时间里既不能提前关掉，也不能重新计时。
            // 物品冷却挡住了"再点一次"，而这里写下的到期时刻决定了图标什么时候变回球形
            stack.getOrCreateNbt().putLong(ACTIVE_UNTIL_KEY, world.getTime() + BREATH_TICKS);

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);

            if (world instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.BUBBLE_POP,
                        user.getX(), user.getY() + 1.0D, user.getZ(),
                        14, 0.3D, 0.4D, 0.3D, 0.03D);
            }

            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.6F, 1.2F);
        }

        return TypedActionResult.success(stack);
    }
}
