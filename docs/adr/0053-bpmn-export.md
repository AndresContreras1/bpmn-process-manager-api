[← Decision records](README.md)

# ADR-0053: BPMN 2.0.2 export, and the bpm: namespace

- **Status:** Accepted
- **Decided in:** plan v5 (PR 53)

## Context

A process that only exists inside the product cannot be taken to Camunda Modeler, bpmn.io, Signavio or Bizagi, and
the next pull requests need a file format anyway: the importer reads one, and the editor will speak it. ADR-0052 put
the geometry and a stable `bpmn_id` in the domain; the model also has things BPMN has no element for: the kind of a
participant and the partner behind it, the role that works a lane, the order in which a gateway evaluates its exits,
and everything about a message beyond its name (content, fields, destination, what happens if it fails, the answer
it expects and how it is correlated).

## Decision

- **The relational model stays the source of truth (D25).** The file is written from the same `DiagramaResponse` the
  web draws, by `EscritorBpmn`, a pure function with no database and no Spring. The draft, the publication of a
  version and the migration of the old versions call it with the same input and get the same bytes.
- **The mapping:** a `collaboration` with a `participant` per pool (a black box has no `processRef`) and a
  `messageFlow` per message, each with its `message`; a `process` per modeled pool, executable only for the
  organization, with its `laneSet`; tasks by kind (`userTask`, `serviceTask`, `sendTask`, `receiveTask`), gateways
  with their `default` flow (except the parallel one, which BPMN does not allow to have one), events with their
  `messageEventDefinition`, and the activity's description as its `documentation`. Ids are the `bpmn_id`s; the ones
  the model does not store (the process of a pool, the message of a flow, the definition of an event) are derived
  from them, so they do not change between two exports either.
- **Conditions** are a `tFormalExpression` whose `language` is `urn:bpmn-process-manager:condiciones:1`, the language
  of ADR-0006, and the same URI is the `expressionLanguage` of the definitions. Another tool shows the text; only
  this product evaluates it.
- **What BPMN lacks goes under `urn:bpmn-process-manager:bpm:1`** (prefix `bpm:`): elements inside
  `extensionElements` and, for the order of a flow, a qualified attribute. Both are legal BPMN, and other tools keep
  or ignore them. The namespace has its own schema, `bpmn/xsd/bpm.xsd`, and what is added to it later is optional:
  changing the meaning of `bpm:1` would break the files already exported.
- **Each version stores its file** (`versiones_proceso.modelo_bpmn`), written when it is published and never again:
  a version downloaded in a year is the same file, whatever changes in the exporter. A Java migration (V44) writes
  it for the versions that already existed, from their snapshot and with the geometry the draft calculation gives.
  Before that it adds to each snapshot the `bpmnId` of its elements, read from the tables (deletes are logical, so
  the rows are still there); the fingerprint leaves `bpmnId` out, so no published process looks changed.
- **The schemas of the OMG are in the repository** (`bpmn/xsd`, from omg.org/spec/BPMN/20100501, the files of
  BPMN 2.0.2), and every export in the tests is validated against them and `bpm.xsd` with the hardened
  `SchemaFactory` of `SecureXml`, which resolves `import` and `include` only inside that folder.
- **The download** is `application/xml` with `Content-Disposition: attachment` and a file name made from the
  process name, in ASCII. The owning store gets its draft; a store the process is shared with gets the version in
  force, which is all it sees of it (HU-23).

## Consequences

- The web still draws with its own canvas; the editor (F15) will load these files into bpmn-js.
- The importer (PR 54) reads the same mapping back, so a file exported here and imported again gives the same model.
- A `.bpmn` from this product carries its rules for anyone who opens it: a reader that does not know `bpm:` still
  sees a valid, drawable process.
- The XML of every version takes space in the row next to its JSON: a few tens of kilobytes for a large diagram.
