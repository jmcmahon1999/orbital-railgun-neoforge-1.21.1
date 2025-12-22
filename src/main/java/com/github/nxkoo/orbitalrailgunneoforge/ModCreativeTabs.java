package com.github.nxkoo.orbitalrailgunneoforge;

import com.github.nxkoo.orbitalrailgunneoforge.item.OrbitalRailgunItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ForgeOrbitalRailgunMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORBITAL_RAILGUN_TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.orbital_railgun"))
                    .icon(() -> new ItemStack(ForgeOrbitalRailgunMod.ORBITAL_RAILGUN.get()))
                    .displayItems((params, output) -> {
                        output.accept(ForgeOrbitalRailgunMod.ORBITAL_RAILGUN.get());
                    })
                    .build());
}