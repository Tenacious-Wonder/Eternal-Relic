package org.eternalrelic.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import org.eternalrelic.EternalRelic;

/**
 * 本模组的音效注册入口。
 *
 * <p>每段音效以「静态常量 + register 方法」的形式声明：静态字段初始化时即完成注册，
 * 因此 {@link #register()} 只需被调用一次来触发类加载。</p>
 */
public final class ModSounds {

    /** 奥塔的搏动声 —— 奥塔的枝叶开始生效时响起的心跳声。 */
    public static final SoundEvent AOTA_PULSE = register("aota_pulse");

    /** 守夜之瞳归位声 —— 义眼装入眼眶时的一记机械声。 */
    public static final SoundEvent NIGHTWATCH_INSTALL = register("nightwatch_install");

    /** 守夜之瞳脱落声 —— 义眼从眼眶里松开、掉到脚下时的声响。 */
    public static final SoundEvent NIGHTWATCH_RELEASE = register("nightwatch_release");

    private ModSounds() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成音效注册。
     */
    public static void register() {
    }

    private static SoundEvent register(String name) {
        Identifier id = new Identifier(EternalRelic.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
