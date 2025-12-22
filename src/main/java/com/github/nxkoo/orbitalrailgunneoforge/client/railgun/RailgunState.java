package com.github.nxkoo.orbitalrailgunneoforge.client.railgun;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import com.github.nxkoo.orbitalrailgunneoforge.item.OrbitalRailgunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class RailgunState {
    public enum HitKind {
        NONE,
        BLOCK,
        ENTITY
    }

    private static final RailgunState INSTANCE = new RailgunState();

    // ==== NEW: minimum warm-up delay before firing (in ticks) ====
    private static final int MIN_WARMUP_TICKS = 20;

    private boolean charging;
    private int chargeTicks;
    private boolean requestedFire;
    private boolean hudHidden;

    private float cooldownPercent;

    private HitKind hitKind = HitKind.NONE;
    private Vec3 hitPos = Vec3.ZERO;
    private float hitDistance;
    private HitResult currentHit;
    private OrbitalRailgunItem activeRailgun;

    private boolean lastCharging;

    private boolean strikeActive;
    private int strikeTicks;
    private Vec3 strikePos = Vec3.ZERO;
    private ResourceKey<Level> strikeDimension;

    private float transientVisualStrikeRadius = Float.NaN;
    private int transientVisualStrikeRadiusTicks;

    private RailgunState() {}

    public static RailgunState getInstance() {
        return INSTANCE;
    }

    public void tick(Minecraft minecraft) {
        if (minecraft.isPaused()) {
            return;
        }

        LocalPlayer player = minecraft.player;
        activeRailgun = getActiveRailgun(player);

        if (player != null) {
            cooldownPercent = player.getCooldowns().getCooldownPercent(ForgeOrbitalRailgunMod.ORBITAL_RAILGUN.get(), minecraft.getTimer().getGameTimeDeltaPartialTick(true));
        } else {
            cooldownPercent = 0.0F;
        }

        boolean wasCharging = charging;
        lastCharging = wasCharging;
        charging = player != null && activeRailgun != null && player.isUsingItem();

        if (charging) {
            chargeTicks++;
            requestedFire = requestedFire && player.isUsingItem();
            updateHitInformation(minecraft, player);
        } else {
            chargeTicks = 0;
            requestedFire = false;
            hitKind = HitKind.NONE;
            hitPos = Vec3.ZERO;
            hitDistance = 0.0F;
            currentHit = null;
        }

        if (strikeActive) {
            strikeTicks++;
            if (strikeTicks >= 1600) {
                clearStrike();
            } else if (minecraft.level == null || minecraft.level.dimension() != strikeDimension) {
                clearStrike();
            }
        }

        if (transientVisualStrikeRadiusTicks > 0) {
            transientVisualStrikeRadiusTicks--;
            if (transientVisualStrikeRadiusTicks <= 0) {
                transientVisualStrikeRadius = Float.NaN;
            }
        }
    }

    private void updateHitInformation(Minecraft minecraft, LocalPlayer player) {
        HitResult result = null;
        Level level = minecraft.level;
        if (level != null) {
            Vec3 start = player.getEyePosition(1.0F);
            Vec3 direction = player.getViewVector(1.0F);
            double distance = Math.max(1.0D, OrbitalConfig.RANGE.get());
            Vec3 end = start.add(direction.scale(distance));
            ClipContext context = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
            result = level.clip(context);
        }

        currentHit = result;
        if (result == null) {
            hitKind = HitKind.NONE;
            hitPos = Vec3.ZERO;
            hitDistance = 0.0F;
            return;
        }

        switch (result.getType()) {
            case BLOCK -> {
                BlockHitResult blockHitResult = (BlockHitResult) result;
                hitKind = HitKind.BLOCK;
                hitPos = Vec3.atCenterOf(blockHitResult.getBlockPos());
                hitDistance = (float) player.position().distanceTo(hitPos);
            }
            case ENTITY -> {
                EntityHitResult entityHitResult = (EntityHitResult) result;
                hitKind = HitKind.ENTITY;
                hitPos = entityHitResult.getLocation();
                hitDistance = (float) player.position().distanceTo(hitPos);
            }
            default -> {
                hitKind = HitKind.NONE;
                hitPos = Vec3.ZERO;
                hitDistance = 0.0F;
            }
        }
    }

    @Nullable
    private OrbitalRailgunItem getActiveRailgun(@Nullable LocalPlayer player) {
        if (player == null) {
            return null;
        }
        if (player.getUseItem().getItem() instanceof OrbitalRailgunItem railgun) {
            return railgun;
        }
        return null;
    }

    public boolean canRequestFire(LocalPlayer player) {
        if (!charging || requestedFire || strikeActive || activeRailgun == null) {
            return false;
        }
        // ==== NEW: prevent firing until warm-up threshold reached ====
        if (chargeTicks < MIN_WARMUP_TICKS) {
            return false;
        }
        if (player == null || player.getCooldowns().isOnCooldown(activeRailgun)) {
            return false;
        }
        return hitKind != HitKind.NONE;
    }

    public void markFired() {
        requestedFire = true;
    }

    public boolean isCharging() {
        return charging;
    }

    public boolean wasChargingLastTick() {
        return lastCharging;
    }

    public float getChargeSeconds(float partialTicks) {
        return (chargeTicks + partialTicks) / 20.0F;
    }

    public float getChargeProgress() {
        if (!charging) {
            return 0.0F;
        }
        return Math.min(chargeTicks / 40.0F, 1.0F);
    }

    public static float getClientChargeProgress() {
        return INSTANCE.getChargeProgress();
    }

    public float getCooldownPercent() {
        return cooldownPercent;
    }

    public HitKind getHitKind() {
        return hitKind;
    }

    public Vec3 getHitPos() {
        return hitPos;
    }

    public float getHitDistance() {
        return hitDistance;
    }

    public HitResult getCurrentHit() {
        return currentHit;
    }

    @Nullable
    public OrbitalRailgunItem getActiveRailgunItem() {
        return activeRailgun;
    }

    public void onStrikeStarted(BlockPos blockPos, ResourceKey<Level> dimension) {
        strikeActive = true;
        strikeTicks = 0;
        strikePos = Vec3.atCenterOf(blockPos);
        strikeDimension = dimension;
    }

    public boolean isStrikeActive() {
        return strikeActive;
    }

    public Vec3 getStrikePos() {
        return strikePos;
    }

    public float getStrikeSeconds(float partialTicks) {
        return (strikeTicks + partialTicks) / 20.0F;
    }

    public ResourceKey<Level> getStrikeDimension() {
        return strikeDimension;
    }

    public void clearStrike() {
        strikeActive = false;
        strikeTicks = 0;
        strikePos = Vec3.ZERO;
        strikeDimension = null;
        transientVisualStrikeRadius = Float.NaN;
        transientVisualStrikeRadiusTicks = 0;
    }

    public void setTransientVisualStrikeRadius(float radius, int ttlTicks) {
        transientVisualStrikeRadius = Math.max(0.0F, radius);
        transientVisualStrikeRadiusTicks = Math.max(0, ttlTicks);
    }

    public Optional<Float> getTransientVisualStrikeRadius() {
        if (transientVisualStrikeRadiusTicks > 0 && !Float.isNaN(transientVisualStrikeRadius)) {
            return Optional.of(transientVisualStrikeRadius);
        }
        return Optional.empty();
    }
}
