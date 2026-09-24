package org.eternalrelic.registry;

import org.eternalrelic.bodypart.MeleeBodyPartDetector;
import org.eternalrelic.capability.attached.ShoulderGuardEffect;
import org.eternalrelic.capability.carried.*;
import org.eternalrelic.capability.worn.NightwatchEyeVision;
import org.eternalrelic.capability.worn.WornRelicEffect;
import org.eternalrelic.network.SoulLanternNetwork;

public class RegistryInit {
    public static void init() {
        ModBlocks.register();
        ModBlockEntityTypes.register();
        ModItems.register();
        ModSounds.register();
        ModParticleTypes.register();
        ModRecipes.register();
        ModScreens.register();
        MeleeBodyPartDetector.register();
        CarriedRelicEffect.register();
        DamageWardEffect.register();
        EnchantedRabbitFootEffect.register();
        DayNightEmblemEffect.register();
        ShoulderGuardEffect.register();
        SoulLanternEffect.register();
        CourageEmblemEffect.register();
        WornRelicEffect.register();
        NightwatchEyeVision.register();
        SoulLanternNetwork.registerServer();
    }
}
