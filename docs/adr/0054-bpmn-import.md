[← Decision records](README.md)

# ADR-0054: BPMN import by differences, and what is kept from other tools

- **Status:** Accepted
- **Decided in:** plan v5 (PR 54)

## Context

ADR-0053 made the product write BPMN 2.0.2. The way back has to take files from Camunda Modeler, bpmn.io, Signavio,
Bizagi or the OMG test suite, which use BPMN the model does not have (sub-processes, boundary events, timers, data
objects), draw vertically, write in ISO-8859-1, and carry their own extensions; and it has to be the protocol of
the editor later, which saves the whole diagram as XML (D27). Two ways were possible: store the XML and derive the
model from it, or keep the relational model as the truth (D25) and apply the file to it.

## Decision

- **The file is applied to the draft by differences.** Each element is matched by its `bpmn_id`: what is new is
  created, what changed is edited, what the file no longer has is deleted. One transaction, one line in the
  history (so the geometry is recalculated once), one entry in the audit log and a row in `importaciones` with the
  report. Importing the file a process exports changes nothing: no row is written and no optimistic version goes
  up. The organization's pool is always the one the process already has, whatever id it has in the file.
- **The pipeline:** `EntradaBpmn` (the size limit, and files declared in ISO-8859-1, ISO-8859-15, windows-1252 or
  US-ASCII passed to UTF-8: in those every byte under 0x80 is ASCII, so nothing can hide a DOCTYPE, and `SecureXml`
  keeps accepting only UTF-8 and UTF-16) → `LectorBpmn` (DOM from `SecureXml`) → `ConversionBpmn` (what each element
  becomes, and the rules) → `AplicacionDelModelo`. What breaks the OMG schema is a warning in the report, not a
  rejection: many tools write BPMN that does not validate and is read fine.
- **Each element is supported, converted or left out**, and the report says which and why: a manual task becomes a
  user task, a script or business rule task a service task, a timer start a plain start, a message anchored to a
  task that does not send it leaves from the border of its pool; a sub-process, a boundary event or an
  intermediate timer is left out, with the flows that touched it. A process no participant names becomes one more
  pool; a file with no lanes gets one; a vertical drawing is turned so the flow goes left to right; a black box
  becomes a customer if it is drawn above the organization and a supplier if below.
- **Hard rules reject the file, all at once:** a flow into a start event or out of an end event, a flow between
  pools, a message inside one pool. What the model stores but does not publish (exits of a gateway without a
  condition) is imported, and the diagnosis flags it (E-07) as it does for any draft.
- **Permissions are those of the API:** pools and lanes follow the store's structure policy (D16), deleting needs an
  administrator, as the `DELETE` endpoints do, and lanes asking for roles the store does not have need an
  administrator, who creates them while importing. Everything is checked before writing anything.
- **What other tools write is kept (D25):** the attributes in other namespaces and the children of
  `extensionElements` that are not `bpm:`, per element, in `extension_xml`, and written back on export: Camunda's
  `assignee`, `formKey`, `properties` and `historyTimeToLive`, bpmn.io's colors in the diagram. Only elements,
  attributes and text are kept (no comments or processing instructions), at most 16 KB per element and 512 KB per
  file; prefixes are reassigned so they never clash with the product's own.
- **The body limit** of the two import routes is 5 MB, the one of `LimitesXml.BPMN`, in the API and in NGINX; the
  body is read as a stream, so a request without `Content-Length` does not reach memory whole either.
- **Fuzzing:** a seeded generator breaks the golden corpus on purpose (bytes, slices, tags, entities, a DOCTYPE,
  deep nesting, dangling references) and requires the pipeline to answer only 400 or 409, a few hundred times in
  every build and a hundred thousand every night with the seed of the day. It is not Jazzer: no instrumentation
  agent in a Java 25 suite.

## Consequences

- The golden corpus (`src/test/resources/bpmn`) holds the class A reference models of the OMG BPMN MIWG suite
  (CC BY 3.0) and two diagrams drawn with bpmn-js, one with the Camunda extension. Each one is imported, exported,
  validated against the OMG schemas and imported back with no change.
- A file whose ids are not valid `xsd:ID`s, or repeat, gets new ids for those elements, and the report says so.
- The model's positions are relative to their lane, so an imported drawing is laid out again by the product's own
  calculation: sizes and spacing change, the order and the lanes do not.
- The editor (F15) and autosave (PR 40) will use `PUT /procesos/{id}/modelo` as their save.
- [ADR-0055](0055-descriptive-bpmn-elements.md) brought timers, timer boundary events, annotations, groups and data
  into the model: they are imported now instead of being left out.
