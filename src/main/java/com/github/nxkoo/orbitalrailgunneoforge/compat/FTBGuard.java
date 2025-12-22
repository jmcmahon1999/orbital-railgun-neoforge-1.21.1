package com.github.nxkoo.orbitalrailgunneoforge.compat;

import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class FTBGuard {
    private FTBGuard() {
    }

    public static boolean canBreakBlock(ServerLevel level, ServerPlayer shooter, BlockPos pos) {
        if (!ClaimCompat.hasFTB()) {
            return true;
        }
        if (!FTBChunksCompat.isPositionClaimed(level, pos)) {
            return true;
        }
        if (!OrbitalConfig.ALLOW_BLOCK_BREAK_IN_CLAIMS.get()) {
            return false;
        }
        return FTBChunksCompat.canModifyBlock(level, pos, shooter);
    }

    public static boolean canDamageEntity(ServerLevel level, ServerPlayer shooter, Entity target) {
        if (!ClaimCompat.hasFTB()) {
            return true;
        }
        if (!FTBChunksCompat.isPositionClaimed(level, target.blockPosition())) {
            return true;
        }
        if (!OrbitalConfig.ALLOW_ENTITY_DAMAGE_IN_CLAIMS.get()) {
            return false;
        }
        return FTBChunksCompat.canDamageEntity(level, target, shooter);
    }

    public static boolean canAffectPosFromPos(ServerLevel level, BlockPos targetPos, ServerPlayer shooter) {
        if (!ClaimCompat.hasFTB()) {
            return true;
        }
        if (!FTBChunksCompat.isPositionClaimed(level, targetPos)) {
            return true;
        }
        if (!OrbitalConfig.ALLOW_EXPLOSIONS_IN_CLAIMS.get()) {
            return false;
        }
        return FTBChunksCompat.canExplode(level, targetPos, shooter);
    }
}
