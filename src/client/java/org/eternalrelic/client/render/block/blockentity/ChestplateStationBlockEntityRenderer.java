package org.eternalrelic.client.render.block.blockentity;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import org.eternalrelic.block.entity.ChestplateStationBlockEntity;

/**
 * 把台面上那件胸甲画出来 —— 借用游戏自带的盔甲形状与贴图，自己排"摆在哪儿、露出哪几个部件、摆什么姿态"。
 *
 * <p><b>为什么不用实体</b>：在台子上真放一个隐形盔甲架也能显示，但那会带来一个要 tick、要存档、
 * 会被清理实体的插件扫到的活物，方块被拆、区块卸载都得单独处理。这里走的是"方块自己存、自己画"：
 * 不产生任何实体，也不受服务器上其它插件影响。</p>
 *
 * <p><b>画的是什么</b>：游戏启动时就已经把玩家那套人形模型的各个部件加载好了，这里做的只是
 * 把胸甲该露的那几个部件（躯干与双臂）摆到台子上。因此任何模组加的胸甲放上来都能正确显示——
 * 它长什么样由游戏自己的规则决定，不认物品名单。</p>
 *
 * <p><b>⚠️ 模型必须自己造一份，不能用游戏共享的那一份。</b>下面要给手臂摆姿态，
 * 而 {@code EntityModelLoader} 里的模型是全局共用的同一个对象——直接改它，
 * 玩家自己身上穿的盔甲手臂也会被一起拧转。所以这里按原版外层盔甲的数据重新造一份。</p>
 *
 * <p><b>姿态用的是原版的自然垂下。</b>制作者的参考模型里手臂是平举的，但那个姿态摆上台子后
 * 像"张开翅膀"，已按制作者要求退回自然垂下。这一块（姿态与高低）制作者另有安排，
 * 接手前先与他确认。</p>
 *
 * <p><b>位置也照着那份参考定</b>：参考里躯干落在方块坐标第 7.5~19.5 像素处，
 * 换算过来就是"玩家脚底站在方块往下一格半的地方"，也就是下面的 {@link #FOOT_Y}。
 * 要整体调高低，只改这一个数。</p>
 *
 * <p><b>那几条容易漏的细节</b>：皮革染色、附魔光晕、盔甲纹饰。前两条在下面按原版的做法补上了；
 * 纹饰（1.20 新加的那个）暂时没做，所以打过纹饰的胸甲在台子上看不到纹饰，其余照常。</p>
 */
@Environment(EnvType.CLIENT)
public class ChestplateStationBlockEntityRenderer implements BlockEntityRenderer<ChestplateStationBlockEntity> {

    /** 玩家贴图的尺寸，造模型时用来算贴图坐标。 */
    private static final int PLAYER_TEXTURE_SIZE = 64;

    /**
     * 玩家模型的脚底落在方块坐标系里的高度（单位：格）。
     *
     * <p>由制作者的参考位置反推：躯干底边落在第 7.5 像素、而站立时躯干底边在脚底往上
     * 第 12 像素处，于是脚底落在 7.5 − 12 = −4.5 像素，即 −0.28125 格。</p>
     */
    private static final double FOOT_Y = -0.28125D;

    /** 贴图路径的缓存：每帧都要算一次，而同一件胸甲算出来的结果永远一样。 */
    private static final Map<String, Identifier> TEXTURE_CACHE = new HashMap<>();

    /** 外层盔甲模型（胸甲用的就是它），台子专用的独立一份。 */
    private final ArmorEntityModel<LivingEntity> outerArmor;

    /**
     * @param ctx 渲染器工厂上下文。本渲染器自己造模型（见类文档），用不到它；
     *            但这个参数是工厂签名的要求，不能省。
     */
    public ChestplateStationBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.outerArmor = new ArmorEntityModel<>(ArmorEntityModel
                .getModelData(new Dilation(1.0F))
                .getRoot()
                .createPart(PLAYER_TEXTURE_SIZE, PLAYER_TEXTURE_SIZE));
    }

    /**
     * 画出台上摆着的那件胸甲。
     *
     * <p>台子空着、或放着不是胸甲的东西时，这一帧什么都不画（台座模型照旧显示）。</p>
     */
    @Override
    public void render(ChestplateStationBlockEntity entity, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemStack chestplate = entity.getChestplate();

        if (!(chestplate.getItem() instanceof ArmorItem armor) || armor.getSlotType() != EquipmentSlot.CHEST) {
            return;
        }

        poseForDisplay(this.outerArmor);

        matrices.push();

        // 摆法与游戏画人物时完全一致：先把玩家模型的原点（脚底）挪到台子上、转向南边，
        // 再把 y 轴翻过来（人物模型在它自己的坐标里是"上为负"，不翻会倒着长），
        // 最后那一下 -1.501 是游戏把模型抬到脚踩地面所用的固定量
        matrices.translate(0.5D, FOOT_Y, 0.5D);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0D, -1.501D, 0.0D);

        drawArmor(this.outerArmor, matrices, vertexConsumers, light, chestplate, armor);

        matrices.pop();
    }

    /**
     * 胸甲会高出、也宽过方块本身，因此要告诉游戏"别因为方块包围盒装不下就不画"。
     */
    @Override
    public boolean rendersOutsideBoundingBox(ChestplateStationBlockEntity blockEntity) {
        return true;
    }

    // ==================== 摆出展示用的姿态 ====================

    /**
     * 把模型摆成展示姿态：只留胸甲覆盖的那几个部件，手臂沿用原版的自然垂下。
     *
     * <p>每次渲染都重摆一遍——同一个方块每一帧都要重画，而这份模型是这个渲染器专用的那一份。</p>
     *
     * @param model 要摆姿态的模型
     */
    private static void poseForDisplay(ArmorEntityModel<LivingEntity> model) {
        model.setVisible(false);

        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
    }

    // ==================== 画一件盔甲 ====================

    /**
     * 按原版的规矩画一件盔甲：先看要不要染色，再看有没有附魔。
     *
     * @param model           已经摆好姿态的模型
     * @param matrices        当前的变换栈
     * @param vertexConsumers 绘制缓冲
     * @param light           光照
     * @param stack           台面上的那件胸甲
     * @param armor           它对应的防具物品
     */
    private static void drawArmor(ArmorEntityModel<LivingEntity> model, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, ItemStack stack, ArmorItem armor) {
        // 皮革甲可以染色：先铺一层染过的底色，再叠一层原图的"覆盖层"——原版就是这么画的
        if (armor instanceof DyeableArmorItem dyeable) {
            int color = dyeable.getColor(stack);

            draw(model, matrices, vertexConsumers, light, textureOf(armor, null),
                    (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
            draw(model, matrices, vertexConsumers, light, textureOf(armor, "overlay"), 1.0F, 1.0F, 1.0F);
        } else {
            draw(model, matrices, vertexConsumers, light, textureOf(armor, null), 1.0F, 1.0F, 1.0F);
        }

        // 附了魔的装备自带一层流动的光。不补这一步，台子上的附魔装备看起来就是件白板
        if (stack.hasGlint()) {
            model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint()),
                    light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * 用一张贴图把整个模型画一遍。
     *
     * @param texture 盔甲贴图
     * @param red     红色分量（染色时用；不染色传 1）
     * @param green   绿色分量
     * @param blue    蓝色分量
     */
    private static void draw(ArmorEntityModel<LivingEntity> model, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, Identifier texture,
            float red, float green, float blue) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(texture));
        model.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, red, green, blue, 1.0F);
    }

    /**
     * 算出一件盔甲该用哪张贴图。
     *
     * <p>路径规则是游戏自己定的：材质名 + 第一层或第二层（护腿在第二层）+ 可选的覆盖层。
     * 照它的规则算，别的模组加的盔甲也能取到正确的贴图。</p>
     *
     * @param armor   防具物品
     * @param overlay 要取"覆盖层"时传 {@code overlay}，否则传 {@code null}
     * @return 贴图编号
     */
    private static Identifier textureOf(ArmorItem armor, String overlay) {
        String path = "textures/models/armor/" + armor.getMaterial().getName()
                + "_layer_1" + (overlay == null ? "" : "_" + overlay) + ".png";

        return TEXTURE_CACHE.computeIfAbsent(path, Identifier::new);
    }
}
