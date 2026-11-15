# End-to-end tests

A real Chrome, driven by Selenium, against the stack of containers. The web app has unit tests of its own for the
logic that needs no screen; what these check is that the pieces fit together, which is the thing unit tests of a
frontend tend not to catch.

```bash
docker compose up -d --build --wait   # from the root of the repository
./mvnw -B -f e2e/pom.xml test
```

`E2E_BASE_URL` and `E2E_API_URL` say where to look; without them it assumes the stack on port 80.

Chrome asks for the web in Spanish of Colombia, the language of a new organization, so NGINX serves the `es-CO`
build and the scenarios read Spanish. Everything is found by `id`; the texts they check are the Spanish ones.

## What each scenario covers

| Class | What it walks through |
|---|---|
| `EntrarYCrearProcesoTest` | A wrong password is refused and says so; the right one gets in, creates a process, and finds it in the list as a draft |
| `ModelarYPublicarTest` | A half-modelled diagram cannot be published; in the modeler a task is added from the context pad of the start, named, and connected to the end, and the diagnosis marks it until its type is changed to a user task; it saves by itself, the diagnosis drops to zero errors and the modeler publishes version 1, whose task and two events the viewer counts; and then version 1 is retired from the banner of the frozen diagram, which is the one change a published version accepts |
| `ModeladorBpmnTest` | The modeler draws what the process has, a name changed on the canvas reaches the API with nobody pressing save, and the bpmn.io logo links to bpmn.io with nothing on top of it |
| `VistaEstructuraTest` | The structure view without a mouse: the arrows walk the tree; a task is created in a lane, connected, moved to another lane, renamed and deleted from the keyboard, each saved; axe-core finds nothing serious with the tree open, and every target of the context pad is at least 24 × 24 px |
| `PresenciaYComparacionTest` | Two people on one process: the second one opens the modeler read-only, sees who holds the lock and takes control; a save that finds a newer one offers to compare, lists what was added, changed and removed, and reloads; and the comparison page shows what changed between a version and the draft |
| `RepositorioDeProcesosTest` | A folder is created beside the list and a process is filed in it from its filing card, with tags and a classification; the folder and a tag find it in the list, and the classification reaches the API; a folder that still holds a process is not deleted |
| `CompartirYAdministrarTest` | A process is published and shared by tax id; the other store finds it in *shared with your store*, opens it, is told it is the version in force, and has no edit or model button. An administrator creates a role and adds someone, whose temporary password is shown once and is gone after a reload. The search box leaves one row of two; the panel of process roles comes back with what was saved already ticked; somebody is deactivated, disappears from the list, is found again with *Show deactivated* and brought back. An editor, signed in as herself, sees neither the users menu nor the store history, and the users screen tells her whose it is |
| `DocumentacionYAdjuntosTest` | From the Documentation button of a published process: Markdown typed, previewed as the HTML the API returns and saved; the characterization sheet filled and saved; a text file attached with the button that reaches *available* in the queue (the stack has no ClamAV) and downloads through its signed link with the same bytes; the manual generated, waited for and downloaded as a PDF. Someone read-only sees the saved documentation and sheet, with no editor, drop zone or button to ask for the manual. axe-core over the four tabs, with the preview and a document's versions open, in both themes |
| `OperarUnPedidoTest` | An order from end to end: it is opened from the cases, the case page shows the token standing on the task that is waiting, the task is taken and completed from the tray, and the case finishes with its three nodes marked while the dashboard counts one order and nothing wrong. A second scenario moves the clock and then saves the partners, in that order, which is what finds out whether the panel read their version again |
| `RecorrerEnInglesTest` | A browser in English: without a session the home page comes in English; signing in to an organization that works in Spanish opens the web in Spanish; picking English in the header saves it and reopens the same screen in English; a link without a language goes to English, the process reads *Draft* and the account shows its date in English; following the organization again brings the web back to Spanish |
| `SesionEnPestanasTest` | Nothing the browser stores says who signed in, and a reload keeps the session; with two tabs open, signing out in one takes the other to the sign-in page, which says it happened in another tab |
| `SeguridadDeLaCuentaTest` | A passkey registered from the account with Chrome's virtual authenticator, which brings the recovery codes, signs in without a password and then completes the half-done login of the password; an authenticator app turned on with its first code computed from its key, after which the password alone is not enough, a wrong code is refused and a recovery code gets in, leaving nine |
| `AccesibilidadTest` | axe-core over the home page, the sign-in and the registration, and over every screen of the sidebar with an administrator, in the light theme and then in the dark one, switched from the header as a person would: a serious or critical violation of WCAG 2.1 AA fails the test and names the rule and the elements |

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

**Accessibility is checked with axe-core** (`com.deque.html.axe-core:selenium`), the engine behind most
accessibility tools. Only serious and critical violations fail: those are the ones that leave someone out.

**Selenium 4 finds its own driver.** Selenium Manager downloads the one that matches the installed Chrome, so there
is no WebDriverManager and no driver to keep up to date.

## What is not here yet

Nothing of the messaging: a batch of orders from the simulated customer needs a process that starts with a message,
and building one through the API is a fixture of its own. The clock is covered without them, because it can be
moved with nothing in the trays.

**A form is sent with Enter when its button sits below the edge of the window.** The headless browser does not
scroll the page by itself, so the click would be lost; a person presses Enter there anyway, so the test does the
same thing a person does.
