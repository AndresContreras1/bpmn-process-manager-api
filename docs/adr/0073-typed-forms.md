[← Decision records](README.md)

# ADR-0073: Typed forms frozen in the version

- **Status:** Accepted
- **Decided in:** plan v5 (PR 69), decisions D6 and D36

## Context

Until now whoever completed a task handed over free key-value data, and whoever opened a case passed free variables.
Nothing said which keys a step expects, of which type, or which of them are mandatory, so a typo in a key or a number
sent as text only showed up later, when a gateway compared it and took the wrong path. A real store needs each step to
ask for what it needs, in a shape the server checks, and to keep the answers with their types for the conditions of
the gateways (D6) to read.

## Decision

- **A form per user task and per plain start event.** It is a list of fields stored as JSON in
  `nodos_flujo.formulario` (V59). A check constraint keeps it on a user activity or a plain start event only, the
  only nodes a person fills: a message or timer start opens on its own, and a service task has nobody to fill it. The
  services say the same first (`409`), also when a node with a form would change its type. `PUT
  /actividades/{id}/formulario` and `PUT /eventos/{id}/formulario` replace it whole with the node's `version`; an
  empty list removes it. Both are `MODELAR` on the element, like any other edit of the draft.
- **Field types:** `TEXTO`, `TEXTO_LARGO`, `NUMERO`, `MONEDA`, `FECHA`, `FECHA_HORA`, `SI_NO`, `LISTA`,
  `LISTA_MULTIPLE`, `PERSONA`, `ARCHIVO` and `SECCION`. A section has no value: it titles and groups the fields after
  it, up to the next section, and its `visibleSi` hides them all.
- **Validations per type:** `requerido`; `minimo` and `maximo` for a number, an amount or how many options a multiple
  list takes; `largoMinimo` and `largoMaximo` for texts (up to 1,000 characters on one line and 10,000 on several,
  counted in code points); `desde` and `hasta` for dates; `moneda` (ISO 4217) decides how many decimals an amount
  takes. A property that does not apply to the field's type is an error, not something silently ignored. A form has
  up to 100 fields, a list up to 200 options, and a limit fits fifteen integer digits and six decimals.
- **Patterns with RE2/J.** A user writes the `patron` of a text, so it is compiled with RE2/J, which matches in linear
  time and has no backtracking (back-references and look-arounds are refused when saving). The one costly thing left,
  compiling nested counted repetitions such as `((a{1000}){1000}){1000}`, is refused before compiling: a pattern is up
  to 200 characters and its counted repetitions, multiplied by those that contain them, cannot pass 1,000, the same
  idea as the program size limit of RE2 in C++. The count reads the pattern the way RE2/J parses it: text between
  `\Q` and `\E` is literal, parentheses included, and a flags group such as `(?i)` or an empty `\Q\E` leaves the
  repetition after it on the atom before it, so none of them can hide a level of nesting.
- **Conditions in the D6 grammar, compiled by the server (D36).** `visibleSi` and `requeridoSi` are written in the
  language of the gateway conditions and read the case variables and, by name, the fields above the one that asks.
  Asking for a field below, or for a section, is an error, so a form has no cycles and is evaluated top to bottom.
  Every answer that carries a form brings each condition compiled as a JSON tree (`visibleSiArbol`,
  `requeridoSiArbol`): `Y` and `O` with two parts, `NO` with one, and the leaf `COMPARACION` with `ruta`, `operador`
  and a typed `valor`. The web only walks that tree; the grammar is never written again in TypeScript. The tree is not
  stored: it is compiled again from the text each time, so it can never drift from it. A condition over 1,000
  characters, which only a BPMN file can bring, is not compiled for an answer (it comes without a tree): the compiler
  goes down one level per `not` or parenthesis, and the diagnosis refuses it by its length anyway.
- **Frozen in the version.** The form travels in the diagram, so publishing freezes it in the version's definition
  with the rest; a case reads the form of its own version, and changing the draft does not touch the cases already
  open. A version published before PR 69 keeps its fingerprint: the form only enters the canonical form when a node
  has one.
- **The server decides on every answer.** Completing a task or opening a case with a form validates the answers
  against the version's form, walking it from the top: a hidden field (by its own `visibleSi` or its section's) is
  not asked and whatever was sent for it is dropped; a visible one is mandatory if `requerido` or `requeridoSi` holds;
  a key the form does not ask for is an error. Any failure answers `400` in the usual Problem Details shape, with an
  `errors` map keyed by the field under its body property (`datos.monto`, `variables.pedido`), and nothing is saved.
- **Typed values, under the convention of plan v3.** What passes is stored with its type under `tarea.<taskNameInCamel>`
  (or as the case variables when opening): numbers as numbers (an integer as an integer), `SI_NO` as a boolean, dates
  as ISO 8601 text, which is how the conditions compare them, a multiple list as a list, and a person or a file as its
  id. A person is an active user of the store; a file is a document of the same process whose latest version passed
  the antivirus (PR 61), checked through a port in `common` that `modelado` implements, so `ejecucion` does not look
  into documents.
- **Defaults from the case.** `porDefecto` names a case variable whose value is offered when the form opens, if it fits
  the field. `GET /tareas/{id}/formulario` answers the frozen form, the offered `valores` and the case variables its
  conditions read; `GET /procesos/{id}/formulario-de-inicio` answers the start form of the version in force.
- **Free data only for tests and corrections.** Without a form, a `PRODUCCION` case takes no data when it opens or
  when a task completes (`400`, one error per key); a `PRUEBA` case keeps taking free key-value data, which is how a
  draft is walked by hand. Correcting variables stays `PATCH /casos/{id}/variables`, the administrator's.
- **Checked again when publishing.** A form that arrives inside a BPMN file or through the editor's autosave did not go
  through the API that saves it, so the diagnosis checks every form with the same rules and reports what is wrong as
  `E-22` (also a form on a node that nobody fills), which stops publishing.
- **In BPMN as `bpm:formulario`.** The writer puts each form in the node's `extensionElements`: one
  `bpm:campoFormulario` per field with its properties as attributes and its options as `bpm:opcion`, declared in
  `bpm.xsd`. The reader brings it back whole; a field whose type does not exist is left out with a note, as are fields
  past the hundredth and a form on a node that does not take one, and a limit that does not read as a number is left
  empty with a note instead of loosening the field in silence. The web declares the same types in its `bpm:`
  package, so saving from bpmn-js keeps the forms instead of dropping what it does not know.
- **One way to store a field.** A field is kept in the shape it comes back from BPMN: a blank text is no text,
  `requerido: false` is not saying it, an empty list of options is none, and a limit loses its trailing zeros and its
  exponent (`1E+3` and `1000.00` are `1000`). Without that, exporting and importing a form, or the modeler's autosave
  of a process nobody touched, would count as an edit and show changes nobody made.
- **Dates are wall-clock values.** `FECHA_HORA` is a day and a time with no zone, stored as written: the organization's
  local time, not an instant. A time that a change of clock skips or repeats in the store's zone is not checked; a
  form that needs an instant asks for a date and a time zone apart, or waits for a typed instant field.

## Consequences

- The API is complete for forms; the web still completes tasks with the key-value editor. The form designer in the
  modeler's properties panel and the form a person fills, with the interpreter of the compiled conditions and its
  Vitest suite, are plan PR F23.
- A start form has no case yet, so its `porDefecto` offers nothing; it is useful on tasks.
- Existing processes keep working: a task without a form completes as before in `PRUEBA`, the default mode of a case
  opened by hand. A `PRODUCCION` case of a process without forms only moves with tasks completed without data.
- An answer is checked against the store and the process at the moment it arrives: a person deactivated or a
  document deleted after the form was opened makes that field fail.
