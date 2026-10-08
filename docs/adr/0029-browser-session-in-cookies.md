[← Decision records](README.md)

# ADR-0029: The browser's session in HttpOnly cookies

- **Status:** Accepted
- **Decided in:** plan v5 (D29)

## Context

The web app kept the access token and the refresh token in `localStorage` and sent the first one in
`Authorization: Bearer`. Any script that runs in the page can read `localStorage`: one XSS in the app or in one of
its dependencies would take both tokens away, and the refresh token renews the session for a week from any machine.
A strict Content Security Policy makes an XSS harder; it does not make it impossible.

Keeping the JWT in `localStorage` is what the course teaches; this decision goes against it on purpose.

## Decision

- The login, the renewal and the password change answer the session in two cookies the page's JavaScript cannot
  read: `__Host-acceso`, with the JWT, for the whole API, and `__Secure-refresco`, with the refresh token, only for
  `/api/v1/auth`. Both are `HttpOnly`, `Secure` and `SameSite=Lax`, and each one lives as long as its token. The body
  says who signed in and when the access expires; it never carries a token.
- The API authenticates from the access cookie. `Authorization: Bearer` is left for API keys, which do not exist yet.
- What changes something and carries the session cookies, and the login itself, has to send the CSRF token: the
  value of the `XSRF-TOKEN` cookie, which only pages of the app's own origin can read, in the `X-XSRF-TOKEN` header.
  Angular's `HttpClient` does it by itself; `GET /api/v1/auth/csrf` sets the cookie, and the app asks for it when it
  starts.
- Logging out answers `204` and clears the cookies even when the access token has expired, so a browser can always
  leave. The refresh cookie it carries is proof enough to close its session.
- The JWT follows RFC 8725: `iss`, `aud`, a `jti`, and in its header the `kid` of the key that signed it. A previous
  key stays accepted while the key rotates.

## Consequences

- An XSS can still act inside the page while it is open, but it cannot take the session with it.
- A client that is not a browser has to behave like one: keep the cookies and send the CSRF header. The end-to-end
  suite, the load tests, the Postman collection and the curl examples do it by hand.
- Browsers accept `Secure` cookies over plain HTTP only from `localhost`: anywhere else the app needs HTTPS, which
  production has.
- Rotating `JWT_SECRET` does not sign anybody out: the old key goes to `JWT_PREVIOUS_SECRET` for the fifteen minutes
  an access token lives.

## Verification

- `AuthControllerTest`: the attributes of both cookies, a body without tokens, the `403` without the CSRF token, a
  refused renewal that clears the cookies, logging out without a session, and the `XSRF-TOKEN` cookie.
- `SeguridadIntegracionTest`: a valid token in `Authorization: Bearer` opens nothing; a change with the session
  cookie and without the CSRF token answers `403` and changes nothing; the refresh cookie alone closes its session.
- `JwtServiceTest`: the `kid`, `iss`, `aud` and `jti` of every token; another audience, another issuer, an unknown
  `kid` and an unsigned token refused; a key rotated without closing sessions.
- The end-to-end suite signs in through the web app with the cookies and the CSRF token, as a person does.
