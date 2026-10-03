package org.eternalrelic.item;

import java.util.List;
import java.util.Set;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * 驯兽哨 —— 拿在手上右键，把附近的伙伴叫到身边。
 *
 * <p>它招呼的是<b>已经认你为主的宠物</b>：狗、猫、鹦鹉这些能驯服的生物，
 * 只要在 30 格以内，就会被挪到你脚边。走丢在树林里的狗、掉进矿洞的猫，吹一声就回来了。</p>
 *
 * <p><b>只认自己的宠物</b>：判断用的是「它认的主人是不是你」，因此队友的狗不会被你叫走，
 * 野生的、没驯服的也不会理你。</p>
 *
 * <p><b>把它们摆成一圈</b>而不是全叠在同一个点上：原版传送是「放到那个坐标」，
 * 十几只一起传会挤在同一格里互相推挤。按人数分角度摆在身边一圈，落地就是散开的。</p>
 *
 * <p><b>跨维度不管</b>：只招呼与你处在同一个世界的伙伴。从下界吹哨子把主世界的狗拽下来，
 * 既说不通，也会让那只狗落在没有路的岩浆湖边。</p>
 *
 * <p>它带有 10 秒冷却，交给游戏的物品冷却去计——哨子图标上会出现一圈遮罩。</p>
 */
public class BeastWhistleItem extends Item {

    /**
     * 哨声传得到多远（格）。
     */
    private static final double RADIUS = 30.0D;

    /**
     * 伙伴们落在离你多远的圈上（格）。
     */
    private static final double RING_RADIUS = 1.6D;

    /**
     * 两次吹哨之间至少隔多久。200 刻 = 10 秒；交给游戏的物品冷却去计。
     */
    private static final int COOLDOWN_TICKS = 200;

    public BeastWhistleItem(Settings settings) {
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

        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return TypedActionResult.success(stack);
        }

        Box area = user.getBoundingBox().expand(RADIUS);
        List<TameableEntity> pets = serverWorld.getEntitiesByClass(TameableEntity.class, area,
                pet -> pet.isAlive() && pet.isTamed() && pet.isOwner(user));

        if (pets.isEmpty()) {
            user.sendMessage(Text.translatable("message.eternal_relic.beast_whistle.none")
                    .formatted(Formatting.GRAY), false);
            return TypedActionResult.success(stack);
        }

        callToRing(serverWorld, user, pets);

        user.sendMessage(Text.translatable("message.eternal_relic.beast_whistle.called",
                Text.literal(Integer.toString(pets.size())).formatted(Formatting.GOLD)), false);

        user.getItemCooldownManager().set(this, COOLDOWN_TICKS);

        return TypedActionResult.success(stack);
    }

    /**
     * 把伙伴们按人数分角度摆在玩家身边一圈。
     *
     * @param world 玩家所在的世界
     * @param user  吹哨的玩家
     * @param pets  要叫过来的伙伴
     */
    private static void callToRing(ServerWorld world, PlayerEntity user, List<TameableEntity> pets) {
        int count = pets.size();

        for (int i = 0; i < count; i++) {
            TameableEntity pet = pets.get(i);

            double angle = Math.PI * 2.0D * i / count;
            double x = user.getX() + Math.cos(angle) * RING_RADIUS;
            double z = user.getZ() + Math.sin(angle) * RING_RADIUS;

            pet.teleport(world, x, user.getY(), z, Set.of(), pet.getYaw(), pet.getPitch());
        }
    }
}
