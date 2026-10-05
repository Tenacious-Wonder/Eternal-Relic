package org.eternalrelic.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * 「那个东西在你哪个方向」的共用说法 —— 把两个水平坐标差折成八个方位。
 *
 * <p>回声螺壳报最近的怪、蜡封手账报记下的地点，都要说「在你东北方」这类话。
 * 方位怎么算是一段纯粹的数学，两处各写一遍必然有一天会走散（一处把北认成 +Z，
 * 玩家就会看到「方向反着报」这种最说不清的症状），所以收在这里。</p>
 *
 * <p><b>坐标约定</b>：游戏里北是 <b>−Z</b>、南是 <b>+Z</b>、东是 <b>+X</b>、西是 <b>−X</b>
 * （与 F3 里那三个数一致）。方位从正北开始顺时针排，正东是第 2 个。</p>
 */
public final class Bearing {

    /**
     * 八个方位的键名，从正北起顺时针。
     *
     * <p>拼出来的完整键是 {@code message.eternal_relic.bearing.<键名>}。</p>
     */
    private static final String[] NAMES = {
            "north", "north_east", "east", "south_east",
            "south", "south_west", "west", "north_west"
    };

    /**
     * 一个方位占的角度（八分之一圈）。
     */
    private static final double SECTOR_RADIANS = Math.PI / 4.0D;

    private Bearing() {
    }

    /**
     * 把「目标相对自己」的水平偏移折成一个方位词。
     *
     * @param offsetX 目标在自己的东边多少格（东为正）
     * @param offsetZ 目标在自己的南边多少格（南为正）
     * @return 可以直接写进提示里的方位词，例如「东北」
     */
    public static MutableText of(double offsetX, double offsetZ) {
        // atan2 以「北」为 0、顺时针增大：北偏移是 -Z，因此第一个参数给 offsetX、第二个给 -offsetZ
        double angle = Math.atan2(offsetX, -offsetZ);

        // 四舍五入到最近的方位；负数角度用 floorMod 折回 0~7，正南偏西那几格才不会被折错
        int index = (int) Math.round(angle / SECTOR_RADIANS);

        return Text.translatable("message.eternal_relic.bearing." + NAMES[Math.floorMod(index, NAMES.length)]);
    }
}
