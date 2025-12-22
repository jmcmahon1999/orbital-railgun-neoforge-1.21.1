package com.github.nxkoo.orbitalrailgunneoforge.compat;

import dev.ftb.mods.ftbchunks.api.*;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.UUID;

final class ClaimQuery {
    private ClaimQuery() {
    }

    static boolean isClaimed(ServerLevel level, BlockPos pos) {
        return get(level, pos).isPresent();
    }

    static boolean canEdit(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<ClaimedChunk> chunk = get(level, pos);
        if (chunk.isEmpty()) {
            return true;
        }
        return hasEditPermission(chunk.get(), player);
    }

    static boolean canAttack(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<ClaimedChunk> chunk = get(level, pos);
        if (chunk.isEmpty()) {
            return true;
        }
        return hasAttackPermission(chunk.get(), player);
    }

    static boolean canExplode(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<ClaimedChunk> chunk = get(level, pos);
        if (chunk.isEmpty()) {
            return true;
        }
        return hasEditPermission(chunk.get(), player);
    }

    private static Optional<ClaimedChunk> get(ServerLevel level, BlockPos pos) {
        ClaimedChunkManager manager = FTBChunksAPI.api().getManager();
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkDimPos chunkDimPos = new ChunkDimPos(level.dimension(), chunkPos.x, chunkPos.z);
        return Optional.ofNullable(manager.getChunk(chunkDimPos));
    }

    private static boolean hasEditPermission(ClaimedChunk chunk, ServerPlayer player) {
        ChunkTeamData data = chunk.getTeamData();
        if (data == null) {
            return true;
        }
        if (data.canPlayerUse(player, FTBChunksProperties.BLOCK_EDIT_MODE)) {
            return true;
        }
        return isMemberOrAlly(data, player.getUUID());
    }

    private static boolean hasAttackPermission(ClaimedChunk chunk, ServerPlayer player) {
        ChunkTeamData data = chunk.getTeamData();
        if (data == null) {
            return true;
        }
        if (data.canPlayerUse(player, FTBChunksProperties.NONLIVING_ENTITY_ATTACK_MODE)) {
            return true;
        }
        if (data.canPlayerUse(player, FTBChunksProperties.ENTITY_INTERACT_MODE)) {
            return true;
        }
        return isMemberOrAlly(data, player.getUUID());
    }

    private static boolean isMemberOrAlly(ChunkTeamData data, UUID playerId) {
        if (data.isTeamMember(playerId) || data.isAlly(playerId)) {
            return true;
        }
        Team team = data.getTeam();
        if (team != null) {
            if (team.getMembers().contains(playerId)) {
                return true;
            }
            UUID ownerId = team.getOwner();
            if (ownerId != null) {
                try {
                    FTBTeamsAPI.API api = FTBTeamsAPI.api();
                    if (api != null && api.isManagerLoaded() && api.getManager().arePlayersInSameTeam(ownerId, playerId)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }
}

