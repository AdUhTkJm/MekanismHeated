package io.aduhtkjm.mekanismheated.content.upgrade;

/**
 * Implemented (via mixin) by Mekanism's {@code BasicInventorySlot} so the upgrade slots of a machine can be widened to
 * also accept this mod's heat upgrades. Only slots whose machine implements {@link IHeatedUpgradeTile} are widened.
 */
public interface IHeatedUpgradeSlot {

    /**
     * Widens this slot's item validator and insert predicate, so this mod's heat upgrades can be placed in it. Used for
     * the upgrade input slot, which is where the upgrades are installed from.
     */
    void mekanismheated$allowHeatedUpgradeInstall();

    /**
     * Widens this slot's item validator only, so this mod's heat upgrades count as valid contents while what is allowed
     * to insert them stays the same. Used for the upgrade output slot, which is where uninstalled upgrades are put: the
     * machine itself is what inserts them there, so only the "is this a valid item at all" check needs to know about
     * them.
     */
    void mekanismheated$allowHeatedUpgradeItems();
}
