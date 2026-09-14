package org.eternalrelic.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.relic.AotaPulseParticleEffect;

/**
 * 本模组的粒子注册入口。
 *
 * <p>搏动粒子需要携带球心与半径，因此使用能带数据的粒子类型，而不是简单类型。</p>
 */
public final class ModParticleTypes {

    /** 奥塔的搏动粒子 —— 以玩家胸口为中心涨落的绿色光点。 */
    private static final ParticleType<AotaPulseParticleEffect> AOTA_PULSE =
            register("aota_pulse", FabricParticleTypes.complex(new AotaPulseParticleEffect.Factory()));

    private ModParticleTypes() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成粒子注册。
     */
    public static void register() {
    }

    /**
     * @return 奥塔的搏动粒子类型
     */
    public static ParticleType<AotaPulseParticleEffect> aotaPulse() {
        return AOTA_PULSE;
    }

    private static <T extends net.minecraft.particle.ParticleEffect> ParticleType<T> register(
            String name, ParticleType<T> type) {
        return Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(EternalRelic.MOD_ID, name), type);
    }
}
