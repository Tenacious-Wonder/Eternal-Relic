package org.eternalrelic.capability.carried;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;

import org.eternalrelic.registry.ModRelics;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.RelicArrivalParticleEffect;
import org.eternalrelic.relic.RelicAttachment;
import org.eternalrelic.relic.RelicAttribute;
import org.eternalrelic.relic.RelicDefinition;
import org.eternalrelic.relic.RelicEffect;

/**
 * 「携带生效」能力：遗物带在身上时，把它的属性加成挂到玩家身上。
 *
 * <p><b>「带在身上」有两个来源</b>：一是背包里放着本体（主背包或副手），
 * 二是**正穿着或正拿着的装备上附着了一枚**（见 {@link RelicAttachment}）。
 * 附着那几份怎么与背包份合起来算，**逐件遗物可配**（见 {@link RelicAttachment.Stacking}）：
 * 有的要求「四个部位各算一份」，有的「钉几件都只算一次」。件数怎么算见 {@link #countRelics}。</p>
 *
 * <p><b>件数按遗物算，加成按属性挂</b>：一件遗物今天可以同时给好几种属性
 * （鳞甲内衬既给盔甲韧性又给护甲值），所以这里有两个层次的编号——
 * 「这件遗物带了几份」是遗物级的（{@link #relicKeyOf}），
 * 「这条加成挂在玩家身上哪一条记录上」是（遗物 × 属性）级的（{@link #modifierIdOf}）。
 * 前者决定数值算几份，后者保证同一条属性不会反复叠加。</p>
 *
 * <p>这一层只认遗物表里的数据，不认具体是哪件遗物：把 {@link ModRelics} 里登记的
 * 携带效果逐件应用到玩家身上，因此新增属性类遗物只需登记，不必改动这里。</p>
 *
 * <p><b>加成用哪种运算由遗物表决定</b>：
 * {@link org.eternalrelic.relic.RelicBonusKind#PERCENT} 先把基础值与其它模组提供的加成相加、
 * 最后才乘以本遗物的倍率，其它模组增加同一属性的手段依然完整生效；
 * {@link org.eternalrelic.relic.RelicBonusKind#FLAT} 则直接加上一个固定数值，
 * 供护甲这类以点数为单位的属性使用。两种方式都只按遗物编号占一个固定位置，
 * 不与其它模组争抢位置，因此不会互相覆盖。</p>
 */
public final class CarriedRelicEffect {

    /** 每隔多少刻核对一次背包内容。5 刻约为 0.25 秒，玩家察觉不到延迟。 */
    private static final int CHECK_INTERVAL_TICKS = 5;

    /** 记录每位玩家当前已生效的携带件数：玩家编号 → （遗物键 → 件数）。 */
    private static final Map<UUID, Map<UUID, Integer>> APPLIED_COUNTS = new HashMap<>();

    /** 遗物 → 件数表里的键。由遗物编号派生，算一次就够（见 {@link #relicKeyOf}）。 */
    private static final Map<Item, UUID> RELIC_KEYS = new HashMap<>();

    /** 遗物 → （它影响的属性 → 挂在玩家身上的标识）。算一次就存起来（见 {@link #modifierIdOf}）。 */
    private static final Map<Item, Map<RelicAttribute, UUID>> MODIFIER_IDS = new HashMap<>();

    /** 只带一件遗物时，搏动声的音量。 */
    private static final float PULSE_MIN_VOLUME = 1.0F;

    /** 携带量达到 {@link #PULSE_FULL_COUNT} 件时，搏动声的音量。 */
    private static final float PULSE_MAX_VOLUME = 1.4F;

    /** 携带到这么多件时搏动声最响；再多也不再变响，免得一堆遗物叠出一声炸响。 */
    private static final float PULSE_FULL_COUNT = 5.0F;

    private CarriedRelicEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上核对背包的回调。
     *
     * <p>每个核对周期清点一次玩家携带的遗物，数量变化时才重算属性。这样无论遗物是捡起、
     * 拖动、放进箱子还是丢在地上，都被同一套逻辑覆盖，不依赖某一处特定的背包写入入口。</p>
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                applyFor(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 先清掉旧版编号留下的加成，再照常核对一遍（理由见 purgeLegacyModifiers）
            purgeLegacyModifiers(handler.getPlayer());
            applyFor(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                APPLIED_COUNTS.remove(handler.getPlayer().getUuid()));
    }

    /**
     * 核对一名玩家携带的遗物，只在件数确实变化时才更新属性。
     *
     * <p>每个核对周期都会走一遍这里，但件数没变时立即返回，
     * 因此频繁的背包操作不会反复重算属性。</p>
     *
     * @param player 目标玩家
     */
    private static void applyFor(ServerPlayerEntity player) {
        Map<UUID, Integer> carried = countRelics(player);

        if (carried.equals(appliedCountsOf(player))) {
            return;
        }

        recalculate(player, carried);
        APPLIED_COUNTS.put(player.getUuid(), carried);
    }

    /**
     * 重新挂载玩家身上全部遗物的携带效果。
     *
     * <p>件数没变过的遗物直接跳过；件数变了的，把它登记的每一种属性都重挂一遍
     * （鳞甲内衬这类同时给两种属性的遗物，就是在这里被逐条处理的）。</p>
     *
     * @param player  目标玩家
     * @param carried 当前携带的遗物及件数
     */
    private static void recalculate(ServerPlayerEntity player, Map<UUID, Integer> carried) {
        Map<UUID, Integer> applied = appliedCountsOf(player);

        for (RelicDefinition definition : ModRelics.all()) {
            if (!definition.hasCarriedEffect()) {
                continue;
            }

            UUID relicKey = relicKeyOf(definition);
            int previous = applied.getOrDefault(relicKey, 0);
            int current = carried.getOrDefault(relicKey, 0);

            if (previous == current) {
                continue;
            }

            for (RelicEffect effect : definition.effects()) {
                applyEffect(player, definition, effect, current);
            }

            if (previous <= 0 && current > 0 && definition.hasArrivalEffect()) {
                playArrivalEffect(player, current);
            }
        }
    }

    /**
     * 把一件遗物的某一种属性加成挂上（或摘下）。
     *
     * @param player     目标玩家
     * @param definition 遗物定义
     * @param effect     要处理的那一条属性加成
     * @param count      当前生效的携带件数；0 表示这件遗物此刻不生效
     */
    private static void applyEffect(ServerPlayerEntity player, RelicDefinition definition, RelicEffect effect,
            int count) {
        EntityAttributeInstance attribute = player.getAttributeInstance(effect.attribute().attribute());
        if (attribute == null) {
            return;
        }

        boolean isMaxHealth = effect.attribute().attribute() == EntityAttributes.GENERIC_MAX_HEALTH;

        // ⚠️ 生命上限的折算必须用"**改动之前**"的上限，所以只能先量、再改。
        // 这里曾经写成"改完之后拿加成反推旧上限"，反推出来的是**完全没有加成时的上限**，
        // 而不是改动前那一刻的上限 —— 于是反复穿脱加生命上限的遗物能把血一路抬到满
        // （每来回一次约乘 1.39 倍），是个可以刷的漏洞。改动顺序之前先读这段注释。
        double oldMax = isMaxHealth ? player.getMaxHealth() : 0.0D;

        UUID modifierId = modifierIdOf(definition, effect.attribute());
        attribute.removeModifier(modifierId);

        if (count > 0) {
            attribute.addPersistentModifier(new EntityAttributeModifier(
                    modifierId,
                    "eternal_relic:" + definition.id().getPath(),
                    effect.valueFor(count),
                    effect.kind().operation()));
        }

        if (isMaxHealth) {
            keepHealthRatio(player, oldMax);
        }
    }

    /**
     * 统计玩家身上生效的遗物及件数。
     *
     * <p>两个来源：一是**背在身上的本体**（主背包与副手，玩家把遗物换到副手时应当继续生效）；
     * 二是**附着在正穿着 / 正拿着的装备上**的那一份（见 {@link RelicAttachment#activeRelics}）。</p>
     *
     * <p>附着份**永远只算一份**：同一枚遗物钉在头盔、胸甲、护腿、靴子上，加起来还是一份。
     * 它与背包份之间算不算叠加，由这件遗物自己的登记值决定——不叠加的取两边较多的那个。</p>
     *
     * @param player 目标玩家
     * @return 遗物键 → 生效件数；一件都没有时为空表
     */
    private static Map<UUID, Integer> countRelics(PlayerEntity player) {
        Map<UUID, Integer> carried = new HashMap<>();

        for (ItemStack stack : player.getInventory().main) {
            collectRelic(stack, carried);
        }

        for (ItemStack stack : player.getInventory().offHand) {
            collectRelic(stack, carried);
        }

        for (Map.Entry<Item, Integer> entry : RelicAttachment.activeRelicCounts(player).entrySet()) {
            RelicDefinition definition = ModRelics.definitionOf(entry.getKey());
            if (definition != null && definition.hasCarriedEffect()) {
                collectAttached(definition, entry.getValue(), carried);
            }
        }

        return carried;
    }

    /**
     * 把「附着在装备上」的那几份计入统计。
     *
     * <p>件数怎么算由这件遗物自己的登记值决定：要求「多件装备各算一份」的就按件数加，
     * 否则一律只算一份。要不要叠在背包份之上，也在同一份登记里。</p>
     *
     * @param definition    遗物定义
     * @param attachedCount 正穿着 / 正拿着的装备上一共附了几枚
     * @param carried       统计结果，就地累加
     */
    private static void collectAttached(RelicDefinition definition, int attachedCount, Map<UUID, Integer> carried) {
        UUID relicKey = relicKeyOf(definition);
        int inBackpack = carried.getOrDefault(relicKey, 0);

        RelicAttachment.Spec spec = RelicAttachment.specOf(definition.item());
        RelicAttachment.Stacking stacking = spec == null ? RelicAttachment.Stacking.NONE : spec.stacking();

        int fromAttachment = stacking.acrossItems() ? attachedCount : 1;
        int total = stacking.withCarried() ? inBackpack + fromAttachment : Math.max(inBackpack, fromAttachment);

        carried.put(relicKey, total);
    }

    /**
     * 把一格物品计入携带统计。
     *
     * @param stack   待检查的物品堆
     * @param carried 统计结果，就地累加
     */
    private static void collectRelic(ItemStack stack, Map<UUID, Integer> carried) {
        if (stack.isEmpty()) {
            return;
        }

        RelicDefinition definition = ModRelics.definitionOf(stack.getItem());
        if (definition == null || !definition.hasCarriedEffect()) {
            return;
        }

        // 只认附着份的遗物（坚铁甲片）：背在背包里不算数，必须真的缝在装备上。
        // 附着那一份由 countRelics 的另一半负责，这里只是把「背包」这一路让开。
        if (definition.isAttachmentOnly()) {
            return;
        }

        carried.merge(relicKeyOf(definition), stack.getCount(), Integer::sum);
    }

    /**
     * @param player 目标玩家
     * @return 该玩家当前已生效的携带件数；没有记录时为空表
     */
    private static Map<UUID, Integer> appliedCountsOf(ServerPlayerEntity player) {
        return APPLIED_COUNTS.getOrDefault(player.getUuid(), Map.of());
    }

    /**
     * 生命上限变化后，按变化前的血量比例重新折算当前血量。
     *
     * <p><b>调用前必须先量好旧上限</b>（见上面那段）：旧上限读一次就够，而且只有"改之前"读到
     * 的才是真的旧上限 —— 加成一旦挂上/摘下，就已经没有别的办法把它还原出来了。
     * 折算结果不允许超过新的生命上限，因此撤销携带效果时当前血量会平滑回落到上限之内，
     * 不会留下比上限还高的数值。</p>
     *
     * @param player 目标玩家
     * @param oldMax 变更**之前**的生命上限
     */
    private static void keepHealthRatio(ServerPlayerEntity player, double oldMax) {
        double newMax = player.getMaxHealth();

        if (oldMax <= 0.0D || newMax <= 0.0D) {
            return;
        }

        float scaled = (float) (player.getHealth() * (newMax / oldMax));
        player.setHealth(Math.max(0.0F, Math.min(scaled, (float) newMax)));
    }

    /**
     * 摘掉旧版编号留下的属性加成。
     *
     * <p>早期一件遗物只挂一条加成，那条加成在玩家身上的编号直接由遗物编号派生，
     * 也就是今天的 {@link #relicKeyOf}。改成「遗物 + 属性」各一个编号（见 {@link #modifierIdOf}）
     * 之后，旧存档里那份按旧编号挂上的加成不会被新代码认出来，会一直留着、
     * 与新的那一份叠在一起（属性凭空翻倍）。因此玩家一上线就先按旧编号摘一遍；
     * 没挂过旧编号的玩家身上本来就找不到这条记录，摘了也不会有任何变化。</p>
     *
     * @param player 刚进入游戏的玩家
     */
    private static void purgeLegacyModifiers(ServerPlayerEntity player) {
        for (RelicDefinition definition : ModRelics.all()) {
            if (!definition.hasCarriedEffect()) {
                continue;
            }

            UUID legacyId = relicKeyOf(definition);

            for (RelicAttribute attribute : RelicAttribute.values()) {
                EntityAttributeInstance instance = player.getAttributeInstance(attribute.attribute());
                if (instance != null) {
                    instance.removeModifier(legacyId);
                }
            }
        }
    }

    /**
     * 由一个遗物派生它在件数表里的键。
     *
     * <p>键必须固定：同一个遗物每次都要落到同一条记录上，否则反复统计会不断堆积。
     * 这里用遗物编号稳定派生，而不是在代码里另写一串常量，避免两处信息各写一遍。</p>
     *
     * <p><b>算一次就存起来</b>：这个方法在「每 5 刻 × 每名玩家 × 每件携带遗物」的核对里被调用，
     * 同一件物品在好几个格子里还会重复问同一个值；而它每次都要把遗物编号拼成字符串、
     * 取出字节、再算一遍 MD5。缓存之后返回值一分不变（旧存档里玩家身上挂着的加成认的就是这个值），
     * 只是不再重复算。</p>
     *
     * @param definition 遗物定义
     * @return 该遗物在件数表里的键
     */
    private static UUID relicKeyOf(RelicDefinition definition) {
        return RELIC_KEYS.computeIfAbsent(definition.item(),
                item -> UUID.nameUUIDFromBytes(definition.id().toString().getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 由一件遗物与它的一种属性，派生这条加成挂在玩家身上的固定标识。
     *
     * <p>一件遗物给几种属性，就有几个标识：它们各挂各的，互不覆盖，
     * 因此「把鳞甲内衬从胸甲上拆下来」时两条属性会一起撤掉，而不会只撤掉其中一条。</p>
     *
     * @param definition 遗物定义
     * @param attribute  这条加成影响的属性
     * @return 该（遗物 × 属性）的属性加成标识
     */
    private static UUID modifierIdOf(RelicDefinition definition, RelicAttribute attribute) {
        return MODIFIER_IDS
                .computeIfAbsent(definition.item(), item -> new EnumMap<>(RelicAttribute.class))
                .computeIfAbsent(attribute, key -> UUID.nameUUIDFromBytes(
                        (definition.id() + "/" + key.name()).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 在玩家刚开始携带遗物时播放一次「入手」表现：搏动声与涌出后收敛的粒子。
     *
     * <p>只有遗物表里登记了「入手表现」的遗物才会走到这里；其余遗物安静地生效，
     * 既不响也不冒粒子。</p>
     *
     * <p>只在这一刻发生一次：放进背包时响一次，拿出后再放回来才会再次响起，
     * 携带期间既不重复播放声音，也不持续冒粒子。声音与粒子都从玩家身上发出，
     * 因此附近的玩家既能听到、也能看到。</p>
     *
     * @param player 刚开始携带遗物的玩家
     * @param count  当前携带件数。只影响声音大小：带得越多略响一些，粒子数不随它变化
     */
    private static void playArrivalEffect(ServerPlayerEntity player, int count) {
        float volume = PULSE_MIN_VOLUME
                + Math.min(1.0F, count / PULSE_FULL_COUNT) * (PULSE_MAX_VOLUME - PULSE_MIN_VOLUME);
        ServerWorld world = player.getServerWorld();

        // 由服务端广播：玩家自己与附近玩家都能听到（实测这条路径可靠）
        world.playSound(
                null,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                ModSounds.RELIC_ARRIVAL,
                SoundCategory.PLAYERS,
                volume,
                1.0F);

        // 胸口位置作为球心：眼球高度略往下一点
        double chestX = player.getX();
        double chestY = player.getEyeY() - 0.35D;
        double chestZ = player.getZ();

        int total = 36 + player.getRandom().nextInt(12);
        int farCount = total / 3;
        int nearCount = total - farCount;

        // 第一批：扩散到三格，走完两轮涨落
        world.spawnParticles(
                new RelicArrivalParticleEffect(chestX, chestY, chestZ, 3.0F, 1.0F),
                chestX, chestY, chestZ,
                farCount,
                0.35D, 0.35D, 0.35D,
                0.0D);

        // 第二批：只扩散到一格半，同样走两轮
        world.spawnParticles(
                new RelicArrivalParticleEffect(chestX, chestY, chestZ, 1.5F, 1.0F),
                chestX, chestY, chestZ,
                nearCount,
                0.30D, 0.30D, 0.30D,
                0.0D);
    }
}
