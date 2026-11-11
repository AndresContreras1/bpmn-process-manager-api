[← Decision records](README.md)

# ADR-0062: The process repository

- **Status:** Accepted
- **Decided in:** plan v5 (PR 59)

## Context

`categoria` is one free-text word. An organization with many processes needs to file them, know who answers for each
one, when to review it and how reserved it is.

## Decision

- **Folders are a tree of their own table,** each with its parent. A folder is only deleted when empty, sisters do not
  share a name, a folder cannot end up inside itself, and the tree has at most eight levels.
- **What a process has is on its own row:** folder, owner, next review, classification. `categoria` stays.
- **Tags are an array column** with a GIN index. They are read with the process, so a page of the list does not need
  another query per row, and row-level security covers them without a join table.
- **Filing is its own endpoint,** `PUT /procesos/{id}/ficha`: it does not touch the model, so it does not change the
  draft's fingerprint nor count as an unpublished change.
- **A folder filter includes what is inside it.** The ids are worked out in memory: an organization has few folders.

## Consequences

- Deleting a process logically keeps its row: the foreign key to the folder is `on delete set null`, so it does not stop
  the folder from being deleted.
