package com.github.nxkoo.orbitalrailgunneoforge.client;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import com.github.nxkoo.orbitalrailgunneoforge.client.railgun.RailgunState;
import com.github.nxkoo.orbitalrailgunneoforge.config.OrbitalConfig;
import com.github.nxkoo.orbitalrailgunneoforge.item.OrbitalRailgunItem;
import com.github.nxkoo.orbitalrailgunneoforge.network.C2S_RequestFire;
import com.github.nxkoo.orbitalrailgunneoforge.network.Network;
import com.github.nxkoo.orbitalrailgunneoforge.registry.ModSounds;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("removal")
@EventBusSubscriber(modid = ForgeOrbitalRailgunMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
 final class ClientEvents {
    private static final ResourceLocation RAILGUN_CHAIN_ID = ForgeOrbitalRailgunMod.id("shaders/post/railgun.json");
    private static final Field PASSES_FIELD = findPassesField();
    private static final Set<ResourceLocation> MODEL_VIEW_UNIFORM_PASSES = Set.of(
            ForgeOrbitalRailgunMod.id("strike"),
            ForgeOrbitalRailgunMod.id("gui"),
            ForgeOrbitalRailgunMod.id("chromatic_abjuration")
    );

    private static PostChain railgunChain;
    private static boolean chainReady;
    private static int chainWidth = -1;
    private static int chainHeight = -1;

    private static boolean attackWasDown;
    private static boolean chargingLastTick;

    // Track only real hotbar/held-item changes (not stack mutations during use)
    private static int prevSelectedSlot = -1;
    private static Item prevMainHandItem = null;

    // --- Freeze/latch state for pause ---
    private static boolean pausedLatched = false;        // are we rendering from a paused snapshot?
    private static boolean latchedStrike = false;        // was strike active when we latched?
    private static boolean latchedCharge = false;        // was charge active when we latched?
    private static Vec3 latchedTargetPos = Vec3.ZERO;    // hit/strike position at latch
    private static float latchedDistance = 0f;           // distance at latch
    private static int latchedHitKind = 0;               // RailgunState.HitKind ordinal at latch
    private static float latchedTime = 0f;               // effect seconds at latch (held steady)
    private static float lastVisibleEffectSeconds = 0f;  // live seconds (for caching before latch)

    // --- HUD toggle while charging (F1-like) ---
    private static boolean hudHiddenDuringCharge = false;
    private static boolean prevHideGuiValue = false;

    // --- Iris/Oculus shaderpack compat state ---
    private static boolean haveIris = false;
    private static boolean lastShaderpackActive = false;

    static {
        if (PASSES_FIELD != null) {
            PASSES_FIELD.setAccessible(true);
        } else {
            ForgeOrbitalRailgunMod.LOGGER.error("Failed to locate orbital railgun post chain passes field");
        }
        // Detect Iris/Oculus once at class load
        haveIris = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
        lastShaderpackActive = queryIrisShaderpackInUse();
    }

    private ClientEvents() {}

    @SubscribeEvent
    public static void onRegisterReloadListeners( RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) { return null; }
            @Override protected void apply(Void object, ResourceManager resourceManager, ProfilerFiller profiler) {
                // If a shaderpack is active, keep our chain torn down; otherwise (re)load it.
                if (isShaderpackActive()) {
                    closeChain();
                    clearPauseLatch();
                    ForgeOrbitalRailgunMod.LOGGER.info("[orbital_railgun] Shaderpack active — skipping PostChain build on reload.");
                } else {
                    ClientEvents.reloadChain(resourceManager);
                    clearPauseLatch();
                }
            }
        });
    }

    private static void clearPauseLatch() {
        pausedLatched = false;
        latchedStrike = false;
        latchedCharge = false;
        latchedTargetPos = Vec3.ZERO;
        latchedDistance = 0f;
        latchedHitKind = 0;
        lastVisibleEffectSeconds = 0f;
        latchedTime = 0f;
    }

    private static void reloadChain(ResourceManager resourceManager) {
        if (isShaderpackActive()) {
            // Hard guard: never build while a shaderpack is running.
            closeChain();
            chainReady = false;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        closeChain();
        if (minecraft.getMainRenderTarget() == null) {
            chainReady = false;
            return;
        }

        try {
            railgunChain = new PostChain(minecraft.getTextureManager(), resourceManager, minecraft.getMainRenderTarget(), RAILGUN_CHAIN_ID);
            chainReady = true;
            chainWidth = -1;
            chainHeight = -1;
            resizeChain(minecraft);
            ForgeOrbitalRailgunMod.LOGGER.info("[orbital_railgun] Built PostChain (no shaderpack active).");
        } catch (IOException exception) {
            ForgeOrbitalRailgunMod.LOGGER.error("Failed to load orbital railgun post chain", exception);
            chainReady = false;
            closeChain();
        }
    }

    private static void resizeChain(Minecraft minecraft) {
        if (railgunChain == null) return;

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) return;

        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width == chainWidth && height == chainHeight) return;

        railgunChain.resize(width, height);
        chainWidth = width;
        chainHeight = height;
    }

    @SubscribeEvent
    public static void onScreenRender( ScreenEvent.Render.Post event) {
        if (isShaderpackActive()) return; // skip any PostChain handling while shaderpack active
        if (!chainReady || railgunChain == null) return;
        resizeChain(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onRenderStage( RenderLevelStageEvent event) {
        // If a shaderpack is active, **do not** run our post chain at all.
        if (isShaderpackActive()) return;

        if (!chainReady || railgunChain == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        RailgunState state = RailgunState.getInstance();
        Level level = minecraft.level;

        boolean strikeActiveLive = state.isStrikeActive() && state.getStrikeDimension() != null && state.getStrikeDimension().equals(level.dimension());
        boolean chargeActiveLive = state.isCharging();
        boolean anyActiveLive = strikeActiveLive || chargeActiveLive;

        resizeChain(minecraft);

        boolean paused = minecraft.isPaused();
        float partial = paused ? 0f : event.getPartialTick().getGameTimeDeltaPartialTick(true);

        // ---- Latch a snapshot on the first paused frame while the effect is active ----
        if (paused && anyActiveLive && !pausedLatched) {
            pausedLatched = true;
            latchedStrike = strikeActiveLive;
            latchedCharge = chargeActiveLive;

            float effectSecondsLive = strikeActiveLive
                    ? state.getStrikeSeconds(0f)
                    : state.getChargeSeconds(0f);
            lastVisibleEffectSeconds = effectSecondsLive; // cache last visible
            latchedTime = lastVisibleEffectSeconds;

            Vec3 liveTarget = strikeActiveLive ? state.getStrikePos() : state.getHitPos();
            latchedTargetPos = liveTarget == null ? Vec3.ZERO : liveTarget;

            Vec3 cam = event.getCamera().getPosition();
            latchedDistance = (float) (latchedTargetPos == null ? 0.0 : cam.distanceTo(latchedTargetPos));

            latchedHitKind = state.getHitKind().ordinal();
        }

        // ---- If paused and latched, render from the snapshot and ignore live state ----
        boolean renderStrike, renderCharge;
        Vec3 targetPos;
        float distance;
        int hitKindOrdinal;
        float effectSeconds;

        if (paused && pausedLatched) {
            renderStrike = latchedStrike;
            renderCharge = latchedCharge;
            targetPos = latchedTargetPos;
            distance = latchedDistance;
            hitKindOrdinal = latchedHitKind;
            effectSeconds = latchedTime; // freeze time
        } else {
            // Not paused (or no latch) -> use live state
            if (!anyActiveLive) {
                // nothing to draw
                // also clear any stale latch if we weren't paused
                if (!paused) clearPauseLatch();
                return;
            }

            renderStrike = strikeActiveLive;
            renderCharge = chargeActiveLive;

            effectSeconds = renderStrike
                    ? state.getStrikeSeconds(partial)
                    : state.getChargeSeconds(partial);

            if (!paused) lastVisibleEffectSeconds = effectSeconds;

            targetPos = renderStrike ? state.getStrikePos() : state.getHitPos();
            if (targetPos == null) targetPos = Vec3.ZERO;

            Vec3 cam = event.getCamera().getPosition();
            distance = (float) cam.distanceTo(targetPos);

            hitKindOrdinal = state.getHitKind().ordinal();

            // if we've just unpaused, drop the latch so we resume live immediately
            if (!paused && pausedLatched) clearPauseLatch();
        }

        // ---- Upload uniforms and render ----
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        Matrix4f inverseProjection = new Matrix4f(projection).invert();
        Matrix4f modelView = new Matrix4f(event.getPoseStack().last().pose());
        Vec3 cameraPos = event.getCamera().getPosition();

        List<PostPass> passes = getPasses();
        if (passes.isEmpty()) {
            if (!paused) clearPauseLatch();
            return;
        }

        applyUniforms(
                passes,
                modelView, projection, inverseProjection,
                cameraPos, targetPos,
                distance, effectSeconds,
                /* isBlockHit */ (hitKindOrdinal != RailgunState.HitKind.NONE.ordinal()) ? 1.0F : 0.0F,
                renderStrike,
                state,
                hitKindOrdinal
        );

        OrbitalShaderUniforms.applyColorUniforms(passes);

        // Process the post chain
        railgunChain.process(partial);

        // Ensure subsequent passes (like hands) render to the correct framebuffer.
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
//        if (event.phase != TickEvent.Phase.END) return;

        // Watch for shaderpack state flips at runtime and rebuild/teardown accordingly.
        if (pollIrisStateChanged()) {
            if (isShaderpackActive()) {
                // Shaderpack just turned ON -> tear down our chain
                closeChain();
                ForgeOrbitalRailgunMod.LOGGER.info("[orbital_railgun] Shaderpack enabled — tearing down PostChain.");
            } else {
                // Shaderpack just turned OFF -> (re)build our chain
                ResourceManager rm = Minecraft.getInstance().getResourceManager();
                reloadChain(rm);
                ForgeOrbitalRailgunMod.LOGGER.info("[orbital_railgun] Shaderpack disabled — rebuilding PostChain.");
            }
            clearPauseLatch();
        }

        Minecraft minecraft = Minecraft.getInstance();
        RailgunState state = RailgunState.getInstance();
        state.tick(minecraft);

        // Hide/show HUD exactly while charging
        updateHudHiddenForCharge(minecraft, state);

        LocalPlayer player = minecraft.player;

        // --- Equip cue ONLY on true held-item change (slot or item), not during scoping ---
        if (player != null) {
            int currentSlot = player.getInventory().selected;
            Item currentItem = player.getMainHandItem().getItem();

            if (prevSelectedSlot == -1) {
                prevSelectedSlot = currentSlot;
                prevMainHandItem = currentItem;
            } else {
                boolean slotChanged = currentSlot != prevSelectedSlot;
                boolean itemChanged = currentItem != prevMainHandItem;

                if ((slotChanged || itemChanged) && currentItem instanceof OrbitalRailgunItem) {
                    playLocalPlayerSound(ModSounds.EQUIP.get(), 1.0F, 1.0F);
                }

                prevSelectedSlot = currentSlot;
                prevMainHandItem = currentItem;
            }
        }

        // scope_on on charging start (rising edge)
        handleChargeAudio(state);

        boolean attackDown = player != null && minecraft.options != null && minecraft.options.keyAttack.isDown();
        if (attackDown && !attackWasDown && state.canRequestFire(player)) {
            attemptFire(minecraft, state, player);
        }
        attackWasDown = attackDown;
    }

    // Safety: restore HUD on logout/disconnect
    @SubscribeEvent
    public static void onClientLogout( ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        if (hudHiddenDuringCharge) {
            mc.options.hideGui = prevHideGuiValue;
            hudHiddenDuringCharge = false;
        }
    }

    private static void attemptFire(Minecraft minecraft, RailgunState state, LocalPlayer player) {
        if (minecraft.gameMode == null) return;

        HitResult hitResult = state.getCurrentHit();
        if (!(hitResult instanceof BlockHitResult blockHitResult)) return;

        BlockPos target = blockHitResult.getBlockPos();
        OrbitalRailgunItem item = state.getActiveRailgunItem();
        if (item == null) return;

        item.applyCooldown(player);
        minecraft.gameMode.releaseUsingItem(player);
        state.markFired();
        PacketDistributor.sendToServer(new C2S_RequestFire(target));
    }

    @SubscribeEvent
    public static void onComputeFov( ViewportEvent.ComputeFov event) {
        if (RailgunState.getInstance().isCharging()) {
            double baseFov = Minecraft.getInstance().options.fov().get();
            event.setFOV(baseFov);
        }
    }

    private static void applyUniforms(
            List<PostPass> passes,
            Matrix4f modelView, Matrix4f projection, Matrix4f inverseProjection,
            Vec3 cameraPos, Vec3 targetPos,
            float distance, float timeSeconds,
            float isBlockHit, boolean strikeActive,
            RailgunState state, int hitKindOrdinal
    ) {
        if (passes.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget renderTarget = minecraft.getMainRenderTarget();
        if (renderTarget == null) return;

        float width = renderTarget.width > 0 ? renderTarget.width : renderTarget.viewWidth;
        float height = renderTarget.height > 0 ? renderTarget.height : renderTarget.viewHeight;

        float strikeRadius = state.getTransientVisualStrikeRadius()
                .orElse((float) (OrbitalConfig.DESTRUCTION_DIAMETER.get() * 0.5D));

        for (PostPass pass : passes) {
            EffectInstance effect = pass.getEffect();
            if (effect == null) continue;

            ResourceLocation passName = getPassName(pass);
            boolean expectsModelViewMatrix = passName != null && MODEL_VIEW_UNIFORM_PASSES.contains(passName);

            setMatrix(effect, "ProjMat", projection);
            if (expectsModelViewMatrix) setMatrix(effect, "ModelViewMat", modelView);
            setMatrix(effect, "InverseTransformMatrix", inverseProjection);
            setVec3(effect, "CameraPosition", cameraPos);
            setVec3(effect, "BlockPosition", targetPos);
            setVec3(effect, "HitPos", targetPos);
            setVec2(effect, "OutSize", width, height);

            // Authoritative time (frozen during pause via latch)
            setFloat(effect, "iTime", timeSeconds);
            // Mirror to prevent any pass from advancing independently
            setFloat(effect, "Time", timeSeconds);
            setFloat(effect, "GameTime", timeSeconds);
            setFloat(effect, "iGlobalTime", timeSeconds);
            setFloat(effect, "FrameTimeCounter", timeSeconds);

            setFloat(effect, "Distance", distance);
            setFloat(effect, "IsBlockHit", isBlockHit);
            setFloat(effect, "StrikeActive", strikeActive ? 1.0F : 0.0F);
            setFloat(effect, "SelectionActive", state.isCharging() ? 1.0F : 0.0F);
            setInt(effect, "HitKind", hitKindOrdinal);
            setFloat(effect, "StrikeRadius", strikeRadius);
        }
    }

    private static ResourceLocation getPassName(PostPass pass) {
        String name = pass.getName();
        return name != null ? ResourceLocation.tryParse(name) : null;
    }

    private static List<PostPass> getPasses() {
        if (railgunChain == null) return Collections.emptyList();
        if (PASSES_FIELD == null) return Collections.emptyList();
        try {
            Object value = PASSES_FIELD.get(railgunChain);
            if (value instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<PostPass> passes = (List<PostPass>) list;
                return passes;
            }
            ForgeOrbitalRailgunMod.LOGGER.error(
                    "Orbital railgun post chain passes had unexpected type: {}",
                    value == null ? "null" : value.getClass().getName()
            );
        } catch (IllegalAccessException exception) {
            ForgeOrbitalRailgunMod.LOGGER.error("Failed to access orbital railgun post chain passes", exception);
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private static Field findPassesField() {
        try {
            return ObfuscationReflectionHelper.findField(PostChain.class, "passes");
        } catch (ObfuscationReflectionHelper.UnableToFindFieldException ignored) {
            try {
                return ObfuscationReflectionHelper.findField(PostChain.class, "f_110009_");
            } catch (ObfuscationReflectionHelper.UnableToFindFieldException exception) {
                ForgeOrbitalRailgunMod.LOGGER.error(
                        "Unable to find passes field on PostChain using Mojmap or SRG identifiers",
                        exception
                );
                return null;
            }
        }
    }

    private static void setMatrix(EffectInstance effect, String name, Matrix4f matrix) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set(matrix);
        }
    }

    private static void setVec3(EffectInstance effect, String name, Vec3 vec) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set((float) vec.x, (float) vec.y, (float) vec.z);
        }
    }

    private static void setVec2(EffectInstance effect, String name, float x, float y) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y);
        }
    }

    private static void setFloat(EffectInstance effect, String name, float value) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setInt(EffectInstance effect, String name, int value) {
        Uniform uniform = effect.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void closeChain() {
        if (railgunChain != null) {
            try {
                railgunChain.close();
            } catch (Exception ignored) {
            }
            railgunChain = null;
        }
        chainReady = false;
        chainWidth = -1;
        chainHeight = -1;
    }

    private static void handleChargeAudio(RailgunState state) {
        boolean charging = state.isCharging();
        boolean wasCharging = chargingLastTick;

        // only play when charging flips false -> true
        if (charging && !wasCharging) {
            playLocalPlayerSound(ModSounds.SCOPE_ON.get(), 1.0F, 1.0F);
        }

        chargingLastTick = charging;
    }

    private static void playLocalPlayerSound(SoundEvent sound, float volume, float pitch) {
        if (sound == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        player.playSound(sound, volume, pitch);
    }

    // --- HUD toggle helper ---
    private static void updateHudHiddenForCharge(Minecraft mc, RailgunState state) {
        boolean charging = state.isCharging();

        if (charging && !hudHiddenDuringCharge) {
            // Latch previous F1 state and hide HUD
            prevHideGuiValue = mc.options.hideGui;
            mc.options.hideGui = true;
            hudHiddenDuringCharge = true;
        } else if (!charging && hudHiddenDuringCharge) {
            // Restore exactly what the player had before charging
            mc.options.hideGui = prevHideGuiValue;
            hudHiddenDuringCharge = false;
        }

        // Safety: if world vanished (dimension change/logout) restore
        if (mc.level == null && hudHiddenDuringCharge) {
            mc.options.hideGui = prevHideGuiValue;
            hudHiddenDuringCharge = false;
        }
    }

    // ===== Iris/Oculus compat (reflection) =====

    /** True if Oculus/Iris is present AND a shaderpack is currently in use. */
    private static boolean isShaderpackActive() {
        if (!haveIris) return false;
        return lastShaderpackActive;
    }

    /** Poll Iris and update internal state; return true if active/inactive changed since last check. */
    private static boolean pollIrisStateChanged() {
        if (!haveIris) return false;
        boolean now = queryIrisShaderpackInUse();
        boolean changed = (now != lastShaderpackActive);
        lastShaderpackActive = now;
        return changed;
    }

    /** Reflection call to Iris API: net.irisshaders.iris.api.v0.IrisApi#isShaderPackInUse() */
    private static boolean queryIrisShaderpackInUse() {
        if (!haveIris) return false;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isShaderPackInUse").invoke(api);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            // If the API isn't available or throws, fail safe (treat as not active).
            return false;
        }
    }
}
