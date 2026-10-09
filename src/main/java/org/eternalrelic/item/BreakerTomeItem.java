package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.skill.ShieldRushSkill;

/**
 * 破阵之书 —— <b>一次性道具</b>：右键读一遍，学会「破阵」（举盾蓄力冲刺）。
 *
 * <p>书本身没有任何携带效果；它换来的是一门<b>永久</b>的技能 ——
 * 学会之后把书给别人、或者把书丢了，技能都还在（记在玩家自己身上，见
 * {@link ShieldRushSkill}）。</p>
 *
 * <p><b>已经会了就不消耗</b>：再读一遍只回一句提示、书留着，免得白读一本
 * （与红土蜡块、远行绑腿同一条道理）。</p>
 *
 * <p>学会时放的是原版「升级」那一段音效 —— 一门手艺到手，值得有个仪式感。</p>
 */
public class BreakerTomeItem extends Item {

    /** 学会时的音量。 */
    private static final float LEARN_VOLUME = 1.0F;

    /** 学会时的音调。抬高一档，听着像"叮"的一声。 */
    private static final float LEARN_PITCH = 1.2F;

    public BreakerTomeItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键读一遍。
     *
     * <p>只由服务端执行：学习标记与物品数量都只有服务端说了算。</p>
     *
     * @param world 玩家所在的世界
     * @param user  读书的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；学会时消耗一本
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }

        if (ShieldRushSkill.hasLearned(player)) {
            player.sendMessage(Text.translatable("message.eternal_relic.breaker_tome.already")
                    .formatted(Formatting.GRAY), true);
            return TypedActionResult.success(stack);
        }

        ShieldRushSkill.learn(player);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, LEARN_VOLUME, LEARN_PITCH);

        player.sendMessage(Text.translatable("message.eternal_relic.breaker_tome.learned"), true);

        stack.decrement(1);
        return TypedActionResult.success(stack);
    }
}
