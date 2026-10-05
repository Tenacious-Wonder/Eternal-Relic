package org.eternalrelic.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.eternalrelic.util.Bearing;

/**
 * 蜡封手账 —— 蹲下右键记下脚下的地点，平时右键报出那个地点在哪个方向、离多远。
 *
 * <p><b>它记在物品自己身上</b>（物品数据里那四个键），与拾荒口袋里装的东西、装备上钉的遗物
 * 是同一条道理：手账会被丢进箱子、交给队友、带过维度，记下的那一页必须跟着它走。
 * 服务器内存里不留任何东西，因此退出重进、换维度都不会丢。</p>
 *
 * <p><b>只记一个地点</b>：这本手账不是地图，它是「在陌生地方给自己留一个记号」用的小东西。
 * 只留一页，写下新的就把旧的那一页划掉——也因此它不会被玩家拿来当传送坐标本用。</p>
 *
 * <p><b>跨维度会明说</b>：从下界翻开手账时，它不会报一个毫无意义的距离，而是直说
 * 「记下的那一页在另一个维度」。</p>
 */
public class WaxSealedJournalItem extends Item {

    /**
     * 记下的地点坐标在这本手账的数据里用的三个键。
     */
    private static final String KEY_X = "WaypointX";
    private static final String KEY_Y = "WaypointY";
    private static final String KEY_Z = "WaypointZ";

    /**
     * 记下的地点在哪个维度 —— 没有它，跨维度时报出的距离会是错的。
     */
    private static final String KEY_DIMENSION = "WaypointDimension";

    /**
     * 上下差超过这么多格才额外提一句（格）。同层面的小落差说出来只是啰嗦。
     */
    private static final int VERTICAL_HINT = 4;

    public WaxSealedJournalItem(Settings settings) {
        super(settings);
    }

    /**
     * 右键翻开手账：蹲着就是写，站着就是读。
     *
     * @param world 玩家所在的世界
     * @param user  翻手账的玩家
     * @param hand  用的是哪只手
     * @return 使用结果；不消耗物品
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        if (user.isSneaking()) {
            mark(stack, world, user);
        } else {
            report(stack, world, user);
        }

        return TypedActionResult.success(stack);
    }

    /**
     * 把脚下的地点写进这一页。
     *
     * @param stack 手账
     * @param world 玩家所在的世界
     * @param user  记地点的玩家
     */
    private static void mark(ItemStack stack, World world, PlayerEntity user) {
        BlockPos pos = user.getBlockPos();
        NbtCompound nbt = stack.getOrCreateNbt();

        nbt.putInt(KEY_X, pos.getX());
        nbt.putInt(KEY_Y, pos.getY());
        nbt.putInt(KEY_Z, pos.getZ());
        nbt.putString(KEY_DIMENSION, world.getRegistryKey().getValue().toString());

        user.sendMessage(Text.translatable("message.eternal_relic.wax_sealed_journal.marked",
                coordinates(pos.getX(), pos.getY(), pos.getZ())), false);
    }

    /**
     * 报出记下的地点在哪个方向。
     *
     * @param stack 手账
     * @param world 玩家所在的世界
     * @param user  翻手账的玩家
     */
    private static void report(ItemStack stack, World world, PlayerEntity user) {
        NbtCompound nbt = stack.getNbt();

        // 一页都没写过（新做出来的手账，或者上一任主人没记过）
        if (nbt == null || !nbt.contains(KEY_X)) {
            user.sendMessage(Text.translatable("message.eternal_relic.wax_sealed_journal.empty")
                    .formatted(Formatting.GRAY), false);
            return;
        }

        String recorded = nbt.getString(KEY_DIMENSION);
        String here = world.getRegistryKey().getValue().toString();

        if (!recorded.equals(here)) {
            user.sendMessage(Text.translatable("message.eternal_relic.wax_sealed_journal.other_dimension",
                    dimensionName(recorded)), false);
            return;
        }

        int x = nbt.getInt(KEY_X);
        int y = nbt.getInt(KEY_Y);
        int z = nbt.getInt(KEY_Z);

        // 方块坐标记的是方块的角，加半格才是它的中心；不补这半格，贴着站的时候方位会偏
        double offsetX = x + 0.5D - user.getX();
        double offsetZ = z + 0.5D - user.getZ();
        long distance = Math.round(Math.sqrt(offsetX * offsetX + offsetZ * offsetZ));

        user.sendMessage(Text.translatable("message.eternal_relic.wax_sealed_journal.bearing",
                coordinates(x, y, z),
                Bearing.of(offsetX, offsetZ).formatted(Formatting.GOLD),
                Text.literal(Long.toString(distance)).formatted(Formatting.GOLD)), false);

        double vertical = y - user.getY();
        if (Math.abs(vertical) >= VERTICAL_HINT) {
            user.sendMessage(Text.translatable("message.eternal_relic.wax_sealed_journal.vertical",
                    Text.literal(Long.toString(Math.round(Math.abs(vertical)))).formatted(Formatting.GOLD),
                    Text.translatable(vertical > 0
                            ? "message.eternal_relic.wax_sealed_journal.above"
                            : "message.eternal_relic.wax_sealed_journal.below")), false);
        }
    }

    /**
     * 把三个数拼成坐标文字。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @param z 纵坐标
     * @return 染成金色的坐标
     */
    private static Text coordinates(int x, int y, int z) {
        return Text.literal(x + ", " + y + ", " + z).formatted(Formatting.GOLD);
    }

    /**
     * 把维度的内部名（如 {@code minecraft:the_nether}）换成游戏里的正式叫法。
     *
     * <p>借原版自己的翻译键（{@code dimension.minecraft.the_nether} → 「下界」），
     * 因此中文、英文与其它语言都跟着游戏走，不必自己再抄一份维度名单。</p>
     *
     * @param dimensionId 维度的内部名
     * @return 可读的维度名
     */
    private static Text dimensionName(String dimensionId) {
        return Text.translatable("dimension." + dimensionId.replace(':', '.'));
    }
}
