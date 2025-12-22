package com.github.nxkoo.orbitalrailgunneoforge.compat;

import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

public final class OPACGuard {
    private OPACGuard() {
    }

    public static boolean canBreakBlock(ServerLevel level, ServerPlayer shooter, BlockPos pos) {
        boolean allowInClaims = OrbitalConfig.ALLOW_BLOCK_BREAK_IN_CLAIMS.get();
        try {
            var api = xaero.pac.common.server.api.OpenPACServerAPI.get(level.getServer());
            if (api == null) {
                return true;
            }

            boolean claimed = isBlockClaimed(api, level, pos);
            if (!allowInClaims && claimed) {
                return false;
            }

            var protection = api.getChunkProtection();
            if (protection == null) {
                return !claimed;
            }

            boolean protect = protection.onEntityPlaceBlock(shooter, level, pos);
            if (protect) {
                return false;
            }

            return allowInClaims || !claimed;
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean canDamageEntity(ServerLevel level, ServerPlayer shooter, Entity target) {
        boolean allowInClaims = OrbitalConfig.ALLOW_ENTITY_DAMAGE_IN_CLAIMS.get();
        try {
            var api = xaero.pac.common.server.api.OpenPACServerAPI.get(level.getServer());
            if (api == null) {
                return true;
            }

            boolean claimed = isBlockClaimed(api, level, target.blockPosition());
            if (!allowInClaims && claimed) {
                return false;
            }

            var protection = api.getChunkProtection();
            if (protection == null) {
                return !claimed;
            }

            boolean protect = protection.onEntityInteraction(
                shooter, shooter, target,
                shooter.getMainHandItem(), InteractionHand.MAIN_HAND,
                true,
                false,
                true
            );
            if (protect) {
                return false;
            }

            return allowInClaims || !claimed;
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean canAffectPosFromPos(ServerLevel fromLevel, ChunkPos fromChunk, ServerLevel toLevel, ChunkPos toChunk) {
        boolean allowInClaims = OrbitalConfig.ALLOW_EXPLOSIONS_IN_CLAIMS.get();
        try {
            var api = xaero.pac.common.server.api.OpenPACServerAPI.get(fromLevel.getServer());
            if (api == null) {
                return true;
            }

            boolean claimed = isChunkClaimed(api, toLevel, toChunk);
            if (!allowInClaims && claimed) {
                return false;
            }

            var protection = api.getChunkProtection();
            if (protection == null) {
                return !claimed;
            }

            boolean protect = protection.onPosAffectedByAnotherPos(
                toLevel, toChunk,
                fromLevel, fromChunk,
                true,
                true,
                true
            );
            if (protect) {
                return false;
            }

            return allowInClaims || !claimed;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isBlockClaimed(xaero.pac.common.server.api.OpenPACServerAPI api, ServerLevel level, BlockPos pos) {
        var claims = api.getServerClaimsManager();
        if (claims == null) {
            return false;
        }
        return claims.get(level.dimension().location(), pos) != null;
    }

    private static boolean isChunkClaimed(xaero.pac.common.server.api.OpenPACServerAPI api, ServerLevel level, ChunkPos chunk) {
        var claims = api.getServerClaimsManager();
        if (claims == null) {
            return false;
        }
        return claims.get(level.dimension().location(), chunk) != null;
    }
}
