//package com.github.nxkoo.orbitalrailgunneoforge;
//
//import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
//import com.github.nxkoo.orbitalrailgunneoforge.network.Network;
//import com.github.nxkoo.orbitalrailgunneoforge.registry.ModSounds;
//import com.github.nxkoo.orbitalrailgunneoforge.util.OrbitalRailgunStrikeManager;
//import net.minecraft.resources.ResourceLocation;
//import net.neoforged.bus.api.IEventBus;
//import net.neoforged.fml.ModContainer;
//import net.neoforged.fml.common.Mod;
//import net.neoforged.fml.config.ModConfig;
//import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//import net.neoforged.neoforge.common.NeoForge;
//import net.neoforged.neoforge.registries.DeferredRegister;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//@Mod(ForgeOrbitalRailgunModsss.MOD_ID)
//public class ForgeOrbitalRailgunModsss {
//    public static final String MOD_ID = "orbital_railgun";
//    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
//
//    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
//
//    public ForgeOrbitalRailgunModsss( IEventBus modBus, ModContainer modContainer) {
//        ITEMS.register(modBus);
//        ModSounds.SOUNDS.register(modBus);
//        modBus.addListener(this::onCommonSetup);
//
//        modContainer.registerConfig(ModConfig.Type.COMMON, OrbitalConfig.COMMON_SPEC);
//        modContainer.registerConfig(ModConfig.Type.CLIENT, OrbitalConfig.CLIENT_SPEC);
//
//        NeoForge.EVENT_BUS.register(this);
//
//        OrbitalRailgunStrikeManager.register();
//    }
//
//    private void onCommonSetup(final FMLCommonSetupEvent event) {
//        event.enqueueWork(Network::init);
//    }
//
//    public static ResourceLocation id(String path) {
//        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
//    }
//}
