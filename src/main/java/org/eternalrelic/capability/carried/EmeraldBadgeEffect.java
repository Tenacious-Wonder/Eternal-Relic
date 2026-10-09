package org.eternalrelic.capability.carried;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.TradeOffer;

import org.eternalrelic.registry.ModItems;

/**
 * 「商旅」能力：带着绿宝石徽章时，<b>与村民交易一律打七五折</b>（少付 25%）。
 *
 * <p>这是模组里第一件正式碰<b>村民交易</b>的遗物 —— 在此之前，跟村民打交道这件事
 * 与遗物毫无关系。</p>
 *
 * <h2>怎么打折</h2>
 * <p>原版的交易价格在每一条"报价"（{@code TradeOffer}）上记着一个叫
 * <b>特别加价</b>的整数：<b>负数就是便宜</b>。游戏自己的"村庄英雄"效果正是靠它给折扣的。
 * 因此这里也走同一条路：玩家开始交易时，把每条报价的特别加价往下压 25%。</p>
 *
 * <p><b>原值要记下来，走的时候还回去。</b>那个字段不是我们的专属地盘 ——
 * 原版随时可能因为英雄效果或需求变化改它。所以第一次打折前先把原值抄一份，
 * 玩家走开时按原样恢复，免得"打折打完了，英雄效果的折扣也跟着没了"。
 * 抄在 {@link WeakHashMap} 里：交易刷新会重建报价对象，旧记录自己就被回收了。</p>
 *
 * <h2>何时调用</h2>
 * <p>由 {@link org.eternalrelic.mixin.EmeraldBadgeMixin} 挂在
 * {@code MerchantEntity#setCustomer} 上 —— 那是"谁正在跟这个村民交易"被设定的地方，
 * 玩家点开交易界面时走一次、关掉时再走一次（传 {@code null}）。</p>
 */
public final class EmeraldBadgeEffect {

    /** 折扣比例：少付 25%。 */
    private static final float DISCOUNT_RATIO = 0.25F;

    /** 每条报价被打折前的原值，按报价对象本身记着（弱引用，交易刷新后自动丢）。 */
    private static final Map<TradeOffer, Integer> ORIGINAL_PRICES = new WeakHashMap<>();

    private EmeraldBadgeEffect() {
    }

    /**
     * 把这位交易者面前的一整批报价，调整成"这位玩家该看到的价格"。
     *
     * @param offers   这位交易者当前的报价单
     * @param customer 正在交易的玩家；关掉界面时是 {@code null}
     */
    public static void apply(Iterable<TradeOffer> offers, PlayerEntity customer) {
        boolean discount = customer instanceof ServerPlayerEntity player
                && CarriedStacks.inEffect(player, ModItems.EMERALD_BADGE);

        for (TradeOffer offer : offers) {
            if (offer == null) {
                continue;
            }

            if (discount) {
                giveDiscount(offer);
            } else {
                restore(offer);
            }
        }
    }

    /**
     * 给一条报价打折（只在第一次打折前抄原值）。
     *
     * @param offer 一条报价
     */
    private static void giveDiscount(TradeOffer offer) {
        int original = ORIGINAL_PRICES.computeIfAbsent(offer, key -> key.getSpecialPrice());

        // 按"第一件要付的东西"的数量折算折扣 —— 与游戏自己的算法同一个口径
        ItemStack firstBuy = offer.getOriginalFirstBuyItem();
        int cut = Math.max(1, (int) Math.floor(firstBuy.getCount() * DISCOUNT_RATIO));

        offer.setSpecialPrice(original - cut);
    }

    /**
     * 把一条报价恢复成打折前的样子。
     *
     * @param offer 一条报价
     */
    private static void restore(TradeOffer offer) {
        Integer original = ORIGINAL_PRICES.remove(offer);

        if (original != null) {
            offer.setSpecialPrice(original);
        }
    }
}
