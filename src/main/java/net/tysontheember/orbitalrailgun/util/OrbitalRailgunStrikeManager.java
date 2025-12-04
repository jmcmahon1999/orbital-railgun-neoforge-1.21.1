package net.tysontheember.orbitalrailgun.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.tysontheember.orbitalrailgun.ForgeOrbitalRailgunMod;
import net.tysontheember.orbitalrailgun.config.OrbitalConfig;
import net.tysontheember.orbitalrailgun.registry.ModSounds;
import net.tysontheember.orbitalrailgun.network.Network;
import net.tysontheember.orbitalrailgun.network.S2C_PlayStrikeEffects;
import net.tysontheember.orbitalrailgun.strike.StrikeExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.tysontheember.orbitalrailgun.compat.ClaimCompat;
import net.tysontheember.orbitalrailgun.compat.ClaimGuards;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OrbitalRailgunStrikeManager {
    private static final ResourceKey<DamageType> STRIKE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ForgeOrbitalRailgunMod.id("strike"));
    private static final Component CLAIM_BLOCKED_MESSAGE = Component.literal("❌ Railgun blocked by claim protection.");

    private static final Map<StrikeKey, ActiveStrike> ACTIVE_STRIKES = new ConcurrentHashMap<>();

    /**
     * Result returned when requesting a new orbital strike.
     *
     * @param success whether the strike was scheduled.
     * @param message optional error message when the strike cannot be scheduled.
     */
    public record StrikeRequestResult(boolean success, @Nullable Component message) {
        /** Convenience factory for a successful request result (no message). */
        public static StrikeRequestResult ok() {
            return new StrikeRequestResult(true, null);
        }

        /** Convenience factory for a failed request result with an explanatory message. */
        public static StrikeRequestResult failure(Component message) {
            return new StrikeRequestResult(false, message);
        }
    }

    private OrbitalRailgunStrikeManager() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(OrbitalRailgunStrikeManager::onServerTick);
    }

    public static void startStrike(ServerPlayer player, BlockPos target) {
        ServerLevel serverLevel = player.serverLevel();
        if (serverLevel.isClientSide()) {
            return;
        }
        double configuredDiameter = OrbitalConfig.DESTRUCTION_DIAMETER.get();
        double clampedDiameter = Mth.clamp(configuredDiameter, 1.0D, 256.0D);
        double radius = clampedDiameter * 0.5D;
        requestStrike(serverLevel, target, player, radius, 1.0F);
    }

    /**
     * Requests an orbital strike at the supplied position.
     *
     * @param level            level to strike.
     * @param target           center of the strike.
     * @param shooter          optional shooter used for claim checks.
     * @param radius           horizontal radius in blocks.
     * @param damageMultiplier scale applied to configured damage when detonating.
     * @return result describing whether the strike was accepted.
     */
    public static StrikeRequestResult requestStrike(ServerLevel level, BlockPos target, @Nullable ServerPlayer shooter,
                                                    double radius, float damageMultiplier) {
        return requestStrike(level, target, shooter, radius, damageMultiplier, false);
    }

    /**
     * Requests an orbital strike at the supplied position with optional claim validation.
     *
     * @param level            level to strike.
     * @param target           center of the strike.
     * @param shooter          optional shooter used for claim checks.
     * @param radius           horizontal radius in blocks.
     * @param damageMultiplier scale applied to configured damage when detonating.
     * @param validateClaims   whether to validate claim permissions immediately.
     * @return result describing whether the strike was accepted.
     */
    public static StrikeRequestResult requestStrike(ServerLevel level, BlockPos target, @Nullable ServerPlayer shooter,
                                                    double radius, float damageMultiplier, boolean validateClaims) {
        if (level.isClientSide()) {
            return StrikeRequestResult.failure(Component.literal("Orbital strikes can only be scheduled on the server."));
        }

        double normalizedRadius = Mth.clamp(radius, 0.5D, 128.0D);
        float normalizedMultiplier = Math.max(0.0F, damageMultiplier);

        if (validateClaims && areClaimsEnforced()) {
            if (shooter == null) {
                Component message = Component.literal(String.format(
                        "Strike blocked: a player context is required for claim checks at %d, %d, %d.",
                        target.getX(), target.getY(), target.getZ()));
                return StrikeRequestResult.failure(message);
            }
            if (!ClaimGuards.canAffectPosFromPos(level, target, level, target, shooter)) {
                shooter.displayClientMessage(CLAIM_BLOCKED_MESSAGE, true);
                Component message = Component.literal(String.format(
                        "Strike blocked by claim protection at %d, %d, %d.",
                        target.getX(), target.getY(), target.getZ()));
                return StrikeRequestResult.failure(message);
            }
        }

        boolean claimsPrevalidated = validateClaims && areClaimsEnforced();

        scheduleStrike(level, target, shooter, normalizedRadius, normalizedMultiplier, claimsPrevalidated);
        return StrikeRequestResult.ok();
    }

    private static void scheduleStrike(ServerLevel level, BlockPos target, @Nullable ServerPlayer shooter,
                                       double radius, float damageMultiplier, boolean claimsPrevalidated) {
        double trackedExtent = Math.max(radius * 4.0D, 128.0D);
        List<Entity> tracked = new ArrayList<>(level.getEntities(null,
                AABB.ofSize(Vec3.atCenterOf(target), trackedExtent, trackedExtent, trackedExtent)));
        StrikeKey key = new StrikeKey(level.dimension(), target.immutable());
        UUID shooterId = shooter != null ? shooter.getUUID() : null;
        int tickCount = level.getServer().getTickCount();
        ACTIVE_STRIKES.put(key, new ActiveStrike(key, tracked, tickCount, shooterId, radius, damageMultiplier, claimsPrevalidated));

        if (ModSounds.RAILGUN_SHOOT.isPresent()) {
            double soundX;
            double soundY;
            double soundZ;
            if (shooter != null) {
                soundX = shooter.getX();
                soundY = shooter.getY();
                soundZ = shooter.getZ();
            } else {
                soundX = target.getX();
                soundY = target.getY();
                soundZ = target.getZ();
            }
            level.playSound(null, soundX, soundY, soundZ,
                    ModSounds.RAILGUN_SHOOT.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
        }
        float serverRadius = (float) radius;
        Network.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(target.getX(), target.getY(), target.getZ(),
                        512.0D, level.dimension())),
                new S2C_PlayStrikeEffects(target, level.dimension(), serverRadius)
        );
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Iterator<Map.Entry<StrikeKey, ActiveStrike>> iterator = ACTIVE_STRIKES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<StrikeKey, ActiveStrike> entry = iterator.next();
            ActiveStrike strike = entry.getValue();
            ServerLevel level = event.getServer().getLevel(strike.key.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }

            int age = event.getServer().getTickCount() - strike.startTick;
            if (age >= 700) {
                iterator.remove();
                damageEntities(level, strike, age);
                explode(level, strike);
            } else if (age >= 400 && OrbitalConfig.SUCK_ENTITIES.get()) {
                pushEntities(level, strike, age);
            }
        }
    }

    private static void pushEntities(Level level, ActiveStrike strike, int age) {
        Vec3 center = Vec3.atCenterOf(strike.key.pos());
        for (Entity entity : strike.entities) {
            if (entity == null || !entity.isAlive() || entity.level() != level) {
                continue;
            }
            if (entity instanceof Player player && player.isSpectator()) {
                continue;
            }
            Vec3 direction = center.subtract(entity.position());
            double length = direction.length();
            if (length < 1e-4) {
                continue;
            }
            double magnitude = Math.min(1.0 / Math.max(Math.abs(length - 20.0), 0.001) * 4.0 * (age - 400.0) / 300.0, 5.0);
            Vec3 velocity = direction.normalize().scale(magnitude);
            entity.setDeltaMovement(entity.getDeltaMovement().add(velocity));
            entity.hurtMarked = true;
        }
    }

    private static void damageEntities(ServerLevel level, ActiveStrike strike, int age) {
        // Center & radius
        Vec3 center = Vec3.atCenterOf(strike.key.pos());
        double radius = Math.sqrt(strike.radiusSquared);

        // Use the registry holder you already set up
        Holder<DamageType> damageType = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(STRIKE_DAMAGE);

        // Create the damage source (1.20.1 Mojang mappings support this ctor)
        DamageSource source = new DamageSource(damageType);

        // Bail if disabled
        double configuredDamage = OrbitalConfig.STRIKE_DAMAGE.get();
        if (configuredDamage <= 0.0D) return;
        float damage = (float) configuredDamage * strike.damageMultiplier;

        // Respect claims (same flags you already use)
        boolean respectClaims = areClaimsEnforced();
        ServerPlayer shooter = respectClaims ? resolveShooter(level, strike) : null;
        boolean enforceClaims = respectClaims && !(shooter == null && strike.claimsPrevalidated);

        boolean blockedAny = false;
        BlockPos blockedPos = null;

        // **LIVE QUERY** of entities currently within radius (cylindrical-ish by AABB inflate)
        AABB box = AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0);
        List<Entity> targets = level.getEntities(null, box);

        for (Entity entity : targets) {
            if (entity == null || !entity.isAlive() || entity.level() != level) continue;
            if (entity instanceof Player p && p.isSpectator()) continue;

            // precise distance check (sphere)
            if (entity.position().distanceToSqr(center) > strike.radiusSquared) continue;

            if (enforceClaims && !ClaimGuards.canDamageEntity(level, shooter, entity)) {
                blockedAny = true;
                blockedPos = entity.blockPosition().immutable();
                continue;
            }

            // Reset invulnerability frames a bit so the big blast actually lands
            entity.invulnerableTime = Math.min(entity.invulnerableTime, 2);

            // Apply damage
            entity.hurt(source, damage);
        }

        if (blockedAny && enforceClaims) {
            notifyClaimBlocked(strike, shooter, ClaimBlockType.DAMAGE, blockedPos != null ? blockedPos : strike.key.pos());
        }
    }

    private static void explode(ServerLevel level, ActiveStrike strike) {
        BlockPos center = strike.key.pos();
        boolean respectClaims = areClaimsEnforced();
        ServerPlayer shooter = respectClaims ? resolveShooter(level, strike) : null;
        boolean enforceClaims = respectClaims && !(shooter == null && strike.claimsPrevalidated);

        if (enforceClaims && !ClaimGuards.canAffectPosFromPos(level, center, level, center, shooter)) {
            notifyClaimBlocked(strike, shooter, ClaimBlockType.EXPLOSION, center);
            return;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        boolean blockedAny = false;
        BlockPos blockedPos = null;
        int horizontalRange = strike.horizontalRange;

        // --- CHANGED: scan full vertical range from top build limit down to configured floor ---
        final int worldMinY = level.getMinBuildHeight();
        final int worldMaxY = level.getMaxBuildHeight() - 1;
        final int cfgFloor = OrbitalConfig.MIN_DESTROY_Y.get();
        final int yEnd   = Math.max(worldMinY, cfgFloor); // clamp to world min
        final int yStart = worldMaxY;                     // always start at top build limit

        LongOpenHashSet allowedPositions = new LongOpenHashSet();

        for (int y = yStart; y >= yEnd; --y) {
            for (int x = -horizontalRange; x <= horizontalRange; x++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    double horizontalDistanceSq = (double) x * x + (double) z * z;
                    if (horizontalDistanceSq > strike.radiusSquared) continue;

                    mutable.set(center.getX() + x, y, center.getZ() + z);
                    BlockState state = level.getBlockState(mutable);
                    if (state.isAir()) continue;

                    if (enforceClaims && !ClaimGuards.canBreakBlock(level, shooter, mutable)) {
                        blockedAny = true;
                        blockedPos = mutable.immutable();
                        continue;
                    }

                    double maxHardness = OrbitalConfig.MAX_BREAK_HARDNESS.get();
                    boolean infinite = maxHardness < 0.0D;
                    boolean isFluid = !state.getFluidState().isEmpty();

                    // Always resolve ID once
                    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());

                    // 1) Blacklist check ALWAYS applies
                    if (id != null && OrbitalConfig.isBlockBlacklistedNormalized(id.toString())) {
                        if (OrbitalConfig.DEBUG.get()) {
                            ForgeOrbitalRailgunMod.LOGGER.info("[OrbitalStrike] Skipped blacklisted block: {}", id);
                        }
                        continue;
                    }

                    // 2) Hardness check only applies when not infinite and the block is not a fluid
                    if (!infinite && !isFluid) {
                        double hardness = state.getDestroySpeed(level, mutable);
                        if (hardness > maxHardness) {
                            if (OrbitalConfig.DEBUG.get()) {
                                ForgeOrbitalRailgunMod.LOGGER.info(
                                        "[OrbitalStrike] Skipped block due to hardness: {} ({} > {})",
                                        id, hardness, maxHardness
                                );
                            }
                            continue;
                        }
                    }

                    allowedPositions.add(mutable.asLong());
                }
            }
        }
        if (blockedAny && enforceClaims) {
            notifyClaimBlocked(strike, shooter, ClaimBlockType.BLOCKS, blockedPos != null ? blockedPos : center);
        }

        if (!allowedPositions.isEmpty()) {
            double radius = Math.sqrt(strike.radiusSquared);
            double diameter = radius * 2.0D;
            StrikeExecutor.begin(level, center, diameter);
            StrikeExecutor.filterAllowed(allowedPositions);
        }
    }

    private static boolean areClaimsEnforced() {
        boolean ftb = ClaimCompat.hasFTB() && OrbitalConfig.RESPECT_CLAIMS.get();
        boolean opac = ClaimCompat.hasOPAC() && OrbitalConfig.RESPECT_OPAC_CLAIMS.get();
        return ftb || opac;
    }

    private static ServerPlayer resolveShooter(ServerLevel level, ActiveStrike strike) {
        if (strike.shooter == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(strike.shooter);
    }

    private static void notifyClaimBlocked(ActiveStrike strike, ServerPlayer shooter, ClaimBlockType type, BlockPos pos) {
        if (strike.markNotified(type) && shooter != null) {
            shooter.displayClientMessage(CLAIM_BLOCKED_MESSAGE, true);
        }
        if (OrbitalConfig.DEBUG.get()) {
            ForgeOrbitalRailgunMod.LOGGER.info("[OrbitalStrike] Claim protection blocked {} at {}", type.name().toLowerCase(), pos);
        }
    }

    private record StrikeKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private enum ClaimBlockType {
        BLOCKS,
        EXPLOSION,
        DAMAGE
    }

    private static final class ActiveStrike {
        private final StrikeKey key;
        private final List<Entity> entities;
        private final int startTick;
        private final UUID shooter;
        private final double radiusSquared;
        private final int horizontalRange;
        private final float damageMultiplier;
        private final boolean claimsPrevalidated;
        private boolean blockNotified;
        private boolean explosionNotified;
        private boolean damageNotified;

        private ActiveStrike(StrikeKey key, List<Entity> entities, int startTick, UUID shooter, double radius,
                             float damageMultiplier, boolean claimsPrevalidated) {
            this.key = key;
            this.entities = entities;
            this.startTick = startTick;
            this.shooter = shooter;
            this.radiusSquared = radius * radius;
            this.horizontalRange = Math.max(0, Mth.ceil(radius));
            this.damageMultiplier = damageMultiplier;
            this.claimsPrevalidated = claimsPrevalidated;
        }

        private boolean markNotified(ClaimBlockType type) {
            return switch (type) {
                case BLOCKS -> {
                    if (blockNotified) yield false;
                    blockNotified = true;
                    yield true;
                }
                case EXPLOSION -> {
                    if (explosionNotified) yield false;
                    explosionNotified = true;
                    yield true;
                }
                case DAMAGE -> {
                    if (damageNotified) yield false;
                    damageNotified = true;
                    yield true;
                }
            };
        }
    }
}
