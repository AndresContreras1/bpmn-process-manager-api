[← Decision records](README.md)

# ADR-0049: Passkeys without an HTTP session, on tables of the store

- **Status:** Accepted
- **Decided in:** plan v5 (PR 49b)

## Context

Spring Security 7 brings WebAuthn: the `webAuthn()` DSL, which registers and checks passkeys, and two JDBC
repositories with their own schema (`user_entities` and `user_credentials`). Neither fits the API as it is:

- The DSL keeps the options of each ceremony, and so its challenge, in the HTTP session, and on success it signs the
  person into Spring's session. The API has no HTTP session: it is stateless behind the session cookies of
  ADR-0029, and the answer of a device can reach another instance than the one that sent the options.
- The tables of the JDBC repositories have no `empresa_id`. They would be the only tables of a person without row-level
  security (PR 51), outside the deletion of a closed store (PR 35) and outside the anonymization of a person.

## Decision

- The API uses the parts of Spring Security that do the work, `Webauthn4JRelyingPartyOperations` and its model and
  Jackson module, from its own controllers. The sessions come out of the same `AperturaDeSesion` as the login with
  the password, in the same cookies.
- The challenge of each ceremony lives in `desafios_webauthn`, as the OIDC requests of PR 50 do: the browser gets an
  id of 256 bits, of which only the SHA-256 is kept, together with the purpose (register, verify or sign in), the
  person when it is known and what is needed to rebuild the options. It works once and for five minutes.
- The credentials live in `passkeys`, with the store of their owner, row-level security and their line in the
  deletion of a store. The user handle is `usuarios.webauthn_id`, 64 random bytes created with the first passkey.
  `CredencialesWebAuthn` and `PersonasWebAuthn` implement Spring's two repository interfaces over them.
- Every ceremony asks the device to verify the person (fingerprint, face or PIN) and to keep a discoverable
  credential, and asks for no attestation. A passkey counts as two factors: as the second one after the password,
  and alone to sign in without a password.

## Consequences

- Spring's model of the options can be written to JSON but not read back, so the API keeps only the challenge, the
  person and the credentials it offered, and rebuilds the options from them with the same fixed parts.
- The API decides nothing about the make of a device: with no attestation, any authenticator that verifies the
  person is accepted. Asking for one would need a list of trusted models (FIDO MDS), which a small business does
  not need.
- Moving to the DSL later would mean a session store shared by every instance, such as Spring Session on
  PostgreSQL, and a translation from Spring's session to the cookies.
