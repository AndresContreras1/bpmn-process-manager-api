[← Documentation](README.md)

# Execution and simulation

## Running a process

A case is one run of a published version: an order. It is opened on the version in force, with the reference its
messages will be matched by and whatever the process already knows about it, and from there it walks the diagram
on its own until it needs somebody.

```bash
# Open an order on the version in force
curl -s -X POST http://localhost:8080/api/v1/procesos/1/casos -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" \
  -d '{"referencia":"ORD-1001","variables":{"payment":{"status":"APPROVED"}}}'

# What the store is waiting for, and who has to do it
curl -s "http://localhost:8080/api/v1/tareas?rolProcesoId=2" -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"

# Complete it; whatever is handed over lands in the case variables under tarea.pickAndPackItems
curl -s -X POST http://localhost:8080/api/v1/tareas/77/completar -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"datos":{"packedItems":3}}'

# The case with the steps it went through, and its timeline
curl -s http://localhost:8080/api/v1/casos/42 -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"
curl -s http://localhost:8080/api/v1/casos/42/eventos -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"
```

What each node does when a case reaches it:

| Node | What happens |
|---|---|
| Start event | Completes at once and puts one token on each of its outgoing flows |
| Activity done by a person | Waits in the tray of its lane's process role until someone completes it |
| Activity that sends a message, or one done by the store with a message anchored to it | Writes the message in the outbox and goes on at once |
| Activity that receives a message, or an intermediate message event | Waits until that message arrives |
| End event that sends a message | Sends it and ends that path |
| Activity done by the store with nothing anchored to it | Completes at once |
| Exclusive gateway that splits | Takes the first outgoing flow whose condition holds, in the order they were given; if none does, the default one |
| Inclusive gateway that splits | Takes every outgoing flow whose condition holds; if none does, the default one |
| Parallel gateway that splits | Takes all of them at once |
| Parallel gateway that merges | Waits until as many tokens have arrived as there are flows coming in |
| Inclusive gateway that merges | Waits while any other live token of the case can still reach it |
| Exclusive gateway that merges | Does not synchronize: every token that arrives goes on |
| End event | Consumes its token. When the last live token of the case dies, the case is finished |
| Anything in another participant | Is not run: the other pools are partners or black boxes, and what they draw inside is documentation |

**Conditions.** A gateway decides with the conditions written on its outgoing flows, in a small language of its own:
a variable of the case, one of `==`, `!=`, `>`, `>=`, `<` and `<=`, and a value, combined with `and`, `or`, `not`
and brackets. `payment.status == APPROVED` and `order.total > 5000 and order.vip == true` are conditions. There are
no function calls, no assignments and no access to anything but the variables, so a condition written by a user
cannot run code. It is the same language the diagnosis checks when publishing: a condition that is published is a
condition that runs.

A variable the case does not have makes its comparison false and leaves a note in the timeline. If that leaves a
gateway with nowhere to go, the case stops in `ERROR` with the gateway that found no path written down. An
administrator corrects the variables with `PATCH /api/v1/casos/{id}/variables` and `POST /api/v1/casos/{id}/reintentar`
evaluates that gateway again. A case can also be cancelled, which switches off its live tokens; nothing is deleted.

**Variables.** They are the JSON the case carries: what was passed when it was opened, under
`tarea.<taskNameInCamel>` whatever each person handed over when completing a task, and under the name each message
declares, the body of every message received. `caso.referencia` and `caso.tick` are always readable and come from
the case itself.

**Whose tray.** A task is born with the process role of its lane, and `GET /tareas?rolProcesoId=2` is the tray of
that role. An administrator can also say which roles each person belongs to, with
`PUT /usuarios/{id}/roles-proceso` and the whole list, and then `GET /tareas?mias=true` answers only the tasks of
the caller's roles — someone with no roles gets an empty tray. It is a filter and not a door: completing a task
still only asks for the access role, so a store that does not want to manage memberships simply never sets any.

## Messages and the clock

Participants do not call each other. What a process sends goes to an outbox, and what reaches it goes to an inbox:
two lists that can be read, which is what explains an order that is waiting. Nothing is real behind them — the
partners are simulated — and nothing is lost either.

```bash
# The order arrives as a message and opens a case; claveExterna makes sending it twice safe
curl -s -X POST http://localhost:8080/api/v1/procesos/1/mensajes-entrantes -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Order placed","cuerpo":{"orderId":"ORD-1001"},"claveExterna":"shop-1001"}'

# What the process has sent and what has reached it
curl -s http://localhost:8080/api/v1/procesos/1/bandeja-salida -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"
curl -s http://localhost:8080/api/v1/procesos/1/bandeja-entrada -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"

# Move the store's clock one tick: what was due is delivered and the partners answer
curl -s -X POST http://localhost:8080/api/v1/simulacion/tick -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"ticks":1}'

# Where the simulation is, and what a case sent and received
curl -s http://localhost:8080/api/v1/simulacion -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"
curl -s http://localhost:8080/api/v1/casos/42/mensajes -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"
```

**Sending.** When a case goes through a node with a message anchored to it, the message is written in the outbox
with the fields it declares, taken from the variables of the case — a field the case does not have travels empty
and is written in the timeline. It carries the key the answer will come back with: the correlation field, or the
reference of the case. It is due one tick later, never the same tick it was sent: otherwise moving the clock once
would deliver everything at once and an order waiting for the payment gateway could never be seen.

**Receiving.** A message that arrives is matched to a case by its key, and there are four possible endings, decided
in this order: there is an open case with that key and a node waiting for that message, and it is delivered; there
is a case but it has not reached the point of waiting yet, and the message stays in the inbox until a later tick;
there is no case and the message is one that opens them, and one is opened; there is no case and it does not open
any, and it is discarded, written down. A message without a key is never delivered to a case just because the name
matches: guessing would put the answer of one order into another.

**If a message does not arrive**, the message itself says what happens: the process goes on, it is diverted to the
activity that handles the problem, or the order is given up and the case ends `FALLIDO`.

**The clock.** A store has its own clock, a counter of ticks that starts at zero and only goes up, and
`POST /api/v1/simulacion/tick` moves it. Each due message is delivered in its own transaction with its case locked,
so twenty orders move one after another rather than all at once. A store can also ask for its clock to run on its
own, with `modoSimulacion` on `AUTOMATICO`; by default it is `MANUAL`, which is what makes a demo repeatable.

## The simulated partners

Nothing on the other side of a message is real. There is no payment gateway, no carrier and no email provider:
there are four simulated partners that receive what the process sends, decide what happens and answer what the
diagram says they answer. Each store says how they behave, and everything they decide comes from the store's seed
rather than from chance — so the same demo shown twice gives the same rejections, the same lost parcels and the
same amounts.

| Partner | What it does with a message | What it answers |
|---|---|---|
| Payment gateway | Applies the store's rejection rule over the body it was sent, and what the rule does not reject is left to the rejection rate | The answer the diagram expects, with `status`, a `transactionId` and the amount, after `ticksRespuestaPagos` |
| Carrier | Takes the parcel | The tracking answer if the diagram expects one, and the delivery confirmation `ticksEntrega` later, saying `DELIVERED` or `LOST` according to the loss rate |
| Notifications | Delivers the email, the message or the call, or does not | Nothing. When it does not get through, what happens next is the message's own `siFalla` |
| Customer | Receives whatever the store sends it, always | Nothing. It also buys: a batch of orders comes from here |

**The rejection rule** is written in the same language as the flow conditions, read over the body of the outgoing
message: `total > 5000` makes every order above five thousand fail, with no chance involved. It is checked when it
is saved, not when it runs, because a rule found to be wrong halfway through a demo cannot be fixed without
stopping the demo. The rate covers what the rule does not: `0` approves everything and `100` rejects everything,
which is how a test says what it wants to happen.

```bash
# How this store's partners behave; send all of it or none of it
curl -s -X PUT http://localhost:8080/api/v1/empresas/actual/configuracion -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" \
  -d '{"politicaEstructura":"ADMINISTRADOR_Y_EDITOR","version":0,
       "simulacion":{"semilla":42,"tasaRechazoPagos":10,"ticksRespuestaPagos":1,
                     "reglaRechazoPagos":"total > 5000","ticksRespuestaTransporte":1,"ticksEntrega":3,
                     "tasaPerdidaEnvios":5,"tasaFalloNotificaciones":2}}'

# Twenty orders from the simulated customer, and then move the clock
curl -s -X POST http://localhost:8080/api/v1/simulacion/pedidos -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"procesoId":1,"cantidad":20}'
curl -s -X POST http://localhost:8080/api/v1/simulacion/tick -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"ticks":1}'
```

**A batch of orders** comes in as the message that opens a case of that process, one per order, with a numbered
reference and an amount made up from the seed. They enter through the same door as any other message, so a
simulated order and a real one walk exactly the same path: nothing downstream knows where they came from. A
process that is opened by hand does not take batches — its cases are opened one at a time.

## The dashboard

How operations are going, for one process or for the whole store. It answers what somebody actually asks when they
open it: how many orders there are and what state they are in, how long a finished one takes, who has work
waiting, what has been sent and received, and what did not go as expected.

```bash
curl -s http://localhost:8080/api/v1/procesos/1/tablero -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"
curl -s http://localhost:8080/api/v1/empresas/actual/tablero -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"
```

**Time is counted in ticks**, not in hours: the simulation's time is the one that can be reproduced, and mixing
the two would be counting two different things in one column. Alongside the average there is a p95 — the time that
at least ninety-five out of a hundred orders stay under. It is deliberately not the maximum: out of twenty orders,
one slow one does not move it and two do, which is what makes it worth looking at.

**What did not go as expected** is three numbers taken from the case timelines: messages that never reached the
partner, gateways that found no path, and conditions that asked for a variable the case did not have. Each one is
fixed a different way, so each one is counted separately. There is no "declined payments" column: whether a
payment was declined is a fact of the business that lives in the diagram, and the engine would have to guess it by
reading inside the body of a message — while these three it knows for certain, because it wrote them.

**It costs six queries**, whether the store has five orders or five thousand, and a test counts the statements so
it stays that way. A dashboard that grew with the orders would stop being openable exactly on the day it mattered.

For whoever runs the server rather than the store, Actuator publishes four gauges next to the memory and
connection-pool ones: `casos.abiertos`, `tareas.pendientes`, `mensajes.salientes.pendientes` and
`mensajes.entrantes.pendientes`. Those are for the whole installation — a gauge tagged per store would create a
new series every time somebody registers — and they are the administrator's, like the rest of Actuator.
