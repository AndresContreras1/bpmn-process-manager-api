[← Decision records](README.md)

# ADR-0014: A deterministic diagnosis, in two levels

- **Status:** Accepted
- **Decided in:** plan v3 (D14)

## Context

Publishing needs to know whether a diagram can run, and whoever models needs to know what will probably go wrong
before it runs. The AI review already gave findings, but it costs a call, needs an external service and does not
promise the same answer twice.

## Decision

`GET /api/v1/procesos/{procesoId}/diagnostico` checks the diagram against a fixed catalogue, `CodigoDeDiagnostico`:
15 errors (`E-01` to `E-15`, severity `ALTA`) and 14 warnings (`A-01` to `A-14`, severity `MEDIA` or `BAJA`).

- The answer carries the counts `errores` and `advertencias` and one list, `hallazgos`, errors first. A finding has
  the shape of a finding of the AI review (`severidad`, `elemento`, `problema`, `sugerencia`) plus `codigo` and
  `elementoId`.
- Errors block publishing; warnings do not.
- `?sinElemento=GATEWAY:5` answers the diagnosis of the diagram that would be left after deleting that element, with
  the same cascade as the deletion, and `A-06` lists what would go with it.

## Consequences

- The same diagram always gives the same findings in the same order, at no cost and with no external service. Any
  role can ask, including a store the process is shared with.
- The editor asks for `sinElemento` before confirming a deletion and says how the count of errors would change
  (HU-10.3, HU-13.3, HU-16.3).
- The AI review stays as a second opinion; the diagnosis is the first.
- A refused publication answers `409` with its errors listed in `errors`.

## Verification

- `DiagnosticoServiceTest`: one test per code, each over a diagram that is right everywhere else, and one that
  proves the demo process fires none; the order and the counts; the what-if for a gateway, a flow and a participant.
- `DiagnosticoControllerTest`: the route and its parameter.
- `VersionesDeProcesoIntegracionTest`: an error blocks publishing and leaves no version.
