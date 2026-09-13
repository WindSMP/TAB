package me.neznamy.tab.shared.features.nametags;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.cpu.TimedCaughtTask;
import me.neznamy.tab.shared.cpu.ThreadExecutor;
import me.neznamy.tab.shared.data.Server;
import me.neznamy.tab.shared.data.World;
import me.neznamy.tab.shared.features.types.*;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

import me.neznamy.tab.shared.chat.component.TabComponent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sub-feature for NameTags for managing prefix/suffix.
 */
@RequiredArgsConstructor
public class PrefixSuffixManager extends RefreshableFeature implements GroupListener, WorldSwitchListener,
        ServerSwitchListener, CustomThreaded {

    /** Short debounce window for multiple API changes affecting the same player. */
    private static final int UPDATE_COALESCE_MILLIS = 50;

    /** Parent feature */
    private final NameTag feature;

    /** UUIDs only; never retain platform Player objects in a global/pending cache. */
    private final Set<UUID> queuedUpdates = ConcurrentHashMap.newKeySet();

    @Override
    @NotNull
    public String getRefreshDisplayName() {
        return "Updating prefix/suffix";
    }

    @Override
    public void refresh(@NotNull TabPlayer refreshed, boolean force) {
        if (force) {
            updateProperties(refreshed);
            updatePrefixSuffix(refreshed);
        } else {
            boolean prefix = refreshed.teamData.prefix.update();
            boolean suffix = refreshed.teamData.suffix.update();
            if (prefix || suffix) updatePrefixSuffix(refreshed);
        }
    }

    @Override
    @NotNull
    public String getFeatureName() {
        return feature.getFeatureName();
    }

    @Override
    public void onGroupChange(@NotNull TabPlayer player) {
        if (updateProperties(player)) updatePrefixSuffix(player);
    }

    @Override
    public void onServerChange(@NonNull TabPlayer player, @NotNull Server from, @NotNull Server to) {
        if (updateProperties(player)) updatePrefixSuffix(player);
    }

    @Override
    public void onWorldChange(@NotNull TabPlayer player, @NotNull World from, @NotNull World to) {
        if (updateProperties(player)) updatePrefixSuffix(player);
    }

    /**
     * Loads all properties from config and returns {@code true} if at least
     * one of them either wasn't loaded or changed value, {@code false} otherwise.
     *
     * @param   p
     *          Player to update properties of
     * @return  {@code true} if at least one property changed, {@code false} if not
     */
    private boolean updateProperties(@NotNull TabPlayer p) {
        boolean changed = p.updatePropertyFromConfig(p.teamData.prefix, "");
        if (p.updatePropertyFromConfig(p.teamData.suffix, "")) changed = true;
        return changed;
    }

    /**
     * Updates team prefix and suffix of given player.
     *
     * @param   player
     *          Player to update prefix/suffix of
     */
    public void updatePrefixSuffix(@NonNull TabPlayer player) {
        for (TabPlayer viewer : feature.getOnlinePlayers().getPlayers()) {
            if (viewer.teamData.hasTeamRegistered(player)) {
                String prefix = player.teamData.prefix.getFormat(viewer);
                TabComponent prefixComponent = feature.getPrefixCache().get(prefix);
                viewer.getScoreboard().updateTeam(
                        player.teamData.teamName,
                        prefixComponent,
                        feature.getSuffixCache().get(player.teamData.suffix.getFormat(viewer)),
                        feature.getLastColorCache().get(prefix).getLastStyle().toEnumChatFormat()
                );
            }
        }
        feature.getProxyHandler().sendProxyMessage(player);
    }

    /**
     * Coalesces separate prefix/suffix API calls made in a short burst into one
     * viewer fan-out. Placeholder refreshes already update both values together
     * and continue to call {@link #updatePrefixSuffix(TabPlayer)} directly.
     */
    public void queuePrefixSuffixUpdate(@NotNull TabPlayer player) {
        UUID playerId = player.getUniqueId();
        if (!queuedUpdates.add(playerId)) return;
        feature.getCustomThread().executeLater(new TimedCaughtTask(TAB.getInstance().getCpu(), () -> {
            queuedUpdates.remove(playerId);
            TabPlayer current = TAB.getInstance().getPlayer(playerId);
            if (current != null) updatePrefixSuffix(current);
        }, getFeatureName(), "Coalesced prefix/suffix update"), UPDATE_COALESCE_MILLIS);
    }

    /**
     * Loads properties from config.
     *
     * @param   player
     *          Player to load properties for
     */
    public void loadProperties(@NotNull TabPlayer player) {
        player.teamData.prefix = player.loadPropertyFromConfig(this, "tagprefix", "");
        player.teamData.suffix = player.loadPropertyFromConfig(this, "tagsuffix", "");
    }

    @Override
    @NotNull
    public ThreadExecutor getCustomThread() {
        return feature.getCustomThread();
    }
}
