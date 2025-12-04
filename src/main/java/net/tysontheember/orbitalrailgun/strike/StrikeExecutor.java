package net.tysontheember.orbitalrailgun.strike;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tysontheember.orbitalrailgun.ForgeOrbitalRailgunMod;
import net.tysontheember.orbitalrailgun.config.OrbitalConfig;
import net.tysontheember.orbitalrailgun.util.OrbitalRailgunStrikeManager;
import net.tysontheember.orbitalrailgun.util.OrbitalRailgunStrikeManager.StrikeRequestResult;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = ForgeOrbitalRailgunMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StrikeExecutor {
    private static final java.util.ArrayDeque<StrikeWork> ACTIVE = new java.util.ArrayDeque<>();

    private StrikeExecutor() {}

    /**
     * Queues an orbital strike using the shared strike manager logic.
     *
     * @param level     server level the strike should run in.
     * @param impactCenter position of the strike impact.
     * @param power     damage multiplier supplied by the caller.
     * @param radius    horizontal radius in blocks.
     * @param requester optional player responsible for the strike (used for claim checks).
     * @return result describing whether the strike was accepted.
     */
    public static StrikeRequestResult queueStrike(ServerLevel level, BlockPos impactCenter, float power, int radius,
                                                  @Nullable ServerPlayer requester) {
        double requestedRadius = Math.max(radius, 0);
        return OrbitalRailgunStrikeManager.requestStrike(level, impactCenter, requester, requestedRadius, power, true);
    }

    public static void begin(ServerLevel level, BlockPos impactCenter, double diameter) {
        final double radius = Math.max(0.0, diameter * 0.5);
        final int r = (int) Math.ceil(radius);

        final int minY = level.getMinBuildHeight();
        final int maxY = level.getMaxBuildHeight() - 1;

        StrikeWork work = new StrikeWork(level);

        for (int y = maxY; y >= minY; --y) {
            final int baseX = impactCenter.getX();
            final int baseZ = impactCenter.getZ();
            for (int dx = -r; dx <= r; ++dx) {
                for (int dz = -r; dz <= r; ++dz) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        long key = BlockPos.asLong(baseX + dx, y, baseZ + dz);
                        if (work.seen.add(key)) {
                            work.queue.enqueue(key);
                        }
                    }
                }
            }
        }
        ACTIVE.addLast(work);
    }


    public static void filterAllowed(LongSet allowed) {
        StrikeWork work = ACTIVE.peekLast();
        if (work == null) return;

        LongArrayList ordered = new LongArrayList();
        while (!work.queue.isEmpty()) {
            long key = work.queue.dequeueLong();
            if (allowed.contains(key)) ordered.add(key);
        }
        work.queue.clear();
        work.seen.clear();
        for (long key : ordered) {
            if (work.seen.add(key)) work.queue.enqueue(key);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        StrikeWork work = ACTIVE.peekFirst();
        if (work == null) return;
        if (work.queue.isEmpty()) { ACTIVE.removeFirst(); return; }

        int budget = OrbitalConfig.BLOCKS_PER_TICK.get();
        boolean drop = OrbitalConfig.DROP_BLOCKS.get();

        boolean allowUnbreakables = OrbitalConfig.MAX_BREAK_HARDNESS.get() < 0.0D;

        for (int i = 0; i < budget && !work.queue.isEmpty(); i++) {
            long key = work.queue.dequeueLong();
            BlockPos pos = BlockPos.of(key);

            if (!work.level.isLoaded(pos)) continue; // avoid force-loading
            BlockState state = work.level.getBlockState(pos);
            if (state.isAir()) continue;

            if (!allowUnbreakables && state.getDestroySpeed(work.level, pos) < 0) continue;

            boolean isFluid = !state.getFluidState().isEmpty();
            if (drop && !isFluid) {
                work.level.destroyBlock(pos, true, null); // heavier (loot, entities)
            } else {
                // For fluids (and when not dropping), just vaporize to avoid bucket loot attempts
                work.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); // block update + clients
            }
        }
    }

    private static final class StrikeWork {
        final ServerLevel level;
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final LongOpenHashSet seen = new LongOpenHashSet();

        StrikeWork(ServerLevel level) {
            this.level = level;
        }
    }
}
