[← Decision records](README.md)

# ADR-0019: Cache only what never changes

- **Status:** Accepted
- **Decided in:** plan v3 (D19)

## Context

A second-level cache had been taken out of an earlier change: the persistence slices roll back, the test contexts
shared a `CacheManager`, and one test could read what another had left. Running a case reads its published version
on every task, message and tick.

## Decision

Only what a published version says is cached, because publishing freezes it: the JSON of its diagram
(`definiciones-de-version`) and the graph the engine walks, with its conditions compiled and its reachability worked
out (`grafos-de-version`). These are the only two `@Cacheable` methods in the API.

- `CacheConfig` declares a Caffeine `CacheManager` in each Spring context, sized by `CACHE_VERSIONES_MAXIMO` and
  dropping entries idle for `CACHE_VERSIONES_INACTIVIDAD`.
- The key is the store and the version (`#empresaId + ':' + #versionId`), so nothing is ever invalidated.
- What changes, which version is in force and whether the caller may read the process, is asked of the database on
  every request.
- `CACHE_VERSIONES=false` turns it off, and the `test` profile runs with it off.

## Consequences

- A version published or retired is seen by the next request, because the key is the version and not the process.
- Running a case, a hit costs no query: the version arrives as a lazy proxy, and its diagram is read only on a miss.
- k6 runs with the cache on and off gave overlapping latency ranges on a laptop, so the cache is measured by a count
  and not a p95: the first read of a version costs one statement and the next ones none
  ([Architecture](../architecture.md#the-published-version-cache)).
- Actuator counts hits and misses per cache under `cache.gets`.

## Verification

- `CacheDeVersionesTest`, the only test that turns it on: the second read costs no statement, the store is part of
  the key, a warm entry does not skip the permission check, a new or retired version is seen at once, and Actuator
  counts the hits.
- `MultitenenciaTest.claves_de_cache_empiezan_por_la_empresa`: every `@Cacheable` key contains `#empresaId`.
- `ProcesosApplicationTests`: the `test` profile starts without a `CacheManager`.
