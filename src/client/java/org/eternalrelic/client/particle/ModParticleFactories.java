package org.eternalrelic.client.particle;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

import org.eternalrelic.registry.ModParticleTypes;

/**
 * 粒子在客户端的接线：把粒子类型与它的外观实现对应起来。
 *
 * <p>没有这一步，粒子在服务端能生成、到了客户端却没有样子可画。</p>
 */
public final class ModParticleFactories {

    private ModParticleFactories() {
    }

    /**
     * 由客户端入口调用，为每种粒子挂上它的外观实现。
     */
    public static void register() {
        // 先唤醒注册类，确保粒子类型已完成登记
        ModParticleTypes.register();

        ParticleFactoryRegistry.getInstance()
                .register(ModParticleTypes.aotaPulse(), AotaPulseParticle.Factory::new);
    }
}
