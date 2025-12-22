package com.github.nxkoo.orbitalrailgunneoforge.command;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers mod commands with the Forge dispatcher when the server loads them.
 */
@EventBusSubscriber(modid = ForgeOrbitalRailgunMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ModCommands {
    private ModCommands() {
    }

    /**
     * Handles the {@link RegisterCommandsEvent} and installs the orbital strike command.
     *
     * @param event registration event dispatched by Forge during server startup.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(OrbitalStrikeCommand.register());
    }
}
