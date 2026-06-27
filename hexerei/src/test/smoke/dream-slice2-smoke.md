# Dream World — Slice 2 smoke test (hexerei:dream dimension + teleport)

Cross-dimension behavior is **not** GameTested — teleport/death/login across dimensions is unreliable in the
shared multi-arena GameTest world (same rationale CLAUDE.md gives for the altar power scan). This procedure is
the authoritative gate for Slice 2.

## A. Automated gate (CI-runnable, no client)
These confirm the code compiles, all unit tests pass, the jar assembles, and the datapack/dimension load
without a registration/codec error.

1. **Full build** — `cd hexerei && JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon build`
   → expect `BUILD SUCCESSFUL`; jar at `build/libs/hexerei-1.21.1-0.1.0.jar`. (Runs the JUnit suite incl.
   `DreamReturnTest`, processes resources incl. the `hexerei:dream` datapack.)
2. **Worldgen codec** — `./gradlew --no-daemon runData` → `BUILD SUCCESSFUL` (parses the dimension JSON).
3. **Dedicated-server boot** — `./gradlew --no-daemon runServer`, wait for `Done (… )! For help, type "help"`.
   A malformed dimension/dimension_type or a bad event subscriber crashes here. Confirm the log has **no**
   `hexerei`/dimension errors, then in the server console: `execute in hexerei:dream run forceload add 0 0`
   should succeed (proves the level exists), then `stop`.

### Automated-gate result (2026-06-28)
- Build (`./gradlew build`): **PASS** — `BUILD SUCCESSFUL`, JUnit suite green (incl. `DreamReturnTest`),
  jar assembled at `build/libs/hexerei-1.21.1-0.1.0.jar`, datapack resources processed.
- runData (worldgen codec): **PASS** (Task 2, `2e4a64e`).
- Server boot + `hexerei:dream` exists (step A3): **NOT RUN — manual** (needs a live server console; the
  build + runData cover compile/codec, but runtime dimension creation is confirmed in-session by step A3).

## B. Interactive player-loop checks (require a connected client — manual)
With the jar installed on a dev/dedicated server, op yourself and:

1. **Cross over.** Brew a Dreaming Draught (cauldron: `mandrake_root` + `wormwood`) with ≥ `SCRY_COST`(=1)
   essence and drink it. → You are teleported to `hexerei:dream`, standing on a 5×5 stone platform at
   `~0,64,0`. `data get entity @s` shows `hexerei:dream_state` with `dreaming:1b`, a `returnDim`, and a
   `returnPos` (your drink spot).
2. **Timer wake.** Wait `DREAM_TICKS` (30 s). → You are teleported back to the drink position;
   `data get entity @s ...dream_state` shows `dreaming:0b`.
3. **Death wake.** Re-enter, then `/kill @s` (or take lethal damage) while in `hexerei:dream`. → You do **not**
   die; you wake at the return position with ~2 hearts (`DEATH_WAKE_HEALTH=4`).
4. **Login recovery.** Re-enter, disconnect, reconnect. → On login you are woken (not stuck in the dream);
   `dreaming:0b`.
5. **Fizzle (no teleport).** With < 1 essence, drink the draught. → No teleport, no dreaming state (slice-1
   fizzle preserved).

### Interactive result (date / server build / tester)
- [ ] 1 cross over  - [ ] 2 timer wake  - [ ] 3 death wake  - [ ] 4 login recovery  - [ ] 5 fizzle
- Notes:
