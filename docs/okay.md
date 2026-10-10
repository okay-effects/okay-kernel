# okay on the kernel

`okay/` (sbt `okayCompat`, artifact `dev.okay::okay-kernel-okay`, Scala 3.9, JVM) is the bridge between okay and the
kernel's protocol. It depends on okay's published artifact (`io.github.sergey-scherbina::okay:0.2.0-SNAPSHOT`, from
ivy local) and on the kernel's API, and changes nothing in okay. To publish okay locally, run this in okay:

```
sbt 'set LocalProject("okayContJVM") / publish / skip := false' \
    'set LocalProject("okayContJVM") / Compile / packageDoc / publishArtifact := false' \
    'set LocalProject("okayJVM") / Compile / packageDoc / publishArtifact := false' \
    okayContJVM/publishLocal okayJVM/publishLocal
```

okay-cont is not in okay's release wave, so it is `publish / skip`. okay's scaladoc currently fails on its JMH
sources.

## 1. `OnOkay`: okay's machine as the kernel's `Effects`

The machine is okay-cont's `Cont`, the nodes `okay.Effects.machine` compiles every `A ! R` to, run by
`okay.cont.Machine`. A kernel `Prog` is okay's program itself in a box (`OnOkay.P`, holding a `Cont`). `Cont` is
okay's enum, so it cannot implement `Prog` without a change to okay. The bridge does not translate anything; it only
wraps. The program is run by okay's loop, in constant stack.

| kernel | on okay's machine |
|---|---|
| `pure`, `flatMap`, `map`, `delay` | `Return`, `Bind`, `Bind`, `Delay`. A function's exception becomes a failure where it ran |
| `perform(op)` | a request to the nearest delimiter: `Shift0(k => Return(Perform(op, k)))` |
| `handle(h, body)` | `Reset(body)`, followed by the handler's dispatch outside it |
| `shift0` | a request that only a handler's delimiter or a `$`'s takes |
| `dollar(body, k)` | `Reset(body)`, with `k` as its return |
| `failed`, `recover`, `ensuring`, `uncancelable` | an `Unwind` request (`k` dropped), and delimiters that take it |
| `computation` (export) | the program under the top delimiter. A request nobody took is answered in place by the host if it can, otherwise it becomes `Step.Yield` |
| `foreign` (import) | the handlers in force are collected (`Env`), the computation is started with them as its host, and its yields are performed here |

**Dynamic lookup on a static machine.** okay finds a handler at compile time, from the row (`Member`, `Reach`: a
fixed number of delimiters out). The kernel finds it at run time, by the operation's `Type`, in the context where
the operation runs. The bridge therefore does not use okay's static reach for kernel programs. Every operation is a
request to the nearest delimiter. Every delimiter of the bridge is a `Reset` with a dispatch frame outside it. The
dispatch takes the requests that concern it and forwards the rest outward, again with one `Shift0`, its `k` composed
as the rest under it, the delimiter put back, and the rest up to the next delimiter. These are deep handlers built
from shift0 and reset. The stack does the lookup that the reference machine does with `indexWhere`. That is why a
`k` resumed under other handlers sees those handlers, and why a `shift0` body runs outside its delimiter.

**Signals.** Failures, aborts, cancellations and pending cancellations are values that an operation can be answered
with. The operation reads its answer and unwinds from the place it was performed. This gives `k.fail`, `k.cancel`,
and an `Aborting` handler's abort passing through `ensuring` finalizers on its way to that handler (and not caught by
`recover`). Regions (`uncancelable`) work the same way: a cancellation inside a region re-yields with the
cancellation pending, the next answer marks the region (`Mark`), and the region cancels when it ends, whether by a
value or by a failure.

**Trusted steps (casts): two.** `trust` is the reference machine's step: a value is given where the program, built
typed, expects it. `level` exists because okay's `Cont` is indexed by stacks of answer types, which are phantom: the
machine never reads them. The bridge builds its programs at one index; the body of a `Reset`, a `Shift0`, and the
border's `Target` are the same objects seen one level in.

## 2. okay's own programs

`Mapping[E]` says how one of okay's effects is seen by the kernel: its `Type`, its operations as kernel operations,
and the answers in both directions. `Kernel[R]` maps a whole row, and the compiler builds it from a `Mapping` for each
effect. The standard mappings, which the caller gives the kernel `Type`s:

| okay-cont | kernel |
|---|---|
| `State % S`: `Get`, `Put` | `State.of(t)`: `Get`, `Put` (answers `Void` and `Unit`) |
| `Reader % R`: `Ask`, `Asks(f)` | `Reader.of(t)`: `Ask` (for `Asks`, `f` is applied on okay's side) |
| `Writer % W`: `Tell` | `Writer.of(t)`: `Tell` |
| `Throws % E`: `Raise` | `Throws.of(t)`: `Raise` |
| `Choose`: `Among(Seq)` | `Choose.TYPE`: `Among(List)` |

- `OkayPrograms.lift(p: Free[R, A]): Program[A]` turns an okay program into a kernel program. On `OnOkay` it is run in
  place, under a `Border` delimiter. Each operation of `R` reaches the border by okay's own static reach
  (`Reach.Here` from the program's context, one level further for each of okay's handlers inside it, which okay
  crosses), and from there it goes on as the bridge's request. On any other implementation, `lift` imports the
  exported computation.
- `OkayPrograms.exported(p)` gives a `Computation[A]` whose row is the mapped types. So a program written in okay's
  words (`effect(State.Get[Int]())`, `A ! R`) runs under the kernel's standard handlers on any implementation. This
  is tested on `Steps`, `StepsJava`, and `OnOkay` itself.
- `OkayPrograms.imported[R, A](c)` turns a kernel computation into okay's `Free[R, A]`. Each operation it hands out is
  found in the row by its `Type` and performed by okay's handler of it, by its path. The answer goes back to the
  computation's `k`. okay's handlers (`StateCell`, `WriterLog`, `Every`) therefore run kernel programs, multi-shot
  `k` included.

## 3. What is exact, what is approximated

Exact: the reference machine's semantics for `OnOkay`. The TCK passes all 13 checks, including the digest
-8623788747993822161 of the thousand random programs and nesting through `Steps` and `StepsJava`. The checks that
pass include a dynamic lookup by `Type` when `k` is resumed under other handlers, a shift0 body outside its delimiter,
a failure of a `Returning` answer caught by `recover`, an abort through finalizers and through a foreign program
(which is cancelled, with its finalizers' operations answered here), cancellation waiting in regions, and `k`
multi-shot from any thread. An unwinding from okay's program starts at the border, not at the operation. This is
still exact, because the only things in between are okay's own frames and handlers, and they have no finalizers.

Approximated or different in cost:
- **No answer in place inside the bridge.** An `Answering` handler's operation (a state's `get`, a reader's `ask`) is
  a capture to the nearest delimiter, forwarded through each delimiter in between, and then resumed. The semantics
  are the reference's, but each operation costs a capture per delimiter crossed. The host is asked at the top after
  that capture, not at the operation. The handlers of a foreign computation are offered in place exactly as the
  reference offers them (`Answering` ones and the host).
- **Import into okay** (`imported`): okay has no failures and no cancellation. A computation's `Failed` is thrown out
  of okay's run, and a `Cancelled` is thrown as `CancellationException`. An operation that none of the row's effects
  maps is failed back into the computation (`k.fail`). The computation is started with no host (`Answers.NONE`), so
  okay's answering handlers are reached by a yield, not in place. An okay handler that drops `k` (okay's `Throws`
  handler) drops the imported computation without cancelling it, so its finalizers do not run.
- **Identity.** okay's effects carry no kernel `Type` of their own, so the caller gives each mapping its `Type`.
  okay's `State % Int` and the kernel's `State.of(t)` are the same effect only where a mapping says so.
- When a foreign computation is cancelled for one signal and another signal crosses it during that cancellation, the
  first signal goes on. The reference does the same, through its `FAfter` frames.

## 4. Tests

`okay/src/test`: `TestTckOkay` (the TCK, `others = List(Steps, StepsJava.INSTANCE)`) and `TestOkayPrograms`. The
second covers an okay program (State, Writer, Choose) exported and run under the reference machines' standard
handlers; the same program lifted into `OnOkay`; stepped by hand with multi-shot `k`; a kernel program from each
machine imported under okay's handlers; a round trip; okay's raise aborting through a kernel `ensuring`; okay's own
handlers inside the program with the kernel's outside; and 100 000 operations in constant stack. 22 tests, all green.
