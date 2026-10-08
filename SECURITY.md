# Security policy

## Reporting a vulnerability

Please report a vulnerability privately, through
[a security advisory](https://github.com/AndresContreras1/bpmn-process-manager-api/security/advisories/new) in this
repository. Do not open a public issue, a pull request or a discussion about it: a public report hands the problem to
everyone before there is a fix.

A useful report says:

- what an attacker can do, and what they need first (an account, a role, another store's data);
- the steps or the request that shows it, with the version or the commit you tested;
- what you expected to happen instead.

You will get an answer within five working days, and news at least every two weeks until it is solved. Once a fix is
published, the advisory says what was wrong and, if you want, who found it.

## What is in scope

- The API, including authentication, the isolation between stores, the roles and the sharing between stores.
- The web app and what NGINX serves.
- The container images, the Compose files and the GitHub workflows of this repository.

Out of scope: findings that need a compromised server or browser, denial of service by volume, social engineering,
and reports produced by a scanner without a way to exploit them.

## Supported versions

Only the latest commit of `main` gets fixes. There are no maintained release branches yet.

## Testing safely

Test against your own copy, started with `docker compose up` or `./mvnw spring-boot:run`. Do not test against a
deployment you do not run, do not read or change data that is not yours, and stop as soon as you can show the
problem.

## How the project protects itself

[Security](docs/security.md) describes the controls of the API and the checks of the supply chain, and the
[runbook](docs/runbook.md) what to do when one of them raises an alert.
