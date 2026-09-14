package org.eternalrelic.capability.worn;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import org.eternalrelic.registry.ModItems;
import org.eternalrelic.registry.ModSounds;
import org.eternalrelic.relic.NightwatchEye;

/**
 * 「装入生效」能力：把守夜之瞳装进玩家的眼窝，并让代价一直挂在玩家身上。
 *
 * <p>目前这一层承载的是守夜之瞳这一族遗物：装入一只眼，就从生命上限里扣掉一颗心，
 * 同时响起一记机械声，并叠上玩家受击声——让"拿血换视野"这件事在听觉上说得通。</p>
 *
 * <p><b>扣减用「加法」而不是「按比例」</b>：加法在属性结算顺序里排在最前，先于一切按百分比
 * 放大的加成（例如奥塔的枝叶的 +12%）。因此玩家失去的是实打实的底数，枝叶也放大不了这份损失，
 * 正是需求里说的「优先计算」。</p>
 *
 * <p><b>「戴着哪只眼」记在哪里</b>：不另设一份名单，而是直接以「生命上限上有没有这条扣减」
 * 为准。这样状态与代价天然一致，不会出现「记录说戴着、血却已经还回去」的错位。
 * 退出再进时，玩家的属性会被游戏自己存进存档，状态因此自动延续；只有死亡重生与穿越维度时
 * 玩家会被换成一个新的个体、属性不跟着走，才需要 {@link ServerPlayerEvents#COPY_FROM}
 * 把这条扣减重新挂上去。</p>
 *
 * <h2>怎么取下来</h2>
 * <p>装入是拿生命上限换视野，取下来则要付出另一份代价：吃下金苹果、附魔金苹果，或获得恢复二
 * 及以上的效果。满足条件时 {@link #releaseAll 取下全部已装入的眼睛}——生命上限还给玩家，
 * 眼睛则以<b>能量耗尽</b>的形态落到脚下，需要与附魔之瓶合成才能重新装上。</p>
 *
 * <p>金苹果与附魔金苹果由 {@code PlayerEntityMixin} 在玩家进食时通知；恢复效果则在
 * {@link #register() 这里}逐刻核对。两条来源合起来正是需求里写的三种恢复方式。</p>
 */
public final class WornRelicEffect {

    /** 装入一只眼要付出的代价：一颗心（游戏里 2 点生命）。 */
    private static final double HEALTH_COST = 2.0D;

    /** 每隔多少刻核对一次玩家身上的恢复效果。10 刻约为 0.5 秒。 */
    private static final int CHECK_INTERVAL_TICKS = 10;

    /** 能把眼睛取下来的恢复等级下限：恢复二，也就是等级编号 1（编号从 0 起算）。 */
    private static final int RESTORING_REGENERATION_AMPLIFIER = 1;

    /** 装入时机械声的音量。 */
    private static final float INSTALL_VOLUME = 1.0F;

    /** 装入时叠加的受击声音量。比机械声轻一些，垫在下面，避免喧宾夺主。 */
    private static final float HURT_VOLUME = 0.8F;

    /** 取下时松开声的音量。 */
    private static final float RELEASE_VOLUME = 1.0F;

    /** 取下时叠加的物品落地声音量。 */
    private static final float PICKUP_VOLUME = 0.7F;

    /** 物品落地声的音调。压低一档，听着像东西落在脚边，而不是被拾起。 */
    private static final float PICKUP_PITCH = 0.7F;

    private WornRelicEffect() {
    }

    /**
     * 由 {@link org.eternalrelic.EternalRelic#onInitialize()} 调用，挂上重生搬运与恢复核对的回调。
     *
     * <p>游戏把玩家换成一个新个体时，只搬运背包、血量这类数据，属性不跟着走。
     * 这里把旧玩家身上的眼睛重新装到新玩家身上，避免「眼睛还装着、血量却白白还回来」。</p>
     */
    public static void register() {
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            for (NightwatchEye eye : NightwatchEye.values()) {
                if (isWorn(oldPlayer, eye)) {
                    apply(newPlayer, eye);
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (hasRestoringRegeneration(player)) {
                    releaseAll(player);
                }
            }
        });
    }

    /**
     * 把一只守夜之瞳装进玩家的眼窝。
     *
     * @param player 装入的玩家
     * @param eye    装入的是哪只眼
     * @return 是否装入成功；已经戴着同一只眼、或不在服务端时返回 {@code false}
     */
    public static boolean wear(PlayerEntity player, NightwatchEye eye) {
        if (player.getWorld().isClient || isWorn(player, eye)) {
            return false;
        }

        apply(player, eye);
        playInstallSound(player);
        return true;
    }

    /**
     * @param player 待检查的玩家
     * @param eye    待检查的眼
     * @return 该玩家是否已经戴着这只眼
     */
    public static boolean isWorn(PlayerEntity player, NightwatchEye eye) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        return maxHealth != null && maxHealth.getModifier(modifierIdOf(eye)) != null;
    }

    /**
     * 取下玩家身上已经装入的全部眼睛：生命上限还给玩家，眼睛以能量耗尽的形态落到脚下。
     *
     * <p>只处理真正装着的那几只。玩家背包里还没装入的眼睛不受影响——需求里明确过，
     * 没戴上的眼睛不该被牵连。</p>
     *
     * @param player 目标玩家
     * @return 是否至少取下了一只
     */
    public static boolean releaseAll(PlayerEntity player) {
        boolean released = false;

        for (NightwatchEye eye : NightwatchEye.values()) {
            if (release(player, eye)) {
                released = true;
            }
        }

        if (released) {
            playReleaseSound(player);
        }

        return released;
    }

    /**
     * 播放义眼脱落时的声响：一记松开声，叠上一层物品落地的闷响。
     *
     * <p>叠加的那层用的是原版拾取音，但把音调压低了一档——原版音调听着像"捡起来"，
     * 压过之后更像"掉下去"，正好对应眼睛落到脚边这件事。</p>
     *
     * @param player 刚取下义眼的玩家
     */
    private static void playReleaseSound(PlayerEntity player) {
        World world = player.getWorld();
        double x = player.getX();
        double y = player.getEyeY();
        double z = player.getZ();

        world.playSound(null, x, y, z,
                ModSounds.NIGHTWATCH_RELEASE, SoundCategory.PLAYERS, RELEASE_VOLUME, 1.0F);
        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, PICKUP_VOLUME, PICKUP_PITCH);
    }

    /**
     * 播放装入义眼时的声响：一记机械归位声，叠上一层玩家受击声。
     *
     * <p>受击声不是装饰。装入的代价是实打实的一颗心，这声闷响让玩家在听觉上立刻明白
     * 「刚才那一下是从自己身上扣的」。两层声音都从玩家身上发出并广播，
     * 因此附近的玩家同样听得到。</p>
     *
     * @param player 刚装入义眼的玩家
     */
    private static void playInstallSound(PlayerEntity player) {
        World world = player.getWorld();
        double x = player.getX();
        double y = player.getEyeY();
        double z = player.getZ();

        world.playSound(null, x, y, z,
                ModSounds.NIGHTWATCH_INSTALL, SoundCategory.PLAYERS, INSTALL_VOLUME, 1.0F);
        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, HURT_VOLUME, 1.0F);
    }

    /**
     * 取下其中一只眼。
     *
     * @param player 目标玩家
     * @param eye    要取下的眼
     * @return 是否确实取下了；原本就没装着时返回 {@code false}
     */
    private static boolean release(PlayerEntity player, NightwatchEye eye) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth == null || maxHealth.getModifier(modifierIdOf(eye)) == null) {
            return false;
        }

        maxHealth.removeModifier(modifierIdOf(eye));

        // 取下的眼睛不是直接消失，而是耗尽能量后落到脚下，等玩家用附魔之瓶重新充能
        player.dropItem(new ItemStack(drainedItemOf(eye)), false);
        return true;
    }

    /**
     * @param player 待检查的玩家
     * @return 该玩家是否带着能把眼睛取下来的恢复效果（恢复二及以上）
     */
    private static boolean hasRestoringRegeneration(PlayerEntity player) {
        StatusEffectInstance regeneration = player.getStatusEffect(StatusEffects.REGENERATION);
        return regeneration != null && regeneration.getAmplifier() >= RESTORING_REGENERATION_AMPLIFIER;
    }

    /**
     * @param eye 守夜之瞳的某一只眼
     * @return 该只眼能量耗尽后的物品形态
     */
    private static Item drainedItemOf(NightwatchEye eye) {
        return switch (eye) {
            case LEFT -> ModItems.NIGHTWATCH_EYE_LEFT_DRAINED;
            case RIGHT -> ModItems.NIGHTWATCH_EYE_RIGHT_DRAINED;
        };
    }

    /**
     * 把一只眼的代价挂到玩家的生命上限上。
     *
     * @param player 目标玩家
     * @param eye    装入的眼
     */
    private static void apply(PlayerEntity player, NightwatchEye eye) {
        EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHealth == null || maxHealth.getModifier(modifierIdOf(eye)) != null) {
            return;
        }

        maxHealth.addPersistentModifier(new EntityAttributeModifier(
                modifierIdOf(eye),
                "eternal_relic:nightwatch_eye_" + eye.id(),
                -HEALTH_COST,
                EntityAttributeModifier.Operation.ADDITION));

        clampHealth(player);
    }

    /**
     * 生命上限降低后，把当前血量收回到新的上限之内。
     *
     * <p>例如满血十颗心的玩家装入一只眼后，上限降到九颗心，当前血量随之收到九颗心，
     * 不会留下比上限还高的数值。</p>
     *
     * @param player 目标玩家
     */
    private static void clampHealth(PlayerEntity player) {
        float maxHealth = player.getMaxHealth();
        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    /**
     * 由一只眼派生它挂在生命上限上的固定标识。
     *
     * <p>标识必须固定：同一只眼每次都要挂到同一条记录上，否则反复装入会不断堆积扣减。
     * 这里由眼的短名稳定派生，而不是另写一串常量，避免两处信息各写一遍。</p>
     *
     * @param eye 守夜之瞳的某一只眼
     * @return 该只眼对应的属性扣减标识
     */
    private static UUID modifierIdOf(NightwatchEye eye) {
        return UUID.nameUUIDFromBytes(
                ("eternal_relic:nightwatch_eye_" + eye.id()).getBytes(StandardCharsets.UTF_8));
    }
}
