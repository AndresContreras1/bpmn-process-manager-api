[← Decision records](README.md)

# ADR-0052: The diagram's geometry in the domain, and one way to read XML

- **Status:** Accepted
- **Decided in:** plan v5 (PR 52)

## Context

The next pull requests export and import BPMN 2.0 XML, and later the editor speaks it. Two things have to be in place
before any of them:

- **Geometry.** A BPMN file carries, next to the model, where each element is drawn (BPMN DI): absolute bounds for
  each shape, the waypoints of each flow, the bounds of each label, all tied to the element by its `id`. The model
  only kept the position of each node relative to its lane, and the web worked out the rest in `lienzo.ts` every time
  it drew. Nothing on the server knew where a pool, a lane or a flow was, and no element had an `id` that would
  survive a round trip through a file.
- **Parsing.** Every XML parser the JDK offers reads DTDs, expands entities and fetches what a document points to,
  unless each factory is told otherwise, flag by flag. One factory created without those flags is an XXE, a
  *billion laughs* or a request to an internal address made from the server.

## Decision

- **The relational model stays the source of truth.** The XML is the exchange format; the geometry lives in the
  domain, in two tables: `formas_bpmn` (a `BPMNShape` per pool, lane and node: `x`, `y`, `ancho`, `alto` and the
  bounds of its label) and `aristas_bpmn` (a `BPMNEdge` per sequence and message flow: its points as `[[x,y],...]`
  and the bounds of its label). Both have the tenant's row-level security.
- **Every element has a `bpmn_id`** that never changes: pools, lanes, nodes, sequence flows and message flows. A new
  element takes its kind and twelve random hex digits (`Activity_1f3a9c0b7e42`); the existing ones took their kind
  and their database id (`Activity_42`). Both are valid `xsd:ID`s and unique in a process.
- **One calculation, two languages.** `DisposicionBpmn` is the server's port of `lienzo.ts`: the same sizes, margins,
  label placement and flow routing. The same reference diagram is tested on both sides with the same numbers, so a
  change to either calculation fails its test until the other one follows.
- **The geometry follows the model by itself.** Every change to a process is recorded in its history, and the
  history publishes `ProcesoModificado`; `modelado` recalculates the geometry of that process in the same
  transaction. If it fails, the change is not saved either. The geometry sits in its own tables, so recalculating
  it never raises the `version` of an element nobody touched, and optimistic locking keeps meaning what it did.
- **The diagrams that already existed** are converted by a Java Flyway migration (`V42`), with the same
  `DisposicionBpmn` and only the active elements. A test deletes the geometry the service stored, runs the migration
  over the same data and requires the same result.
- **`SecureXml` is the only way to create an XML parser**: StAX, DOM, SAX, `Transformer` and `SchemaFactory`. Each
  one has DTDs forbidden, external entities off, empty `ACCESS_EXTERNAL_*` and secure processing on; XInclude is off.
  Every document is read first by a limited StAX reader that stops it on the first DTD or entity, and enforces
  limits before and while it is parsed: size, number of elements, depth, attributes per element, text length and
  time. For BPMN: 5 MB, 100,000 elements, 64 levels, 64 attributes, 1 MB of text and 10 seconds. Only UTF-8 and
  UTF-16 are accepted. A rejected document is a 400 with the reason. An ArchUnit rule fails the build if anything
  outside `SecureXml` creates a factory.

## Consequences

- The web still draws with `lienzo.ts` until the BPMN editor replaces the canvas; both calculations must stay equal
  until then, and the reference tests hold them together.
- Each change to a process rewrites its geometry: a few dozen rows for a large diagram, inside a transaction that
  already writes the history. Recalculating only what moved would save little and could drift.
- The migration uses the domain class. It has already run on every database that had diagrams when the calculation
  changes; a new database has nothing to convert.
- Code that needs XML, such as the importer, the exporter and the validation against the BPMN schema, gets it from
  `SecureXml` with the limits of its case, and cannot forget a flag.
