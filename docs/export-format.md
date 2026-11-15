[← Documentation](README.md)

# Export format

What the full export of a store holds, file by file, and how to check it without the API. The administrator asks for
it with `POST /api/v1/organizacion/exportaciones` ([API reference](api-reference.md#exporting-the-organization)); the
link to download it arrives by e-mail and works for 24 hours. This page describes format **1**, the value of
`formato` in `manifest.json`: a change to the layout below changes that number.

## Layout

Every path is relative, made of ASCII letters, digits, dots, dashes and underscores. A name somebody typed, such as a
document's, is cleaned before it becomes part of a path: accents dropped, anything else turned into a dash, no dot or
dash at the start, at most 80 characters. Dates are ISO 8601 as Java prints them (`2026-11-12T10:00:00.123456`, or
`2026-11-12T10:00` when seconds are zero), without a zone, as the database keeps them; those of the manifest, of the
exports and of the audit trail are in UTC. JSON keys are the API's camelCase names of the database columns.

```
manifest.json                      the list of every other file, with its size and SHA-256; always last
organizacion.json                  the store and its settings
personas.json                      the people, without any secret
roles.json, membresias.json        process roles and who holds them
carpetas.json, concesiones.json    folders and permission grants
procesos/procesos.json             every process, also the deleted ones (activo: false)
procesos/<id>-<name>/
  proceso.json                     the process: name, mode, state, folder, owner, review date, tags
  borrador.bpmn, borrador.json     the current draft as BPMN 2.0.2 and as the API's diagram (active processes only)
  versiones/versiones.json         each published version: number, state, date, who published it, fingerprint
  versiones/v<n>.json              the diagram exactly as it was published
  versiones/v<n>.bpmn, v<n>.svg    its BPMN file and its drawing, when it has them
  documentacion.json               the Markdown of the process and of each element (bpmnId)
  caracterizacion.json             the characterization sheet
  manuales/manuales.json           the manuals asked for
  manuales/<id>-v<n>.html, .pdf    each manual that was built
  documentos/documentos.json       each document with every version: name, type, size, SHA-256, antivirus state
  documentos/<id>/v<n>-<name>      the file of each version that passed the checks
comentarios.json                   every comment, with its thread, anchor, mentions and state
historial.json                     the history of the store and of every process
bitacora/bitacora.json             the audit trail, in chain order, with every field of its hashes
bitacora/verificacion.json         the trail verified when the export was built
bitacora/anclas.json               the daily anchors of the trail: day, sequence, hash, whether locked, when
bitacora/anclas/<day>.json         each anchor file, as the immutable storage keeps it
casos/casos.json, casos/casos.csv  the cases, with their reference, state and variables
casos/actividades.json             the steps of each case
casos/eventos.json                 the timeline of each case
casos/mensajes-entrantes.json      messages received, with their body
casos/mensajes-salientes.json      messages sent, with their body
```

`<id>-<name>` is the process id and its name in lowercase ASCII with dashes: `procesos/7-order-fulfillment`. A file
listed in `documentos.json` carries its path in `archivo`; one blocked by the antivirus, or not checked yet, has no
file. Documents deleted in the app are not exported. A version that passed the checks but whose file the storage no
longer has carries `"faltante": true` instead of `archivo`, and a built manual missing its HTML or PDF lists them in
`faltantes` (`["pdf"]`): the export goes on without that file, and the manifest does not list it.

## What is not in it

Password hashes, WebAuthn handles, TOTP secrets, recovery codes, passkeys, sessions, refresh tokens, links sent by
e-mail and the identities linked with Google or Microsoft. Those tables are not read. Neither are the store's
verified domains, its idempotency keys, its queue of jobs, the rate limit buckets, the notifications, the save
points of the drafts, the AI reviews, who had a process open, the reports of BPMN imports or the sharing with other
stores: the draft and the versions say what each process is, and the rest is not the store's work. The keys of the
object storage are used to read the files, not exported.

## The manifest

```json
{
  "formato" : 1,
  "exportacion" : 4,
  "tienda" : { "id" : 2, "nombre" : "Acme Store" },
  "pedidaPor" : { "id" : 7, "nombre" : "Ana", "email" : "ana@acme.com" },
  "pedidaEn" : "2026-11-12T10:00:00.123456",
  "generadaEn" : "2026-11-12T10:00:11.654321",
  "zonaHoraria" : "UTC",
  "archivos" : [ { "ruta" : "organizacion.json", "tamano" : 812, "sha256" : "9f86d0..." } ]
}
```

Each file's `sha256` is the hex SHA-256 of its bytes inside the ZIP. The SHA-256 of the whole ZIP is the one the
e-mail quotes and `GET /api/v1/organizacion/exportaciones/{id}` answers; it is also the one the deletion certificate
names when the store is gone.

```bash
sha256sum exportacion-2026-11-12-4.zip
unzip -p exportacion-2026-11-12-4.zip personas.json | sha256sum
```

## The CSV

`casos/casos.csv` has the same columns as `casos.json`, in this order: `id, procesoId, versionDelProceso,
referencia, estado, tickInicio, tickFin, fechaCreacion, fechaFin, variables`. It follows RFC 4180: comma separated,
CRLF at the end of each line, and a cell with a comma, a double quote or a line break between double quotes, with
its quotes doubled. A text that starts with `=`, `+`, `-`, `@`, a tab or a carriage return gets a leading apostrophe,
so a spreadsheet shows it instead of running it as a formula; numbers are written as they are. It is UTF-8 without a
byte order mark.

## Checking the audit trail outside the API

Each entry of `bitacora/bitacora.json` has `empresaId, secuencia, momento, actorTipo, actorId, accion, recurso,
recursoId, exito, ip, userAgent, requestId, traceId, detalle, hashAnterior` and `hash`. The entries go from
`secuencia` 1 upward without gaps; the first `hashAnterior` is 64 zeros, and each later one is the `hash` of the entry
before. `hash` is the SHA-256, in hex, of the compact JSON array of the first fifteen values, in that order:

```python
import hashlib, json
anterior = "0" * 64
for numero, r in enumerate(json.load(open("bitacora/bitacora.json", encoding="utf-8")), start=1):
    campos = [r[k] for k in ("empresaId", "secuencia", "momento", "actorTipo", "actorId", "accion", "recurso",
              "recursoId", "exito", "ip", "userAgent", "requestId", "traceId", "detalle", "hashAnterior")]
    texto = json.dumps(campos, separators=(",", ":"), ensure_ascii=False)
    assert r["secuencia"] == numero and r["hashAnterior"] == anterior
    assert hashlib.sha256(texto.encode("utf-8")).hexdigest() == r["hash"], numero
    anterior = r["hash"]
```

A chain that closes on itself could have been rewritten whole. The anchors answer that: each file in
`bitacora/anclas/` is what the immutable storage kept that day (in S3, with Object Lock), with the `secuencia` and
the `hash` of the last entry then. That entry in the chain must have that hash.

## The deletion certificate

When a closed store is purged, a JSON certificate is written to the immutable storage at `certificados/<id>/borrado`
and its SHA-256 goes to the store's audit trail, which outlives the store, as the action `TIENDA_BORRADA`:

```json
{
  "formato" : 1,
  "tipo" : "certificado-de-borrado",
  "tienda" : 2,
  "bajaSolicitadaEn" : "2026-11-12T10:00",
  "borradaEn" : "2026-12-13T03:30:00.500",
  "emitidoEn" : "2026-12-13T03:30:04.100",
  "zonaHoraria" : "UTC",
  "filas" : { "procesos_compartidos" : 0, "usuarios" : 3 },
  "totalDeFilas" : 412,
  "almacen" : { "prefijo" : "empresas/2/", "objetosBorrados" : 9, "intento" : 1 },
  "ultimaExportacion" : { "id" : 4, "sha256" : "5e8a...", "listaEn" : "2026-11-20T08:00" },
  "conservadas" : [ "empresas", "bitacora", "anclas_bitacora" ]
}
```

It names the store only by its id, and nobody by anything. `filas` has one entry per table the purge deletes from;
`ultimaExportacion` is `null` when the store never exported. `objetosBorrados` counts what the attempt of the job
that issued it deleted, and `intento` says which attempt that was: from the second on, an earlier one may have deleted
some objects already. A store purged before the certificate existed, whose job was still queued, gets one with
`filas`, `totalDeFilas` and the dates `null`. The operator keeps it: the store has nobody left who can
sign in ([Security](security.md#the-life-of-the-data)).
