package com.github.nxkoo.orbitalrailgunneoforge.compat;

import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

public final class ClaimGuards {
    private ClaimGuards() {
    }

    public static boolean canBreakBlock(ServerLevel level, ServerPlayer shooter, BlockPos pos) {
        boolean ftb = shouldCheckFTB();
        boolean opac = shouldCheckOPAC();
        if (!ftb && !opac) {
            return true;
        }
        if (shooter == null) {
            return false;
        }
        if (OrbitalConfig.OPS_BYPASS_CLAIMS.get() && shooter.hasPermissions(2)) {
            return true;
        }

        boolean allowed = true;
        if (ftb) {
            allowed &= FTBGuard.canBreakBlock(level, shooter, pos);
        }
        if (allowed && opac) {
            allowed &= OPACGuard.canBreakBlock(level, shooter, pos);
        }
        return allowed;
    }

    public static boolean canDamageEntity(ServerLevel level, ServerPlayer shooter, Entity target) {
        boolean ftb = shouldCheckFTB();
        boolean opac = shouldCheckOPAC();
        if (!ftb && !opac) {
            return true;
        }
        if (shooter == null) {
            return false;
        }
        if ( OrbitalConfig.OPS_BYPASS_CLAIMS.get() && shooter.hasPermissions(2)) {
            return true;
        }

        boolean allowed = true;
        if (ftb) {
            allowed &= FTBGuard.canDamageEntity(level, shooter, target);
        }
        if (allowed && opac) {
            allowed &= OPACGuard.canDamageEntity(level, shooter, target);
        }
        return allowed;
    }

    public static boolean canAffectPosFromPos(ServerLevel fromLevel, BlockPos fromPos, ServerLevel toLevel, BlockPos toPos,
                                              ServerPlayer shooter) {
        boolean ftb = shouldCheckFTB();
        boolean opac = shouldCheckOPAC();
        if (!ftb && !opac) {
            return true;
        }
        if (shooter == null && (ftb || opac)) {
            return false;
        }
        if (shooter != null && OrbitalConfig.OPS_BYPASS_CLAIMS.get() && shooter.hasPermissions(2)) {
            return true;
        }

        boolean allowed = true;
        if (ftb) {
            allowed &= FTBGuard.canAffectPosFromPos(toLevel, toPos, shooter);
        }
        if (allowed && opac) {
            ChunkPos fromChunk = new ChunkPos(fromPos);
            ChunkPos toChunk = new ChunkPos(toPos);
            allowed &= OPACGuard.canAffectPosFromPos(fromLevel, fromChunk, toLevel, toChunk);
        }
        return allowed;
    }

    private static boolean shouldCheckFTB() {
        return ClaimCompat.hasFTB() && OrbitalConfig.RESPECT_CLAIMS.get();
    }

    private static boolean shouldCheckOPAC() {
        return ClaimCompat.hasOPAC() && OrbitalConfig.RESPECT_OPAC_CLAIMS.get();
    }
}
