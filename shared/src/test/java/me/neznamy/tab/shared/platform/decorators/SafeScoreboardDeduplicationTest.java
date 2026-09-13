package me.neznamy.tab.shared.platform.decorators;

import me.neznamy.tab.shared.chat.EnumChatFormat;
import me.neznamy.tab.shared.chat.component.TabComponent;
import me.neznamy.tab.shared.platform.Scoreboard;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeScoreboardDeduplicationTest {

    @Test
    void unchangedPrefixOnlySendsInitialTeamPacket() {
        CountingScoreboard scoreboard = new CountingScoreboard();
        TabComponent prefix = TabComponent.legacyText("§cAdmin ");
        TabComponent suffix = TabComponent.empty();

        scoreboard.registerTeam("adminA", prefix, suffix, Scoreboard.NameVisibility.ALWAYS,
                Scoreboard.CollisionRule.ALWAYS, Collections.singleton("Alice"), 0, EnumChatFormat.RED);
        scoreboard.updateTeam("adminA", prefix, suffix, EnumChatFormat.RED);
        scoreboard.updateTeam("adminA", prefix, suffix, EnumChatFormat.RED);

        assertEquals(1, scoreboard.teamPackets);
    }

    @Test
    void equivalentRecreatedComponentsAreSuppressed() {
        CountingScoreboard scoreboard = new CountingScoreboard();
        scoreboard.registerTeam("adminA", TabComponent.legacyText("§cAdmin "), TabComponent.empty(),
                Scoreboard.NameVisibility.ALWAYS, Scoreboard.CollisionRule.ALWAYS,
                Collections.singleton("Alice"), 0, EnumChatFormat.RED);

        scoreboard.updateTeam("adminA", TabComponent.legacyText("§cAdmin "),
                TabComponent.legacyText(""), EnumChatFormat.RED);

        assertEquals(1, scoreboard.teamPackets);
    }

    @Test
    void oneRealChangeSendsExactlyOneUpdate() {
        CountingScoreboard scoreboard = new CountingScoreboard();
        TabComponent suffix = TabComponent.empty();
        scoreboard.registerTeam("adminA", TabComponent.legacyText("§cAdmin "), suffix,
                Scoreboard.NameVisibility.ALWAYS, Scoreboard.CollisionRule.ALWAYS,
                Collections.singleton("Alice"), 0, EnumChatFormat.RED);

        scoreboard.updateTeam("adminA", TabComponent.legacyText("§4Owner "), suffix, EnumChatFormat.DARK_RED);
        scoreboard.updateTeam("adminA", TabComponent.legacyText("§4Owner "), suffix, EnumChatFormat.DARK_RED);

        assertEquals(2, scoreboard.teamPackets);
    }

    @Test
    void twelveHundredStaticTargetsDoNotGrowWithRefreshCycles() {
        CountingScoreboard scoreboard = new CountingScoreboard();
        TabComponent prefix = TabComponent.legacyText("§7");
        TabComponent suffix = TabComponent.empty();
        for (int i = 0; i < 1200; i++) {
            scoreboard.registerTeam("t" + i, prefix, suffix, Scoreboard.NameVisibility.ALWAYS,
                    Scoreboard.CollisionRule.ALWAYS, Collections.singleton("p" + i), 0, EnumChatFormat.GRAY);
        }
        for (int cycle = 0; cycle < 10; cycle++) {
            for (int i = 0; i < 1200; i++) {
                scoreboard.updateTeam("t" + i, prefix, suffix, EnumChatFormat.GRAY);
            }
        }

        assertEquals(1200, scoreboard.teamPackets);
    }

    private static final class CountingScoreboard extends SafeScoreboard<TabPlayer> {

        private int teamPackets;

        private CountingScoreboard() {
            // Player is only needed for invalid-operation error messages, which
            // these state-transition tests never trigger.
            super(null);
        }

        @Override public void registerObjective(Objective objective) { }
        @Override public void setDisplaySlot(Objective objective) { }
        @Override public void unregisterObjective(Objective objective) { }
        @Override public void updateObjective(Objective objective) { }
        @Override public void setScore(Score score) { }
        @Override public void removeScore(Score score) { }
        @Override public Object createTeam(String name) { return new Object(); }
        @Override public void registerTeam(Team team) { teamPackets++; }
        @Override public void unregisterTeam(Team team) { teamPackets++; }
        @Override public void updateTeam(Team team) { teamPackets++; }
    }
}
