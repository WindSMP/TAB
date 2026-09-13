# TAB production audit and rollout

This directory is an opt-in baseline. It does not replace the live files in
`plugins/TAB/`. Merge it into the installation after checking group names, proxy
mode and the two anti-override switches.

## Expensive features and placeholders found in the shipped configuration

| Location | Dynamic inputs found | Cost/risk | Baseline action |
|---|---|---|---|
| Header/footer | `%animation:Welcome%`, `%player%`, `%online%`, `%staffonline%`, `%animation:time%`, `%ping%`, `%memory-used%`, `%memory-max%`, `%animation:web%` | Rebuilds and serialises components per player; animations invalidate as fast as 200–400 ms | Remove all animations, ping and memory; keep only `%online%` at 3000 ms |
| Tablist objective | `%ping%` | Player-info/objective updates for every ping change | Disable |
| Scoreboard definitions | `%date%`, `%animation:MyAnimation1%`, `%online%`, `%worldonline%`, `%staffonline%`, `%group%`, `%ping%`, `%world%` | Many independent line/team updates; 100 ms animation dominates | Disable and remove definitions from the production file |
| Belowname | `%health%` | Entity/objective fan-out on health changes | Keep disabled |
| Bossbar | `%animation:barcolors%` | Periodic bossbar update | Keep disabled |
| Layout | `%player%`, `%group%`, `%world%`, `%time%` | Fake entries and periodic slot updates | Keep disabled |
| Team prefix/suffix | `%luckperms-prefix%`, `%luckperms-suffix%` in `_DEFAULT_` | Permission-plugin lookup can invalidate teams for every viewer | Replace with static per-group values |
| Sorting | `%group%` through `GROUPS`; `%player%` through `PLACEHOLDER_A_TO_Z` | Group refresh is acceptable at 10 s; the second rule is redundant because team names are made unique already | Keep only `GROUPS` |
| Conditional placeholder | `%player%`, `%essentials_nickname%` | Nick comparison and nested property invalidation | Remove unless nickname display is required |
| Configured refresh overrides | `%server_uptime%`, `%server_tps_1_colored%`, `%server_unique_joins%`, `%player_health%`, `%player_ping%`, `%vault_prefix%`, `%rel_factionsuuid_relation_color%` | 200–1000 ms intervals; relational placeholders evaluate viewer × target | Remove unused entries; use 2–10 s intervals |

The animation file also contains `MyAnimation1` (100 ms), `ServerName` (300 ms),
`web` (200 ms), `vote` (1000 ms), `Welcome` (400 ms), `time` (3000 ms) and
`barcolors` (1000 ms). None is referenced by this production baseline.

Do not put ping, TPS, health, balances, shards, clocks, animated/RGB gradients or
relational placeholders in `tagprefix`/`tagsuffix`. A relational team property is
especially dangerous: its refresh task evaluates every viewer-target pair, which
is 1,440,000 evaluations per interval at 1,200 players before packet suppression.

## Scoreboard teams and anti-override

Disable `scoreboard-teams` entirely if rank nametags, collision control and TAB
sorting are not required. If teams remain enabled, inspect the live
`plugins/TAB/anti-override.log` over a representative peak period. With no genuine
conflicts, this fork supports `scoreboard-teams.anti-override: false` and
`tablist-name-formatting.anti-override: false`. On the 26.2 adapters those flags
return the original foreign packet before TAB allocates a replacement packet.

Re-enable the relevant switch if another plugin or the server overwrites TAB's
team/display-name state. Do not disable the entire hidden `pipeline-injection`
setting: the handler also implements spectator, layout, ping-spoof, nick and
scoreboard compatibility logic.

## Metrics and allocation interpretation

Temporarily set `diagnostics.packet-metrics: true`, restart/reload, wait for a
stable sample period and run `/tab dump <player>` twice. The second dump contains
per-second deltas for TAB-created team, tablist, header/footer and scoreboard
packets, suppressed identical updates, placeholder evaluations and full resyncs.
Leave this off normally.

These counters sit at TAB's own send boundary. They intentionally do not count an
unmodified packet merely forwarded by `ChannelDuplexHandler.write()`. Likewise,
the profiler's 79% inclusive nested Netty frame is not TAB self-allocation. Compare
allocation flame graphs using self bytes in TAB's parsing, component conversion
and NMS packet construction frames, with the pass-through handler excluded.

## Expected packet model

For a static state, the old central paths emitted one update for every request.
For example, 1,200 targets refreshed 10 times for 1,200 viewers can request
14,400,000 team packets. The new per-viewer state guard emits the 1,440,000 initial
packets and suppresses the next 12,960,000 identical requests. With the static
configuration, periodic team requests should also disappear at the source, so the
steady-state target is zero TAB team packets rather than merely deduplicating them.
These are deterministic model counts, not a substitute for a production capture.

## Rollout and restart requirements

1. Back up live TAB YAML files and `anti-override.log`.
2. Merge the baseline and replace its example groups/proxy values.
3. Build and deploy the fork. A full server restart is required for the new jar,
   Netty interception switches and clean packet-state caches.
4. YAML-only text/interval/feature changes can use TAB reload, but at this scale a
   rolling full restart is safer and gives a clean before/after profile.
5. Capture packet counts and allocation self bytes under comparable player load.
6. Test Java 25 Canvas/Folia plus representative ViaVersion/ViaBackwards client
   versions before completing the rollout.
