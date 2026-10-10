# okay-kernel

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
| `Monad` | `pure[A]`, `flatMap[A, B]`, `map`, `delay`, `perform[X](op: Op[X]): Prog[X]`, `handle[A, R](h: Handler[A, R], body: Program[A]): Prog[R]`, `run[A](p: Prog[A]): A` |
| `Fn`, `Thunk`, `Sink` | single-abstract-method traits: a lambda of any of the languages |

No `Any` in its signatures, no context function, union, type member or higher kind, no `scala.Function1` — so Scala 2
(by its TASTy reader) and Java read them, typed. Inside a monad's machine its nodes are its own business.

Scala 3 writes handlers typed by its rows too: `okay.Handler[E, A, R, Rest]` — itself a kernel `Handler[A, R]` —
its clauses on `R ! Rest`, its `handle` taking `E` off a program's row and putting `Rest` on.

## The monads

- `Steps`: okay-java's machine — nodes over an explicit stack of handlers on the heap, handlers nested as deep as
  the heap allows, one step a kind of node.
- `Ref` (tests): the textbook, recursive — what every monad must agree with.
- `OnCore.zero`, `OnCore.cont` (`okaycore/`): okay-core's machines through its `Effects`.

## Scala 3's typed view (`okay`, `okay.std`)

`A ! R` is `Eff[R, A]`, a `Program[A]` with its effects in its type: `R` an intersection, `Writer % String + Async`
being `Writer[String] & Async`; contravariant, so a program needing fewer effects is one needing more. A handler
takes its effect off the row: `writer(p: A ! (Writer[W] & R)): (List[W], A) ! R`. Programs of other languages come in
by `Eff.from`, their effects as said, checked at run time.

## Tests

`sbt test`: the same suite on every monad, on the JVM, Scala.js and Scala Native; Java's programs and Scala 3's in
one, both ways (`src/test/scala-jvm`); Scala 2's (`scala2/`).
