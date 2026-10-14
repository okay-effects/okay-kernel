# okay-kernel

## The kernel: a protocol

okay-kernel is the PROTOCOL by which implementations of effects and delimited control — okay's, okay-core's,
okay-java's, systems to come — work with each other in one process: a program written in one language runs under
handlers written in another. The design: [docs/design.md](docs/design.md).

| module | what |
|---|---|
| `api` (`okay-kernel-api`) | the protocol and the API, pure Java, no dependency: `Type` (an effect's exact identity), `Op`, `Computation`, `Step` (`Done`, `Yield`, `Failed`, `Cancelled`), `Resume` (`resume`, `fail`, `cancel`), `Answers`; code written once over `Effects`; the shapes of handlers; `Direct`; the vocabulary of standard effects and its handlers written once (`std.Handlers`) |
| `scala` (`okay-kernel-scala`) | Scala's own: `TypeOf` (exact types found by the compiler), `@@`; on Scala.js and Scala Native the API's mirror. Scala 3 and 2.13 from one source |
| `steps` (`okay-kernel-steps`) | the reference machine: `Effects` and the protocol, exact rather than fast. Scala 3 and 2.13, JVM, JS, Native |
| `tck` (`okay-kernel-tck`) | what every implementation is checked by, on its own `Effects`: laws, standard handlers, the protocol, nesting through implementations, a thousand random programs with one digest |
| `legacy/` | the kernel before the protocol, unchanged, until each part moves onto the API or to its own repository (`sbt legacy/test`) |

Implementations bridge to it from their own repositories, as optional modules: okay-core (`okay-core-kernel`),
okay-java (`okay-java-kernel`).

What follows describes the kernel before the protocol, now in `legacy/`.

One kernel API for effects, handlers and continuations, the same in every language and on every platform, every
monad an implementation of it.

```scala
def p(a: Int): Int ! Writer % String + Async =
  for
    _ <- tell("got " + a)
    x <- await(a + 1)
  yield x * 2
```

`p(1)` is a `okay.kernel.Program[Int]` at run time: a recipe that builds itself in any `Monad`. So it is called from
Scala 3, Scala 2, Java, Scala.js and Scala Native alike, and programs written in any of them run under handlers
written in any other. The monad changes nothing a program sees, only the speed.

## The API (`okay.kernel`): typed through, plain generics only

| | |
|---|---|
| `Program[A]` | `in(m: Monad): Prog[A]` — a recipe, built in the monad given |
| `Prog[A]` | a monad's program of value `A`: built by it, given back to it only (Java has no type of a type constructor, so the monad is not in the type) |
| `Op[X]`, `Effect` | an operation as data, of value `X`, of an effect by identity |
| `Handler[A, R]` | `ret(a: A, m): Prog[R]`, and the clause `apply[X](op: Op[X], k: Fn[X, Prog[R]], m): Prog[R]` — `k` typed by the operation's value, the rest up to the handler, deep, any number of times |
| `Answering[A, R]` | `value[X](op: Op[X]): X`: each clause `k(value(op))`, answered in place |
| `Returning[A, R]` | `answer(a: A): R`: `ret` a value, `m.pure(answer(a))` — a monad may take the value itself, no program made for it (`OnCore`: okay-core's own handler, nothing flattened) |
| `Monad` | `pure[A]`, `flatMap[A, B]`, `map`, `delay`, `perform[X](op: Op[X]): Prog[X]`, `handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R]`, `run[A](p: Prog[A]): A` |
| `Fn`, `Thunk`, `Sink` | single-abstract-method traits: a lambda of any of the languages |

No `Any` in its signatures, no context function, union, type member or higher kind, no `scala.Function1` — so Scala 2
(by its TASTy reader) and Java read them, typed. Inside a monad's machine its nodes are its own business.

Scala 3 writes handlers typed by its rows too: `okay.Handler[E, A, R, Rest]` — itself a kernel `Handler[A, R]` —
its clauses on `R ! Rest`, its `handle` taking `E` off a program's row and putting `Rest` on.

## Binary: one jar of the API (`api/`, `dev.okay:okay-kernel-api`)

On the JVM the API is pure Java with no dependency: the interfaces above, `Effect`, and the standard effects' data
(`okay.kernel.std`) — `Effects`, the operations `Tell`, `Raise`, `Get`, `Put`, `Among`, `Await`, `Shift0`, `Shift`,
`Callback`, and the handlers' Java answers `Logged`, `Result`, `Final`. The kernel's Scala and its Java (`java/`) are
compiled against that one jar. One class for an interface, an operation, an answer: a program, a handler, a monad of
one works with those of the other, nothing translated. Their implementations have packages of their own (`okay.*`
in Scala, `okay.jkernel.*` in Java), so both jars share one classpath. An operation of value `Void` is answered
`null`: Java's continuations cast to `Void`. On Scala.js and Scala Native, with no Java, the API is its Scala mirror
(`src/main/scala-mirror`): the same signatures, invariant as Java's.

## The ecosystem: one protocol, the implementations apart

okay-core (Scala 3, the one monad — its kernel `okay.one`, written first in the okay-one repository), okay-java (Java)
and okay (the platform) are libraries of their own. The kernel is the layer they share — the API every language and
platform reads, the TCK, the reference machines — and each implements it in its own repository, or here where it has
no other home:

- `okay.core.kernel.OnCore` (okay-core): the bridge over the one monad — every delimiter a `Deep` handler of one
  request effect over one row, `k` data, export through `Step` ([okay-core's docs/kernel.md](../okay-core/docs/kernel.md));
- `okay.core.Kernel.EFFECTS` (okay-java, branch `kernel-native`): a capture per `k`, nothing allocated a delimiter
  crossed ([okay-java's docs/kernel.md](../okay-java/docs/kernel.md));
- `Steps` (`steps/`): the reference — a step a node over an explicit stack of frames, exact rather than fast, the
  TCK's digest its; one, in Scala, on every platform (its copy in Java, `steps-java`, is gone, 2026-10-14: one
  reference is what an independent check needs, two the same interpreter twice).

`integration/` runs the TCK on each of okay-core, okay-java and the reference in one process, each importing
the others' computations: 48 checks. The okay platform runs on okay-core's one monad (2026-10-14), so its
bridge is `OnCore` — the module `okay/` (`OnOkay`, okay-cont's machine) is gone with okay-cont.

## What the kernel costs

okay-java's mixed program (1000 orders: a reader's base, the first of three suppliers, a failure caught per order, a
state, a log), written once over `Effects`, on each implementation — beside the same program in each one's own words
(`~/work/my/okay-bench`, 2026-10-13, JDK 26, JMH `-f 2 -wi 5 -i 5`, µs a run of 1000 orders, bytes a run):

| implementation | the protocol's program | B | its own program | B |
|---|---|---|---|---|
| okay-java (`okay.core.Kernel`) | 237 | 1 731 610 | 102 (cell) / 172 (state) | 1 025 313 / 1 352 001 |
| okay-core cont (`OnCore.cont`) | 247 | 1 895 962 | 147 | 1 415 329 |
| okay-core zero (`OnCore.zero`) | 310 ± 119 | 1 828 738 | 124 | 1 308 757 |
| okay-core on the one monad (`OnCore`, 2026-10-14, a place by key at run time) | 286 | 2 469 170 | 223 | 1 445 313 |
| steps (the reference, Scala) | 535 | 2 378 644 | — | — |
| okay (`OnOkay`, okay-cont's machine, gone 2026-10-14) | 557 | 4 405 892 | — | — |

The protocol's program costs about twice each one's own on the native bridges (okay-java, okay-core's of before):
`Program.in`, the `Fn` closures and the standard handlers built per order. okay-core's `OnCore` on the one monad is a
library over `Deep` — every kernel handler a `Deep` clause and a level of `k`, every operation a request captured and
forwarded at each delimiter, a read of its answer — what its native path of before went through (474 → 231) is ahead
of it; the rows of `OnCore.zero`/`.cont` are those machines' last, kept for the comparison.

## Scala's own (`scala/`, `dev.okay::okay-kernel-scala`)

`okay.kernel.types`: `TypeOf`, exact types found by the compiler (classes, applied up to three, `A @@ T` tagged by
a singleton's value or a class), named by hand for anything else — `State.key[Int](typeOf[Int])`; on Scala.js and
Scala Native the API's mirror, the same names and signatures. One source for Scala 3 and 2.13.

## Tests

`sbt test`: the API's (Java, JUnit), Scala's on the JVM, Scala.js and Scala Native, the reference machines' (Scala
and Java), the TCK on each implementation in its module (`one/`, `okay/`) and on all of them in one process
(`integration/`) — Scala 3 and 2.13.
