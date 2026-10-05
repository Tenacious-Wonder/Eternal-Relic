package org.eternalrelic;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

import org.eternalrelic.registry.RegistryInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.twcore.api.TwModManager;
import org.twcore.api.event.TwCoreRegisterEvent;

public class EternalRelic implements ModInitializer {
    public static final String MOD_ID = "eternal_relic";
    public static final Logger LOGGER = LoggerFactory.getLogger("TW's EternalRelic");

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        RegistryInit.init();
        TwCoreRegisterEvent.TW_CORE_REGISTRAR.register(EternalRelic::registerCore);
    }

    private static void registerCore() {
        TwModManager.IMPL.register(MOD_ID, 1);
    }
}
