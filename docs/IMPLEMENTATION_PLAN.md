# Work Remaining for Codex

Do not misrepresent this alpha as complete. Implement and **verify** these feature slices, in this order:

1. **Forge bootstrap**: obtain real MDK + wrapper, compile and run dedicated server; inspect actual log and fix all mapping errors.
2. **Persistence robustness**: migrate serialized text engine to structured NBT with data versioning, crash consistency and bounded operation-id history.
3. **Interaction menus**: registered `MenuType` and client `Screen`s, authenticated networking, styled smartphone, bank / taxes / business GUIs.
4. **Role NPCs**: named mayor, banker, BusinessЧлен, tax inspector, guard and sporting collectors with scheduled walks, unique per-city hall UUID, custom textures.
5. **City hall worldgen**: convert proof-of-concept OP-built halls to tested NBT `StructureTemplate` and vanilla village pools / adjacent plots; protected vault, safe pathing and terrain checks.
6. **Business plots and blueprints**: harden basic foundation + blueprint checking already added: claim protection, placement collision with all registered businesses, recipe costing, custom layouts per company and automatic activation.
7. **Business actual production**: inventory-backed stations, worker pathing, production costs, server-authoritative sale-to-bank transfer, taxes.
8. **Collector AI**: real group dispatch three in-game days after missed payment; registered home targeting; finite-state pathfinding, dialogue, bounded collection and rollback. No indefinite spawn-chunk loading; protection checks.
9. **Upgrader Items compatibility**: verify JAR mod ID, public/reflective value access if permitted; documented fallback; add datapack valuations and harden recipe cycles.
10. **QA and release**: expanded `GameTest`, unit tests, client + server integration, economy exploits, multiplayer, resource pack assets and validated JAR.

## Must-test scenarios
- Account access and double-spend; replay protection after restart.
- Loans with multiple simultaneous contracts, expired contracts and custom `/time` changes.
- Collector dispatch at 72,000 ticks after due time; rerun after logout/reload, no duplicates.
- Protected claims and shared homes; withdrawal in middle of collector interaction.
- Bank vault prevents duplication and unpermitted entry.
- Business plots never replace existing buildings; blueprint progression accurate.
- Offline owner workers and chunk unload/reload do not multiply output.

## Progress on 2026-10-28 (0.2 alpha)

- Forge bootstrap/build and baseline client/server banking were verified in 0.1.
- User-prioritized city halls now have an adjacent-village scheduler, NBT StructureTemplate, bounded terrain validation/placement, settlement persistence and vault access protection.
- Registered role NPCs now have persistent hall IDs, routes, hostile-mob defence and basic authenticated banker/mayor/business/tax menus. Visuals still reuse vanilla villagers.
- Minecraft GameTests cover automatic placement, roster uniqueness/persistence, walking, obstruction rejection, menu authorization, vault-door redstone access and interrupted generation. Check the validation report for current results.
- Financial persistence migration, full phone apps, original NPC art, actual business production, collector travel/collection AI and the real Upgrader Items adapter remain open.
