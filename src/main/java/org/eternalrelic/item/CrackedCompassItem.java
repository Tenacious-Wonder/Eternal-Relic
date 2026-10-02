package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 磕碰的罗盘 —— 拿在手上右键，在聊天栏报出你此刻的坐标与朝向。
 *
 * <p>它不给任何数值，只把玩家本来要点 F3 才能看到的两样东西说出来。
 * 做成物品而不是常驻显示，是因为"看一眼就收起来"才是它的用法——
 * 常驻在屏幕上会变成一块永远拆不掉的 HUD。</p>
 *
 * <p>没有冷却，也不消耗东西：它不改变任何状态，反复用也不会让谁变强。</p>
 */
public class CrackedCompassItem extends Item {

    public CrackedCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // 只服务端发话：客户端那份也会跑这个方法，两边各报一次就会刷出两条一样的
        if (!world.isClient()) {
            BlockPos pos = user.getBlockPos();
            user.sendMessage(Text.translatable("message.eternal_relic.cracked_compass",
                    pos.getX(), pos.getY(), pos.getZ(),
                    Text.translatable("message.eternal_relic.facing." + user.getHorizontalFacing().asString())
                            .formatted(Formatting.GOLD)), false);
        }

        return TypedActionResult.success(stack);
    }
}
