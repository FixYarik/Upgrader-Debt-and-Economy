# Upgrader: Debt & Economy — Codex instructions

This repository is a **partial alpha**, not a completed Minecraft mod. Keep this explicit.

## Target
- Minecraft 1.20.1, Forge 47.4.28, Java 17.
- Dependency to inspect: Upgrader Items 1.2.1 Forge (Modrinth version `nIG81STn`).
- Do **not** guess external mod IDs, APIs or classes.

## Mandatory workflow
1. Bootstrap from official Forge MDK if the local Gradle setup is incomplete.
2. Run `python scripts/test_core.py` to check pure Java accounting.
3. Build with a genuine Gradle installation and `gradle build` or the generated official wrapper.
4. Fix compilation errors before new features.
5. Run Minecraft game tests and dedicated-server smoke tests when available.
6. Progress through `docs/IMPLEMENTATION_PLAN.md` without waiting for permission.
7. Never mark an unexecuted test as passed.

## Architecture
- Keep `dev.ude.core` dependency-free and deterministic.
- Update `UdeSavedData.changed()` after every persisted mutation.
- Reconcile financial transactions and test idempotence.
- Protect third-party builds, container ownership and server claims.
- Prefer bounded NPC search loops and finite state machines.
- Add new mechanics as tested feature slices.

## Important limitations
- `CityHallBuilder` is presently OP-command-driven, not automatic village worldgen.
- NPCs are static vanilla villagers; no custom renderers or AI yet.
- Upgrader Items integration is a price **fallback**, not the mod's real valuation API.
- The phone has a minimal GUI, but not full banking/business app interfaces.
- Basic plots, foundations and blueprint-check construction exist; production machines are not implemented.
- No actual theft / damage / collector AI yet; only overdue dispatch notification.
- Unit tests pass but Forge build hasn't been verified. Fix this first.
