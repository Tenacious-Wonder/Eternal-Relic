package org.eternalrelic.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;

/**
 * EVE 能量从服务端发往客户端的那条通路 —— 画面上那根能量条要用它。
 *
 * <p><b>为什么必须专门发一趟</b>：能量值记在服务端的记分板上（见 {@code energy/EveEnergy}），
 * 而 HUD 是客户端画的 —— 客户端自己算不出"我现在有多少能量"，
 * 记分板那套东西也不会自动把某个分数送到客户端手上（只同步被显示出来的那些）。</p>
 *
 * <p>因此由服务端在<b>能量真的变化时</b>发一次：每秒的自然回复、以及将来任何消耗它的行为。
 * 另外玩家一上线也会补发一次（客户端刚进来时手里什么都没有）。</p>
 *
 * <p>包里只有一个整数：当前能量点数。上限与回复速率都由服务端说了算，
 * 客户端拿到的就是"该画多少"。</p>
 */
public final class EveEnergyNetwork {

    /** 能量值的包名。客户端与服务端共用这一个标识。 */
    public static final Identifier ENERGY_PACKET = EternalRelic.id("eve_energy");

    private EveEnergyNetwork() {
    }

    /**
     * 把当前能量点数发给某位玩家。
     *
     * @param player 收信人
     * @param energy 当前能量点数
     */
    public static void sendTo(ServerPlayerEntity player, int energy) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(energy);

        ServerPlayNetworking.send(player, ENERGY_PACKET, buffer);
    }
}
