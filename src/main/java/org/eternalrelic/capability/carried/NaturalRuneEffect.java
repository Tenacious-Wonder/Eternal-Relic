package org.eternalrelic.capability.carried;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;

/**
 * 「踏在自然上」能力：带着平凡的自然符石、脚下又是自然地面时，走得快一点。
 *
 * <p><b>它是第二件「条件性属性加成」</b>（第一件是巡夜斗篷看天色）。条件不成立时要及时把加成收回，
 * 所以不能只往遗物表里写一行，得由本类每 5 刻核对一次、按需挂上或摘掉。</p>
 *
 * <p><b>什么算「自然地面」</b>：按原版的方块标签判——泥土类（含草方块、菌丝、苔藓）、沙子、
 * 主世界的石头类，以及雪。用标签而不是写一份方块名单，别的模组只要把自己的方块挂进这些标签，
 * 也一样算数；而木板、石砖、地毯这些<b>做出来的</b>东西自然不在其中，
 * 这正是"自然"两个字的分量。</p>
 *
 * <p>用的是<b>临时</b>属性加成（不写存档）：脚下换个方块就要挂上摘下一次，
 * 写进存档毫无意义，而临时加成在实体存档时会跳过，玩家退出重进身上是干净的。</p>
 */
public final class NaturalRuneEffect {

    /** 每隔多少刻核对一次脚下。5 刻约为 0.25 秒，与其它携带型能力同一个节奏。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 移速加成 —— 在最终速度上再快三个百分点。 */
    private static final double SPEED_BONUS = 0.03D;

    /** 这条加成在玩家身上的固定标识。 */
    private static final UUID MODIFIER_ID = UUID
            .nameUUIDFromBytes("eternal_relic:natural_rune/speed".getBytes(StandardCharsets.UTF_8));

    /** 这条加成在属性面板里显示的名字。 */
    private static final String MODIFIER_NAME = "eternal_relic:natural_rune";

    private NaturalRuneEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上逐刻核对的回调。
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyFor(player);
            }
        });
    }

    /**
     * 把一名玩家身上的加成调整到与脚下一致。
     *
     * @param player 目标玩家
     */
    private static void applyFor(ServerPlayerEntity player) {
        boolean onNaturalGround = CarriedStacks.inEffect(player, ModItems.NATURAL_RUNE)
                && isNaturalGround(player);

        setModifier(player, onNaturalGround ? SPEED_BONUS : 0.0D);
    }

    /**
     * 玩家脚下踩着的这一块算不算自然地面。
     *
     * <p>问的是"正踩着的那一块"（{@code getSteppingBlockState}），而不是脚所在的那一格空气，
     * 因此站在半砖、台阶上也判得准。</p>
     *
     * @param player 目标玩家
     * @return 是否踩在自然地面上
     */
    private static boolean isNaturalGround(ServerPlayerEntity player) {
        BlockState stepping = player.getSteppingBlockState();

        return stepping.isIn(BlockTags.DIRT)
                || stepping.isIn(BlockTags.SAND)
                || stepping.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || stepping.isIn(BlockTags.SNOW);
    }

    /**
     * 把加成挂上、更新或摘掉。
     *
     * <p>值没变时什么都不做——反复挂同一条会在属性面板上堆出一长串一模一样的记录。</p>
     *
     * @param player 目标玩家
     * @param value  这次的数值；0 表示此刻不该有这条加成
     */
    private static void setModifier(ServerPlayerEntity player, double value) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }

        EntityAttributeModifier existing = instance.getModifier(MODIFIER_ID);

        if (value == 0.0D) {
            if (existing != null) {
                instance.removeModifier(MODIFIER_ID);
            }
            return;
        }

        if (existing != null && existing.getValue() == value) {
            return;
        }

        instance.removeModifier(MODIFIER_ID);
        instance.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, MODIFIER_NAME, value,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
