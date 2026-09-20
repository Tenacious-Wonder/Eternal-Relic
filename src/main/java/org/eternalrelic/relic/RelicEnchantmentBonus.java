package org.eternalrelic.relic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 「遗物补上的附魔」这件事的账本 —— 哪件遗物给它附着的物品补哪条附魔、补几级，
 * 以及「这件东西身上现在一共被补了多少」。
 *
 * <p><b>为什么不能直接写进物品</b>：把附魔写进物品数据（NBT）是最省事的做法，但会留下三个问题——
 * 摘下纹章时那份附魔不会自己消失、面板上分不清哪几级是玩家自己附的、以及玩家能把这件东西拿去铁砧
 * 把「遗物送的附魔」合并到别的物品上（等于把遗物效果复制走）。</p>
 *
 * <p>所以这里走「虚拟附魔」：物品自己的数据一个字节都不动，只在游戏<b>询问附魔等级的那一刻</b>
 * 把遗物那一份加进去（由 {@code EnchantmentHelperMixin} 接在游戏的读取入口上）。这样一来：</p>
 *
 * <ul>
 *   <li>生效范围天然完整——游戏的附魔查询都汇聚到那一处，挖得快、打得疼、耐久掉得慢、
 *       经验修补自动维修，全都不用另外接线；</li>
 *   <li>摘下纹章立刻失效，物品数据里从来没有过这份附魔，也就没有「拆不干净」的可能；</li>
 *   <li>虚拟等级不受原版附魔上限约束，{@code 效率 VI} 这类原版拿不到的等级也能给；</li>
 *   <li>面板显示自成一路（见 {@code RelicEnchantmentTooltip}），可以把遗物那一份单独画成
 *       {@code 保护 II+II}，而不是并成一个看不出来源的数字。</li>
 * </ul>
 *
 * <p>等级一律<b>与原等级相加</b>：原本 {@code 保护 II}、遗物再给 2 级，实际生效就是 4 级；
 * 面板上写成 {@code 保护 II+II}，玩家一眼能看出这 4 级里哪一半是自己练出来的。</p>
 *
 * <p><b>哪些读取看得到这份附魔、哪些看不到（动它之前必读）</b>：看得到的只有「算效果」的两条路——
 * 单条查询与全表遍历；而「把一件物品的附魔整张搬走」的那条路（{@code EnchantmentHelper#get}）
 * <b>故意</b>看不到。铁砧合并、砂轮磨除、附魔台查重走的都是后者，因此虚拟附魔既不会被复制成真附魔，
 * 也不会被砂轮反复磨成经验。这条边界是整套机制成立的前提，改动前请先读
 * {@code org.eternalrelic.mixin.EnchantmentHelperMixin} 的类注释。</p>
 *
 * <p>登记的内容放在 {@code registry/EnchantingRelics}；本类只负责按登记办事。</p>
 */
public final class RelicEnchantmentBonus {

    /** 每件会补附魔的遗物 → 它补的附魔及等级，按登记顺序。 */
    private static final Map<Item, Map<Enchantment, Integer>> BY_RELIC = new LinkedHashMap<>();

    private RelicEnchantmentBonus() {
    }

    /**
     * 登记一件会给所附着物品补附魔的遗物。
     *
     * <p>同一件遗物可以分多次登记：后来补的那条会加在已经登记过的基础上，
     * 因此「一件纹章同时补两条附魔」写成两次调用即可。</p>
     *
     * <p>登记填漏时<b>直接抛错</b>，让它在启动时就暴露：这类登记只有开发者会写，
     * 静默跳过只会变成「进了游戏才发现某件遗物白钉了」。</p>
     *
     * @param relic       遗物本身
     * @param enchantment 它补的附魔
     * @param level       补几级，必须是正数
     */
    public static void register(Item relic, Enchantment enchantment, int level) {
        String name = Registries.ITEM.getId(relic).toString();

        if (enchantment == null) {
            throw new IllegalArgumentException("遗物「" + name + "」没有写明要补哪条附魔");
        }

        if (level <= 0) {
            throw new IllegalArgumentException("遗物「" + name + "」补的附魔等级必须是正数，实际是 " + level);
        }

        BY_RELIC.computeIfAbsent(relic, key -> new LinkedHashMap<>()).put(enchantment, level);
    }

    /**
     * 收出这件物品身上「由遗物补上」的附魔及总等级。
     *
     * <p>同一条附魔被多枚遗物同时补上时按等级相加——两枚各补 1 级就是 2 级。</p>
     *
     * @param stack 待查看的物品
     * @return 附魔 → 补上的等级；一件都没补时为空表
     */
    public static Map<Enchantment, Integer> bonusOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Map.of();
        }

        List<Item> attached = RelicAttachment.attachedTo(stack);
        if (attached.isEmpty()) {
            return Map.of();
        }

        Map<Enchantment, Integer> bonuses = new LinkedHashMap<>();

        for (Item relic : attached) {
            Map<Enchantment, Integer> granted = BY_RELIC.get(relic);
            if (granted == null) {
                continue;
            }

            granted.forEach((enchantment, level) -> bonuses.merge(enchantment, level, Integer::sum));
        }

        return bonuses;
    }

    /**
     * 把「物品自己的附魔」与「遗物补上的那一份」合成一张表，供游戏读取。
     *
     * <p><b>返回的永远是一份拷贝</b>：{@code ItemStack#getEnchantments()} 交出来的是物品自身数据里的
     * 那一个列表，就地往上加等级等于把虚拟附魔写进物品——那正是本类存在的意义所要避免的事。
     * 没有遗物补魔时直接返回原表，不白白复制一份。</p>
     *
     * @param stack 待读取的物品
     * @return 合并后的附魔表
     */
    public static NbtList merged(ItemStack stack) {
        // 先问「有没有遗物补魔」再取原表：没有补魔时这条路就走完了，
        // 一次多余的表拷贝都不做——它是每 tick 会被叫到很多次的路径。
        Map<Enchantment, Integer> bonuses = bonusOf(stack);

        if (bonuses.isEmpty()) {
            return stack.getEnchantments();
        }

        NbtList merged = stack.getEnchantments().copy();
        bonuses.forEach((enchantment, level) -> addLevel(merged, enchantment, level));
        return merged;
    }

    /**
     * 往一张附魔表上加等级：原本有这条就把等级抬上去，没有就补一条新的。
     *
     * @param enchantments 要改写的附魔表（应当是拷贝）
     * @param enchantment  附魔
     * @param extraLevels  要加上去的等级
     */
    private static void addLevel(NbtList enchantments, Enchantment enchantment, int extraLevels) {
        Identifier id = EnchantmentHelper.getEnchantmentId(enchantment);

        for (int i = 0; i < enchantments.size(); i++) {
            NbtCompound entry = enchantments.getCompound(i);

            if (id != null && id.equals(EnchantmentHelper.getIdFromNbt(entry))) {
                EnchantmentHelper.writeLevelToNbt(entry, EnchantmentHelper.getLevelFromNbt(entry) + extraLevels);
                return;
            }
        }

        enchantments.add(EnchantmentHelper.createNbt(id, extraLevels));
    }
}
