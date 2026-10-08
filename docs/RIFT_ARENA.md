# Rift Arena — ruleset rift-3

## Current implementation

Six playable kits share a fixed-step survival engine: Spider-Man, Iron Man, Hulk, Thor, Wolverine and Doctor Strange. Each supplies its own combat strategy through `HeroCombatKit`; charge bookkeeping and collision, projectile and damage helpers are shared. Hero definitions contain health, movement, melee/ranged damage, range, reload and Ultimate/Hyper charge costs. Selection persists per account in DataStore. Portraits and equipment are original provisional Canvas vector silhouettes, not licensed animation sheets.

Left stick moves. Right stick aims and attacks on release. Quick taps auto-aim at the nearest current position, without movement prediction. Dragging back to center or cancelling discards a shot. Gadget complements the chosen kit. Ultimate has its own smaller aim stick. Damage dealt charges Ultimate and Hypercharge; Hyper lasts 15 active seconds and does not advance while paused. Dash remains a movement tool. All melee-only heroes remain playable without inventing a ranged attack.

| Hero | Primary / Gadget | Ultimate | Hypercharge |
|---|---|---|---|
| Spider-Man | Webs or free three-hit melee; Gadget switches freely. Hold melee 2 s for a stronger collision-checked lunge consuming one shared web charge. | Aimed web explosion, area damage and 50% slow. | Three narrow-spread webs, or stronger, wider, faster melee and longer charged lunge; freely switch during all 15 s. |
| Iron Man | Fast long-range repulsors; thruster dash and 5 s stronger piercing shots. | Aimed concentrated energy impact. | Improved movement and piercing projectiles. |
| Hulk | Heavy free melee; short impact charge. Highest health. | Leap to a valid aimed destination and broad ground smash. | Reduced incoming damage and a shockwave after each melee attack, changing its area coverage. |
| Thor | Mjolnir melee or hammer throws; Gadget toggles modes. | Aimed area lightning. | Electric impact zones and piercing throws. |
| Wolverine | Fast free claw combos and recovery between incoming hits; aggressive lunge. | Directed claw rush. | Faster movement, damage and regeneration; successful melee hits also heal. |
| Doctor Strange | Slowing magic projectiles; portal in aim/facing direction, rejecting blocked destinations. | Magic zone pulses for 3 s. | Triple piercing magic with per-volley hit limits. |

Three ranged charges regenerate sequentially at each hero's configured reload rate. Spider-Man's web slow is 50% for 2 s; Sticky extends duration. Reapplication refreshes duration and never stacks speed penalties. Spread volleys share an ID and a bounded per-enemy hit ledger, so three overlapping projectiles cannot deal triple direct damage to one enemy. Ultimate damage does not charge itself.

The tutorial is isolated from competitive sessions and saves completion with a versioned per-account flag. It teaches movement, manual aim/quick tap, switching/attacking, aimed Ultimate, participation-charged Hyper, regenerating resources, XP and the survival objective. It can be replayed from Arena Settings. Hero selection provides concise kit explanations, archetype, mastery level and offense/defense/mobility/control/difficulty indicators. Mastery is informational (50 local kills per level), without fabricated rewards.

## Preserved game and integration

Kotlin Views/MVVM, Room/DataStore/OkHttp and Fastify/PostgreSQL remain unchanged as the application stack. `rift/engine` is platform independent, `rift/render` owns Canvas and multitouch, and ViewModel/repository handle lifecycle, account selection and durable results. No network/database work runs in the frame loop. Simulation is 60 Hz with bounded pools, capped catch-up and seeded random streams. The dedicated Activity excludes the Lobby navbar. Backgrounding pauses; configuration changes retain the current run.

The current mode remains solo survival against four enemy archetypes, elites and telegraphed Sentinel bosses. One fixed world has obstacles. Waves, level-up choices, short transitions, extraction after ten active minutes, pause/restart and results are preserved. This source does **not** implement teams, Rift Core objectives, hero-vs-hero duels or real-time Arena multiplayer. Async social challenges compare survival scores using the same seed, ruleset and character; they are not simultaneous battles.

Authenticated server sessions issue seed/version/character. Offline practice stays local; competitive results persist in Room and upload idempotently. The server checks account ownership, seed, version, character, recomputed score, elapsed time, spawn/XP budgets and upgrade caps. This is consistency validation, **not replay anti-cheat**. A modified client could fabricate plausible metrics. Rankings are scoped to ruleset; versions 1 and 2 still accept valid previously-issued pending results without entering version-3 rankings. API requests without a version retain rift-2/Spider-Man behavior for installed older clients. Updated Android explicitly sends rift-3 for sessions, challenges, rankings and profiles; challenges require a matching requested ruleset.

Global/following/weekly/character rankings paginate; weekly starts Monday 00:00 UTC and uses submission time. Ties use earlier submission, then user UUID. Challenges allow one attempt per participant, expire after seven days, and lock both participants to their stored hero. Abandonment consumes the attempt. Sessions expire after 24 hours; an unfinished competitive session blocks another for twelve minutes unless abandoned. Public profile main hero derives from real recorded runs. Verified result/challenge cards reuse chat. Public personal-record/first-boss milestones require activity consent. Accounts, catalog, favorites and conversations are preserved.

## Database and validation

`008_rift_arena.sql` installs PostgreSQL schema 7. `009_rift_heroes.sql` installs schema 8, broadening existing session/challenge character constraints to six keys. Run it as administrator in `marvel_mobile`; it does not delete data and is rerunnable. Room remains version 7 because hero identity is already stored in the immutable result payload.

Spider-Man was implemented and tested before adding the five other strategies: compile, five new kit-rule tests and two native touch/tutorial tests passed at that gate. The backend migration and all six session/ranking/challenge paths passed the temporary-database suite. The completed suites passed 109 Android unit tests, five native emulator tests and 43 backend tests. Full-archive mastery, per-account selection and restoration were verified. Production was read-only checked at schema 7; schema 8 remains a required administrator action. These checks do not create accounts or messages in production, install on the user's physical phone, or establish physical-device FPS or competitive balance. Real-device feel and cross-hero duel balance cannot be claimed from survival-mode tests.
