package com.github.nxkoo.orbitalrailgunneoforge.network;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ForgeOrbitalRailgunMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class Network {

    private Network() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ForgeOrbitalRailgunMod.MOD_ID)
                .versioned("1");

        registrar.playToServer(
                C2S_RequestFire.TYPE,
                C2S_RequestFire.STREAM_CODEC,
                C2S_RequestFire::handle
        );

        registrar.playToClient(
                S2C_PlayStrikeEffects.TYPE,
                S2C_PlayStrikeEffects.STREAM_CODEC,
                S2C_PlayStrikeEffects::handle
        );
    }
}