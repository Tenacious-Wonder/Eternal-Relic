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

import org.eternalrelic.capability.consumed.WayfarerBlessing;

/**
 * 远行绑腿 —— <b>一次性道具</b>：右键把布条缠上，换来五天的脚力（移速 +12%）。
 *
 * <p><b>它不再是一件「带在背包里就生效」的遗物</b>（制作者 2026-10-06 改的）：
 * 缠上之后物品消失，加速以一条会自己过期的移速加成留在身上五天。因此它登记在
 * 创造模式的「消耗品」那一页，遗物表里只留身份与成色、不再登记任何属性加成。</p>
 *
 * <p><b>玩家看不到任何图标或提示</b>：加速直接加在移速属性上，不占状态效果栏、也没有 HUD 图标
 * —— 这是制作者指定的做法。判定与到期一律交给 {@link WayfarerBlessing}。</p>
 *
 * <p><b>已经在生效时不消耗</b>：身上还缠着布条就再点一下，只回一句提示、物品留着 ——
 * 一次性道具最忌「按一下就白白少一个」（与红土蜡块修不成时不消耗同一条道理）。</p>
 *
 * <p><b>缠布条的声音</b>用的是原版「穿皮革护甲」那一段（皮革摩擦声），
 * 音调压低一档，听着像一圈圈把布条缠紧。</p>
 */
public class WandererGaitersItem extends Item {

    /** 缠布条的音量。 */
    private static final float WRAP_VOLUME = 1.0F;

    /** 缠布条的音调。比原版穿皮革甲低一档，显得厚实些。 */
    private static final float WRAP_PITCH = 0.9F;

    public WandererGaitersItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键把布条缠上。
     *
     * <p>只由服务端执行：属性加成与物品数量都只有服务端说了算。</p>
     *
     * @param world 玩家所在的世界
     * @param user  使用它的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；成功时消耗一个
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }

        if (WayfarerBlessing.isActive(player)) {
            player.sendMessage(Text.translatable("message.eternal_relic.wanderer_gaiters.already")
                    .formatted(Formatting.GRAY), true);
            return TypedActionResult.success(stack);
        }

        WayfarerBlessing.apply(player);

        // 缠布条的声音要让四周都听见——走世界广播
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, WRAP_VOLUME, WRAP_PITCH);

        player.sendMessage(Text.translatable("message.eternal_relic.wanderer_gaiters.wrapped"), true);

        stack.decrement(1);
        return TypedActionResult.success(stack);
    }
}
