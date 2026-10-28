# Upgrader: Debt & Economy — Alpha Source Project

**Status: early source prototype, not a completed Minecraft mod.**

Target: Minecraft Java 1.20.1 / Forge 47.4.28 / Java 17.

## What is already coded

- Deterministic server-side accounting: bank accounts, transfers, fixed-fee loan issuance, partial repayment, overdue dispatch after 72,000 elapsed game ticks, business purchase/registration, bookkeeping tax, homes and idempotent transaction IDs.
- Persistent serialization into Overworld `SavedData` (prototype text encoding stored in NBT).
- Forge source files for `/ude` commands, credit coins, a smartphone item with a compact clickable GUI, ATM and home-marker blocks.
- Admin command to place a **55×65 block** prototype town hall in a clear, flat region, populated with **named vanilla villager** clerks. Not automatic world generation; not final architecture.
- Price fallback for a few vanilla materials. Not Upgrader Items' actual valuation API.
- 40 deterministic core assertions compiled with Java 17 source/bytecode target.

## What is NOT implemented

- No custom NPC models, animations or walking/stealing/fighting sporting collector AI. The collector feature currently **announces** overdue dispatch only.
- No actual bank vault permissions/guards, business automatic machinery or true factory production. Business plots and foundational blueprint checks are implemented, but not complete production.
- A simple smartphone GUI exists, but full banking/business app screens are not implemented. Current actions use authenticated Minecraft commands.
- No actual Upgrader Items integration, its Forge mod ID has NOT been verified; only generic fallback pricing.
- No automatic town-hall structures inside vanilla village world generation.
- No build JAR validated. **Forge dependencies and Gradle could not be downloaded from this workspace.**

## Prerequisites for Forge compilation

1. Install JDK 17, and a compatible Gradle or obtain the *official* Forge MDK 1.20.1-47.4.28 including its Gradle wrapper.
2. Copy/merge the official `gradle/` wrapper files, `gradlew` and `gradlew.bat` into this project. Do not overwrite this project's source.
3. In PowerShell, run `./gradlew.bat build` or with Gradle installed run `gradle build`.
4. Correct any Forge API mismatches found by this first real build; no in-game verification was possible here.
5. For development launch `./gradlew.bat runClient` and `./gradlew.bat runServer`.
6. Install Upgrader Items Forge for 1.20.1 separately only after checking compatibility, and compare logs; do not assume `modId`.

## Offline unit tests

Run `python scripts/test_core.py` with `javac` and `java` available (JDK 17+). This tests **pure Java domain rules** without Forge dependencies.

## Commands coded (unverified in game)

- `/ude help`, `/ude bank open`, `/ude bank balance`, `/ude bank deposit <count>`, `/ude bank withdraw <count>` (100 ₡ per coin)
- `/ude bank transfer <player> <amount>`
- `/ude market prices`, `/ude market sell <item> <count>` (limited vanilla item whitelist)
- `/ude credit take <MICRO|PERSONAL|BUSINESS|COLLATERAL> <amount> <days>`
- `/ude credit list`, `/ude credit repay <loan_uuid> <amount>`
- `/ude home set`
- `/ude business buy <FARM|SHOP|MINE|LOGISTICS|FACTORY>`, `/ude business list`, `/ude business inspect <uuid>`
- OP only: `/ude money grant <player> <amount>` and `/ude cityhall build` (requires safe clear area)

## Construction alpha instructions

Register a business, find its generated 15x15 foundation. Place 4 oak-log corner pillars at offset (+/-6, y=0..3, +/-6), a closed 13x13 oak-plank perimeter at relative y=4, a crafting table at (+0, +0, +2) and a barrel at (+2, +0, +2). Run `/ude business inspect <id>` to validate and open your building. This construction is checked but factories do not automatically produce output yet.

## Important design notes

- Credits are **fictional in-game currency**, no real-world services.
- `markCollectorsDue()` uses the Overworld's `gameTime` (elapsed ticks), so sleep skips the *visual day/night cycle* but doesn't automatically advance loan time by a full 24,000 elapsed ticks. This differs from a wall-clock-like day system and should be made configurable later.
- Gold blocks in the town hall are currently ordinary Minecraft blocks and are not protected. The world-builder command is **creative/admin only** and should be used in disposable test worlds.
- Protect worlds before experimenting, especially with command-driven building.
- Special business sale API is domain-only and is intentionally not exposed as a player command without verified inventory consumption.

## External references

- Forge official MDK downloads: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
- Forge 1.20.1 documentation: https://docs.minecraftforge.net/en/1.20.1/
- Upgrader Items: https://modrinth.com/mod/upgrade-items/version/nIG81STn

See `docs/IMPLEMENTATION_PLAN.md` and `AGENTS.md` for handoff to Codex.
