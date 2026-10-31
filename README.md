# FriendPing — Fabric 1.21.4

GitHub-ready Fabric client mod project. Push this folder to a GitHub repository; the included Actions workflow builds the JAR.

## Build on GitHub

1. Create a new GitHub repository.
2. Upload the contents of this ZIP (the files inside `FriendPing-GitHub`, not the outer folder if you prefer a root-level project).
3. Open **Actions** and wait for **Build FriendPing** to finish.
4. Open the successful workflow run, scroll to **Artifacts**, and download `FriendPing-Fabric-1.21.4`.
5. Put the downloaded JAR in `.minecraft/mods`. Your friend needs the same JAR.

## Controls and commands

- `G` — place a ping on the block/entity under the crosshair.
- `/fp ping` — same action.
- `/fp add <nick>` — add a friend.
- `/fp remove <nick>` — remove a friend.
- `/fp list` — show friends.
- `/fp clear` — clear the friend list.

Friend list is stored in `config/friendping.properties`.

## Sync protocol

For every friend, the mod sends a private message using the server command:

`/m <friend> FP1|x|y|z|targetName`

The receiving mod looks for `FP1|` in incoming game chat and displays a local marker for 15 seconds.

**Server compatibility note:** this is a best-effort client-side chat protocol. It requires Fantime's `/m` command to accept and deliver the payload unchanged, and its private-message format must be visible to the client chat event. If Fantime rewrites, filters, or hides private messages from the normal chat event, the receiver parser will need to be adjusted to Fantime's exact incoming message format. The project does not bypass server restrictions.

## Current implementation notes

- Client-side Fabric mod for Minecraft 1.21.4 / Java 21.
- Friends persist locally.
- Ping ring is rendered in-world as a soft cyan expanding ring with a short vertical accent and a 15-second lifetime.
- The position is a snapshot. Player-follow behavior is not yet implemented; player targets are labeled in the sync payload only.
- The distance/direction HUD and sound are not yet implemented in this starter build.

This is a first buildable project base, not a claim that Fantime synchronization has already been verified. Test on a private world/server first.
