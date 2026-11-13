[← Decision records](README.md)

# ADR-0066: Documentation in Markdown and the process manual

- **Status:** Accepted
- **Decided in:** plan v5 (PR 62)

## Context

A process is documented by those who run it: what each step means, what it takes, what can go wrong. That text has
to be easy to write, safe to show to everyone in the store, and printable as a manual that people can sign off.

## Decision

- **Markdown, converted on the server.** commonmark-java with tables writes the HTML; raw HTML inside the Markdown is
  escaped, and the result goes through a whitelist (OWASP Java HTML Sanitizer): paragraphs, headings, lists,
  emphasis, code, quotes, tables and links to http, https or mailto with `nofollow noopener noreferrer`. No images,
  styles or scripts. Both the Markdown and the cleaned HTML are kept, so showing it never depends on converting it.
- **One text per process and per element,** by `bpmnId`. A text outlives its element, so the manual of an older
  version still has it.
- **A characterization sheet** per process, with nine free-text fields, edited whole with its version.
- **The drawing comes from the web.** The server does not draw BPMN: right after publishing, the modeler sends the
  SVG bpmn-js makes, and the server rebuilds it with a whitelist of SVG elements and attributes, read through
  `SecureXml`. A version keeps the first drawing it gets.
- **The manual is built in the job queue,** in the language of whoever asks: a Thymeleaf template with no external
  resource becomes HTML, and openhtmltopdf prints that HTML as PDF with Batik for the SVG and a URI resolver that
  loads nothing. Both files go to the storage of ADR-0065 and download through signed links.
- **Printing holds no transaction.** The job reads what it needs in one short read-only transaction, prints and
  stores without touching the database, and notes the result in a second short transaction, only if the manual is
  still pending. A slow print can never reach PostgreSQL's one-minute `idle_in_transaction_session_timeout`.
- **PDFBox never looks at the host's fonts.** The manual uses the 14 standard PDF fonts, which the reader supplies.
  PDFBox only needs a font to measure and draw them, and it gets the Liberation Sans it ships with. Without this, the
  first manual scans every installed font and writes a cache to the home folder: minutes on a machine with many
  fonts, and an output that depends on the machine.

## Consequences

- A version published before this, or whose drawing did not arrive, has a manual that says it has no drawing.
- Asking for a manual is exporting the process (`EXPORTAR`); reading one is viewing it.
- Batik and PDFBox make the API image bigger; they only run inside the job that builds a manual.
