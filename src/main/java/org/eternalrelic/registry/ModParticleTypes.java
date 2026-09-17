package org.eternalrelic.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;
import org.eternalrelic.relic.AotaPulseParticleEffect;
import org.eternalrelic.relic.EchoRingShardParticleEffect;
import org.eternalrelic.relic.NightwatchParticleEffect;
import org.eternalrelic.relic.SoulBurstParticleEffect;
import org.eternalrelic.relic.SoulWispParticleEffect;

/**
 * 本模组的粒子注册入口。
 *
 * <p>搏动粒子需要携带球心与半径，因此使用能带数据的粒子类型，而不是简单类型。</p>
 */
public final class ModParticleTypes {

    /** 奥塔的搏动粒子 —— 以玩家胸口为中心涨落的绿色光点。 */
    private static final ParticleType<AotaPulseParticleEffect> AOTA_PULSE =
            register("aota_pulse", FabricParticleTypes.complex(new AotaPulseParticleEffect.Factory()));

    /** 守夜之瞳装取时的微光点 —— 装入时从四周收拢，取下时向四周飞散。 */
    private static final ParticleType<NightwatchParticleEffect> NIGHTWATCH_SPARK =
            register("nightwatch_spark", FabricParticleTypes.complex(new NightwatchParticleEffect.Factory()));

    /** 回响之环碎裂时的白色碎屑 —— 向外扩散、原地停一下，再沉入地下。 */
    private static final ParticleType<EchoRingShardParticleEffect> ECHO_RING_SHARD =
            register("echo_ring_shard", FabricParticleTypes.complex(new EchoRingShardParticleEffect.Factory()));

    /** 引魂燃灯收集灵魂时的青色魂火 —— 从尸体飘向灯的主人，一路歪歪扭扭。 */
    private static final ParticleType<SoulWispParticleEffect> SOUL_WISP =
            register("soul_wisp", FabricParticleTypes.complex(new SoulWispParticleEffect.Factory()));

    /** 魂火抵达时胸口炸开的青色碎屑 —— 散成一小圈后淡去。 */
    private static final ParticleType<SoulBurstParticleEffect> SOUL_BURST =
            register("soul_burst", FabricParticleTypes.complex(new SoulBurstParticleEffect.Factory()));

    /**
     * 从地面升起的灵魂 —— 魂火范围内不时从地里冒出、缓缓向上飘的鬼魂。
     *
     * <p>它不需要携带任何参数：位置由生成它的那一次调用给出，往上飘多快、活多久
     * 都由每颗粒子自己随机，因此用简单类型即可。</p>
     */
    private static final DefaultParticleType SOUL_RISE = registerSimple("soul_rise");

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

    /**
     * @return 守夜之瞳装取时的微光点类型
     */
    public static ParticleType<NightwatchParticleEffect> nightwatchSpark() {
        return NIGHTWATCH_SPARK;
    }

    /**
     * @return 回响之环碎裂时的碎屑粒子类型
     */
    public static ParticleType<EchoRingShardParticleEffect> echoRingShard() {
        return ECHO_RING_SHARD;
    }

    /**
     * @return 引魂燃灯收集灵魂时的魂火粒子类型
     */
    public static ParticleType<SoulWispParticleEffect> soulWisp() {
        return SOUL_WISP;
    }

    /**
     * @return 魂火抵达时胸口碎屑的粒子类型
     */
    public static ParticleType<SoulBurstParticleEffect> soulBurst() {
        return SOUL_BURST;
    }

    /**
     * @return 从地面升起的灵魂的粒子类型
     */
    public static DefaultParticleType soulRise() {
        return SOUL_RISE;
    }

    private static <T extends net.minecraft.particle.ParticleEffect> ParticleType<T> register(
            String name, ParticleType<T> type) {
        return Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(EternalRelic.MOD_ID, name), type);
    }

    /**
     * 登记一种不带参数的粒子。
     *
     * <p>返回值特意是 {@link DefaultParticleType} 而不是 {@code ParticleType}：
     * 生成粒子时要把它交给「按位置撒粒子」的方法，而那里收的是粒子效果本身。
     * {@code DefaultParticleType} 既是类型也是效果，因此能直接传进去。</p>
     *
     * @param name 粒子名
     * @return 登记好的粒子类型
     */
    private static DefaultParticleType registerSimple(String name) {
        return Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(EternalRelic.MOD_ID, name), FabricParticleTypes.simple());
    }
}
