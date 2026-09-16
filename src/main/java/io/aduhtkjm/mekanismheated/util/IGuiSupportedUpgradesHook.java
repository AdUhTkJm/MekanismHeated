package io.aduhtkjm.mekanismheated.util;

import mekanism.common.tile.base.TileEntityMekanism;

public interface IGuiSupportedUpgradesHook {
    TileEntityMekanism mekanismheated$getMachine();
    void mekanismheated$setMachine(TileEntityMekanism machine);
}
