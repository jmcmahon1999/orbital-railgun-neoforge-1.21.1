package com.github.nxkoo.orbitalrailgunneoforge.client.item;

import com.github.nxkoo.orbitalrailgunneoforge.ForgeOrbitalRailgunMod;
import com.github.nxkoo.orbitalrailgunneoforge.item.OrbitalRailgunItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class OrbitalRailgunRenderer extends GeoItemRenderer<OrbitalRailgunItem> {
    public OrbitalRailgunRenderer() {
        super(new DefaultedItemGeoModel<>(ForgeOrbitalRailgunMod.id("orbital_railgun")));
    }
}
