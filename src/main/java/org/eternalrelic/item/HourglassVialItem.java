package org.eternalrelic.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.registry.ModItems;

/**
 * 沙漏药瓶 —— 拿在手上右键，把身上所有<b>正面</b>状态效果各延长 9 秒。
 *
 * <p><b>它是一件会用完的东西</b>：一共 3 次，每次用掉一点耐久（物品栏里的耐久条就是「还剩几次」），
 * 用尽之后变成「空的沙漏药瓶」——<b>不消失，也修不回来</b>。因此耐久不用原版的
 * {@code ItemStack#damage}（那个方法在见底时会把物品整个销毁），而是自己数、见底时换成另一个物品，
 * 与余烬吊坠、回响之环是同一套做法。</p>
 *
 * <p><b>只延长「正面」效果</b>：判据是原版自己的 {@code StatusEffect#isBeneficial()}，
 * 因此力量、速度、生命恢复、抗火、夜视这些都算，中毒、凋零、缓慢、失明都不算——
 * 递到手里的是补药，不是万能药。</p>
 *
 * <p><b>为什么是「取下来再挂上」</b>：1.20.1 的 {@code StatusEffectInstance} 没有修改时长的口子，
 * 因此按原样（等级、是否隐蔽、是否显示粒子与图标）重新造一份挂回去。玩家的等级与粒子表现
 * 因此完全不变，只有剩余时间变长。</p>
 *
 * <p><b>没有可延长的效果时什么都不发生</b>：不扣耐久、不进冷却、只淡淡提示一句 ——
 * 一次性的次数很珍贵，白白扣掉一次比「按了没反应」更让人恼火（红土蜡块同一条道理）。</p>
 *
 * <p>冷却 30 秒交给游戏的物品冷却去计：图标上会出现一圈遮罩，冷却没走完时右键毫无反应。</p>
 */
public class HourglassVialItem extends Item {

    /** 每次给每条正面效果延长多久。180 刻 = 9 秒。 */
    private static final int EXTEND_TICKS = 180;

    /** 两次使用之间至少隔多久。600 刻 = 30 秒。 */
    private static final int COOLDOWN_TICKS = 600;

    /** 使用成功时撒出的符文粒子数量。 */
    private static final int SPARK_COUNT = 14;

    /** 使用成功时的钟声音调。比原版高一些，听着像「拧了一下」。 */
    private static final float CHIME_PITCH = 1.4F;

    public HourglassVialItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键把身上的增益各延长一段。
     *
     * <p>只由服务端执行：状态效果与耐久都只有服务端说了算。</p>
     *
     * @param world 玩家所在的世界
     * @param user  使用药瓶的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；不消耗物品本体（消耗的是耐久）
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.success(stack);
        }

        int extended = extendBuffs(user);

        if (extended == 0) {
            user.sendMessage(Text.translatable("message.eternal_relic.hourglass_vial.nothing")
                    .formatted(Formatting.GRAY), true);
            return TypedActionResult.success(stack);
        }

        serverWorld.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, CHIME_PITCH);

        serverWorld.spawnParticles(ParticleTypes.ENCHANT,
                user.getX(), user.getY() + 1.0D, user.getZ(),
                SPARK_COUNT, 0.5D, 0.6D, 0.5D, 0.4D);

        user.getItemCooldownManager().set(this, COOLDOWN_TICKS);

        consume(user, stack, hand);

        return TypedActionResult.success(stack);
    }

    /**
     * 把身上所有正面效果各延长 {@link #EXTEND_TICKS} 刻。
     *
     * <p>先把要改的收集起来、再统一动手：直接在 {@code getStatusEffects()} 的返回集合上边遍历边改，
     * 会因为集合被改动而抛错。</p>
     *
     * @param user 目标玩家
     * @return 实际延长了几条效果
     */
    private static int extendBuffs(PlayerEntity user) {
        List<StatusEffectInstance> extending = new ArrayList<>();

        for (StatusEffectInstance effect : user.getStatusEffects()) {
            StatusEffect type = effect.getEffectType();

            if (type.isBeneficial() && !type.isInstant() && effect.getDuration() > 0) {
                extending.add(effect);
            }
        }

        for (StatusEffectInstance effect : extending) {
            StatusEffectInstance renewed = new StatusEffectInstance(effect.getEffectType(),
                    effect.getDuration() + EXTEND_TICKS, effect.getAmplifier(),
                    effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());

            user.removeStatusEffect(effect.getEffectType());
            user.addStatusEffect(renewed);
        }

        return extending.size();
    }

    /**
     * 扣掉一次；扣到见底时把手里这一瓶换成空瓶。
     *
     * @param user  使用者
     * @param stack 手里那一瓶（原件）
     * @param hand  用的是哪只手
     */
    private static void consume(PlayerEntity user, ItemStack stack, Hand hand) {
        int used = stack.getDamage() + 1;

        if (used < stack.getMaxDamage()) {
            stack.setDamage(used);
            return;
        }

        user.setStackInHand(hand, new ItemStack(ModItems.HOURGLASS_VIAL_EMPTY));

        user.sendMessage(Text.translatable("message.eternal_relic.hourglass_vial_empty"), true);
        user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }
}
