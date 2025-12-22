package com.github.nxkoo.orbitalrailgunneoforge.compat;

import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

public final class FTBChunksCompat {

    private static final boolean LOADED = ModList.get().isLoaded("ftbchunks");

    private FTBChunksCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isPositionClaimed(ServerLevel level, BlockPos pos) {
        if (!LOADED) {
            return false;
        }
        try {
            return ClaimQuery.isClaimed(level, pos);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean canModifyBlock(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!LOADED) {
            return true;
        }
        if (player == null) {
            return false;
        }

        try {
            if (OrbitalConfig.OPS_BYPASS_CLAIMS.get() && player.hasPermissions(2)) {
                return true;
            }
            return ClaimQuery.canEdit(level, pos, player);
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean canDamageEntity(ServerLevel level, Entity target, ServerPlayer player) {
        if (!LOADED) {
            return true;
        }
        if (player == null) {
            return false;
        }

        try {
            if ( OrbitalConfig.OPS_BYPASS_CLAIMS.get() && player.hasPermissions(2)) {
                return true;
            }
            return ClaimQuery.canAttack(level, target.blockPosition(), player);
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean canExplode(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!LOADED) {
            return true;
        }
        if (player == null) {
            return false;
        }

        try {
            if (OrbitalConfig.OPS_BYPASS_CLAIMS.get() && player.hasPermissions(2)) {
                return true;
            }
            return ClaimQuery.canExplode(level, pos, player);
        } catch (Throwable t) {
            return true;
        }
    }
}

