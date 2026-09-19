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

    /**
     * 遗物入手声 —— 一件遗物开始在背包里生效时响起的心跳声。
     *
     * <p>它不属于某一件遗物：任何在遗物表里登记了「入手表现」的遗物都会用它，
     * 因此素材与命名都保持中性，不偏向任何一件。</p>
     */
    public static final SoundEvent RELIC_ARRIVAL = register("relic_arrival");

    /** 守夜之瞳归位声 —— 义眼装入眼眶时的一记机械声。 */
    public static final SoundEvent NIGHTWATCH_INSTALL = register("nightwatch_install");

    /** 守夜之瞳脱落声 —— 义眼从眼眶里松开、掉到脚下时的声响。 */
    public static final SoundEvent NIGHTWATCH_RELEASE = register("nightwatch_release");

    /**
     * 回响之环碎裂声 —— 它替玩家挡下攻击、当场碎裂时的声响。
     *
     * <p>这个音效事件在 {@code sounds.json} 里挂了两个音频文件，游戏每次随机挑一个播放，
     * 因此不必在代码里自己做随机。</p>
     */
    public static final SoundEvent ECHO_RING_WARD = register("echo_ring_ward");

    /**
     * 飞行中灵魂的呜咽声 —— 由魂火自身发出并跟着它移动，因此听起来是"那个东西在响"。
     *
     * <p>播放时走的是会移动的音效实例，按距离衰减：离得远听不到。</p>
     */
    public static final SoundEvent SOUL_WISP = register("soul_wisp");

    /**
     * 灵魂消散声 —— 魂火碰到玩家、被灯收进去的那一刻响起。
     *
     * <p>素材经降调处理，听感比原件低沉，像是有什么东西沉了下去。</p>
     */
    public static final SoundEvent SOUL_BURST = register("soul_burst");

    /**
     * 引魂燃灯的释放声 —— 按 G 倾泻魂火时响起。
     */
    public static final SoundEvent SOUL_CAST = register("soul_cast");

    /**
     * 魂火低语 —— 地面上那片魂火持续发出的声音。
     *
     * <p>循环播放；魂火熄灭时由播放方把音量收下去，不会戛然而止。</p>
     */
    public static final SoundEvent SOUL_FLAME = register("soul_flame");

    /**
     * 魂火深处的呻吟 —— 在魂火范围内随机位置、随机间隔冒出的短促人声。
     *
     * <p>这个音效事件在 {@code sounds.json} 里挂了三个长短、音高各不相同的音频文件，
     * 游戏每次随机挑一个播放；播放方另外再随机一次音高，因此同一段素材也不会听腻。</p>
     *
     * <p>与 {@link #SOUL_FLAME} 的分工：那一段是"这片火在烧"的底噪，这一段是
     * "火里有东西"的动静。有了它，魂火才像有东西在里面，而不是一台持续运转的机器。</p>
     */
    public static final SoundEvent SOUL_MOAN = register("soul_moan");

    /**
     * 装卸台的锤子敲击声 —— 装或拆成功时，台子上的锤子敲一下。
     *
     * <p>这个音效事件在 {@code sounds.json} 里挂了三段素材（一记单敲、一段有节奏的连敲、
     * 一段金属敲击），游戏每次随机挑一个播放，因此不必在代码里自己做随机。
     * 三段都由制作者提供，**最长的一段原本有 24 秒**，已经剪成 1.4 秒、并加了淡出——
     * 单次敲击用不了那么长，剪短之后也不会在结尾"啪"地断掉。</p>
     */
    public static final SoundEvent STATION_HAMMER = register("station_hammer");

    private ModSounds() {
    }

    /**
     * 由 {@link EternalRelic#onInitialize()} 调用，触发本类静态字段初始化并完成音效注册。
     */
    public static void register() {
    }

    private static SoundEvent register(String name) {
        Identifier id = EternalRelic.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
