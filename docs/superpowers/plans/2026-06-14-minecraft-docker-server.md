# Minecraft 1.20.1 Forge Docker Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a buildable Docker image (`Dockerfile` + `docker-compose.yml`) that runs a Forge 1.20.1 Minecraft server with the 36 mods in `mods/` baked in, persisting world data in `./data`, configurable via `.env`.

**Architecture:** Thin `Dockerfile` on top of `itzg/minecraft-server:java17` (TYPE=FORGE, VERSION=1.20.1, EULA accepted, ONLINE_MODE=FALSE, mods copied to `/mods` for the entrypoint to sync into `/data/mods`). `docker-compose.yml` wires up port mapping, the `./data` volume, and runtime-tunable env vars sourced from `.env`.

**Tech Stack:** Docker, Docker Compose v2, `itzg/minecraft-server` image (Forge support, java17 tag for MC 1.20.1).

---

### Task 1: Project scaffolding — git, .gitignore, .env

**Files:**
- Create: `.gitignore`
- Create: `.env.example`
- Create: `.env` (local copy, gitignored)

- [ ] **Step 1: Initialize git repo**

Run: `git init`
Expected: `Initialized empty Git repository in .../minecraft/.git/`

- [ ] **Step 2: Create `.gitignore`**

```gitignore
/data/
.env
```

- [ ] **Step 3: Create `.env.example`**

```dotenv
# Port on the host that players connect to (maps to the container's 25565)
SERVER_PORT=25565

# JVM heap size, sets both -Xms and -Xmx. Host has ~31GB RAM; this modpack
# (Twilight Forest, Ice and Fire, Ars Magica Legacy, Blood Magic, etc.) is heavy.
MEMORY=8G

# server.properties overrides (PROPERTY-NAME -> UPPER_SNAKE_CASE env var)
DIFFICULTY=normal
MAX_PLAYERS=10
MOTD=Modded Survival Server
VIEW_DISTANCE=10
PVP=true
```

- [ ] **Step 4: Create local `.env` from the example**

Run: `cp .env.example .env`

- [ ] **Step 5: Verify `.gitignore` works**

Run:
```bash
mkdir -p data
git add -A
git status --porcelain
```
Expected: output lists `.gitignore` and `.env.example` only — `data/` and `.env` must NOT appear.

- [ ] **Step 6: Commit**

```bash
git commit -m "chore: project scaffolding (.gitignore, .env.example)"
```

---

### Task 2: Dockerfile

**Files:**
- Create: `Dockerfile`

- [ ] **Step 1: Write the Dockerfile**

```dockerfile
FROM itzg/minecraft-server:java17

ENV EULA=TRUE \
    TYPE=FORGE \
    VERSION=1.20.1 \
    ONLINE_MODE=FALSE \
    REMOVE_OLD_MODS=TRUE

COPY mods/ /mods/
```

- [ ] **Step 2: Build the image**

Run: `docker build -t minecraft-modded:test .`
Expected: build completes with `Successfully tagged minecraft-modded:test` (or final `naming to docker.io/library/minecraft-modded:test done`). The `COPY mods/ /mods/` layer should show the ~150MB of jars being added.

- [ ] **Step 3: Commit**

```bash
git add Dockerfile
git commit -m "feat: Dockerfile for Forge 1.20.1 server with bundled mods"
```

---

### Task 3: docker-compose.yml

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: Write docker-compose.yml**

```yaml
services:
  minecraft:
    build: .
    image: minecraft-modded:latest
    container_name: minecraft-modded
    restart: unless-stopped
    stdin_open: true
    tty: true
    ports:
      - "${SERVER_PORT:-25565}:25565"
    environment:
      MEMORY: "${MEMORY:-8G}"
      DIFFICULTY: "${DIFFICULTY:-normal}"
      MAX_PLAYERS: "${MAX_PLAYERS:-10}"
      MOTD: "${MOTD:-Modded Survival Server}"
      VIEW_DISTANCE: "${VIEW_DISTANCE:-10}"
      PVP: "${PVP:-true}"
    volumes:
      - ./data:/data
```

- [ ] **Step 2: Validate the compose file**

Run: `docker compose config`
Expected: prints the resolved config with no errors, `image: minecraft-modded:latest`, port `25565:25565`, volume `./data:/data`, and the env vars resolved from `.env` (e.g. `MEMORY: 8G`).

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: docker-compose service for the modded server"
```

---

### Task 4: First run, mod-compatibility validation

**Files:**
- Modify: `mods/` (only if incompatible jars must be removed)

- [ ] **Step 1: Build and start the server**

Run: `docker compose up -d --build`
Expected: container `minecraft-modded` starts (`docker compose ps` shows state `running`).

- [ ] **Step 2: Wait for startup to finish or fail**

Run:
```bash
timeout 600 bash -c 'until docker compose logs minecraft 2>/dev/null | grep -qE "Done \(|ModFileLoadingException|FAILED TO BIND|Server thread/FATAL"; do sleep 5; done'
docker compose logs minecraft | tail -150
```
Expected: either a line containing `Done (` (server fully started, world generation complete) or a fatal error/exception in the tail output.

- [ ] **Step 3: Check for the three flagged mods**

Run: `docker compose logs minecraft | grep -iE "fabric-api|dynamiclights|e4mc"`

- If any of these lines include `ModFileLoadingException`, `Incompatible`, or the server did not reach `Done (` in Step 2, proceed to Step 4 to remove the offending jar(s).
- If the server reached `Done (` and these lines (if any) are just informational/no exceptions, skip Step 4 and go to Step 5.

- [ ] **Step 4 (conditional): Remove incompatible mods and retry**

Run (adjust the file list to only the jars that actually errored in Step 3):
```bash
docker compose down
rm -f mods/fabric-api-0.92.6+1.11.14+1.20.1.jar mods/dynamiclights-v1.9-mc1.17-1.21.5-mod.jar mods/e4mc-forge-6.0.5.jar
docker compose up -d --build
timeout 600 bash -c 'until docker compose logs minecraft 2>/dev/null | grep -qE "Done \(|ModFileLoadingException|Server thread/FATAL"; do sleep 5; done'
docker compose logs minecraft | tail -150
```
Expected: `Done (` appears with no fatal mod-loading exceptions.

- [ ] **Step 5: Verify persisted world/config**

Run:
```bash
ls data/
grep -E "^(online-mode|difficulty|max-players|motd|view-distance|pvp)=" data/server.properties
```
Expected: `data/world` directory exists; `server.properties` shows `online-mode=false`, `difficulty=normal`, `max-players=10`, `view-distance=10`, `pvp=true`, and `motd=Modded Survival Server` (motd may be encoded with `\u00XX` escapes — that's normal).

- [ ] **Step 6: Commit (only if Step 4 removed mods)**

```bash
git add mods/
git commit -m "fix: remove mods incompatible with Forge server"
```

---

### Task 5: README

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write README.md**

```markdown
# Modded Minecraft Server (Forge 1.20.1)

Self-contained Docker image for a Forge 1.20.1 server with a curated set of
36 mods baked in from `mods/`.

## Build & run

\`\`\`bash
cp .env.example .env   # first time only, then edit as needed
docker compose up -d --build
docker compose logs -f minecraft   # watch startup; look for "Done ("
\`\`\`

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

\`\`\`bash
docker compose up -d --build
\`\`\`

`REMOVE_OLD_MODS=TRUE` is set in the `Dockerfile`, so the container's
`/data/mods` is resynced from the image's `/mods` on every start —
removed jars are also removed from the running server.

## Stopping / data persistence

\`\`\`bash
docker compose down
\`\`\`

World save, `server.properties`, and logs live in `./data` on the host and
survive image rebuilds and `docker compose down`.

## Connecting

`ONLINE_MODE=FALSE` is set, so non-premium/cracked Forge 1.20.1 clients can
connect using any username. **Security note:** with online-mode disabled,
anyone who can reach the port can join under any name. Don't expose this
port to the public internet without a whitelist or VPN.

## Troubleshooting

- `docker compose logs -f minecraft` — live server log.
- If the server fails to start with a `ModFileLoadingException`, identify
  the offending jar in the log and remove it from `mods/`, then
  `docker compose up -d --build`.
- `docker attach minecraft-modded` — attach to the server console (detach
  with `Ctrl+P, Ctrl+Q`, do NOT use `Ctrl+C`, which stops the server).
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README with build/run/troubleshooting instructions"
```
