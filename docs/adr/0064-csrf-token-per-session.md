[← Decision records](README.md)

# ADR-0064: The CSRF token lasts the session

- **Status:** Accepted
- **Decided in:** a fix after plan v5 PR 60, refining [ADR-0029](0029-browser-session-in-cookies.md)

## Context

ADR-0029 puts the CSRF token in the `XSRF-TOKEN` cookie, which the web copies into `X-XSRF-TOKEN`. Every request
that carried the session cookies answered a new token: the old cookie cleared and a new one set.

Spring Security changes the token when someone authenticates. Its `SessionManagementFilter` runs the session
strategies, `CsrfAuthenticationStrategy` among them, whenever it finds an authentication that the security context
repository did not have. The API is `STATELESS`, and for a stateless chain that filter's repository is a
`NullSecurityContextRepository`, which never has anything. `JwtAuthenticationFilter` authenticates every request from
the access cookie, so to Spring every request was a new login.

The web sends several requests at once. A change could read the token, and leave after a parallel read had answered
the next one: the header said one token, the cookie the other, and the API answered `403` "Sin token CSRF". The
end-to-end test that switches the language failed that way now and then: the preference was not saved, and the page
in the new language read the old one and went back.

Changing the token on every request protects nothing more. An attacker cannot read the cookie of another origin, and
the double-submit check compares the cookie with the header of the same request.

## Decision

- Spring no longer changes the token: the CSRF configuration hands the session management a
  `NullAuthenticatedSessionStrategy` instead of its `CsrfAuthenticationStrategy`.
- `CookiesDeSesion` changes it when a session opens and when it closes, through the same `CookieCsrfTokenRepository`
  the CSRF filter uses, so the cookie is the same one:
  - opening: the login with the password, its half-done step before the second factor, finishing it with the code or
    a passkey, signing in with a passkey, coming back from a provider, confirming a link with one, and the password
    change, which closes every session and opens a new one;
  - closing: logging out, closing the current session from the list of sessions, and a refused renewal, which clears
    the cookies.
- Renewing the session (`POST /api/v1/auth/refresh`) and confirming who you are again keep the token: it is the same
  session.
- The login, the confirmation of a link and every change that carries the session cookies still need the token.

The other way out was to keep Spring's strategy and stop the filter from seeing a login on every request, by saving
the context in a repository the filter reads. The controllers that open sessions would still have had to change the
token by hand, because a password login does not authenticate through a Spring filter; and the OIDC callback would
have changed it twice. One place that opens and closes sessions is easier to follow.

## Consequences

- A token planted in the browser before the login, from a sibling subdomain for instance, is replaced by the login's
  answer itself, not by the next request.
- A client that reads the token before signing in has to read it again after: the curl examples of the
  documentation do. The end-to-end helpers, k6 and the Postman collection choose their own token and send it in both
  the cookie and the header, so nothing changes for them.
- The web stays in its language when saving it fails, and says so: going to the page of the new language would only
  bring the old one back.

## Verification

- `TokenCsrfDeLaSesionIntegracionTest`, with the whole application and a cookie jar that behaves like a browser's:
  two reads after the login do not touch `XSRF-TOKEN`, a change that left with the token read before them is
  accepted, the login and the logout each set a new token, and renewing does not. Without the
  `NullAuthenticatedSessionStrategy`, the reads and the renewal set a new one and the test fails.
- `RecorrerEnInglesTest`, end to end, switches the language and walks the app in English.
