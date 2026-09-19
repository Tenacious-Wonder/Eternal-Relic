package org.eternalrelic;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eternalrelic.capability.carried.CarriedRelicEffect;
import org.eternalrelic.capability.carried.CourageEmblemEffect;
import org.eternalrelic.capability.carried.DamageWardEffect;
import org.eternalrelic.capability.carried.EnchantedRabbitFootEffect;
import org.eternalrelic.capability.carried.SoulLanternEffect;
import org.eternalrelic.capability.worn.NightwatchEyeVision;
import org.eternalrelic.capability.worn.WornRelicEffect;
import org.eternalrelic.network.SoulLanternNetwork;
import org.eternalrelic.registry.ModBlocks;
import org.eternalrelic.registry.ModItems;
import org.eternalrelic.registry.ModParticleTypes;
import org.eternalrelic.registry.ModRecipes;
import org.eternalrelic.registry.ModScreens;
import org.eternalrelic.registry.ModSounds;

/**
 * <h1>TW 的永恒遗物 —— 模组主入口</h1>
 *
 * <p>遗物的生效方式有两种：一种是「带在背包里就生效」（如奥塔的枝叶），另一种是「右键装入后
 * 才生效」（如守夜之瞳）。两者的身份都集中登记在
 * {@link org.eternalrelic.registry.ModRelics 遗物表}里，具体怎么生效则由各自的能力类处理，
 * 因此新增一件遗物通常只需要三处配合（注册物品、填一行遗物表、补语言文件），不必改动核心逻辑。</p>
 *
 * <h2>组成部分</h2>
 * <ul>
 *   <li>{@link ModItems} —— 物品本身：贴图、堆叠上限、创造模式分类。</li>
 *   <li>{@link org.eternalrelic.registry.ModRelics} —— 遗物表：哪件遗物、影响什么属性、数值多少。</li>
 *   <li>{@link ModSounds}、{@link ModParticleTypes} —— 遗物生效时的听觉与视觉表现。</li>
 *   <li>{@link CarriedRelicEffect} —— 「携带生效」能力：核对背包、挂属性、播放表现。</li>
 *   <li>{@link DamageWardEffect} —— 「守护」能力：每次挨打时由遗物挡下或削弱那一击，随后碎裂冷却。</li>
 *   <li>{@link SoulLanternEffect} —— 「攒放」能力：击杀攒魂火，按键一次倾泻出去，原地留下灼烧。</li>
 *   <li>{@link CourageEmblemEffect} —— 「定时馈赠」能力：每隔一分钟替玩家攒下两颗金心。</li>
 *   <li>{@link EnchantedRabbitFootEffect} —— 「受击加速」能力：挨打时换来一段速度，不挡伤害。</li>
 *   <li>{@link WornRelicEffect} —— 「装入生效」能力：把守夜之瞳装进眼中，并让代价跟随玩家。</li>
 *   <li>{@link NightwatchEyeVision} —— 守夜之瞳的视觉：在暗处看清周围、照见活物。</li>
 *   <li>{@link SoulLanternNetwork} —— 把「按下了释放键」从客户端送到服务端的那条通路。</li>
 * </ul>
 *
 * <h2>加载顺序</h2>
 * <p>游戏启动时按顺序唤醒上述注册表：物品先登记，遗物表才有物品可引用；音效与粒子随后登记，
 * 最后挂上各项能力的回调（核对背包、搬运装入状态、逐刻核对视觉、推进魂火），
 * 并把来自客户端的释放请求接住。</p>
 */
public class EternalRelic implements ModInitializer {

    /** 本模组的命名空间，与 {@code fabric.mod.json} 中的 id 保持一致。 */
    public static final String MOD_ID = "eternal_relic";

    /**
     * 模组日志出口，留给排查问题用。
     *
     * <p>目前没有调用点：功能本身不需要长期打印日志，调试时临时用它打几行、查完即删，
     * 比在代码里常驻一堆输出干净。</p>
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 拼一个本模组命名空间下的编号。
     *
     * <p><b>为什么要有这个入口</b>：物品、方块、音效、粒子、配方、界面、网络包……每一处注册都要
     * 「取编号」，原先各自抄一遍 {@code new Identifier(MOD_ID, "…")}。抄写的坏处是命名空间一旦写错
     * 就成了另一个命名空间的东西，游戏既不报错、也找不到，排查起来很费劲。集中到这里之后，
     * 命名空间只有一处。</p>
     *
     * @param path 编号在本模组里的那一段，例如 {@code "soul_lantern"}
     * @return 完整编号
     */
    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    /**
     * 由游戏在模组加载阶段调用一次，完成本模组各项内容的登记。
     */
    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();
        ModSounds.register();
        ModParticleTypes.register();
        ModRecipes.register();
        ModScreens.register();
        CarriedRelicEffect.register();
        DamageWardEffect.register();
        EnchantedRabbitFootEffect.register();
        SoulLanternEffect.register();
        CourageEmblemEffect.register();
        WornRelicEffect.register();
        NightwatchEyeVision.register();
        SoulLanternNetwork.registerServer();
    }
}
