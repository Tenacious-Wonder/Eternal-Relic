package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.capability.carried.HorseWhistleEffect;

/**
 * 唤马哨 —— 拿在手上右键，把远处自己的马、驴、骡叫到身边。
 *
 * <p>招呼的范围与「先搬近、再让它自己跑完最后一段」这两件事，都在
 * {@link HorseWhistleEffect} 里；本类只管右键的门口：什么也不吹不着时不进冷却、
 * 吹着了才响、才进冷却。</p>
 *
 * <p><b>一头都找不到时不进冷却</b>：在矿洞里、或在别人的地界上吹一声，不该白白等半分钟。
 * 这与驯兽哨的做法一致。</p>
 */
public class HorseWhistleItem extends Item {

    /** 两次吹哨之间至少隔多久。600 刻 = 30 秒；交给游戏的物品冷却去计。 */
    private static final int COOLDOWN_TICKS = 600;

    /** 哨声的音调。比原版音符盒的笛声高一些，听着像一声短哨。 */
    private static final float WHISTLE_PITCH = 2.0F;

    public HorseWhistleItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键吹一声。
     *
     * <p>只由服务端执行：实体名单与传送都只有服务端说了算。</p>
     *
     * @param world 玩家所在的世界
     * @param user  吹哨的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；不消耗物品
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient() || !(world instanceof ServerWorld serverWorld)
                || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }

        int called = HorseWhistleEffect.whistle(player);

        if (called == 0) {
            player.sendMessage(Text.translatable("message.eternal_relic.horse_whistle.none")
                    .formatted(Formatting.GRAY), false);
            return TypedActionResult.success(stack);
        }

        // 哨声要让四周都听见——走世界广播，而不是只发给吹哨的人
        serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.PLAYERS, 1.0F, WHISTLE_PITCH);

        player.sendMessage(Text.translatable("message.eternal_relic.horse_whistle.called",
                Text.literal(Integer.toString(called)).formatted(Formatting.GOLD)), false);

        player.getItemCooldownManager().set(this, COOLDOWN_TICKS);

        return TypedActionResult.success(stack);
    }
}
