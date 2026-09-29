[← Documentation](README.md)

# Diagnosis, versions and AI review

Three ways of looking at a diagram before and after it runs: a deterministic diagnosis that decides whether it can
be published, the versions that publishing freezes, and an optional review by a language model that only advises.

## Diagnosis

`GET /api/v1/procesos/{id}/diagnostico` answers what a diagram gets wrong, checked against the modeling rules.
Unlike the [AI review](#ai-review) it is deterministic, costs nothing and needs no external service: the same
diagram always answers the same findings, in the same order. Any role can ask for it, including a store a process
was shared with.

Each finding carries a code of the catalogue, a severity, the element it is about and what to do:

```json
{
  "procesoId": 1,
  "sinElemento": null,
  "errores": 0,
  "advertencias": 1,
  "hallazgos": [
    {
      "codigo": "A-05",
      "severidad": "MEDIA",
      "elemento": "Gateway \"Payment approved?\"",
      "elementoId": 5,
      "problema": "El gateway no tiene salida por defecto: si ninguna condicion se cumple, el caso se queda sin camino.",
      "sugerencia": "Marca como salida por defecto la que deba tomarse cuando no se cumpla ninguna condicion."
    }
  ]
}
```

A code that starts with `E` is an error: something that makes the model unusable, such as a step nothing leads to
(`E-04`), a path that never reaches an end event (`E-03`), or a node that exists to exchange a message and has none
anchored (`E-11`). A code that starts with `A` is a warning: the model works, but it will probably not do what was
meant, such as an exclusive gateway with no default flow (`A-05`), a message awaited in the middle of the flow
with no correlation key (`A-03`), or a draft with changes that the published version does not include (`A-13`). The condition of a sequence flow is read with the same grammar the engine will
evaluate it with, so a condition that would not compile is reported before anyone runs the process.

**Before deleting.** `?sinElemento=TYPE:id`, for example `GATEWAY:5`, answers the diagram that would be left after
deleting that element, with the same cascade the deletion has: a pool takes its lanes, a lane its nodes, and a node
its sequence flows. `A-06` lists what would go along with it, and the rest of the findings say what would break.

```bash
curl "http://localhost:8080/api/v1/procesos/1/diagnostico?sinElemento=GATEWAY:5" -H "Authorization: Bearer $TOKEN"
```

## Published versions

Publishing a process is `PATCH /api/v1/procesos/{id}` with `{ "estado": "PUBLICADO", "version": n }`. It runs the
diagnosis first, and with a single error it answers `409` with the list in `errors` and saves nothing. Otherwise it
saves the whole diagram as the next version, exactly as `GET /procesos/{id}/diagrama` returns it, and answers the
process with `versionPublicada`.

The model keeps being editable: what is edited is the draft. A process read one by one says `borradorPendiente`,
which is true when the diagram of today is not the one in force, and the diagnosis says the same with `A-13`. It is
worked out by comparing fingerprints, so moving a step and moving it back leaves nothing pending. It does not come
in listings, where it would mean reading one whole diagram per row, nor for a guest store, which only sees what is
published.

```bash
# Publish, and read the versions
curl -s -X PATCH http://localhost:8080/api/v1/procesos/1 -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"estado":"PUBLICADO","version":0}'
curl -s http://localhost:8080/api/v1/procesos/1/versiones -H "Authorization: Bearer $TOKEN"

# The diagram as it was published, however much the draft has changed since
curl -s http://localhost:8080/api/v1/procesos/1/versiones/1/diagrama -H "Authorization: Bearer $TOKEN"
```

A version that should not be used any more is retired by an administrator with
`PATCH /api/v1/procesos/{id}/versiones/{n}` and `{ "estado": "RETIRADA" }`. Nothing is deleted: the cases that were
opened with a version are read against it.

What a version says is kept in memory, keyed by store and version, with no invalidation to get wrong:
[Architecture](architecture.md#the-published-version-cache) explains how.

## AI review

`POST /api/v1/procesos/{id}/revision` sends the diagram to a language model and answers with findings: a
severity, the element each one is about, what is wrong and what to do. Administrators and editors can ask for
it, because every review costs a call.

```bash
curl -X POST http://localhost:8080/api/v1/procesos/1/revision -H "Authorization: Bearer $TOKEN"
```

What the endpoint does not do is as important as what it does:

- **It answers in a shape, not in prose.** The request declares the schema the model must answer in, so the
  reply is read as data. Anything outside it answers `502` instead of becoming an invented review.
- **It never changes the model.** The findings are advice. A person decides what to do with them.
- **It sends the diagram in words, not the JSON of the endpoint.** Same content, no internal ids.
- **A diagram that has not changed is not reviewed twice.** The previous review comes back marked
  `reutilizada`, without a call and without spending part of the limit.
- **The limit is per store.** Beyond `REVISION_MAX_REVIEWS` in `REVISION_WINDOW`, the answer is `429` with
  `Retry-After`.
- **Without `GEMINI_API_KEY` it stays off.** The endpoint answers `503` and the rest of the API runs exactly
  as before, which is also how the test suite runs: no test makes a network call.
