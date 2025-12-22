package com.github.nxkoo.orbitalrailgunneoforge;

import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import com.github.nxkoo.orbitalrailgunneoforge.item.OrbitalRailgunItem;
import com.github.nxkoo.orbitalrailgunneoforge.network.S2C_PlayStrikeEffects;
import com.github.nxkoo.orbitalrailgunneoforge.registry.ModSounds;
import com.github.nxkoo.orbitalrailgunneoforge.util.OrbitalRailgunStrikeManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(ForgeOrbitalRailgunMod.MOD_ID)
public class ForgeOrbitalRailgunMod {
    public static final String MOD_ID = "orbital_railgun";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<OrbitalRailgunItem> ORBITAL_RAILGUN = ITEMS.register("orbital_railgun",
            () -> new OrbitalRailgunItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public ForgeOrbitalRailgunMod( IEventBus modEventBus, ModContainer modContainer ) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);

        ModCreativeTabs.TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, OrbitalConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, OrbitalConfig.CLIENT_SPEC);

        modEventBus.addListener(this::addCreative);

        OrbitalRailgunStrikeManager.register();
    }

    private void addCreative( BuildCreativeModeTabContentsEvent event ) {
        if ( event.getTabKey() == CreativeModeTabs.COMBAT ) {
            event.accept(ORBITAL_RAILGUN);
        }
    }

    public static ResourceLocation id( String path ) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}