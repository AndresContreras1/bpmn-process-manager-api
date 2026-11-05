[← Decision records](README.md)

# ADR-0001: Four modules, one direction of dependency

- **Status:** Accepted
- **Decided in:** plan v3 (D1)

## Context

Running a process needs the published model and the process roles, and it needs to talk to partners outside the
store. None of the existing modules should know about it: if `gestion` or `modelado` depended on the code that runs
cases, every change to the engine would reach the modules it is built on, and the packages would form a cycle.

## Decision

Two new top-level modules. `ejecucion` holds cases, their steps, the trays, the messages in transit, the store's
clock and the engine. `integracion` holds the simulated partners.

- `ejecucion` builds on `modelado`, `gestion` and `common`. `common`, `security`, `gestion` and `modelado` never
  depend on `ejecucion`.
- `ejecucion` publishes the port a partner is reached through, `ejecucion.puerto` (`SocioSimulado`,
  `GeneradorDePedidos` and the records they exchange), and `integracion.simulado` implements it.
- `integracion` reaches nothing of the engine beyond that port. It also uses the `Integracion` enum of
  `modelado.model` and the condition language of `common.condiciones`.

## Consequences

- What `ejecucion` needs from `gestion`, the published version, it reads through `VersionService`.
- What `gestion` needs from `modelado` to publish, the diagram and its errors, comes through two ports that `gestion`
  declares and `modelado` implements: `InstantaneaDelModelo` and `DiagnosticoDelModelo`, like `UsoDeRoles` before
  them.
- The condition language is needed by the diagnosis and by the engine, so it lives in `common`.
- The engine asks for the partner of a kind of participant and works with whatever it gets. A real partner would be
  one more implementation of the port.

## Verification

- `EmpaquetadoTest.nadie_depende_de_ejecucion`: no class in `common`, `security`, `gestion` or `modelado` depends on
  `ejecucion`. Its message cites D1. `EmpaquetadoTest.gestion_no_depende_de_modelado` still holds.
- `EmpaquetadoTest.los_simulados_no_miran_dentro_del_motor`: a partner cannot reach the services, repositories,
  entities, controllers or DTOs of `ejecucion`, nor `gestion` or `security`. `los_puertos_no_hablan_de_la_base`: the
  port depends on no service, repository or controller, and on no entity of `ejecucion`.
- `EmpaquetadoTest.paquetes_de_primer_nivel_sin_ciclos` and `paquetes_de_cada_modulo_sin_ciclos` cover the new
  modules with no change. The CI job *Architecture Rules* runs the ArchUnit suite on its own.
- Since plan v5, `ModulosTest` checks the same direction with Spring Modulith, from the `package-info.java` of
  each module: `ejecucion` declares what it uses of `gestion` and `modelado`, and `integracion` reaches only the
  port of `ejecucion` and the model of `modelado`.
