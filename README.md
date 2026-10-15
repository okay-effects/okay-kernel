# okay-kernel

## The kernel: a protocol

okay-kernel is the PROTOCOL by which implementations of effects and delimited control — okay's, okay-core's,
okay-java's, systems to come — work with each other in one process: a program written in one language runs under
handlers written in another. The design: [docs/design.md](docs/design.md).

| module | what |
|---|---|
| `api` (`okay-kernel-api`) | the protocol and the API, pure Java, no dependency: `Type` (an effect's exact identity), `Key<F>` (its typed key), `Op`, `Computation`, `Step` (`Done`, `Yield`, `Failed`, `Cancelled`), `Resume` (`resume`, `fail`, `cancel`), `Answers`; code written once over `Effects`, delimited control apart (`Control`); the five shapes of handlers; the vocabulary of standard effects and its handlers written once (`std.Handlers`) |
| `scala` (`okay-kernel-scala`) | Scala's own: `TypeOf` (exact types found by the compiler), `@@`; on Scala.js and Scala Native the API's mirror. Scala 3 and 2.13 from one source |
| `steps` (`okay-kernel-steps`) | the reference machine: `Effects` and the protocol, exact rather than fast. Scala 3 and 2.13, JVM, JS, Native |
| `tck` (`okay-kernel-tck`) | what every implementation is checked by, on its own `Effects`: laws, standard handlers, the protocol, nesting through implementations, a thousand random programs with one digest |
| `js` (`okay-kernel-js`) | the protocol in JavaScript: a computation a plain JS object (`start(host)`, its steps, `k`, a host), exported from Scala.js as an ES module (`sbt kernelJs/fastLinkJS`) — so a JS implementation (okay-ts) and the kernel's run each other's programs; an effect by its type's name (`State[Integer]`, `Choose`, or one's own) |
| `integration` | the TCK on every implementation in one process, each nesting through the others (not published) |
| `bench` | one program written once over `Effects`, measured on each implementation (JMH, not published) |

Implementations bridge to it in their own repositories: okay-core (`okay.core.kernel.OnCore`), okay-java
(`okay.core.Kernel`). The kernel before the protocol (the first API, `Monad` and `Program.in(m)`, its machines and
bridges) was removed on 2026-10-14 and lives in git's history (0a614df).

## The API (`okay.kernel`)

| | |
|---|---|
| `Program<A>` | a program written once: `in(e: Effects): Prog<A>`, built in any implementation |
| `Prog<A>` | a program of an implementation, of value `A`, given back to it only: its own nodes, no wrapper |
| `Effects` | what an implementation offers code written once: `pure`, `flatMap`, `delay`, `perform`, `handle`, `failed`, `recover`, `ensuring`, `uncancelable`; `computation` and `foreign` to hand a program out and take one in |
| `Control` | delimited control, apart: `shift0` and `$`; an implementation has it or not (`Control.of`) |
| `Handler<A, R>` | a delimiter of one of five shapes: `Answering` (answered in place), `Aborting` (`k` never called), `Returning` (its return a value), `Translating` (each operation a program outside, the rest resumed), `General` (a clause given `k`: deep, any number of times) |
| `Key<F>`, `Type`, `Op<X>` | an effect's typed key and exact identity, interned; an operation as data, of value `X`, carrying its key |
| `Computation<A>`, `Step<A>`, `Resume<X, A>`, `Answers` | the protocol between implementations: a program handed out steps to a value, an operation its host answers (`k` multi-shot, later, from any thread), a failure or a cancellation |
| `Fn`, `Thunk` | single-abstract-method interfaces: a lambda of any of the languages |

No `Any` in its signatures, no context function, union, type member or higher kind, no `scala.Function1`: Scala 2
(by its TASTy reader) and Java read them, typed. On Scala.js and Scala Native, with no Java, the API is its Scala
mirror (`scala/src/main/scala-mirror`), the same names and signatures.

## The ecosystem: one protocol, the implementations apart

okay-core (Scala 3, the one monad — its kernel `okay.one`, written first in the okay-one repository), okay-java (Java)
and okay (the platform) are libraries of their own. The kernel is the layer they share — the API every language and
platform reads, the TCK, the reference machines — and each implements it in its own repository, or here where it has
no other home:

- `okay.core.kernel.OnCore` (okay-core): the bridge over the one monad — every delimiter a `Deep` handler of one
  request effect over one row, `k` data, export through `Step` ([okay-core's docs/kernel.md](../okay-core/docs/kernel.md));
- `okay.core.Kernel.EFFECTS` (okay-java): a capture per `k`, nothing allocated a delimiter
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

`sbt test`: the API's (Java, JUnit), Scala's and the reference machine's on the JVM, Scala.js and Scala Native, the
TCK on the reference — Scala 3 and 2.13. `sbt integration/test`: the TCK on okay-core, okay-java and the reference
in one process, each nesting through the others (48 checks; okay-core and okay-java published locally first).
