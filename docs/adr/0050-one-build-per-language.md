[← Decision records](README.md)

# ADR-0050: One build of the web per language, chosen by NGINX

- **Status:** Accepted
- **Decided in:** plan v5 (F12)

## Context

The web was written in English only, and the product is for organizations in Colombia, the United States, Australia
and France. Each of them reads its own language, and also its own dates, numbers and currency: 1.234,5 in Bogotá and
Paris, 1,234.5 in New York and Sydney. Angular offers two ways to translate:

- `@angular/localize` replaces the texts when compiling: one build per language, with nothing to look up at run time
  and with the dates, numbers and currency of its locale.
- A library that loads the translations in the browser (ngx-translate, Transloco) keeps one build and switches at
  run time, at the price of a lookup per text, of keys instead of texts in the templates and of a locale that Angular's
  pipes do not follow.

## Decision

- The web uses `@angular/localize`, with English (en-US) as the language of the source and four builds: `es-CO`,
  `en-US`, `en-AU` and `fr-FR`. Each one lives in its folder, with its `<base href>` and its `lang`, and its
  currency: COP, USD, AUD and EUR. A text without its translation stops the build (`i18nMissingTranslation`).
- The templates mark their texts with `i18n`, the code with `$localize`, and plurals are whole ICU messages, so that
  each language can agree in number. The translations are JSON files in `frontend/src/locale`, next to the
  `messages.json` that `ng extract-i18n` writes.
- NGINX chooses the build. A path inside a folder is served by that build; any other path, a link without a language
  among them, is redirected to the folder of the reader's language: the cookie `idioma` that the web sets when someone
  picks one, and without it the first language the browser asks for in `Accept-Language`. Without anything close,
  Spanish of Colombia.
- The API keeps the language and the time zone of each organization and, optionally, of each person
  (`/api/v1/auth/preferencias`, `/api/v1/empresas/actual/preferencias`). After signing in, the web opens in the
  language that holds for the person; what the person or an administrator chooses is saved there and in the cookie.
  An organization starts in the language of whoever registers it, and the e-mails go in the language of whoever
  receives them, with a link to the web in that language.
- Every date the API returns is UTC, the API pins the JVM to UTC, and the web shows it in the time zone that holds for
  the person, an IANA name such as `America/Bogota`, with `Intl.DateTimeFormat`.

## Consequences

- Switching the language loads another build: the page is reloaded in the other folder, at the same screen. It
  happens when someone picks a language, not while they work.
- The image carries four builds. Each one is served alone, so what a browser downloads does not grow.
- `ng serve` serves only the source language, at the root; the switcher then only sets the cookie. To see another
  language in development: `ng serve --configuration=development --localize=es-CO`, or the container.
- The texts that the API writes, such as the errors and the history, stay in Spanish; translating them is PR 80.
