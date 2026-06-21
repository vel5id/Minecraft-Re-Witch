# Modded Minecraft Server (Forge 1.20.1)

Self-contained Docker image for a Forge 1.20.1 server with a curated set of
mods baked in from `mods/`.

## Build & run

```bash
cp .env.example .env   # first time only, then edit as needed
docker compose up -d --build
docker compose logs -f minecraft   # watch startup; look for "Done ("
```

The server listens on port `25565` (host port configurable via `SERVER_PORT`
in `.env`).

## Configuration

Edit `.env` (gitignored) to change:

- `SERVER_PORT` — host port mapped to the server's 25565
- `MEMORY` — JVM heap size (default `8G`)
- `DIFFICULTY`, `MAX_PLAYERS`, `MOTD`, `VIEW_DISTANCE`, `PVP` — server.properties overrides

After changing `.env`, recreate the container: `docker compose up -d`.

## Updating mods

Add/remove `.jar` files in `mods/`, then:

```bash
docker compose up -d --build
```

`REMOVE_OLD_MODS=TRUE` is set in the `Dockerfile`, so the container's
`/data/mods` is resynced from the image's `/mods` on every start — removed
jars are also removed from the running server.

## Stopping / data persistence

```bash
docker compose down
```

World save, `server.properties`, and logs live in `./data` on the host and
survive image rebuilds and `docker compose down`.

## Connecting

`ONLINE_MODE=FALSE` is set, so non-premium/cracked Forge 1.20.1 clients can
connect using any username. **Security note:** with online-mode disabled,
anyone who can reach the port can join under any name. Don't expose this
port to the public internet without a whitelist or VPN.

## Troubleshooting

- `docker compose logs -f minecraft` — live server log.
- If the server fails to start with a `LoadingFailedException` /
  `ModFileLoadingException`, identify the offending jar in the log and remove
  it from `mods/`, then `docker compose up -d --build`. Note that
  client-only mods (shaders, HUD/GUI mods) will fail this way on a dedicated
  server — `oculus-mc1.20.1-1.8.0.jar` was removed for this reason.
- `docker attach minecraft-modded` — attach to the server console (detach
  with `Ctrl+P, Ctrl+Q`, do NOT use `Ctrl+C`, which stops the server).
