[← Decision records](README.md)

# ADR-0065: Object storage, documents and the daily anchor

- **Status:** Accepted
- **Decided in:** plan v5 (PR 61)

## Context

A process needs its documents: instructions, contracts, forms. A file from a user is the riskiest thing an API takes
in: it can lie about its type, carry a virus, open into gigabytes, or run as a page if a browser shows it. The audit
trail was chained since PR 31, but whoever could rewrite the whole table could also recompute every hash.

## Decision

- **A port, `Almacen`, with two adapters:** S3 (AWS SDK v2 over the JDK HTTP client) and a disk folder for development
  and single instances. Every key starts with the store (`empresas/<id>/...`) and never carries a file name.
- **SeaweedFS in compose, not MinIO.** The plan said MinIO, but MinIO stopped publishing its images; SeaweedFS speaks
  the same S3 API, Object Lock included, and its image is pinned by digest. In AWS the same code points at S3.
- **Uploads are the raw body, streamed** to a temporary file under the 25 MB cap while hashing it. No multipart: the
  API and NGINX cut at the cap without reading the rest, and NGINX does not buffer it on disk. Temporary files live
  in a folder of the API's own (`ARCHIVOS_TEMPORALES`), not the shared one, and each is deleted when it is done.
- **The type comes from the content,** with Tika's byte signatures against a whitelist; an Office or OpenOffice
  document is told apart by its index. The name is cleaned and takes the extension of the real type.
- **A document is reviewed in the queue,** `PENDIENTE` until ClamAV (by clamd's `INSTREAM`) and, for a container, a
  real decompression with limits move it to `DISPONIBLE` or `BLOQUEADO`. Without an antivirus configured it becomes
  available marked as not reviewed, unless `DOCUMENTOS_EXIGIR_ANTIVIRUS` says to wait.
- **Downloads are signed links served by the API,** five minutes, HMAC-SHA256 over store, document, version and
  expiry, with a key derived from the API secret. The plan said a presigned S3 URL; serving it from the API keeps the
  storage off the internet and lets every download carry `attachment`, `nosniff` and a sandbox policy. SVG and HTML
  go out as `application/octet-stream`.
- **The anchor:** once a day one instance writes each store's last audit-trail link to a bucket with Object Lock in
  compliance mode. Verification reads the latest anchor from the store, not from its table row.

## Consequences

- A store's quota counts every version, blocked ones included, until plans arrive (PR 79).
- Closing a store deletes its files through the job queue, which retries; the anchors stay with the audit trail.
- The EICAR test file is only ever sent from memory in the tests: a Windows host antivirus quarantines it on disk.
