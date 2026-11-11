[← Decision records](README.md)

# ADR-0063: Authorization in three layers

- **Status:** Accepted
- **Decided in:** plan v5 (PR 60)

## Context

An access role says what someone can do in general. An organization also needs to say that a folder is only for finance,
or that only one person publishes a given process, without a separate authorization service.

## Decision

- **Three layers, one evaluator.** `Permisos.puede(actor, accion, recurso)`: the access role sets the ceiling (read-only
  never models), grants on the process and its folders decide, and separation of duties goes last (whoever made the last
  change does not approve it, even as an administrator).
- **A grant restricts.** An action without any grant in the chain keeps the default behaviour. With one, it is only for
  those who have it, and administrators. Any grant also brings `ver`, and `administrar` brings every action.
- **The v3 structure policy is the default:** a grant to model a process overrides it for that process.
- **One enforcement point.** A Spring MVC interceptor maps method and route to an action and resource, in a pure table
  that is tested whole. Routes with no resource in the path check in their service.
- **Refusing `ver` answers 404,** so a hidden process does not reveal that it exists.
- **Lists filter in their own query.** What the actor cannot see is worked out once from the grants, folders and role
  memberships, and added to the query: the number of statements does not depend on the number of processes.
- **Moving something between folders with grants needs `administrar`,** since it changes what it inherits.

## Consequences

- `exportar` closes the file download. The web needs the file to draw it and asks with `para=dibujar`, which only needs
  `ver`: whoever can see a drawing has its content, so this stops casual export, not a determined viewer.
- Operating cases and tasks stays under the access role: grants govern the repository and the model.
