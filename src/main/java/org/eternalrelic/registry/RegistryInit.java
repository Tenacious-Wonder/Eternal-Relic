package org.eternalrelic.registry;

import org.eternalrelic.bodypart.MeleeBodyPartDetector;
import org.eternalrelic.capability.attached.BloomEmblemEffect;
import org.eternalrelic.capability.attached.ChestGuardEffect;
import org.eternalrelic.capability.attached.WindCloakEffect;
import org.eternalrelic.capability.carried.*;
import org.eternalrelic.capability.consumed.WayfarerBlessing;
import org.eternalrelic.capability.worn.NightwatchEyeVision;
import org.eternalrelic.capability.worn.WornRelicEffect;
import org.eternalrelic.command.RelicCommands;
import org.eternalrelic.energy.EveEnergy;
import org.eternalrelic.network.SoulLanternNetwork;
import org.eternalrelic.network.WindCloakNetwork;
import org.eternalrelic.network.ShieldRushNetwork;
import org.eternalrelic.skill.ShieldRushEffect;
import org.eternalrelic.skill.ShieldRushSkill;
import org.eternalrelic.worldgen.LittleHomeSites;
import org.eternalrelic.worldgen.SurfaceRockSites;

public class RegistryInit {
    public static void init() {
        ModBlocks.register();
        ModBlockEntityTypes.register();
        ModItems.register();
        ModSounds.register();
        ModParticleTypes.register();
        WayfarerBlessing.register();
        ModRecipes.register();
        ModScreens.register();
        ModFeatures.register();
        LittleHomeSites.register();
        SurfaceRockSites.register();
        RelicCommands.register();
        MeleeBodyPartDetector.register();
        CarriedRelicEffect.register();
        DamageWardEffect.register();
        EmberPendantEffect.register();
        EnchantedRabbitFootEffect.register();
        TravelerPendantEffect.register();
        LightweightShieldBadgeEffect.register();
        ShepherdBellEffect.register();
        DayNightEmblemEffect.register();
        NightWatchCloakEffect.register();
        ConditionalAttributeEffect.register();
        ThunderEmblemEffect.register();
        BloodFeastEffect.register();
        NaturalRuneEffect.register();
        ScavengerMagnetEffect.register();
        ChestGuardEffect.register();
        BloomEmblemEffect.register();
        EmberheartEmblemEffect.register();
        WindCloakEffect.register();
        HorseWhistleEffect.register();
        SoulLanternEffect.register();
        CourageEmblemEffect.register();
        WornRelicEffect.register();
        NightwatchEyeVision.register();
        ShieldRushEffect.register();
        ShieldRushSkill.register();
        EveEnergy.register();
        SoulLanternNetwork.registerServer();
        WindCloakNetwork.registerServer();
        ShieldRushNetwork.registerServer();
    }
}
