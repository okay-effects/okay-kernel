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

## The API (`okay.kernel`)

| | |
|---|---|
| `Program[A]` | `in(m: Monad): Any` — a program, built in the monad given |
| `Op[A]`, `Effect` | an operation as data, of an effect, by identity |
| `Handler`, `Answering` | `ret`, and the clause `apply(op, k, m)`: `k` the rest up to the handler, deep, any number of times; an answering handler's clause is `k(value(op))`, answered in place |
| `Monad` | `pure`, `flatMap`, `map`, `delay`, `perform(op)` (to the nearest handler of its effect), `handle(h, body)`, `run` |
| `Fn`, `Thunk` | single-abstract-method traits: a lambda of any of the languages |

Its signatures are plain generics and SAM traits only — no context function, union, type member or higher kind, no
`scala.Function1` — so Scala 2 (by its TASTy reader) and Java read them.

## The monads

- `Steps`: okay-java's machine — nodes over an explicit stack of handlers on the heap, handlers nested as deep as
  the heap allows, one step a kind of node.
- `Ref` (tests): the textbook, recursive — what every monad must agree with.

## Scala 3's typed view (`okay`, `okay.std`)

`A ! R` is `Eff[R, A]`, a `Program[A]` with its effects in its type: `R` an intersection, `Writer % String + Async`
being `Writer[String] & Async`; contravariant, so a program needing fewer effects is one needing more. A handler
takes its effect off the row: `writer(p: A ! (Writer[W] & R)): (List[W], A) ! R`. Programs of other languages come in
by `Eff.from`, their effects as said, checked at run time.

## Tests

`sbt test`: the same suite on every monad, on the JVM, Scala.js and Scala Native; Java's programs and Scala 3's in
one, both ways (`src/test/scala-jvm`); Scala 2's (`scala2/`).
