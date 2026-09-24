package org.eternalrelic.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import org.eternalrelic.registry.ModBlockEntityTypes;

/**
 * 胸甲台上摆着的那一件胸甲。
 *
 * <p><b>台子必须自己记住它。</b>玩家放好胸甲就走开、退出游戏、服务器重启之后，那件东西都得还在台上——
 * 这台子既能当工作台也能当展示台，东西不能只在界面开着的时候存在。这一份记录因此同时承担三件事：
 * 存档、下发给客户端渲染、以及方块被拆掉时把胸甲还给玩家（最后一件由 {@code ChestplateStationBlock} 负责）。</p>
 *
 * <p><b>胸甲身上已经装了哪些配件，不记在这里</b>：那属于那件胸甲自己的数据
 * （见 {@code relic.RelicAttachment}），跟着胸甲走。这里只管"台上摆着的是哪一件"。</p>
 *
 * <p><b>为什么每次变动都要主动通知客户端</b>：方块实体的数据不会自动同步。胸甲换了却不告诉客户端，
 * 玩家会看到台上还摆着上一件——渲染在客户端做，它读的就是这里下发的这一份。</p>
 */
public class ChestplateStationBlockEntity extends BlockEntity {

    /** 存档里存放那件胸甲的标签名。 */
    private static final String CHESTPLATE_KEY = "Chestplate";

    /** 台上摆着的胸甲；空着时表示台上没东西。 */
    private ItemStack chestplate = ItemStack.EMPTY;

    public ChestplateStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHESTPLATE_STATION, pos, state);
    }

    /**
     * @return 台上摆着的那一件胸甲；台上空着时返回空堆
     */
    public ItemStack getChestplate() {
        return this.chestplate;
    }

    /**
     * 换上台上的胸甲，并把这一变化记进存档、发给客户端。
     *
     * @param stack 要摆上去的胸甲；空堆表示把台子腾空
     */
    public void setChestplate(ItemStack stack) {
        this.chestplate = stack;

        markChanged();
    }

    /**
     * 把台上的胸甲取下来，台子随之腾空。
     *
     * <p>界面上从正中那一格把胸甲拖走，走的就是这里——与对着台子按住 Shift 右键是同一件事，
     * 两条路都会把台子清空并存给客户端。</p>
     *
     * @return 取下来的那件胸甲；台上本来就空着时返回空堆
     */
    public ItemStack takeChestplate() {
        ItemStack taken = this.chestplate;

        if (!taken.isEmpty()) {
            setChestplate(ItemStack.EMPTY);
        }

        return taken;
    }

    /**
     * 台上那件胸甲**被就地改动过**时调用 —— 例如在它身上装上了一枚配件、或者拆下了一枚。
     *
     * <p>与 {@link #setChestplate} 的区别只在于"换的是不是另一件东西"：装拆配件改的是同一件胸甲身上的
     * 记录，物品本身没换，但改动同样要落盘、同样要发给客户端（否则台子上显示的配件数不会变）。</p>
     */
    public void markChanged() {
        markDirty();

        if (this.world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(this.pos);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        this.chestplate = nbt.contains(CHESTPLATE_KEY)
                ? ItemStack.fromNbt(nbt.getCompound(CHESTPLATE_KEY))
                : ItemStack.EMPTY;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        if (!this.chestplate.isEmpty()) {
            nbt.put(CHESTPLATE_KEY, this.chestplate.writeNbt(new NbtCompound()));
        }
    }

    /**
     * 玩家走进视野、区块刚加载时，随区块数据一并发给客户端的那一份。
     *
     * <p>与存档内容相同即可——渲染要用的正是台上那件胸甲。</p>
     */
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return this.createNbt();
    }

    /**
     * 台上胸甲变化时单独补发的那一份。
     */
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
