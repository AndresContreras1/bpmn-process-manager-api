[← Decision records](README.md)

# ADR-0060: A structure view as the alternative to dragging

- **Status:** Accepted
- **Decided in:** plan v5 (F17)

## Context

A canvas is drawn with a pointer. WCAG 2.2 asks that anything done by dragging can be done without it (2.5.7), and a
screen reader cannot read an SVG drawing as a process.

## Decision

- **A tree, beside the canvas, over the same model:** pools, lanes, nodes and outgoing flows, following the WAI-ARIA tree
  pattern. It reads the canvas and acts through bpmn-js commands, so an action there undoes and saves like any other;
  there is no second way of editing.
- **Everything the canvas does:** create, connect, move to another lane, rename and delete. A new or moved element lands
  in a free slot of the lane; the rules of the canvas apply, and a refusal is announced.
- **The tree logic is plain functions,** tested without a browser. The whole screen is tested with Selenium and axe-core.

## Consequences

- Context pad entries are raised to 24 × 24 px (2.5.8). bpmn-js draws them at 22.
- Positions chosen by the tree are simple on purpose: the model's layout is recalculated when the drawing is reloaded.
