package com.github.nxkoo.orbitalrailgunneoforge.network;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import com.github.nxkoo.orbitalrailgunneoforge.item.OrbitalRailgunItem;
import com.github.nxkoo.orbitalrailgunneoforge.registry.ModSounds;
import com.github.nxkoo.orbitalrailgunneoforge.util.OrbitalRailgunStrikeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2S_RequestFire(BlockPos target) implements CustomPacketPayload {

    public static final Type<C2S_RequestFire> TYPE = new Type<>(ForgeOrbitalRailgunMod.id("request_fire"));

    public static final StreamCodec<ByteBuf, C2S_RequestFire> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2S_RequestFire::target,
            C2S_RequestFire::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2S_RequestFire packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack stack = player.getMainHandItem();
            OrbitalRailgunItem railgun;
            if (stack.getItem() instanceof OrbitalRailgunItem mainHandRailgun) {
                railgun = mainHandRailgun;
            } else {
                stack = player.getOffhandItem();
                if (stack.getItem() instanceof OrbitalRailgunItem offhandRailgun) {
                    railgun = offhandRailgun;
                } else {
                    return;
                }
            }

            if (player.getCooldowns().isOnCooldown(railgun)) return;

            railgun.applyCooldown(player);
            ServerLevel level = player.serverLevel();

            double maxRange = Math.max(0.0D, OrbitalConfig.RANGE.get());
            double dx = packet.target.getX() - player.getX();
            double dz = packet.target.getZ() - player.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            if (horizontalDistSq > maxRange * maxRange) {
                return;
            }
            int x = packet.target.getX();
            int z = packet.target.getZ();
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos sanitizedTarget = new BlockPos(x, surfaceY, z);
            if (!level.hasChunkAt(sanitizedTarget)) {
                return;
            }

            level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    ModSounds.RAILGUN_SHOOT.get(),
                    SoundSource.PLAYERS,
                    1.6f, 1.0f
            );

            OrbitalRailgunStrikeManager.startStrike(player, sanitizedTarget);
        });
    }
}