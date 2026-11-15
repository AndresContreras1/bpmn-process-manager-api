[← Decision records](README.md)

# ADR-0070: Full-text search in PostgreSQL

- **Status:** Accepted
- **Decided in:** plan v5 (PR 65), with D31 (authorization) and D33 (documents)

## Context

A store with a few hundred processes, their elements, attached documents and comment threads needs one box that finds
a word in any of them, in the store's language, without showing what the person may not view. The data already lives
in PostgreSQL behind row-level security; a separate search service (OpenSearch) would be a second copy to keep in sync
and to secure, and it stays out of 1.0.

## Decision

- **PostgreSQL full-text search, nothing else.** Each searchable thing stores a `tsvector` with a GIN index:
  `procesos.busqueda`, `comentarios.busqueda`, `versiones_documento.busqueda` and the new table `elementos_buscables`.
  Weights: names and titles `A`; descriptions, category, tags, conditions and message contents `B`; bodies
  (documentation, comments, the text of a file) `C`.
- **One configuration per language, with `unaccent`.** `busqueda_es`, `busqueda_en` and `busqueda_fr` copy
  PostgreSQL's `spanish`, `english` and `french` and put the `unaccent` dictionary before the stemmer for words with
  non-ASCII letters. The store's language (`configuracion_tienda.idioma`, V40) picks one through
  `configuracion_de_busqueda(empresa_id)`; without a configuration, Spanish. `unaccent` is a trusted extension, so
  the database owner creates it without being a superuser.
- **No immutable wrapper around `unaccent()`.** `unaccent()` is only `STABLE`, which is why generated columns and
  expression indexes usually wrap it in a function declared `IMMUTABLE`. Here the vectors are written by triggers and
  indexed as plain columns, so nothing needs that promise; and putting `unaccent` inside the configuration, instead of
  around the text, lets `ts_headline` normalize the original text the same way: it highlights "crédito" as written
  when someone searches "credito". The Spanish stemmer drops some accents by itself, but not all of them ("frágil"
  keeps its accent and "campaña" its ñ): `unaccent` covers those, and every accent in English and French.
- **Triggers, not generated columns, keep the vectors current in the same transaction.** A generated column only sees
  its own row, and these vectors depend on other tables: the store's language, a process's documentation, an
  element's documentation by `bpmn_id`. So:
  - `procesos` and `comentarios` compute their vector `BEFORE INSERT OR UPDATE` when their text changes; a deleted
    comment (`eliminado_en`) has none.
  - The six tables of the draft (pools, lanes, nodes, flows, messages, artifacts) keep their row in
    `elementos_buscables` with one trigger function that reads the row as JSON; moving an element does not touch it,
    and one deactivated leaves the index.
  - The elements of the published version come from the diagram the version stores (D2), read with `jsonb_each`
    whenever `procesos.version_publicada` changes: on publishing and on retiring.
  - `documentacion` refreshes what it documents: the process with an empty `bpmn_id`, otherwise its elements in the
    draft and in the published version.
  - Changing the store's language does not rebuild anything in the request that changes it: a store with a lot of
    document text would pass the 30-second statement timeout, and the change would fail every time. `gestion`
    publishes `IdiomaDeTiendaCambiado` inside that transaction (only when the two-letter language changed: `en-US` and
    `en-AU` stem the same way), and `modelado` queues a `busqueda.rehacer` job. The job walks each table by id ranges
    with `rehacer_busqueda(empresa, tabla, desde, cuantos)`, each range in its own short transaction: 500 processes,
    elements or comments at a time, 10 document versions. It is idempotent, so a retry after a failure starts over.
  The migration indexes what already exists, and queues reading the text of the documents already available.
- **The text of a file is read in the job queue, after the antivirus.** When a version becomes `DISPONIBLE` and its
  type has text (PDF, plain text, CSV, Markdown, DOCX, XLSX, PPTX, ODT, ODS), the queue reads it, never before. Only
  the parser modules for those types are added (`pdf`, `microsoft`, `miscoffice`, `text`), not the standard package
  with its dozens of formats, and without what they bring for e-mail, Outlook and Access files, which the whitelist
  refuses, and PDFBox's command-line tools. The input is untrusted:
  - each type goes to its own parser, chosen by the type detected on upload, not by what Tika would guess;
  - embedded documents are never opened, and a PDF never goes through OCR;
  - the text stops at `DOCUMENTOS_TEXTO_CARACTERES` characters (1,000,000; up to about 4 MB of UTF-8), whitespace and
    control characters collapsed;
  - a PDF keeps at most 32 MB of what it decompresses in memory; the rest goes to PDFBox's scratch files in the JVM's
    temporary folder (`java.io.tmpdir`), which it deletes when the document closes. They do not go to
    `ARCHIVOS_TEMPORALES` because Tika gives no way to choose their folder; in the container that folder is the API's
    own in-memory `/tmp`, which nothing else writes to;
  - each file gets `DOCUMENTOS_TEXTO_ESPERA` (30 s), counted from the moment it takes one of the reading slots
    (`DOCUMENTOS_TEXTO_HILOS`, 2), not while it waits for one. The thread that reads it is interrupted when time is up.
    A parser that ignores the interruption keeps its thread, so after a 5-second grace that thread is counted as stuck,
    logged as a warning, and its slot is given back: a file crafted to hang a parser takes one thread, but the files
    after it are still read. At most 8 stuck threads are replaced; past that, a stuck thread keeps its slot until it
    ends, so a flood of such files cannot start threads without end. If no slot becomes free within the same time, the
    file is not marked as read: the handler throws a `TrabajoAplazadoException`, and the queue puts the job back after
    its base wait without spending an attempt, so a busy pool never turns a valid file into one searchable by name only
    or ends its job `FALLIDO`. Running the parser in another process, like Tika's own server does, is the stronger
    fence and is left for later;
  - XML inside OOXML and ODF goes through Tika's and POI's hardened readers (no DTDs, no external entities), and the
    ZIP inside was already checked for bombs before the antivirus (ADR-0065);
  - PDFBox reads with the fixed fonts of the manual (ADR-0066), so a font the PDF does not embed never makes it scan
    the machine's fonts.
  A file that cannot be read is searchable by its name only: the reason is logged and the job ends, it is not retried.
  If the storage does not answer, the job fails and the queue retries it like any other. The text is written once
  (`texto_extraido_en`) and is not mapped in the entity, so listing versions never loads it. A text whose vector
  would pass PostgreSQL's 1 MB limit for a `tsvector` indexes its first hundred thousand characters.
- **One endpoint, filtered by store and by permission in the same query.**
  `GET /api/v1/busqueda?q=&tipo=&pagina=&tamano=` is open to every role, like any read; it has no resource in its
  path, so the route filter does not decide it. It is the only read with a rate-limit bucket (`BUSQUEDA`, 60 a minute
  per person): every search ranks and highlights text in the database, and repeating one costs nothing to the
  caller. The filter matches the route on its decoded path, as Spring MVC does, so an encoded letter in the path does
  not skip the bucket. The service asks `Permisos.visibilidad` once, the same answer the process list uses, and the
  query keeps only active processes the person can `VER` and what hangs from them. Row-level security still filters
  the store; another store's content is simply not there.
- **Two queries, both read-only.** The first ranks what matches among what is visible and keeps one page, with the
  total; the second brings what to show for that page. Each builds the `tsquery` once, in a materialized CTE with the
  store's configuration, and every branch reuses it. Ranking is `ts_rank_cd` normalized by length: a name beats a
  description, a description beats a body, and a long file does not win by being long. An element in both the draft
  and the published version is one hit, the draft's when they tie.
- **`websearch_to_tsquery`, never `to_tsquery`.** It understands quotes, `-` and `or`, and never fails on syntax. An
  empty text, or one made only of stop words, finds nothing and answers an empty page.
- **The fragment is never HTML.** `ts_headline` marks the matching words with two Unicode private-use characters
  (U+E000 and U+E001) that are removed from the text first, so they cannot come from it; the service splits the
  fragment by them into plain-text segments `{texto, resaltado}`. The web shows each one as text. `ts_headline`
  itself drops the tags it recognizes and keeps anything else as written, `<img src=x onerror=...>` included, which is
  why its output is never treated as markup.

## Consequences

- Words are matched by their stem in the store's language: a store in English does not stem Spanish words. Right
  after a store changes its language, until its job finishes, the search already uses the new language while part of
  the vectors still has the old one's stems: those rows are found only by the words both languages stem the same way.
- The fragment of a document, and of a process's or element's documentation, comes from its first 20,000
  characters, so a page of 50 hits never makes `ts_headline` re-read millions of characters; a match further in still
  finds the hit, with a fragment from its beginning.
- A stuck parser thread still runs, and uses CPU, until its parser returns or the API restarts. More than 8 crafted
  files stuck at once keep their slots: the other files wait in the queue, postponed without spending attempts, until a
  slot frees up.
- A search is bounded by its 200 characters, the page of 50, the per-person bucket and the 30-second statement
  timeout of the API's role.

- The global search box with Ctrl+K and the read-only portal (F19, F22) use this endpoint.
