package org.eternalrelic.registry;

import org.eternalrelic.bodypart.MeleeBodyPartDetector;
import org.eternalrelic.capability.attached.BloomEmblemEffect;
import org.eternalrelic.capability.attached.ChestGuardEffect;
import org.eternalrelic.capability.attached.WindCloakEffect;
import org.eternalrelic.capability.carried.*;
import org.eternalrelic.capability.worn.NightwatchEyeVision;
import org.eternalrelic.capability.worn.WornRelicEffect;
import org.eternalrelic.command.RelicCommands;
import org.eternalrelic.network.SoulLanternNetwork;
import org.eternalrelic.network.WindCloakNetwork;
import org.eternalrelic.worldgen.LittleHomeSites;

public class RegistryInit {
    public static void init() {
        ModBlocks.register();
        ModBlockEntityTypes.register();
        ModItems.register();
        ModSounds.register();
        ModParticleTypes.register();
        ModRecipes.register();
        ModScreens.register();
        ModFeatures.register();
        LittleHomeSites.register();
        RelicCommands.register();
        MeleeBodyPartDetector.register();
        CarriedRelicEffect.register();
        DamageWardEffect.register();
        EmberPendantEffect.register();
        EnchantedRabbitFootEffect.register();
        TravelerPendantEffect.register();
        ShepherdBellEffect.register();
        DayNightEmblemEffect.register();
        NightWatchCloakEffect.register();
        NaturalRuneEffect.register();
        ScavengerMagnetEffect.register();
        ChestGuardEffect.register();
        BloomEmblemEffect.register();
        WindCloakEffect.register();
        SoulLanternEffect.register();
        CourageEmblemEffect.register();
        WornRelicEffect.register();
        NightwatchEyeVision.register();
        SoulLanternNetwork.registerServer();
        WindCloakNetwork.registerServer();
    }
}
