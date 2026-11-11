[← Decision records](README.md)

# ADR-0061: Presence and comparison in the web

- **Status:** Accepted
- **Decided in:** plan v5 (F18)

## Context

The API tells who is on a process, who is editing it (ADR-0058) and what changes between two states (ADR-0057). The
modeler has to show that, and a person who is not editing must not lose work by accident.

## Decision

- **Without the lock the canvas is a viewer.** Opening the modeler asks for the lock. Whoever does not get it sees the
  same drawing read-only, updated as it is saved, with who has it and a button to take control.
- **Losing control does not lose work:** the autosave stops and what was not saved stays in the local copy.
- **A conflict can be compared before deciding:** the differences between the drawing on screen and what was saved,
  painted and listed, next to "reload".
- **Color is never the only signal.** Each kind of change has its own line style and a legend in words; what was removed
  is not in the drawing, so only the legend can say it.
- **The connection is managed here, not by `EventSource`:** on any error it closes and reconnects after a wait that
  doubles up to 30 seconds, with some randomness, so a server that comes back is not hit by everyone at once.

## Consequences

- An idle tab keeps the lock only while it sends heartbeats; a closed one loses it within 60 seconds.
