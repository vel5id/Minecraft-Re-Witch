# Minecraft 1.20.1 Forge Docker Server — Design

## Goal

Build a self-contained Docker image bundling a Forge 1.20.1 server with the
36-mod set already present in `mods/`, runnable via `docker-compose` with
persistent world data.

## Approach

Thin `Dockerfile` based on `itzg/minecraft-server` (the de-facto standard
Minecraft server image). It already handles Forge installation, EULA
acceptance, JVM memory tuning, graceful shutdown, RCON, and log4j security
patches. Our `Dockerfile` adds one thing: it `COPY`s the curated `mods/`
folder into the image so the resulting image is self-contained.

Alternatives considered:
- Fully custom Dockerfile from a bare JDK image — more control, but
  re-implements Forge installer handling, JVM tuning, and shutdown logic
  that `itzg/minecraft-server` already solves.
- `docker-compose` only, mounting mods as a volume against the stock
  `itzg/minecraft-server` image — simplest, but doesn't produce a built
  image with mods baked in, which is what was requested.

## File layout

```
minecraft/
├── Dockerfile          # FROM itzg/minecraft-server, COPY mods/
├── docker-compose.yml  # port mapping, volumes, env_file
├── .env.example        # documented tunables, committed
├── .env                # local overrides (gitignored)
├── .gitignore          # data/, .env
├── mods/                # existing 36 .jar files
├── data/                # created at runtime: world, server.properties, logs
└── README.md            # build/run/troubleshooting instructions
```

## Configuration

- `TYPE=FORGE`, `VERSION=1.20.1`, Java 17 (required by Forge 1.20.1).
  Forge build resolves to the "recommended" build for 1.20.1 automatically.
- `EULA=TRUE`, `ONLINE_MODE=FALSE` (per user choice — non-premium/cracked
  clients allowed to connect).
- `MEMORY=8G` default (host has 31GB RAM; mod set includes heavy mods —
  Twilight Forest, Ice and Fire, Ars Magica Legacy, Blood Magic). Overridable
  via `.env`.
- Port `25565` mapped to host.
- `server.properties`-equivalent settings (difficulty, max-players, motd,
  view-distance, pvp) supplied via environment variables with sensible
  defaults, overridable in `.env`.
- World, server config, and logs persisted under `./data` (bind mount), so
  rebuilding the image (e.g. to add/remove mods) does not wipe the world.

## Known risks / validation plan

Three jars in `mods/` look potentially incompatible with a Forge server and
have not been verified:
- `fabric-api-0.92.6+1.11.14+1.20.1.jar` — Fabric API, likely inert/incompatible
  under Forge.
- `dynamiclights-v1.9-mc1.17-1.21.5-mod.jar` — version range suggests a
  Fabric-only mod.
- `e4mc-forge-6.0.5.jar` — designed for tunneling singleplayer/LAN worlds;
  unclear value on a dedicated server.

Plan: after the first `docker compose up`, tail logs for
`ModFileLoadingException` or a crash loop. If any of the three jars cause a
hard failure, remove them from `mods/` and rebuild. If they only log a
warning and the server starts fine, leave them.

## Security note

`ONLINE_MODE=FALSE` means any client can connect using any username (no
Mojang/Microsoft identity check). This is fine for local/LAN play. If the
port is exposed to the public internet later, consider a whitelist or VPN
(Tailscale/WireGuard) — not addressed in this iteration.

## Testing / verification

1. `docker build` completes successfully.
2. `docker compose up -d`, tail logs until the server reports `Done` (world
   generation finished, ready for connections).
3. Confirm `./data/world` is created and `./data/server.properties` reflects
   the configured settings (online-mode=false, etc.).
4. Check logs for mod-loading errors (see Known risks above).
5. Manual connect test from a Minecraft 1.20.1 Forge client (if available).
