package org.eternalrelic.client.render.blockentity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

import org.eternalrelic.block.ChestplateStationBlock;
import org.eternalrelic.block.entity.ChestplateStationBlockEntity;
import org.eternalrelic.client.render.armor.StaticArmorRenderer;

@Environment(EnvType.CLIENT)
public class ChestplateStationBlockEntityRenderer implements BlockEntityRenderer<ChestplateStationBlockEntity> {
    /** 盔甲贴图的宽度，模型 UV 按它归一化。 */
    private static final int ARMOR_TEXTURE_WIDTH = 64;
    private static final int ARMOR_TEXTURE_HEIGHT = 32;

    /** 展示姿态下手臂的侧倾角度：绕 Z 轴转 90 度，由自然垂下变成水平横举。 */
    private static final float ARM_ROLL_DEGREES = 45.555F;

    /** 手臂横举后向外让开的距离（模型像素），用于避开胸甲部件。 */
    private static final float ARM_OUTWARD_OFFSET = 3.1F;

    /** 玩家模型的脚底在方块坐标系里的高度（单位：格）。 */
    private static final double FOOT_Y = -0.28125D;

    private final StaticArmorRenderer armorRenderer;

    public ChestplateStationBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        // 内层模型（膨胀 0.5）供护腿，外层（膨胀 1.0）供头盔、胸甲与靴子。
        // 两份都必须单独造：绘制要改部件可见性，而游戏共享的那一份被所有盔甲渲染共用，改了会波及玩家身上的盔甲
        this.armorRenderer = new StaticArmorRenderer(
                createArmorModel(new Dilation(0.5F)),
                createChestplateModel(),
                ctx.getRenderManager().getModels().getModelManager()
                        .getAtlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE));
    }

    /** 画出台上的那件胸甲；台面空着或放着的不是胸甲时，这一帧什么都不画。 */
    @Override
    public void render(ChestplateStationBlockEntity entity, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemStack chestplate = entity.getChestplate();

        // 台面数据理论上只可能是胸甲，这里再判一次是为了挡住存档或其它途径塞进来的非胸甲物品
        if (!(chestplate.getItem() instanceof ArmorItem armor) || armor.getSlotType() != EquipmentSlot.CHEST) {
            return;
        }

        Direction facing = entity.getCachedState().get(ChestplateStationBlock.FACING);

        matrices.push();

        // 摆法与游戏绘制人物时一致，四步都不能少：
        // ① 把模型原点（脚底）挪到台面中心，高度由 FOOT_Y 决定；
        // ② 转身：人物模型自身朝北，180 − 朝向就是原版实体渲染所用的换算；
        // ③ 翻转模型自身的坐标轴——人物模型导出时"上为负"，不翻会头朝下；
        // ④ -1.501 是原版把模型抬到脚踩地面所用的固定量
        matrices.translate(0.5D, FOOT_Y, 0.5D);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - facing.asRotation()));
        matrices.scale(-2.0F, -2.0F, 2.0F);
        matrices.translate(0.0D, -1.45D, 0.0D);

        // 纹饰要用动态注册表解析、由世界提供；方块实体尚未加入世界时拿不到，此时只是不画纹饰
        World world = entity.getWorld();
        DynamicRegistryManager registries = world == null ? null : world.getRegistryManager();

        this.armorRenderer.draw(chestplate, EquipmentSlot.CHEST, registries, matrices, vertexConsumers, light);

        matrices.pop();
    }

    /** 胸甲高过、也宽过方块本身，需要允许在方块包围盒之外绘制。 */
    @Override
    public boolean rendersOutsideBoundingBox(ChestplateStationBlockEntity blockEntity) {
        return true;
    }

    /** 造出台面展示用的胸甲模型：外层盔甲，双臂横举。 */
    private static BipedEntityModel<LivingEntity> createChestplateModel() {
        BipedEntityModel<LivingEntity> model = createArmorModel(new Dilation(1.0F));

        // 原版姿态是自然垂下（手臂沿 +Y 向下），绕 Z 轴转 90 度即指向身体外侧；左右臂方向相反
        model.rightArm.roll = ARM_ROLL_DEGREES;
        model.leftArm.roll = -ARM_ROLL_DEGREES;

        // 横过来之后手臂根部仍落在胸甲所在的 x 区间里，各向外让开一点留出缝隙
        model.rightArm.pivotX -= ARM_OUTWARD_OFFSET;
        model.leftArm.pivotX += ARM_OUTWARD_OFFSET;

        return model;
    }

    /** 按原版外层盔甲的数据造一份模型。 */
    private static BipedEntityModel<LivingEntity> createArmorModel(Dilation dilation) {
        // 尺寸必须取 64×32：原版盔甲贴图的高是 32，写成 64 会让 UV 归一化整体偏移、贴图错位
        return new ArmorEntityModel<>(TexturedModelData
                .of(ArmorEntityModel.getModelData(dilation), ARMOR_TEXTURE_WIDTH, ARMOR_TEXTURE_HEIGHT)
                .createModel());
    }
}
