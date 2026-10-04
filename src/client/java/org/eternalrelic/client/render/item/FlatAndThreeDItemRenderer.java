package org.eternalrelic.client.render.item;

import java.util.function.DoubleSupplier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.client.render.ShepherdBellSwing;
import org.eternalrelic.registry.ModItems;

/**
 * 「物品栏里是平面图标、拿在手上与掉在地上是立体模型」这一类物品的共用渲染器。
 *
 * <p>引魂燃灯与两把锤子都是这个样子，原先各写了一份几乎逐行相同的渲染器，只差"模型编号从哪来"。
 * 合成一个之后，改一处就够了，以后每加一件同类物品也只需在 {@link #register()} 里添一行。</p>
 *
 * <p><b>为什么这里必须写代码，光改模型文件做不到</b>：一件物品只有一个模型，而模型文件只能调
 * 「怎么摆」，换不了「用哪个形状」——平面图标与立体模型是两个完全不同的模型。
 * 要让物品栏看到一个、手上看到另一个，就得在渲染的那一刻按场景挑，这正是本类做的事。</p>
 *
 * <p><b>它怎么会被调起来</b>：物品的模型文件声明继承 {@code minecraft:builtin/entity}
 * （也就是那几个"外壳"文件），原版一看到这个标记，就把渲染整个交给注册过的渲染器，
 * 并把「现在是在哪儿画」一并传进来。用哪两个模型由构造参数给出，本类因此不认识任何具体物品。</p>
 *
 * <p><b>三条踩过坑的细节，别"顺手简化"</b>：</p>
 * <ul>
 *   <li>那几个真正的模型**必须显式声明要加载**——原版只加载「被引用到」的模型，
 *       而外壳里什么都没引用，直接去取会拿到 {@code null}；</li>
 *   <li>外壳里那行 {@code "gui_light": "front"} 不能删，否则物品栏里的平面图标会发暗；</li>
 *   <li>取不到模型要判空、并在两个模型之间兜底，两个都取不到就这一帧不画——
 *       在渲染里抛出异常会让客户端当场闪退。</li>
 * </ul>
 *
 * <p><b>第一人称的摇动是可选的。</b>构造时传了「角度供给」的物品（目前只有牧羊人铃铛）会按那一刻
 * 给出的角度摆动，其余物品照旧纹丝不动——<b>角度为 0 就完全不动</b>。摆动<b>只在第一人称发生</b>：
 * 第三人称与别人眼里的姿态由模型自己的 {@code display} 决定，这里不去插手。</p>
 */
@Environment(EnvType.CLIENT)
public class FlatAndThreeDItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    /** 引魂燃灯的平面图标与立体模型。 */
    private static final Identifier SOUL_LANTERN_FLAT = id("item/soul_lantern_flat");
    private static final Identifier SOUL_LANTERN_3D = id("item/soul_lantern_3d");

    /** 大铁锤的平面图标与立体模型。 */
    private static final Identifier HAMMER_FLAT = id("item/hammer_flat");
    private static final Identifier HAMMER_3D = id("item/hammer_3d");

    /** 小铁锤的平面图标与立体模型。 */
    private static final Identifier SMALL_HAMMER_FLAT = id("item/small_hammer_flat");
    private static final Identifier SMALL_HAMMER_3D = id("item/small_hammer_3d");

    /** 牧羊人铃铛的平面图标与立体模型。 */
    private static final Identifier SHEPHERD_BELL_FLAT = id("item/shepherd_bell_flat");
    private static final Identifier SHEPHERD_BELL_3D = id("item/shepherd_bell_3d");

    /**
     * 摇摆的支点——<b>黑环的最顶端</b>。
     *
     * <p>坐标取自模型里那个黑环旋转之后最高的那个顶点（8.07 / 6.63 / 7.95，除以 16 换算到此处的
     * 0~1 空间）。铃铛因此像挂在这只环上一样左右摆，而不是绕自己的腰拧。</p>
     */
    private static final float SWING_PIVOT_X = 0.504375F;
    private static final float SWING_PIVOT_Y = 0.414375F;
    private static final float SWING_PIVOT_Z = 0.496875F;

    /** 物品栏里用的那个平面图标。 */
    private final Identifier flatModel;

    /** 手持与掉在地上用的立体模型。 */
    private final Identifier threeDModel;

    /**
     * 第一人称里这一刻应该摆到多少度。
     *
     * <p>由物品自己决定：铃铛交给 {@link ShepherdBellSwing}，平时它一直返回 0，也就是「纹丝不动」；
     * 摇响之后才给出那半秒的摆动角度。</p>
     */
    private final DoubleSupplier firstPersonAngle;

    public FlatAndThreeDItemRenderer(Identifier flatModel, Identifier threeDModel) {
        this(flatModel, threeDModel, null);
    }

    public FlatAndThreeDItemRenderer(Identifier flatModel, Identifier threeDModel,
            DoubleSupplier firstPersonAngle) {
        this.flatModel = flatModel;
        this.threeDModel = threeDModel;
        this.firstPersonAngle = firstPersonAngle == null ? () -> 0.0D : firstPersonAngle;
    }

    /**
     * 由客户端入口调用：把六个真正的模型挂进「要加载」的名单，并把渲染器接到这三件物品上。
     *
     * <p>新增一件同类物品时，在上面加两个编号常量、在这里补一行注册即可，
     * 不要再另抄一份渲染器——那正是本类被抽出来的原因。</p>
     */
    public static void register() {
        ModelLoadingPlugin.register(context -> context.addModels(
                SOUL_LANTERN_FLAT, SOUL_LANTERN_3D,
                HAMMER_FLAT, HAMMER_3D,
                SMALL_HAMMER_FLAT, SMALL_HAMMER_3D,
                SHEPHERD_BELL_FLAT, SHEPHERD_BELL_3D));

        registerItem(ModItems.SOUL_LANTERN, SOUL_LANTERN_FLAT, SOUL_LANTERN_3D);
        registerItem(ModItems.HAMMER, HAMMER_FLAT, HAMMER_3D);
        registerItem(ModItems.SMALL_HAMMER, SMALL_HAMMER_FLAT, SMALL_HAMMER_3D);

        // 铃铛平时拿在手里纹丝不动，只有摇响的那半秒里晃两下——角度由它自己的动画给出
        registerItem(ModItems.SHEPHERD_BELL, SHEPHERD_BELL_FLAT, SHEPHERD_BELL_3D,
                ShepherdBellSwing::currentDegrees);
    }

    /**
     * 按「在哪儿显示」挑一个模型，然后照常走一遍完整的物品渲染。
     *
     * @param stack           正在渲染的物品
     * @param mode            显示场景：物品栏、手持、地上……
     * @param matrices        矩阵栈
     * @param vertexConsumers 顶点消费者
     * @param light           光照
     * @param overlay         覆盖层 UV
     */
    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        boolean flat = mode == ModelTransformationMode.GUI;
        BakedModel model = lookup(flat ? this.flatModel : this.threeDModel);

        // 万一要的那个没加载出来，退而用另一个——总比让这一格空着好
        if (model == null) {
            model = lookup(flat ? this.threeDModel : this.flatModel);
        }

        // 两个都没有：宁可这一帧不画，也绝不能让客户端崩在渲染里（那是进游戏就闪退）
        if (model == null) {
            return;
        }

        // 原版把控制权交给我们之前，已经替物品的模型套过一次显示变换、又平移了 -0.5 格
        // （把模型从 0~1 挪到以原点居中）。下面要重新走一遍完整的物品渲染，
        // 所以先把那半格补回来，否则整件东西会偏出去半格。
        matrices.translate(0.5F, 0.5F, 0.5F);

        // 只在第一人称、且这一刻真的该摆时才绕挂环摆一下；平时角度是 0，纹丝不动
        if (isFirstPerson(mode)) {
            float swing = (float) this.firstPersonAngle.getAsDouble();
            if (swing != 0.0F) {
                swingAroundPivot(matrices,
                        mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND ? -swing : swing);
            }
        }

        // 走原版的物品渲染：四个参数里的 false 表示"不要自己再套一次显示变换"，
        // 模型已经由上面挑好并传进去
        MinecraftClient.getInstance().getItemRenderer()
                .renderItem(stack, mode, false, matrices, vertexConsumers, light, overlay, model);
    }

    /**
     * 给一件物品接上本渲染器。
     *
     * @param item        目标物品
     * @param flatModel   它在物品栏里用的平面图标编号
     * @param threeDModel 它拿在手上与掉在地上用的立体模型编号
     */
    private static void registerItem(Item item, Identifier flatModel, Identifier threeDModel) {
        registerItem(item, flatModel, threeDModel, null);
    }

    /**
     * 给一件物品接上本渲染器，并决定它第一人称里怎么摆。
     *
     * @param item             目标物品
     * @param flatModel        它在物品栏里用的平面图标编号
     * @param threeDModel      它拿在手上与掉在地上用的立体模型编号
     * @param firstPersonAngle 第一人称里这一刻该摆多少度；传 {@code null} 表示永远不动
     */
    private static void registerItem(Item item, Identifier flatModel, Identifier threeDModel,
            DoubleSupplier firstPersonAngle) {
        BuiltinItemRendererRegistry.INSTANCE.register(item,
                new FlatAndThreeDItemRenderer(flatModel, threeDModel, firstPersonAngle));
    }

    /**
     * @param mode 显示场景
     * @return 这一帧是不是画在玩家自己的第一人称视野里
     */
    private static boolean isFirstPerson(ModelTransformationMode mode) {
        return mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
    }

    /**
     * 把物品绕它顶端的挂环摆一个角度。
     *
     * <p><b>为什么支点要自己指定</b>：原点在模型中心，直接绕着它转的话，铃铛会像被拧了一圈，
     * 而不是在摇。挂在环上摆，才是这类东西该有的样子。</p>
     *
     * @param matrices 矩阵栈
     * @param degrees  要摆到的角度（度）
     */
    private static void swingAroundPivot(MatrixStack matrices, float degrees) {
        matrices.translate(SWING_PIVOT_X, SWING_PIVOT_Y, SWING_PIVOT_Z);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        matrices.translate(-SWING_PIVOT_X, -SWING_PIVOT_Y, -SWING_PIVOT_Z);
    }

    /**
     * @param path 模型编号在本模组命名空间下的那一段
     * @return 完整的模型编号
     */
    private static Identifier id(String path) {
        return new Identifier(EternalRelic.MOD_ID, path);
    }

    /**
     * @param id 模型的编号
     * @return 编译好的模型；没有这个模型时返回 {@code null}
     */
    private static BakedModel lookup(Identifier id) {
        return MinecraftClient.getInstance().getItemRenderer().getModels().getModelManager().getModel(id);
    }
}
