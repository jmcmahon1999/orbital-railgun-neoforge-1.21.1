package net.tysontheember.orbitalrailgun.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.tysontheember.orbitalrailgun.config.OrbitalConfig;
import net.tysontheember.orbitalrailgun.item.OrbitalRailgunItem;
import net.tysontheember.orbitalrailgun.registry.ModSounds;
import net.tysontheember.orbitalrailgun.util.OrbitalRailgunStrikeManager;

import java.util.function.Supplier;

public record C2S_RequestFire(BlockPos target) {
    public static void encode(C2S_RequestFire packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.target);
    }

    public static C2S_RequestFire decode(FriendlyByteBuf buf) {
        return new C2S_RequestFire(buf.readBlockPos());
    }

    public static void handle(C2S_RequestFire packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

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

            // Apply cooldown as you already do
            railgun.applyCooldown(player);

            // 🔊 Broadcast the firing sound from the player's position (server-side so others hear it)
            ServerLevel level = player.serverLevel();

            // Server-side validation of target range and column
            double maxRange = Math.max(0.0D, OrbitalConfig.RANGE.get());
            double dx = packet.target.getX() - player.getX();
            double dz = packet.target.getZ() - player.getZ();
            double horizontalDistSq = dx * dx + dz * dz;
            if (horizontalDistSq > maxRange * maxRange) {
                return; // out of range; silently drop (could log or notify)
            }
            int x = packet.target.getX();
            int z = packet.target.getZ();
            int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos sanitizedTarget = new BlockPos(x, surfaceY, z);
            if (!level.hasChunkAt(sanitizedTarget)) {
                return;
            }

            level.playSound(
                    /* player */ null,                                  // null = broadcast to nearby players
                    player.getX(), player.getY(), player.getZ(),        // play at shooter (or use packet.target if you prefer)
                    ModSounds.RAILGUN_SHOOT.get(),
                    SoundSource.PLAYERS,
                    1.6f, 1.0f
            );

            // Start the actual strike logic
            OrbitalRailgunStrikeManager.startStrike(player, sanitizedTarget);
        });
        context.setPacketHandled(true);
    }
}
