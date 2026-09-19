package org.eternalrelic.client.particle;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 本模组各粒子之间共用的几件小算术。
 *
 * <p>这三件事原先在每个粒子类里各抄了一份：把「出生点相对球心的指向」归一化成扩散方向、
 * 把「当前刻」换算成区间内的进度比例、以及按进度提亮方块光。抄写的坏处是改一处必漏一处，
 * 而这些都是调过的观感数值，漏掉一份就会让某颗粒子跟别的不一致。</p>
 *
 * <p>本类只装这些纯计算、不保存任何粒子状态，因此写成包内可见的静态方法即可，
 * 不必对外公开。</p>
 */
final class ParticleMath {

    /** 方向短于这个长度就当作"没有方向"，用朝上的兜底方向顶上。 */
    private static final double MIN_DIRECTION_LENGTH = 1.0E-4D;

    /** 提亮的封顶值。这个写法与 240 这个数都取自原版 {@code FlameParticle}。 */
    private static final int MAX_BLOCK_LIGHT = 240;

    private ParticleMath() {
    }

    /**
     * 把「出生点 − 球心」这个指向归一化成单位向量。
     *
     * <p>正好生在球心上时这个指向是零向量，除下去会得到 NaN，粒子会当场从画面上消失；
     * 这时统一给一个朝上的方向顶上。</p>
     *
     * @param x 指向的 X 分量
     * @param y 指向的 Y 分量
     * @param z 指向的 Z 分量
     * @return 归一化后的方向；原指向长度为零时返回朝上
     */
    static Vec3d unitDirection(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);

        if (length < MIN_DIRECTION_LENGTH) {
            // 正好生在球心上时给个朝上的方向，免得除以零
            return new Vec3d(0.0D, 1.0D, 0.0D);
        }

        return new Vec3d(x / length, y / length, z / length);
    }

    /**
     * 把「当前刻」换算成某个区间内的进度。
     *
     * @param value 当前刻
     * @param from  区间起点
     * @param to    区间终点
     * @return 归一化进度，0~1；区间为空或反向时返回 1
     */
    static double ratio(double value, double from, double to) {
        if (to <= from) {
            return 1.0D;
        }
        return MathHelper.clamp((value - from) / (to - from), 0.0D, 1.0D);
    }

    /**
     * 按进度提亮方块光分量。
     *
     * <p>只抬升方块光那一半、不动天光，因此白天夜里都亮得一致。</p>
     *
     * @param progress 当前帧的插值进度
     * @param packed   原版打包好的光照值
     * @return 提亮后的打包光照值
     */
    static int brightenBlockLight(float progress, int packed) {
        int blockLight = packed & 0xFF;
        int skyLight = packed >> 16 & 0xFF;

        blockLight = Math.min(MAX_BLOCK_LIGHT, blockLight + (int) (progress * 15.0F * 16.0F));

        return blockLight | skyLight << 16;
    }
}
