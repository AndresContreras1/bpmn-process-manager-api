[← Decision records](README.md)

# ADR-0059: bpmn-js as the one canvas

- **Status:** Accepted
- **Decided in:** plan v5 (F15)

## Context

The web drew its own SVG and edited through one REST call per element. With the model going to and from BPMN (ADR-0053,
ADR-0054) and a whole-file autosave (ADR-0040), a real BPMN editor can sit on top of the same API.

## Decision

- **One component, `lienzo-bpmn`,** with modes `ver`, `editar`, `caso` and `comparar`. bpmn-js is loaded when the
  component opens, and its styles are a separate file, so the screens that do not draw do not pay for them.
- **Own modules:** the palette and the change-type menu limited to what the model has and to the mode of the process;
  a `RuleProvider` with the hard rules (a flow does not cross pools, a start receives nothing, an end emits nothing,
  messages only between pools); the `bpm:` moddle extension, so that what BPMN does not have survives the round trip;
  Spanish translation; the product's colors and `system-ui` in the labels. The rules are plain functions, tested alone.
- **Saving is the autosave of ADR-0040:** two seconds after the last change, one at a time, with `If-Match` and an
  `Idempotency-Key`. A cut retries with the same key; a conflict or a rejected file stops until someone decides. Each
  change leaves a copy in IndexedDB until the server confirms it, offered back when the page reopens.
- **The diagnosis shows as markers** after each save, with the list beside the canvas; color is never the only signal.
- **The bpmn.io logo stays visible and uncovered**: panels sit beside the canvas, never on it, and an E2E checks it.

## Consequences

- The Content Security Policy did not have to change: bpmn-js needs inline styles, which it already allowed, and no
  `unsafe-eval`.
- The model's layout is the product's: a position is relative to its lane, so a reloaded drawing is laid out again.
