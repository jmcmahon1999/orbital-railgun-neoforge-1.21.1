package com.github.nxkoo.orbitalrailgunneoforge.command;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import com.github.nxkoo.orbitalrailgunneoforge.strike.StrikeExecutor;
import com.github.nxkoo.orbitalrailgunneoforge.util.OrbitalRailgunStrikeManager;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * Server command that lets operators trigger orbital strikes at arbitrary world coordinates.
 */
public final class OrbitalStrikeCommand {
    private static final Logger LOGGER = ForgeOrbitalRailgunMod.LOGGER;
    private static final float DEFAULT_POWER = 1.0F;
    private static final int DEFAULT_RADIUS = 3;
    private static final float MIN_POWER = 0.1F;
    private static final float MAX_POWER = 20.0F;
    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 64;

    private OrbitalStrikeCommand() {
    }

    /**
     * Builds the command tree for {@code /orbitalstrike}.
     *
     * @return literal argument builder ready to be registered with the dispatcher.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("orbitalstrike")
                .requires(stack -> stack.hasPermission(2))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(ctx -> execute(ctx, DEFAULT_POWER, DEFAULT_RADIUS))
                                .then(Commands.argument("power", FloatArgumentType.floatArg(MIN_POWER, MAX_POWER))
                                        .executes(ctx -> execute(ctx,
                                                FloatArgumentType.getFloat(ctx, "power"), DEFAULT_RADIUS))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(MIN_RADIUS, MAX_RADIUS))
                                                .executes(ctx -> execute(ctx,
                                                        FloatArgumentType.getFloat(ctx, "power"),
                                                        IntegerArgumentType.getInteger(ctx, "radius")))))));
    }

    /**
     * Performs validation and queues the requested strike.
     *
     * @param context command context that triggered the strike.
     * @param power   damage multiplier requested by the caller.
     * @param radius  radius requested by the caller.
     * @return brigadier result indicating success or failure.
     * @throws CommandSyntaxException when the command source cannot be resolved.
     */
    private static int execute(CommandContext<CommandSourceStack> context, float power, int radius)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");
        ServerLevel level = source.getLevel();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos target = new BlockPos(x, surfaceY, z);

        ServerPlayer player = source.getEntity() instanceof ServerPlayer sp ? sp : null;

        OrbitalRailgunStrikeManager.StrikeRequestResult result =
                StrikeExecutor.queueStrike(level, target, power, radius, player);

        String logMessage = String.format("/orbitalstrike called by %s at %d,%d (y=%d) power=%.2f radius=%d",
                source.getTextName(), x, z, surfaceY, power, radius);
        LOGGER.info(logMessage);

        if (!result.success()) {
            Component error = result.message();
            if (error != null) {
                source.sendFailure(error);
            } else {
                source.sendFailure(Component.literal("Orbital strike failed."));
            }
            return 0;
        }

        Component success = Component.literal(String.format(
                "Scheduled orbital strike at %d, %d, %d (power=%.2f radius=%d)",
                target.getX(), target.getY(), target.getZ(), power, radius));
        source.sendSuccess(() -> success, true);
        return 1;
    }
}
