package net.tysontheember.orbitalrailgun.config;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class OrbitalConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec.DoubleValue RANGE;
    public static final ForgeConfigSpec.DoubleValue MAX_BREAK_HARDNESS;
    public static final ForgeConfigSpec.IntValue COOLDOWN;
    public static final ForgeConfigSpec.IntValue STRIKE_DAMAGE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue SUCK_ENTITIES;
    public static final ForgeConfigSpec.DoubleValue DESTRUCTION_DIAMETER;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MIN_DESTROY_Y;
    public static final ForgeConfigSpec.BooleanValue DROP_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG;
    public static final ForgeConfigSpec.BooleanValue RESPECT_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue RESPECT_OPAC_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_ENTITY_DAMAGE_IN_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BLOCK_BREAK_IN_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_EXPLOSIONS_IN_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue OPS_BYPASS_CLAIMS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("orbitalStrike");
        RANGE = builder
                .comment("Maximum selection range of the orbital railgun")
                .defineInRange("range", 256.0D, 0.0D, 1000000);
        MAX_BREAK_HARDNESS = builder
            .comment("Maximum block hardness the orbital strike can destroy(set to -1 to destroy all blocks)")
            .defineInRange("maxBreakHardness", 50.0D, -1.0D, Double.MAX_VALUE);
        COOLDOWN = builder
            .comment("Cooldown of the Railgun in ticks")
            .defineInRange("cooldown", 2400, 1800, Integer.MAX_VALUE);
        STRIKE_DAMAGE = builder
            .comment("Damage of the Orbital Strike")
            .defineInRange("strikeDamage", 80, 0, Integer.MAX_VALUE);
        BLACKLISTED_BLOCKS = builder
            .comment("Blocks that won't be destroyed by the orbital strike.")
            .defineList("blacklistedBlocks", List.of(
                "minecraft:bedrock",
                "minecraft:end_portal_frame",
                "minecraft:end_portal",
                "minecraft:barrier"
            ), o -> o instanceof String);
        SUCK_ENTITIES = builder
                .comment("Whether entities are pulled towards the strike before detonation.")
                .define("suckEntities", true);
        DESTRUCTION_DIAMETER = builder
                .comment("Diameter in blocks used for destruction/physics (server-authoritative).")
                .defineInRange("destructionDiameter", 16.0D, 1.0D, 256.0D);
        BLOCKS_PER_TICK = builder
                .comment("Max blocks removed per server tick for an active strike (performance safety).")
                .defineInRange("blocksPerTick", 2000, 1, 200000);
        MIN_DESTROY_Y = builder
                .comment(
                        "Strike removes blocks from world max build height down to this Y (clamped to world min).",
                        "Default -64 (Overworld bedrock shelf on modern versions)."
                )
                .defineInRange("minDestroyY", -64, -2048, 4096);
        DROP_BLOCKS = builder
                .comment("If true, destroyed blocks drop items (heavier). If false, they are vaporized.")
                .define("dropBlocks", false);
        DEBUG = builder
                .comment("Toggle Debug mode")
                .define("debugMode", false);


        builder.pop();

        builder.push("protection");
        RESPECT_CLAIMS = builder
                .comment("If true, the railgun respects FTB Chunks claims and cancels actions in protected areas.")
                .define("respectClaims", true);
        RESPECT_OPAC_CLAIMS = builder
                .comment("Respect Open Parties & Claims protection (if the mod is installed).")
                .define("respectOPACClaims", true);
        ALLOW_ENTITY_DAMAGE_IN_CLAIMS = builder
                .comment("If true, the railgun can damage entities inside claims only if the shooter has permission.")
                .define("allowEntityDamageInClaims", false);
        ALLOW_BLOCK_BREAK_IN_CLAIMS = builder
                .comment("If true, the railgun can break blocks inside claims only if the shooter has permission.")
                .define("allowBlockBreakInClaims", false);
        ALLOW_EXPLOSIONS_IN_CLAIMS = builder
                .comment("If true, the railgun explosions are allowed inside claims only if the shooter has permission.")
                .define("allowExplosionsInClaims", false);
        OPS_BYPASS_CLAIMS = builder
                .comment("If true, operators (permission level >= 2) bypass claim checks.")
                .define("opsBypassClaims", true);
        builder.pop();

        COMMON_SPEC = builder.build();

        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();

    }

    private OrbitalConfig() {}

    public static boolean isBlockBlacklistedNormalized(String blockId) {
        if (blockId == null) return false;
        String target = blockId.trim().toLowerCase();
        for (String s : BLACKLISTED_BLOCKS.get()) {
            if (s != null && target.equals(s.trim().toLowerCase())) return true;
        }
        return false;
    }

    public static final class Client {
        public final ForgeConfigSpec.ConfigValue<String> beamColorHex;
        public final ForgeConfigSpec.DoubleValue beamAlpha;

        public final ForgeConfigSpec.ConfigValue<String> markerInnerHex;
        public final ForgeConfigSpec.DoubleValue markerInnerAlpha;

        public final ForgeConfigSpec.ConfigValue<String> markerOuterHex;
        public final ForgeConfigSpec.DoubleValue markerOuterAlpha;

        Client(ForgeConfigSpec.Builder builder) {
            builder.push("client");

            builder.comment("Color overrides for the orbital railgun beam and targeting marker.");
            builder.push("shaderColors");

            beamColorHex = builder
                    .comment("Hex color (#RRGGBB or #RRGGBBAA) applied to the beam shader tint.")
                    .define("beamColor", "#FFFFFF");
            beamAlpha = builder
                    .comment("Alpha multiplier applied to the beam shader.")
                    .defineInRange("beamAlpha", 1.0D, 0.0D, 1.0D);

            markerInnerHex = builder
                    .comment("Hex color (#RRGGBB or #RRGGBBAA) for the targeting marker inner elements.")
                    .define("markerInnerColor", "#FFFFFF");
            markerInnerAlpha = builder
                    .comment("Alpha multiplier for the targeting marker inner elements.")
                    .defineInRange("markerInnerAlpha", 0.95D, 0.0D, 1.0D);

            markerOuterHex = builder
                    .comment("Hex color (#RRGGBB or #RRGGBBAA) for the targeting marker outer elements.")
                    .define("markerOuterColor", "#FFFFFF");
            markerOuterAlpha = builder
                    .comment("Alpha multiplier for the targeting marker outer elements.")
                    .defineInRange("markerOuterAlpha", 0.85D, 0.0D, 1.0D);

            builder.pop();
            builder.pop();
        }
    }
}
