package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import org.eternalrelic.screen.PocketInventory;

/**
 * 拾荒口袋 —— 拿在主手右键打开，界面就是原版的大箱子那一套（54 格）。
 *
 * <p><b>它是一件「物品自己装东西」的东西</b>，项目里此前没有这一类：内容记在口袋自己的数据里
 * （见 {@link org.eternalrelic.relic.PocketStorage}），因此丢出去、放进箱子、交给别人，
 * 里面的东西都跟着走。</p>
 *
 * <p><b>界面为什么一行都不用自己画</b>：原版给「大箱子」留了现成的容器与界面
 * （{@code GenericContainerScreenHandler.createGeneric9x6} + {@code GenericContainerScreen}），
 * 客户端那边原版也早就注册好了。本模组只要把「装什么」这件事交出去，剩下的
 * ——槽位排布、Shift 搬运、快捷栏数字键、拖拽——全是原版行为。</p>
 *
 * <p><b>只认主手</b>：副手、背包里右键都不会打开。这是故意的——它是一件要拿在手上用的家什，
 * 与牧羊人铃铛同一种口径；否则玩家在背包里对着它按右键会莫名其妙弹出一个界面。</p>
 */
public class ScavengerPocketItem extends Item {

    public ScavengerPocketItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // 开界面只有服务端说了算（客户端那一份 openHandledScreen 是空实现），
        // 因此这里判一次，免得客户端白读一遍口袋内容
        if (!world.isClient()) {
            user.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInventory, player) -> GenericContainerScreenHandler.createGeneric9x6(
                            syncId, playerInventory, new PocketInventory(stack)),
                    Text.translatable("container.eternal_relic.scavenger_pocket")));
        }

        return TypedActionResult.success(stack);
    }
}
