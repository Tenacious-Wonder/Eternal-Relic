package org.eternalrelic.client.render.world;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import org.eternalrelic.capability.using.CuratorLensEffect;

/**
 * 箱子印记 —— 举着馆藏透镜时，每个箱子正中心浮起一枚会呼吸的淡黄光斑。
 *
 * <p><b>这是本模组第一处「往世界里画东西」的代码。</b>此前的渲染都是在画某一具体对象
 * （某一个方块实体、某一件物品），而这里要在每一帧里往整个世界的画面上添笔画，因此它不属于
 * 任何方块或物品，单独放在 {@code client.render.world} 下。</p>
 *
 * <h2>为什么自己做，而不是用游戏自带的粒子</h2>
 * <p>游戏自带的粒子有两个绕不开的毛病：一是<b>会被墙挡住</b>，而这里要的正是隔着方块也能看见；
 * 二是<b>有寿命</b>，想让一个位置一直有东西，就得每隔几刻补撒一颗，补与被补之间难免忽明忽暗。</p>
 *
 * <p>所以这里不要粒子系统，直接每帧自己把光斑画上去：想让它一直在就一直在，想让它穿墙就关掉
 * 深度比较，两件事都只是一句话的事。看起来仍是「一颗粒子」，但不受那两条限制。</p>
 *
 * <h2>为什么画在这里、为什么自己直接画</h2>
 * <p>用的是 Fabric 给的 {@code BEFORE_DEBUG_RENDER}——原版调试线框（碰撞箱、区块边界那些）
 * 正是在这一刻画的。选它有两个好处：此时相机变换已经就位，坐标可以直接按世界坐标给；
 * 而且这一刻就是给「画点东西到画面上」准备的，不与别的渲染打架。</p>
 *
 * <p>关掉的开关在画完后一律复位——这一帧后面还有别的渲染要用它们。</p>
 *
 * <h2>箱子清单多久数一次</h2>
 * <p>每帧都去数一遍是浪费：玩家举着镜子慢慢转视角时，箱子并没有动。因此改成每
 * {@value #REFRESH_INTERVAL_TICKS} 刻重数一次，画面依旧跟手，开销降到四分之一。</p>
 */
public final class ChestMarkRenderer {

    /** 每隔多少刻重新数一遍身边的箱子。5 刻为四分之一秒，走路时看不出延迟。 */
    private static final int REFRESH_INTERVAL_TICKS = 5;

    /** 光斑的贴图。它是一张白色的柔边圆点，颜色由下面那三个分量染上去。 */
    private static final Identifier MARK_TEXTURE =
            new Identifier("eternal_relic", "textures/world/curator_mark.png");

    /** 光斑的基础半径（格）。0.28 即直径约半格多，比箱子本身小一圈。 */
    private static final float MARK_RADIUS = 0.28F;

    /** 光斑染色后的红分量。与绿、蓝合起来是旧纸张那种泛黄的白。 */
    private static final float MARK_RED = 0.97F;

    /** 光斑染色后的绿分量。 */
    private static final float MARK_GREEN = 0.90F;

    /** 光斑染色后的蓝分量。压得最低，于是整体偏暖黄。 */
    private static final float MARK_BLUE = 0.66F;

    /** 基础不透明度。留出足够的透明，才不会像贴了一张实心圆片。 */
    private static final float MARK_ALPHA = 0.62F;

    /** 一胀一缩的幅度（相对基础半径）与快慢（弧度/秒）。 */
    private static final float PULSE_AMPLITUDE = 0.18F;
    private static final double PULSE_SPEED = 1.7D;

    /** 明暗起伏的幅度。与缩放同相，看起来才像是「一呼一吸」而不是各动各的。 */
    private static final float ALPHA_SWING = 0.18F;

    /** 上下浮动的幅度（格）与快慢（弧度/秒）。比缩放慢一些，像是浮在水面上。 */
    private static final float BOB_AMPLITUDE = 0.06F;
    private static final double BOB_SPEED = 1.1D;

    /** 当前该浮印记的箱子。每 {@link #REFRESH_INTERVAL_TICKS} 刻整体换一次。 */
    private static final List<BlockPos> MARKED = new ArrayList<>();

    /** 距离上一次重数过了多少刻。初值给满，让第一次 tick 立刻数一遍。 */
    private static int ticksSinceRefresh = REFRESH_INTERVAL_TICKS;

    private ChestMarkRenderer() {
    }

    /**
     * 由 {@link org.eternalrelic.client.EternalRelicClient#onInitializeClient()} 调用，
     * 挂上「隔几刻数一次箱子」与「每帧画印记」两个回调。
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                // 退回主菜单或换维度途中：清单留着没有意义，也不该继续指着旧世界里的坐标
                MARKED.clear();
                return;
            }

            if (++ticksSinceRefresh < REFRESH_INTERVAL_TICKS) {
                return;
            }

            ticksSinceRefresh = 0;
            MARKED.clear();
            MARKED.addAll(CuratorLensEffect.chestsInSight(client.player));
        });

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ChestMarkRenderer::render);
    }

    /**
     * 把清单里的每个箱子都点上一枚印记。
     *
     * @param context 本帧的世界渲染现场，提供相机与矩阵
     */
    private static void render(WorldRenderContext context) {
        if (MARKED.isEmpty()) {
            return;
        }

        // 这一刻的矩阵里还没有相机的位置，得自己挪过去；挪完之后印记坐标就能直接按世界坐标给
        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, MARK_TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 关掉背面剔除：光斑是朝着相机的一片纸，从背后看也要照画，否则转头时会突然消失
        RenderSystem.disableCull();

        // 这两句是「穿透方块可见」的关键：不写深度、也不比较深度，画出来的光斑就压在所有东西之上
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        // 时间按「游戏里的一天」取模再换算成秒：世界开得越久数字越大，直接拿去算正弦迟早会丢精度
        double seconds = (context.world().getTime() % 24000L + context.tickDelta()) / 20.0D;
        Camera camera = context.camera();

        for (BlockPos pos : MARKED) {
            appendMark(buffer, matrices, camera, pos, seconds);
        }

        tessellator.draw();

        // 收工复位：这些开关是全局的，这一帧后面还有原版自己的渲染要接着用
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    /**
     * 往画笔里添一枚正对相机的光斑。
     *
     * <p>画法是先挪到箱子的正中心，再把坐标系转向相机，于是只要在平面的四个角各给一个顶点，
     * 得到的就是一张永远正对着玩家的方片——圆形的观感来自贴图自己把四个角画成透明。</p>
     *
     * @param buffer   正在攒顶点的画笔
     * @param matrices 已经把相机挪掉的矩阵栈
     * @param camera   本帧的相机，提供朝向
     * @param pos      这个箱子占的那一格
     * @param seconds  本帧的游戏内时刻（秒），用来算呼吸与浮动
     */
    private static void appendMark(BufferBuilder buffer, MatrixStack matrices, Camera camera,
            BlockPos pos, double seconds) {
        // 每枚印记各有各的相位，由它所在的格子算出来：相邻的箱子因此不会像节拍器一样同时胀缩
        float phase = phaseOf(pos);

        float wave = (float) Math.sin(seconds * PULSE_SPEED + phase);
        float radius = MARK_RADIUS * (1.0F + PULSE_AMPLITUDE * wave);
        float alpha = MARK_ALPHA + ALPHA_SWING * wave;
        float bob = (float) Math.sin(seconds * BOB_SPEED + phase) * BOB_AMPLITUDE;

        matrices.push();
        // 箱子那一格的正中心；上下浮动也在这里加上
        matrices.translate(pos.getX() + 0.5D, pos.getY() + 0.5D + bob, pos.getZ() + 0.5D);
        // 转向相机：乘上相机朝向之后，接下来在平面里给的顶点就正对玩家
        matrices.multiply(camera.getRotation());
        Matrix4f position = matrices.peek().getPositionMatrix();

        appendCorner(buffer, position, radius, -radius, 1.0F, 1.0F, alpha);
        appendCorner(buffer, position, -radius, -radius, 0.0F, 1.0F, alpha);
        appendCorner(buffer, position, -radius, radius, 0.0F, 0.0F, alpha);
        appendCorner(buffer, position, radius, radius, 1.0F, 0.0F, alpha);

        matrices.pop();
    }

    /**
     * 往画笔里添光斑的一个角。
     *
     * @param buffer   正在攒顶点的画笔
     * @param position 已经挪到箱子中心、并转向相机的坐标变换
     * @param x        该角在平面里的横坐标
     * @param y        该角在平面里的纵坐标
     * @param u        贴图横坐标
     * @param v        贴图纵坐标（0 在上）
     * @param alpha    本帧的不透明度
     */
    private static void appendCorner(BufferBuilder buffer, Matrix4f position,
            float x, float y, float u, float v, float alpha) {
        buffer.vertex(position, x, y, 0.0F)
                .texture(u, v)
                .color(MARK_RED, MARK_GREEN, MARK_BLUE, alpha)
                .next();
    }

    /**
     * 由箱子所在的格子派生这枚印记的相位。
     *
     * <p>三个方向各乘一个互不相同的系数，是为了让<b>任何两个格子都不会算出同一个相位</b>——
     * 若只用坐标之和，沿对角线排开的一串箱子仍会整齐划一地一起胀缩。</p>
     *
     * @param pos 箱子所在的格子
     * @return 该格对应的相位（弧度，落在 0 到 2π 之间）
     */
    private static float phaseOf(BlockPos pos) {
        float raw = pos.getX() * 1.7F + pos.getY() * 2.3F + pos.getZ() * 3.1F;
        float full = (float) (Math.PI * 2.0D);
        return raw % full;
    }
}
