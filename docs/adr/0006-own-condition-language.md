[← Decision records](README.md)

# ADR-0006: A condition language of its own, which runs no code

- **Status:** Accepted
- **Decided in:** plan v3 (D6)

## Context

Conditions already existed as text on sequence flows (`payment.status == APPROVED`), but only as documentation.
SpEL or a script engine could evaluate them, and would also let a value typed by a user call methods.

## Decision

A grammar of the project's own, parsed by hand in `EvaluadorDeCondiciones` (`common.condiciones`): paths of
variables, six comparators (`==`, `!=`, `>`, `>=`, `<`, `<=`), `and`, `or`, `not`, brackets, and literals (numbers,
quoted text, bare words, `true` and `false`). No functions, no assignments, no calls.

- It is compiled when publishing: a condition that does not compile is the diagnosis error `E-08`.
- It is evaluated against the case variables when running.
- A comparison over a variable the case does not have is false, and leaves a `VARIABLE_AUSENTE` line in the
  timeline. Values whose types cannot be compared count as not equal; it is not an error.

## Consequences

- A condition that publishes is a condition that runs: the diagnosis and the engine use the same parser.
- The language lives in `common` because both `modelado` and `ejecucion` need it. The simulated payment gateway
  reads the store's rejection rule (`total > 5000`) with it too.
- The conditions that were documentation became executable without changing their form.
- Anything beyond comparing variables needs a change to the grammar.

## Verification

- `EvaluadorDeCondicionesTest`: what compiles and why not, each operator by type, `and` binding tighter than `or`,
  brackets, `not`, and missing variables.
- `EmpaquetadoTest.condiciones_sin_lenguajes_que_ejecutan_codigo`: no class of the application depends on
  `org.springframework.expression`, `javax.script` or `jdk.dynalink`. Its message cites D6.
- The nightly *Mutation Testing* workflow mutates `common.condiciones` together with the services.
- `DiagnosticoServiceTest`: `E-08` fires for a half-written condition.
