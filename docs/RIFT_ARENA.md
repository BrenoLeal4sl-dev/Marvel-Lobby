# Rift Arena

## Implementation order

The first milestone is a playable vertical slice, followed by competitive/social integration. Existing catalog, accounts, navigation and chat remain the source of truth. No password recovery is added.

Android uses Kotlin Views, MVVM, Room, DataStore, Coil and OkHttp. Backend is Fastify/TypeScript with PostgreSQL RLS and authenticated sessions; community connections are follows, not a separate friendship graph. The Arena uses those connections for its friends ranking.

`rift/engine` owns a platform-independent fixed-step simulation and seeded random streams. `rift/render` owns Canvas drawing, animation states and touch input. `rift/presentation` owns lifecycle, menu, pause, upgrade and results. Data/repository owns local results and subsequent upload. A dedicated unexported Activity provides a full-screen game without the Lobby navbar. Its ViewModel retains the run during configuration changes; leaving foreground pauses rather than advancing simulation.

Rendering uses Choreographer at display cadence with 60 Hz simulation, capped catch-up, bounded enemy/projectile pools and preloaded fonts/paints. Network and database work run outside combat. Camera follows with exponential smoothing. World coordinates are independent of screen size.

Spider-Man: directional movement, aimed web shot (nearest target or last direction), invulnerable dash, radial Web Burst. One arena with obstacles, four enemy archetypes, elites and a boss with telegraphed attacks. Upgrades combine projectile piercing, branching, blast, slowing, defensive and mobility effects. Three choices pause play. Runs end on death or extraction at ten minutes.

Sprites are explicitly provisional, code-native silhouettes. Animation states idle/run/attack/dash/hit/death/special are independent of simulation. No Comic Vine thumbnails are presented as sprites; replacement sheets can be loaded before combat through the renderer's asset boundary. No sound/music claimed in this slice.

Competitive phase: authenticated server-issued session/seed/version, offline play during a session, durable submission queue, server-recomputed score, bounded duration/events and idempotent submission. Offline practice cannot enter verified rankings. Global/following/weekly/character rankings use pagination and include own rank. Async challenges reuse a seed and version; no real-time multiplayer. Result cards open the Arena and reference server records. Public achievements require activity consent.

## Competitive contract

PostgreSQL migration `008_rift_arena.sql` installs schema version 7. Android Room 6→7 adds a per-owner durable result archive, without deleting catalog/account/chat records. Practice results remain local. A server session permits play without a network connection; pending results upload at game over or via History → Synchronize results. Sessions expire after 24 hours, challenges after seven days. An unfinished session blocks another competitive run for twelve minutes unless explicitly abandoned; practice stays available.

The server ignores no part of the supplied score: it recomputes the formula and rejects a mismatch. It checks account ownership, seed, character/version, elapsed server time, spawn/XP budgets, upgrade caps and combo/boss bounds. This is basic consistency validation, **not** a replay anti-cheat system: a modified client could still fabricate plausible metrics. No claim of tamper-proof rankings is made. Runs are scoped to a game version so balancing changes can use a new ruleset.

Ranking keeps each player's best run. Weekly starts Monday at 00:00 UTC and uses submission time. Ties use the earlier submission, then user UUID for a deterministic position. Following includes the current account and accounts it follows. Profile counters cover all validated runs in the current ruleset. Local history displays the latest 100 records; aggregate local totals include every saved record.

Challenges have one issued attempt per participant. Explicitly abandoning consumes the attempt; process death does not restore a live simulation. Configuration changes do preserve the engine and pause it. A challenge shares the fixed world, initial conditions and seeded director/upgrade streams; player decisions still affect combat and whether enemy pools are full. Seeds are not public competitive-profile data. Direct chat cards are resolved on the server, so clients cannot assign arbitrary scores or share an unvalidated result.

Only new personal records and the first boss defeat publish an activity, and only while activity sharing is enabled. Opting out removes previous Arena activities too. Routine runs do not enter the feed. Notifications remain in-app, as in the existing community.

## Assets and tuning

Current visuals are provisional Canvas silhouettes, not final licensed sprite sheets. No external image loading occurs during play. The game supports idle, run, attack, dash, hit, death and special animation states; movement drives the limb cycle. One Spider-Man definition is registered. Future characters should provide an ability/visual implementation through the registry rather than branching on character names in the simulation.

The ten-minute limit is an extraction win. The director introduces fast enemies after 18 seconds, ranged after 32, tanks after 55, elites after 65; Sentinel waves start at 90 seconds and repeat while previous bosses have been defeated. Sentinel alternates a telegraphed radial volley and a marked ground strike. Exact difficulty and perceived fun still require playtesting on real devices; automated tests establish mechanics, not subjective quality.

## Validation gates

Pure engine tests cover normalized movement, obstacles, cooldown/dash, seed repeatability, combat, progression, boss patterns and score. Native tests exercise multi-touch, pause/background, Activity recreation and resumption. Compile/run after engine/input, progression/boss and integration milestones. Do not claim physical-device FPS from emulator or unit tests.

The final Android compilation and lint gate passed with 90 unit tests and zero lint errors. Backend validation passed 39 tests against a temporary database, including ownership, ranking, challenge attempts, idempotent submissions, score bounds and activity privacy. The production Aiven check after migration confirmed TLS, schema versions 1–7 and restricted-role table permissions. Automated production checks do not create real accounts, messages or runs.

Twenty native emulator tests passed: Arena controls/lifecycle, existing account/chat/identity/favorite migration checks, and Arena archive durability, per-owner isolation, duplicate protection, immutable upload acknowledgement and totals across 105 records while displaying the latest 100. No installation or performance validation was performed on the user's physical phone.
