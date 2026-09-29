[← Documentation](README.md)

# Overview

## The problem

A single online order crosses several teams and outside companies: the storefront, a payment provider, the warehouse
and a carrier. When that workflow only lives in people's heads, handoffs break. Payments get captured for orders that
never ship, returns stall between teams, and every new hire learns the process by trial and error.

## The solution

The platform turns each workflow into a shared model that everyone reads the same way: who does each step, in which
order, where decisions are made, and what information is exchanged with customers and partners. Models use BPMN
(Business Process Model and Notation), the standard notation for business processes (ISO/IEC 19510), so any analyst
can read them.

A published model is not only a drawing: orders run on it. Opening a case walks the diagram step by step, leaves
each task in the tray of the role that has to do it, and takes the decisions the gateways describe, so the picture
on the wall and what the store actually does are the same thing.

## Who it is for

| Audience | What they get |
|---|---|
| Store owners and operations managers | One up-to-date map of how orders are handled, with the history of every change |
| Process editors and team leads | A modeling workspace that rejects design mistakes the moment they are made |
| Partner companies | Read-only access to the processes that a store decides to share with them |
| Developers | A documented REST API for back-office tools, with the included web app as a first client |

## Key concepts

| Term | Meaning | Example |
|---|---|---|
| Process | A workflow that the store runs again and again | Order fulfillment |
| Participant (pool) | A company or system that takes part in the process | The store, the customer, the payment gateway |
| Lane | A team or role inside a participant | Sales, Warehouse |
| Event | Something that happens: where the process starts, where it waits for a message, and where a path ends | Order received |
| Activity | A unit of work, done by a person, by the store itself, or to send or receive a message | Pick and pack items |
| Gateway | A point where the flow splits or merges. When it splits, an exclusive gateway takes exactly one path, an inclusive gateway takes every path whose condition holds, and a parallel gateway takes all of them. | Payment approved? |
| Sequence flow | The order of the steps inside a participant, with an optional condition | Payment approved? → Pick and pack items |
| Message flow | Information exchanged between two participants, sent from one step and awaited at another, with the fields it carries | Payment authorization request |
| Correlation key | The value that ties together the messages of one case | `orderId` |
| Diagnosis | What a diagram gets wrong against the modeling rules: errors and warnings, each one pointing at an element | *Nothing leads to "Pick and pack items"* |
| Published version | The diagram frozen the day it was published. It does not change when the model does: what is edited afterwards is the draft | Version 2 of *Order fulfillment* |
| Case | One run of a published version, from start to end | Order `ORD-1001` |
| Task | A step of a case that waits for a person, in the tray of its lane's role | *Pick and pack items* of `ORD-1001`, waiting for Warehouse |
| Membership | Which process roles a person belongs to, so they can ask for their own tray | *Ana* is in *Warehouse* |
| Case variables | What the case knows, and what the conditions of its gateways read | `payment.status`, `order.total` |

## How it works

1. **Register the store.** The store gets its private workspace and its first administrator.
2. **Invite the team.** The administrator adds users and gives each one an access level: administrator, editor or
   read-only. A user can be created without a password: the API answers a temporary one, once, and that person can
   do nothing until they change it.
3. **Define process roles.** Roles describe who does the work, such as *Sales* or *Warehouse*, and every process of
   the store can reuse them.
4. **Model the process.** Editors add the participants, a lane for each role, the events where the process starts
   and ends, the activities and gateways, the order between them, and the messages exchanged with other
   participants. Everything can be corrected afterwards without starting over: a step moves to another lane, an
   arrow is reconnected to a different step, and lanes and participants are reordered as a whole.
5. **Validate as you go.** Every change is checked against the modeling rules: a step that cannot send a message
   does not get one, and a message that may fail says what the process does then. A change that would break a rule
   is rejected with the reason, so a model never ends up in an invalid state.
6. **Ask what is missing.** At any moment the diagram can be checked against the whole catalogue of rules: what
   is unreachable, what has nowhere to go, which decision has no alternative path, which message nobody sends. The
   same question answers what would be left if an element were deleted, so a deletion can be confirmed knowing what
   it takes with it.
7. **Publish.** When the process is ready, publishing it saves the whole diagram as a version, which never
   changes again. Publishing is refused while the diagnosis finds errors. What is edited afterwards is the draft,
   and the process says so; publishing again saves the next version, and publishing without having changed anything
   is refused.
8. **Share.** An administrator can give a partner company on the platform read-only access to a process, for example
   a logistics provider that needs to see how orders are handed over. The guest reads the version in force, never
   the half-finished draft.
9. **Run it.** With a version published, an order is opened as a case, by hand or by the message that starts the
   process. It walks the diagram on its own until it needs somebody: each activity done by a person waits in the
   tray of its role, and completing it moves the case on. Gateways decide with the variables of the case, and
   everything that happens is written down, so a case that stops can be read instead of guessed at.
10. **Let the partners answer.** What the process sends to a payment gateway or a carrier goes to an outbox, and
    what comes back goes to an inbox. Nothing is real behind them: the partners are simulated, and the store's own
    clock, in ticks, decides when each message arrives. Moving the clock is what makes an order advance, and the
    same steps always give the same result.
11. **Keep track.** Every change is recorded in the process history with its author and date. Deleted items are
    retired, not erased, so the record stays complete.

The web app in [`frontend/`](../frontend/README.md) covers all of it: signing in and registering a store, the process list and
its detail, the diagram viewer with its published versions, a modelling editor with a live diagnosis, the
administration of users, roles, sharing and settings, and the operation —cases with their timeline, the tray of
tasks, the simulation clock and the dashboard. The [API](api-reference.md) is still the whole contract underneath,
and the [Postman collection](../postman/) walks it end to end.

## Built-in guarantees

| Guarantee | What it means for the business |
|---|---|
| Private workspace | A store's data is only visible to its own users. A request for another store's data is answered as if the data did not exist. |
| Access that follows the role | Each person can only do what their access level allows, and a change of role or a deactivation applies immediately. |
| Protected sign-in | Sign-in tokens are short-lived, a copied token is detected and its session closed, and repeated failed sign-ins are paused. |
| No lost work | When two people edit the same item, the second save is refused instead of silently overwriting the first. |
| No duplicates on retries | A create request that is retried with the same idempotency key, for example after a network failure, creates the item only once. |
| Always-valid models | The modeling rules are checked on every change, not only when a process is published. |
| What is published does not move | Publishing saves the diagram as a version that never changes. The model keeps being editable, the process says when the draft is ahead of it, and a partner store always reads what was published. |
| A case runs on the version it was opened with | Publishing a new version does not move an order that is already running: it finishes on the diagram it started with. |
| One task, one person | Two people completing the same task at the same time do not complete it twice: the second is told it is already done. |
| A case that stops says why | Every decision, task and missing variable is written down in order, so an order that did not move can be read and rescued instead of started over. |
| Nothing is lost between participants | What a process sends and what reaches it are both written down, with what was done with each one. A message that arrives for nobody is kept and explained, not dropped. |
| The same message twice does not count twice | A partner that repeats a message with the same identifier gets the first answer back instead of a second order. |
| Time that can be reproduced | Orders do not age with the wall clock: the store has its own clock in ticks, moved by whoever is testing. The same steps always give the same result. |
| Partners that behave the same way twice | What the simulated gateway, carrier and notifier decide comes from the store's seed, not from chance. The same demo shown twice gives the same rejections, the same lost parcels and the same amounts. |
| Complete history | Every change keeps its author and date, and deleted items stay on record. The store reads its own history: users, roles, processes and its registration, in one place. |
| Nothing breaks by surprise | A diagram can be checked against the rules at any moment, and before deleting anything it says what would go with it and what would be left without a path. |
| A second opinion | A model can review a diagram and point out what is missing, such as a decision with no alternative path. It only advises: nothing is changed without a person. |
