# Hypixel SkyBlock Items — Fabric (Minecraft 26.2)

A starter client-side Fabric mod for single-player. It downloads the public Hypixel SkyBlock item definitions and lets you search IDs and receive a vanilla-material representation.

## Your setup
- Minecraft: 26.2
- Fabric Loader: 0.19.3
- Fabric API: 0.161.0+26.2
- Java: 25

## Commands in a single-player world
- `/hypixel help`
- `/hypixel reload`
- `/hypixel search Hyperion`
- `/hypixel give HYPERION`

Search first to find the exact ID. Item IDs are case-insensitive.

## Build on Windows
1. Install a JDK 25 (not just a JRE).
2. Install Gradle 9.5.1 or newer.
3. Open PowerShell in this project folder.
4. Run: `gradle build`
5. The mod jar will be in `build/libs/`.
6. Put that jar in your TLauncher instance's `mods` folder alongside Fabric API.

## Important limitations
- This starter reads the public item resource endpoint; no Hypixel API key is needed for this endpoint.
- It maps the API's `material` field to a vanilla Minecraft item and applies the Hypixel name. It prints stats in chat.
- It does not yet recreate Hypixel's custom abilities, reforges, enchantments, dungeon stars, pets' behavior, exact item models, or official textures. Those require additional gameplay code and a resource pack.
- This is an unofficial fan project and is not affiliated with Hypixel.
- Back up your world before testing mods.
