package org.eternalrelic.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import org.eternalrelic.registry.ModBlockEntityTypes;

/**
 * <h1>台上摆着的那件胸甲</h1>
 *
 * <p>
 * 保存台面上的胸甲，负责存档与下发给客户端渲染，是台子得以跨会话记住内容的部分。
 * 胸甲身上已装的配件不属于本类的数据，它们跟着那件胸甲走（见 {@code relic.RelicAttachment}）。
 * </p>
 *
 * <h2>写入约定</h2>
 * <p>
 * 台面上的胸甲只能经 {@link #setChestplate} 更换，它同时完成存档与下发；
 * {@link #getChestplate()} 交出的是副本，改动副本不会影响台面，改完必须写回。
 * 台上那件被就地改动过（例如装上或拆下一枚配件）时，同样是改写后的那一件写回来。
 * </p>
 *
 * @see org.eternalrelic.block.ChestplateStationBlock
 */
public class ChestplateStationBlockEntity extends BlockEntity {
    private static final String CHESTPLATE_KEY = "Chestplate";

    /** 台上摆着的胸甲；空堆表示台面空着。 */
    private ItemStack chestplate = ItemStack.EMPTY;

    public ChestplateStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHESTPLATE_STATION, pos, state);
    }

    /**
     * @return 台上那件胸甲的副本；台面空着时返回空堆
     */
    public ItemStack getChestplate() {
        return this.chestplate.copy();
    }

    /**
     * 换上台上的胸甲，并存入存档、下发客户端。
     *
     * @param stack 要摆上去的胸甲；空堆表示把台面腾空
     */
    public void setChestplate(ItemStack stack) {
        this.chestplate = stack;
        markDirty();
    }

    /**
     * 取走台上的胸甲，台面随之腾空；界面取出与按住 Shift 右键取回都走这里。
     *
     * @return 取下的那件胸甲；台面本来就空着时返回空堆
     */
    public ItemStack takeChestplate() {
        ItemStack taken = this.chestplate;

        // 台面空着也照样写一次：服务端以为空、客户端还留着旧内容时，只有再发一次同步才能纠正过来
        setChestplate(ItemStack.EMPTY);

        return taken;
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

    @Override
    public void markDirty() {
        super.markDirty();

        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return this.createNbt();
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
