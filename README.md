# RTPQueue

**MADE BY AUXXY**

A random-teleport matchmaking queue plugin for Paper 1.21+.
Players queue for a world, get paired with an opponent, and both are teleported
to the same safe random location for a fight.

---

## Features

- **Matchmaking queue** — pick a world from a GUI, get paired automatically.
- **Safe random teleport** — chunks load asynchronously, so no main-thread lag spikes.
  Rejects lava, water, cactus, magma, ocean biomes, out-of-border spots, and bad Y levels.
  Handles the Nether's ceiling with a downward air-pocket scan.
- **Countdown + grace period** — nobody gets hit before the fight actually starts.
- **Damage isolation** — players mid-match can only damage their own opponent.
- **Auto return** — both fighters go back to where they came from when the match ends.
- **Fully configurable** — every message, GUI title, icon, radius, and cooldown.

## Commands

| Command | Aliases | Description |
| --- | --- | --- |
| `/rtpqueue` | `/rtpq` | Open the queue GUI |
| `/rtpqueue join <world>` | | Join a specific world's queue |
| `/rtpqueue leave` | | Leave the queue |
| `/rtpqueue list` | | Show all current queue sizes |
| `/rtpqueue about` | | Plugin info — MADE BY AUXXY |
| `/rtpqueue reload` | | Reload config and messages |

## Permissions

| Node | Default | Purpose |
| --- | --- | --- |
| `rtpqueue.queue` | everyone | Use the queue |
| `rtpqueue.reload` | op | Reload the plugin |
| `rtpqueue.bypass.cooldown` | op | Ignore cooldowns |

## Building

### Let GitHub build it for you (no setup needed)

1. Create a new repository on GitHub.
2. Upload this whole folder's contents to it (keep the structure — `pom.xml` must sit at the repo root).
3. Push. The **Build Plugin** workflow runs automatically.
4. Open the **Actions** tab → click the latest run → download the **RTPQueue** artifact.
   Inside is `RTPQueue-1.0.0.jar`.

To publish a proper release instead, tag a commit:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The **Release** workflow builds the jar and attaches it to a GitHub release.

### Building locally

Requires JDK 21 and Maven:

```bash
mvn clean package
```

The jar lands in `target/RTPQueue-1.0.0.jar`.

## Installing

Drop the jar into your server's `plugins/` folder and restart. `config.yml` and
`messages.yml` are generated on first start.

Enable extra worlds by flipping `enabled: true` under `worlds:` in `config.yml`.

## Compatibility

- Paper 1.21+ (uses Paper's async chunk loading and async teleports)
- Java 21

## Credits

Written from scratch by **AUXXY**. Licensed under the MIT License.

---

## Licensing system & web panel

This repo also contains `license-server/` — a self-hosted licensing dashboard
and validation API for RTPQueue.

- Your own username/password login page (no Discord, no third party)
- Owner account created by you; you create staff logins from the panel
- License keys, plans, expiry, server limits, suspend/revoke
- Per-server **HWID and IP binding**, with one-click resets
- Request statistics and a full audit log
- **Ed25519-signed responses** — the plugin embeds only the public key, so a
  leaked jar cannot forge a valid licence

```bash
cd license-server
npm install
npm run setup     # creates your OWNER account, prints the public key
npm start
```

Then put the key, API URL and public key into the plugin's `config.yml` under
`license:`. Full details in [`license-server/README.md`](license-server/README.md).

Check a server's licence in-game with `/rtpqueue license`.

MADE BY AUXXY
