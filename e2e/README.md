# End-to-end tests

A real Chrome, driven by Selenium, against the stack of containers. These are the only tests the web app has: what
they check is that the pieces fit together, which is the thing unit tests of a frontend tend not to catch.

```bash
docker compose up -d --build --wait   # from the root of the repository
./mvnw -B -f e2e/pom.xml test
```

`E2E_BASE_URL` and `E2E_API_URL` say where to look; without them it assumes the stack on port 80.

## What each scenario covers

| Class | What it walks through |
|---|---|
| `EntrarYCrearProcesoTest` | A wrong password is refused and says so; the right one gets in, creates a process, and finds it in the list as a draft |
| `ModelarYPublicarTest` | A half-modelled diagram cannot be published; a task is added from the palette, named, and connected to the start and the end; the diagnosis drops to zero errors and the process is published as version 1, with its task and its two events counted by the viewer |
| `CompartirYAdministrarTest` | A process is published and shared by tax id; the other store finds it in *shared with your store*, opens it, is told it is the version in force, and has no edit or model button. An administrator creates a role and adds someone, whose temporary password is shown once and is gone after a reload. An editor, signed in as herself, sees neither the users menu nor the store history, and the users screen tells her whose it is |
| `OperarUnPedidoTest` | An order from end to end: it is opened from the cases, the case page shows the token standing on the task that is waiting, the task is taken and completed from the tray, and the case finishes with its three nodes marked while the dashboard counts one order and nothing wrong. A second scenario moves the clock and then saves the partners, in that order, which is what finds out whether the panel read their version again |

## Why it is built this way

**It is a module of its own, outside the root pom.** `./mvnw verify` never sees it, so the normal build does not
grow by a browser.

**The data comes from the API, not from clicking.** Preparing a scenario by clicking would make the test about
publishing fail because the form for adding users broke, and then the failure would say nothing. Only what the test
is about is done through the browser.

**Every identifier is unique per run and per class.** Per class, so two scenarios running at the same time do not
fight over a tax id. Per run, because a store is never deleted: with fixed identifiers, the second run against the
same database fails with `409`, which is exactly what happened the first time this suite was run.

**Locators are `id` and `data-testid`, never a Bootstrap class or the shape of the HTML.** The frontend puts an
`id` on what is unique and a `data-testid` on what repeats, precisely so that changing how a screen looks does not
break a test about what it does.

**There is no `Thread.sleep`.** A fixed wait is either too long or too short depending on the day. Every wait here
is for a condition of the page, and after ten seconds the failure says what it was waiting for.

**A failure leaves a screenshot in `target/e2e/`.** An end-to-end failure without the screen is a guessing game:
the message says what did not appear, and the picture says what appeared instead. Both bugs this suite found on its
first run were diagnosed from those images.

**Selenium 4 finds its own driver.** Selenium Manager downloads the one that matches the installed Chrome, so there
is no WebDriverManager and no driver to keep up to date.

## What is not here yet

Nothing of the messaging: a batch of orders from the simulated customer needs a process that starts with a message,
and building one through the API is a fixture of its own. The clock is covered without them, because it can be
moved with nothing in the trays.

**A form is sent with Enter when its button sits below the edge of the window.** The headless browser does not
scroll the page by itself, so the click would be lost; a person presses Enter there anyway, so the test does the
same thing a person does.
