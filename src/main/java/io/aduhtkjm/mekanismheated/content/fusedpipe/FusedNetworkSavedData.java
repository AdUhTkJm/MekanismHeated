package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.aduhtkjm.mekanismheated.Mod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * World-level saved data that stores the buffer state of every fused-pipe network, keyed by
 * network UUID. Each network serialises its energy, fluid, chemical, heat and item buffers here
 * instead of distributing shares to individual tile entities on every background save.
 * <p>
 * Attached to the Overworld so the data survives dimension unloads and is always accessible.
 */
@NonnullDefault
public class FusedNetworkSavedData extends SavedData {

    public static final String DATA_NAME = "mekanismheated_fused_networks";

    private final Map<UUID, CompoundTag> networks = new HashMap<>();

    public static FusedNetworkSavedData create() {
        return new FusedNetworkSavedData();
    }

    public static FusedNetworkSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        FusedNetworkSavedData data = create();
        for (String key : tag.getAllKeys()) {
            UUID uuid = UUID.fromString(key);
            data.networks.put(uuid, tag.getCompound(key));
        }
        Mod.LOGGER.debug("data loaded: {}", data.networks);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (Map.Entry<UUID, CompoundTag> entry : networks.entrySet()) {
            tag.put(entry.getKey().toString(), entry.getValue());
        }
        Mod.LOGGER.debug("data saved: {}", tag);
        return tag;
    }

    /**
     * Stores the serialised buffer state of a network. Overwrites any previous entry for the same UUID.
     */
    public void putNetwork(UUID uuid, CompoundTag data) {
        networks.put(uuid, data);
        setDirty();
    }

    /**
     * Returns and removes the serialised buffer state for the given network UUID,
     * or {@code null} if no entry exists.
     */
    @Nullable
    public CompoundTag consumeNetwork(UUID uuid) {
        CompoundTag tag = networks.remove(uuid);
        if (tag != null) {
            setDirty();
        }
        return tag;
    }

    /**
     * Removes the serialised buffer state for the given network UUID.
     */
    public void removeNetwork(UUID uuid) {
        if (networks.remove(uuid) != null) {
            setDirty();
        }
    }

    /**
     * Returns {@code true} if a serialised entry exists for the given UUID.
     */
    public boolean hasNetwork(UUID uuid) {
        return networks.containsKey(uuid);
    }

    /**
     * Convenience: obtains or creates the {@link FusedNetworkSavedData} attached to the Overworld.
     */
    public static FusedNetworkSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
              new Factory<>(FusedNetworkSavedData::create, FusedNetworkSavedData::load),
              DATA_NAME
        );
    }
}
