package org.eternalrelic.capability.carried;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.relic.RelicAttachment;

/**
 * 「巡夜」能力：带着巡夜斗篷时，夜里且身处暗处就跑得快一点；缝在胸甲上时，另外再给一点盔甲韧性。
 *
 * <p><b>它是全项目第一件「条件性属性加成」。</b>此前的属性加成只有一种形态：带着就一直有。
 * 这一件不一样——移速要看天色，暗了才给、天亮了就收回，所以不能只写进遗物表，
 * 得由本类每 5 刻核对一次、按需挂上或摘掉。遗物表里它的效果一栏因此是空的。</p>
 *
 * <p><b>两条加成的条件不一样，分开判</b>：</p>
 * <ul>
 *   <li><b>移速</b>：带着斗篷（背包 / 副手，或缝在正穿着 / 拿着的装备上）、世界正在夜里、
 *       且脚下亮度低于 {@value #DARK_LIGHT_LEVEL} —— 三条同时成立才给。
 *       ⚠️ 下界与末地没有昼夜，{@code World#isNight()} 在那两处恒为 false，因此那里永远不生效；</li>
 *   <li><b>盔甲韧性</b>：只看「<b>正穿着的那件胸甲</b>上缝着没有」，与天色无关——
 *       这是制作者定的：斗篷披在胸甲外才给这份韧性，放背包里没有。</li>
 * </ul>
 *
 * <p><b>用「临时」属性加成，不写存档。</b>天色一天要变好几回，这种加成一天挂上摘下许多次，
 * 写进存档毫无意义；临时加成在实体被写进存档时会跳过，所以玩家退出重进身上是干净的，
 * 不必再写一处清理。这与旅人吊坠给坐骑加速用的是同一个手法。</p>
 */
public final class NightWatchCloakEffect {

    /** 每隔多少刻核对一次天色与携带情况。5 刻约为 0.25 秒，与其它携带型能力同一个节奏。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 判定为「暗处」的亮度上限：所处位置亮度低于此值时才算数。 */
    private static final int DARK_LIGHT_LEVEL = 7;

    /** 夜里的移速加成 —— 在最终速度上再快一成。 */
    private static final double NIGHT_SPEED_BONUS = 0.10D;

    /** 缝在胸甲上时给的盔甲韧性。 */
    private static final double CHEST_TOUGHNESS_BONUS = 0.5D;

    /** 移速加成在玩家身上的固定标识。 */
    private static final UUID SPEED_MODIFIER_ID = UUID
            .nameUUIDFromBytes("eternal_relic:nightwatch_cloak/night_speed".getBytes(StandardCharsets.UTF_8));

    /** 韧性加成在玩家身上的固定标识。 */
    private static final UUID TOUGHNESS_MODIFIER_ID = UUID
            .nameUUIDFromBytes("eternal_relic:nightwatch_cloak/chest_toughness".getBytes(StandardCharsets.UTF_8));

    /** 两条加成在属性面板里显示的名字。 */
    private static final String MODIFIER_NAME = "eternal_relic:nightwatch_cloak";

    private NightWatchCloakEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.registry.RegistryInit#init()} 调用，挂上核对天色与携带情况的回调。
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
     * 把一名玩家身上的两条加成调整到与当前情况一致。
     *
     * @param player 目标玩家
     */
    private static void applyFor(ServerPlayerEntity player) {
        boolean onChest = wornOnChest(player);
        boolean carried = onChest || CarriedStacks.carries(player, ModItems.NIGHTWATCH_CLOAK);

        // 移速：带着斗篷、世界正在夜里、脚下又够暗，三条同时成立才给
        boolean darkNight = carried && player.getWorld().isNight()
                && player.getWorld().getLightLevel(player.getBlockPos()) < DARK_LIGHT_LEVEL;

        setModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_MODIFIER_ID,
                darkNight ? NIGHT_SPEED_BONUS : 0.0D, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

        // 韧性：只看「正穿着的那件胸甲上缝着没有」，与天色无关
        setModifier(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, TOUGHNESS_MODIFIER_ID,
                onChest ? CHEST_TOUGHNESS_BONUS : 0.0D, EntityAttributeModifier.Operation.ADDITION);
    }

    /**
     * @param player 目标玩家
     * @return 正穿着的那件胸甲上是不是缝着斗篷
     */
    private static boolean wornOnChest(PlayerEntity player) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        return !chest.isEmpty() && RelicAttachment.isAttached(chest, ModItems.NIGHTWATCH_CLOAK);
    }

    /**
     * 把一条加成挂上、更新或摘掉。
     *
     * <p>值没变时什么都不做——反复挂同一条会在属性面板上堆出一长串一模一样的记录。
     * 值为 0 表示此刻不该有这条加成，摘掉即可（没挂过时什么都不会发生）。</p>
     *
     * @param player    目标玩家
     * @param attribute 要改的属性
     * @param id        这条加成的固定标识
     * @param value     这次的数值；0 表示这条加成此刻不该有
     * @param operation 加成的运算方式
     */
    private static void setModifier(PlayerEntity player, EntityAttribute attribute, UUID id, double value,
            EntityAttributeModifier.Operation operation) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) {
            return;
        }

        EntityAttributeModifier existing = instance.getModifier(id);

        if (value == 0.0D) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }

        if (existing != null && existing.getValue() == value && existing.getOperation() == operation) {
            return;
        }

        instance.removeModifier(id);
        instance.addTemporaryModifier(new EntityAttributeModifier(id, MODIFIER_NAME, value, operation));
    }
}
