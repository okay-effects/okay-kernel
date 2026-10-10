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
| `steps-java` (`okay-kernel-steps-java`) | the reference machine in pure Java |
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

## The ecosystem: the kernel over two libraries

okay-core (Scala 3) and okay-java (Java) are libraries of their own, of no dependency: neither knows the kernel. The
kernel is the layer over them — the API every language and platform shares, its machines, and an adapter a library:
`OnCore` (`okaycore/`) runs the kernel's programs on okay-core's machines, `OnJava` (`okayjava/`) on okay-java's. The kernel's own Java — a machine of the
API and the standard handlers in Java — is the module `java/` (`dev.okay:okay-kernel-java`, `okay.jkernel`), built
here; okay-java stays a library.

## The monads

- `Steps`: okay-java's machine — nodes over an explicit stack of handlers on the heap, handlers nested as deep as
  the heap allows, one step a kind of node.
- `Ref` (tests): the textbook, recursive — what every monad must agree with.
- `OnCore.zero`, `OnCore.cont` (`okaycore/`): okay-core's machines through its `Effects`.
- Both adapters are `Direct` (`okay.kernel.Direct`, optional, Scala's): Scala 3's typed programs built in the
  library's own nodes, no kernel `Prog` around each node; Java's programs and the handlers by `Monad`. The standard
  handlers are `Returning`: okay-core's own handler under `OnCore`, nothing flattened.
- `OnJava.monad` (`okayjava/`): okay-java's machine of prompts — a handler an `Eff.handle`, an operation `shift0` to
  the prompt of the nearest handler of its effect, an answering one's `Eff.sync`, no capture.
- `okay.jkernel.Steps` (`java/`): the machine in Java, compiled by javac against the API's jar — the same random
  programs, the same outcomes (`TestAgree`, and `AgreeTest` in Java).

## What the kernel costs

okay-java's mixed program (1000 orders: a reader's base, the first of three suppliers, a failure caught per order, a
state, a log), written once over `Effects`, on each implementation (bench/, JDK 26, JMH `-f 2 -wi 5 -i 5`):

| implementation | µs | B / order | its own program, in its own words |
|---|---|---|---|
| okay-java (`okay.core.Kernel`, native) | 245 | 1767 | 182 (okay-java's MixedBenchmark, JDK 26) |
| okay-core cont (`OnCore.cont`, native) | 314 | 2395 | 142 |
| okay-core zero (`OnCore.zero`, native) | 348 | 2089 | 125 |
| steps-java (the reference, Java) | 405 | 2520 | — |
| steps (the reference, Scala) | 524 | 2414 | — |

The implementations' own typed programs are unchanged by implementing the kernel (okay-core's changelog.d/kernel-native.md,
okay-java's docs/kernel.md). The kernel's path on okay-core is being made faster.
## Scala 3's typed view (`okay`, `okay.std`)

`A ! R` is `Eff[R, A]`, a `Program[A]` with its effects in its type: `R` an intersection, `Writer % String + Async`
being `Writer[String] & Async`; contravariant, so a program needing fewer effects is one needing more. A handler
takes its effect off the row: `writer(p: A ! (Writer[W] & R)): (List[W], A) ! R`. Programs of other languages come in
by `Eff.from`, their effects as said, checked at run time.

## Tests

`sbt test`: the same suite on every monad, on the JVM, Scala.js and Scala Native; Java's programs and Scala 3's in
one, both ways (`src/test/scala-jvm`); the Java module's jar with the Scala one, in one run (`TestBinary`); the Java
module's own, JUnit (`java/`); Scala 2's (`scala2/`).
