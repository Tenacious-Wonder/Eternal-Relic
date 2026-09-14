package org.eternalrelic;

import net.fabricmc.api.ModInitializer;

import org.eternalrelic.capability.carried.CarriedRelicEffect;
import org.eternalrelic.registry.ModItems;
import org.eternalrelic.registry.ModParticleTypes;
import org.eternalrelic.registry.ModSounds;

/**
 * <h1>TW 的永恒遗物 —— 模组主入口</h1>
 *
 * <p>遗物是一类「带在背包里就生效」的特殊物品。本模组把遗物的身份与效果集中登记在
 * {@link org.eternalrelic.registry.ModRelics 遗物表}里，而「怎么扫背包、怎么算、怎么挂到玩家身上」
 * 由通用能力 {@link CarriedRelicEffect} 统一处理。因此新增一件遗物通常只需要三处配合
 * （注册物品、填一行遗物表、补语言文件），不必改动核心逻辑。</p>
 *
 * <h2>组成部分</h2>
 * <ul>
 *   <li>{@link ModItems} —— 物品本身：贴图、堆叠上限、创造模式分类。</li>
 *   <li>{@link org.eternalrelic.registry.ModRelics} —— 遗物表：哪件遗物、影响什么属性、数值多少。</li>
 *   <li>{@link ModSounds}、{@link ModParticleTypes} —— 遗物生效时的听觉与视觉表现。</li>
 *   <li>{@link CarriedRelicEffect} —— 「携带生效」能力：核对背包、挂属性、播放表现。</li>
 * </ul>
 *
 * <h2>加载顺序</h2>
 * <p>游戏启动时按顺序唤醒上述注册表：物品先登记，遗物表才有物品可引用；音效与粒子随后登记，
 * 最后挂上核对背包的回调。</p>
 */
public class EternalRelic implements ModInitializer {

    /** 本模组的命名空间，与 {@code fabric.mod.json} 中的 id 保持一致。 */
    public static final String MOD_ID = "eternal_relic";

    /**
     * 由游戏在模组加载阶段调用一次，完成本模组各项内容的登记。
     */
    @Override
    public void onInitialize() {
        ModItems.register();
        ModSounds.register();
        ModParticleTypes.register();
        CarriedRelicEffect.register();
    }
}
