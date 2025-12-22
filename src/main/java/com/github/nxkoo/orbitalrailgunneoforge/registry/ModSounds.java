package com.github.nxkoo.orbitalrailgunneoforge.registry;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ForgeOrbitalRailgunMod.MOD_ID);

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ForgeOrbitalRailgunMod.MOD_ID, id)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> EQUIP = sound("equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCOPE_ON = sound("scope_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_SHOOT = sound("railgun_shoot");

    private ModSounds() {}
}