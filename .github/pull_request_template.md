## Summary

<!-- What changes and why, one bullet per commit. -->

## Frontend changes

<!-- What the web app, Postman or any other client has to change: new or removed fields, new errors, new endpoints.
     "None" is an answer. -->

## Security

<!-- For each new resource or endpoint, how each of these is covered, or why it does not apply: the store taken from
     the token and the block in the IDOR suite, the row in the role matrix, the history event, the request limit and
     the quota, and how its input is validated. -->

## Testing

<!-- The tests that failed before the change and pass after it, the mutations they catch, and what was run:
     `./mvnw -B clean verify` in both class orders, the end-to-end suite, k6. -->

## Checklist

- [ ] Every new rule comes with a test that failed first
- [ ] `./mvnw -B clean verify` passes, and each commit builds and passes on its own
- [ ] OpenAPI documents every new operation, and any change to the contract is listed under *Frontend changes*
- [ ] A record in `docs/adr/` if the change makes or reverses a decision
- [ ] The docs, the roadmap and `CHANGELOG.md` say what changed
