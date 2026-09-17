package org.eternalrelic.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.registry.ModItems;

/**
 * 引魂燃灯的物品渲染 —— 同一件东西，在物品栏里是平面图标，拿在手上与掉在地上是立体模型。
 *
 * <p><b>为什么这里必须写代码，光改模型文件做不到</b>：一件物品只有一个模型，而模型文件只能调
 * 「怎么摆」，换不了「用哪个形状」。要让物品栏看到一个平面、手上看到一个立体，
 * 就得在渲染的那一刻按场景挑模型——这正是本类做的事。</p>
 *
 * <p><b>它怎么会被调起来</b>：物品的模型文件声明继承 {@code minecraft:builtin/entity}，
 * 原版一看到这个标记，就把渲染整个交给注册过的渲染器，并把「现在是在哪儿画」一并传进来。
 * 所以 {@code soul_lantern.json} 只是一个壳，真正的形状都在下面这两个模型里。</p>
 *
 * <p><b>掉在地上的与拿在手上一样大</b>：姿态与大小都写在立体模型的 {@code display.ground} 里，
 * 不在这里做。摆在模型里而不是代码里，是为了让人在 Blockbench 里一眼看得到、也方便再调。</p>
 *
 * <p>⚠️ <b>那个壳里必须写 {@code "gui_light": "front"}</b>，否则物品栏里的图标会发暗。
 * 原版 {@code DrawContext.drawItem} 会问模型「你是平面还是立体」，回答「立体」就**不关掉 GUI
 * 立体光照**——而 {@code builtin/entity} 自带 {@code gui_light: side}，于是物品栏里那张平面图标
 * 会被按方块的方式打光。这一条踩过坑，改动模型文件时别把那行删掉。</p>
 */
@Environment(EnvType.CLIENT)
public class SoulLanternItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    /** 物品栏里那个平面图标。 */
    private static final Identifier FLAT_MODEL =
            new Identifier(EternalRelic.MOD_ID, "item/soul_lantern_flat");

    /** 手持与掉在地上用的立体模型。 */
    private static final Identifier THREE_D_MODEL =
            new Identifier(EternalRelic.MOD_ID, "item/soul_lantern_3d");

    /**
     * 由客户端入口调用：把这两个模型挂上，并把渲染器接上。
     *
     * <p><b>为什么要显式声明这两个模型</b>：原版只会加载「被什么东西引用到」的模型。
     * 物品自己的模型是个只写了 {@code builtin/entity} 的空壳，这两个真正的形状因此没人引用，
     * 不会被编译进模型表——直接去取会拿到 {@code null}。这里把它们加进「要加载」的名单里。</p>
     */
    public static void register() {
        ModelLoadingPlugin.register(context -> context.addModels(FLAT_MODEL, THREE_D_MODEL));
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.SOUL_LANTERN, new SoulLanternItemRenderer());
    }

    /**
     * 按「在哪儿显示」挑一个模型，然后照常走一遍物品渲染。
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
        MinecraftClient client = MinecraftClient.getInstance();
        ItemRenderer itemRenderer = client.getItemRenderer();
        boolean flat = mode == ModelTransformationMode.GUI;

        BakedModel model = lookup(flat ? FLAT_MODEL : THREE_D_MODEL);

        // 万一要的那个没加载出来，退而用另一个——总比让这一格空着好
        if (model == null) {
            model = lookup(flat ? THREE_D_MODEL : FLAT_MODEL);
        }

        // 两个都没有：宁可这一帧不画，也绝不能让客户端崩在渲染里（那是进游戏就闪退）
        if (model == null) {
            return;
        }

        // 原版把控制权交给我们之前，已经替物品的模型套过一次显示变换、又平移了 -0.5 格
        // （把模型从 0~1 挪到以原点居中）。下面要重新走一遍完整的物品渲染，
        // 所以先把那半格补回来，否则整盏灯会偏出去半格。
        matrices.translate(0.5F, 0.5F, 0.5F);

        itemRenderer.renderItem(stack, mode, false, matrices, vertexConsumers, light, overlay, model);
    }

    /**
     * @param id 模型的编号
     * @return 编译好的模型；没有这个模型时返回 {@code null}
     */
    private static BakedModel lookup(Identifier id) {
        return MinecraftClient.getInstance().getItemRenderer().getModels().getModelManager().getModel(id);
    }
}
