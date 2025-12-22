package com.github.nxkoo.orbitalrailgunneoforge.network;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import com.github.nxkoo.orbitalrailgunneoforge.client.railgun.RailgunState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2C_PlayStrikeEffects(BlockPos pos, ResourceKey<Level> dimension, float serverStrikeRadius) implements CustomPacketPayload {

    public static final Type<S2C_PlayStrikeEffects> TYPE = new Type<>(ForgeOrbitalRailgunMod.id("play_strike_effects"));

    public static final StreamCodec<ByteBuf, S2C_PlayStrikeEffects> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, S2C_PlayStrikeEffects::pos,
            ResourceKey.streamCodec(Registries.DIMENSION), S2C_PlayStrikeEffects::dimension,
            ByteBufCodecs.FLOAT, S2C_PlayStrikeEffects::serverStrikeRadius,
            S2C_PlayStrikeEffects::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2C_PlayStrikeEffects packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleClient(packet);
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(S2C_PlayStrikeEffects packet) {
        Minecraft mc = Minecraft.getInstance();
        RailgunState state = RailgunState.getInstance();
        state.onStrikeStarted(packet.pos(), packet.dimension());
        state.setTransientVisualStrikeRadius(packet.serverStrikeRadius(), 40);
    }
}