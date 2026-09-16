package io.aduhtkjm.mekanismheated.content.upgrade;

import java.util.Map;

/**
 * Implemented (via mixin) by Mekanism's upgrade component so this mod's heat upgrades can be stored next to Mekanism's
 * own upgrades, in the same upgrade slot and with the same install progress.
 */
public interface IHeatedUpgradeComponent {

    /**
     * Gets the live map of installed heat upgrades to their amounts. Only meant to be read; change the installed
     * upgrades through the add/remove methods so the machines get told to re-apply the multipliers.
     */
    Map<HeatedUpgrade, Integer> mekanismheated$getHeatedUpgrades();

    /**
     * Gets how many of the given heat upgrade are installed.
     */
    int mekanismheated$getHeatedUpgrades(HeatedUpgrade upgrade);

    /**
     * Installs up to the given number of the given heat upgrade, respecting {@link HeatedUpgrade#MAX}.
     *
     * @param upgrade      Heat upgrade to install.
     * @param maxAvailable Maximum number of upgrades available to install.
     *
     * @return The number of upgrades that were installed.
     *
     * @apiNote Call from the server.
     */
    int mekanismheated$addHeatedUpgrades(HeatedUpgrade upgrade, int maxAvailable);

    /**
     * Removes one, or all, of the given installed heat upgrade, moving them into the component's upgrade output slot.
     *
     * @param upgrade   Heat upgrade to remove.
     * @param removeAll Whether to remove every installed upgrade of this type instead of just one.
     *
     * @apiNote Call from the server.
     */
    void mekanismheated$removeHeatedUpgrades(HeatedUpgrade upgrade, boolean removeAll);
}
