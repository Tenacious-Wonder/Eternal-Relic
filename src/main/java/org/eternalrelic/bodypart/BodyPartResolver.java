package org.eternalrelic.bodypart;

import java.util.List;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * <h1>把「弹射物命中玩家的那一点」换算成人身上的部位</h1>
 *
 * <h2>怎么算的</h2>
 * <p>命中点是<b>世界坐标</b>（比如「东经 128、高度 70、南纬 40」），而「打中左肩还是右肩」是
 * <b>相对玩家身体</b>的说法——玩家一转身，同一个世界坐标对应的部位就变了。所以这里先把命中点
 * 换算到玩家自己的身体坐标系里：原点在脚底正中，纵轴向上，横轴指向玩家<b>右手边</b>。</p>
 *
 * <h2>为什么用「盒子」而不是「高度区间」</h2>
 * <p>第一版把身体按高度切成几段来比大小，结果一错到底。换成盒子之后有两处本质不同：</p>
 * <ul>
 *   <li><b>盒子是照着玩家模型定的，不是照着碰撞箱。</b>比如手臂盒子在横向 0.3 格之外，
 *       而身体碰撞箱只有 0.3 格半宽——按碰撞箱去切，永远切不出手臂。</li>
 *   <li><b>盒子可以「都不中」，然后归给最近的那个。</b>命中点是弹射物与身体轮廓的交点，
 *       它落在身体<b>表面</b>；而下面这些盒子都在身体<b>内部</b>（身体盒只有 0.3 格深，
 *       轮廓却有 0.6 格深）。所以十个里有九个点落不进任何盒子——真正干活的是
 *       「挑最近的盒子」这一步，前面那轮精确匹配只是让明显落进去的点不必绕远。</li>
 * </ul>
 * <p>这套盒子范围取自同类模组（Modern Damage Control，MIT）按玩家模型校准的结果。</p>
 *
 * <h2>一个必须知道的几何限制</h2>
 * <p>射手在玩家<b>正前方或正后方</b>时，横向由射手的瞄准决定，左肩、右肩、正胸分得很准。
 * 射手在玩家<b>正侧面</b>时，命中点的横向恒等于身体半宽（永远落在同一侧），横向信息自然消失，
 * 结果会稳定地给出「靠近射手的那一侧肩膀」——这与事实相符，因为那一箭确实打的就是那一侧。</p>
 *
 * <p>本类是纯计算，不读也不改任何游戏状态。</p>
 */
public final class BodyPartResolver {

    /** 头：玩家模型最上面那一块。 */
    private static final Box HEAD_BOX = new Box(-0.2, 1.5, -0.2, 0.2, 1.8, 0.2);

    /** 右肩：躯干右侧向外伸出的那一条（玩家自己的右手边）。 */
    private static final Box RIGHT_SHOULDER_BOX = new Box(0.3, 0.7, -0.15, 0.4, 1.5, 0.15);

    /** 左肩：躯干左侧向外伸出的那一条。 */
    private static final Box LEFT_SHOULDER_BOX = new Box(-0.4, 0.7, -0.15, -0.3, 1.5, 0.15);

    /** 正胸：躯干的上半。 */
    private static final Box CHEST_BOX = new Box(-0.3, 1.1, -0.15, 0.3, 1.5, 0.15);

    /** 腹部：躯干的下半。 */
    private static final Box ABDOMEN_BOX = new Box(-0.3, 0.7, -0.15, 0.3, 1.1, 0.15);

    /** 腿与脚：合并成一段，不再细分左右。 */
    private static final Box LEGS_BOX = new Box(-0.2, 0.0, -0.2, 0.2, 0.7, 0.2);

    /** 站立时的身高，也是下面所有盒子高度数值的基准。 */
    private static final double STANDING_HEIGHT = 1.8;

    /**
     * 参与判定的部位盒子，<b>顺序即优先级</b>。
     *
     * <p>先比小部位（头、两侧肩膀），最后才轮到躯干。这样当一个点同时贴着肩膀盒与躯干盒时，
     * 会算成肩膀——否则肩膀那两条又窄又靠外的盒子永远抢不过中间的大块躯干。</p>
     */
    private static final List<PartBox> PART_BOXES = List.of(
            new PartBox(HEAD_BOX, BodyPart.HEAD),
            new PartBox(RIGHT_SHOULDER_BOX, BodyPart.RIGHT_SHOULDER),
            new PartBox(LEFT_SHOULDER_BOX, BodyPart.LEFT_SHOULDER),
            new PartBox(LEGS_BOX, BodyPart.LEGS),
            new PartBox(CHEST_BOX, BodyPart.CHEST),
            new PartBox(ABDOMEN_BOX, BodyPart.ABDOMEN));

    private BodyPartResolver() {
    }

    /**
     * 判断一次命中落在玩家的哪个部位。
     *
     * @param player 被命中的玩家，用他<b>当前姿态</b>的实际位置与朝向作为基准
     * @param hitPos 命中点的世界坐标
     * @return 命中部位
     */
    public static BodyPart resolve(PlayerEntity player, Vec3d hitPos) {
        Vec3d local = toBodyLocal(player, hitPos);

        for (PartBox candidate : PART_BOXES) {
            if (candidate.box().contains(local)) {
                return candidate.part();
            }
        }

        // 一个都落不进去，就归给最近的那一个。以第一个为起点，保证无论如何都有结果。
        PartBox nearest = PART_BOXES.get(0);
        double nearestDistance = distanceTo(local, nearest.box());
        for (int i = 1; i < PART_BOXES.size(); i++) {
            PartBox candidate = PART_BOXES.get(i);
            double distance = distanceTo(local, candidate.box());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest.part();
    }

    /**
     * 把世界坐标的命中点换算到玩家自己的身体坐标系。
     *
     * <p>换算结果的含义随姿势而变，因为「离脚底多高」这个说法本身就依赖人是站着的：</p>
     * <ul>
     *   <li><b>站立、潜行</b>——人是竖直的，直接量离脚底的高度。潜行时整体压矮，
     *       所以高度要按当前身高与站立身高的比例还原回去，否则头顶那块会落到身体外面。</li>
     *   <li><b>游泳、爬行、鞘翅滑翔、激流冲刺、睡觉</b>——人躺平了，此时「往上」不再是头，
     *       <b>「往前」才是头</b>。所以改用前后方向的偏移来充当高度：身体中心算作躯干，
     *       往前是头、往后是腿。</li>
     * </ul>
     *
     * <p>横向（左右）在任何姿势下都按玩家<b>右手边</b>为正来量，与人是否躺下无关。</p>
     */
    private static Vec3d toBodyLocal(PlayerEntity player, Vec3d hitPos) {
        Vec3d relative = hitPos.subtract(player.getPos());

        double yaw = Math.toRadians(player.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);

        // 玩家右手边 = 面朝方向顺时针转 90°，即 (forwardX, forwardZ) 变成 (-forwardZ, forwardX)。
        double side = relative.x * -forwardZ + relative.z * forwardX;
        double depth = relative.x * forwardX + relative.z * forwardZ;

        if (isLyingFlat(player)) {
            // 躺平时「前后」的偏移范围就是 ±半身高，加上半身高正好平移到 0 ~ 身高这一段，
            // 于是下面那些按站立姿势量好的盒子可以原样套用，不必另备一套。
            return new Vec3d(side, depth + STANDING_HEIGHT / 2.0, 0.0);
        }

        double scale = player.getHeight() / STANDING_HEIGHT;
        return new Vec3d(side, relative.y / scale, 0.0);
    }

    /**
     * 玩家此刻是否躺平着（身体沿水平方向伸展）。
     *
     * <p>判据是原版给这些姿态定的身高：游泳、爬行、鞘翅滑翔、激流冲刺都是 0.6 格，
     * 睡觉更是完全躺下，这几种一律按躺平处理，改用前后方向充当高度。</p>
     *
     * <p><b>激流冲刺必须在列</b>：它是三叉戟拖着人向前冲，人一样是横着的；漏掉它，
     * 下面那行按比例还原高度的算法会拿 0.6 格的身高去除实际高度，把身体拉长三倍。</p>
     *
     * <p><b>潜行不在此列</b>：它虽然也把人压矮到 1.5 格，但人仍然是竖着的，
     * 由按比例还原那一路处理即可。</p>
     */
    private static boolean isLyingFlat(PlayerEntity player) {
        EntityPose pose = player.getPose();
        return pose == EntityPose.SWIMMING
                || pose == EntityPose.FALL_FLYING
                || pose == EntityPose.SPIN_ATTACK
                || pose == EntityPose.SLEEPING;
    }

    /**
     * 求一点到盒子的最短距离；点落在盒内时为 0。
     */
    private static double distanceTo(Vec3d point, Box box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 一个部位盒子与它代表的部位。 */
    private record PartBox(Box box, BodyPart part) {
    }
}
