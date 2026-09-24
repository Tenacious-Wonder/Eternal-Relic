package org.eternalrelic.registry;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.block.entity.ChestplateStationBlockEntity;

/**
 * 本模组的方块实体注册入口。
 *
 * <p>与 {@link ModBlocks}、{@link ModItems} 同一套写法：静态字段初始化时即完成注册，
 * {@link #register()} 只用来唤醒类加载。</p>
 */
public final class ModBlockEntityTypes {

    /** 胸甲台的方块实体类型：负责记住台上摆着的那件胸甲。 */
    public static final BlockEntityType<ChestplateStationBlockEntity> CHESTPLATE_STATION =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, EternalRelic.id("chestplate_station"),
                    BlockEntityType.Builder.create(ChestplateStationBlockEntity::new, ModBlocks.CHESTPLATE_STATION)
                            .build(null));

    private ModBlockEntityTypes() {
    }

    /**
     * 由 {@link RegistryInit#init()} 调用，触发本类静态字段初始化并完成方块实体注册。
     */
    public static void register() {
    }
}
