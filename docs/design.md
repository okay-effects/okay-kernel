# okay-kernel: the design (a draft, for discussion)

## 0. What is asked

- Delimited continuations with answer-type modification (ATM), and algebraic effects with rows in their types.
- Scala 3, Scala 2, Java; the JVM, JavaScript, Native.
- The kernel is the minimal `shift0` and `$` (Materzok–Biernacki); everything else is a library over it.
- No prompts: a handler is found by its position, known statically.
- ATM per handler: every effect in a row carries its own answer types, and its operations may change them.
- A row is a set: the order of effects in a type says nothing, the order of handlers everything.
- Java is type safe, as Scala is.
- The first version has the standard effects and streams.
- Speed: the mixed program no slower than okay-core's `Cont0` (125 µs for 1000 orders); 100 000 nested handlers
  and any number of binds in constant stack.
- Written first in Scala 3, in a subset that ports to Scala 2 and Java by translation: no casts, no macros.

## 1. Layers

```
5  fronts     Scala 3: A ! E1 + E2, contextual sugar     Scala 2: implicits     Java: explicit evidence
4  boundary   only if the languages do not share one artifact (§7, Q1)
3  std        Reader, State, Writer, Throws, Choose, PState, Emit and streams, Async — over layer 2 only
2  effects    rows, evidence, handlers (a $ and a clause), operations (a shift0 to a level), Answering
1  kernel     Cont0: shift0, $, pure, flatMap, delay — nodes and one machine
```

Each layer uses only the one below it. Layers 1–3 are the portable subset (§6); layer 5 is sugar, never required.

## 2. Layer 1: the kernel

`Cont0[I, O, A]` is a program of value `A` under its nearest delimiter. The rest up to that delimiter, as `k`, is
`A => I`, and the whole answers `O`. `I` and `O` are programs of the level outside, so the levels of the CPS
hierarchy are the nesting of the answer types; at the top they are plain values.

| operation | type | meaning |
|---|---|---|
| `pure(a)` | `Cont0[S, S, A]` | a value; the answer unchanged |
| `p.flatMap(f)` | `Cont0[T, O, A]`, `A => Cont0[I, T, B]` → `Cont0[I, O, B]` | answers composed end to end |
| `shift0(f)` | `f: (A => I) => O` → `Cont0[I, O, A]` | the rest up to the nearest delimiter, the delimiter included, as `k`; `f` runs outside it, in its place |
| `e $ k` | `Cont0[I, O, A]`, `A => I` → `O` | `e` under a fresh delimiter, its value given to `k` |
| `delay(p)` | `=> Cont0[I, O, A]` → `Cont0[I, O, A]` | built when run: a tail call |

Laws (the TCK checks them): the monad laws; `pure(v) $ k = k(v)`; `shift0(f) $ k = f(k)`;
`(shift0(f).flatMap(g)) $ k = f(x => g(x) $ k)`. `reset`, `shift`, `reset0` and `lift` are functions over these.

The machine: data nodes (`Pure`, `Bind`, `Map`, `Delay`, `Shift0`, `Dollar`, and its own `Resume`), one small loop,
the delimiters outside the innermost on an explicit stack on the heap; a delimiter's body run nested in a JVM frame
up to a depth (`Deep`, 32), past it pushed. Multi-shot `k`. No threads, so it is the same on every platform.

## 3. Layer 2: effects

**Effects are GADTs, first-order in rows, matched exhaustively.** An effect is a sealed family of operations, as
always: `State[S, X]`, its cases `Get[S]() extends State[S, S]`, `Put[S](s: S) extends State[S, Unit]`, each
`Op[X]` of its value. In a row it is `State[S, ?]`: a type of kind `*`, so no higher kinds anywhere — Scala 2 needs no
type lambdas, Java writes the same types (`State<S, ?>`). A clause is total and typed in every language, with no
cast (prototyped: Scala 3, Scala 2.13, Java 25, `-Werror` / `-Xlint:all`):

- the portable form, in Scala 3, Scala 2 and Java alike: each effect has its CASES AS A VISITOR,
  `Cases[S, X, R] { def get(value: Same[S, X]): R; def put(s: S, value: Same[Unit, X]): R }`, each case's
  `fold(c)` calling its own method with `Refl` — a handler that misses a case does not compile (an abstract method
  not implemented), and `value(s)` gives the operation's value its type `X`. `Same[A, B]` is sealed, its only case
  `Refl[A] extends Same[A, A]`, `apply` the identity: no cast, in Java either;
- Scala 3's own, in its front: `op match { case Get() => k(s); case Put(v) => k(()) }` on `State[S, ?] & Op[X]`, `Op`
  covariant — the compiler refines `X` from the case and checks the match exhaustive (non-exhaustive is an error
  under `-Werror`). Scala 2 does not refine `X` there, so the portable code uses the visitor.

**A row is a list in the type, a set in its meaning.** `R = E1 :: E2 :: Nil` (`State[Int, ?] :: Reader[String, ?] :: Nil`), each element `E @ (I, O)`: the effect
with the answer types of its handler's level, `I` the rest's and `O` the whole's. `E` alone means any answer,
unchanged (`I = O`, polymorphic). What a program may perform is given by two proofs, found by the compiler in Scala
3 and Scala 2 and passed in Java:

- `Member[E, R]`: `E` is in `R`, at an index;
- `Sub[R1, R2]`: every effect of `R1` is in `R2`. A program over fewer effects runs where there are more, in any
  order.

**A program is a recipe over evidence.** `Program[R, A]` is built in an `Evidence[R]`: the level of the handler of
each effect of `R`, in the delimiter stack of the run. An operation of `E` is a `shift0` to the level of `E`'s
handler: the machine crosses the delimiters between, which are captured into `k` with their answers unchanged, and
only `E`'s answer types move. An embedding by `Sub` remaps the levels once, when the program is built; nothing is
translated at run time.

**A handler is a `$` with a clause.** `handle(h)(p)`: `p: Program[E @ (I, O) :: R, A]` under a delimiter of a fresh
level, `h.ret: A => I`; an operation of `E` goes to `h(op, k)`, run outside the delimiter, in its place. The result
is a `Program[R, O]`. `Removed[E, R]` takes `E` off a row wherever it is, as a set.

**Answering in place.** A handler that declares `value(op)` (each clause `k(value(op))`, so necessarily `I = O`) is
answered by the machine with no capture, through an evidence vector of the handlers in force. It is an
optimisation: the TCK checks that it changes nothing a program sees.

**Delimited control as an effect.** `reset` and `shift` with ATM are the effect `Control @ (I, O)` at the nearest
`reset`, written with the same handlers; `shift0` stays the kernel's.

## 4. Layer 3: the standard effects

Reader (and `local`, an interceptor); State (functional, and a cell answered in place); Writer; Throws (and
`handleWith`); Choose (all, first: multi-shot); PState (a typestate: the state's type changed by `put`, ATM per
effect); Emit and streams (a `Source` is a program that emits, a `Stream` its consumer, a `Step` a started position
— generators are `shift0`s to the emitter's level, pulled lazily, pushed, mapped and fused); Async (await by a
callback, the platform saying whether it can block: on JavaScript it cannot, and it is a type error to ask).

## 5. Layer 5: the fronts

- **Scala 3**: `A ! E1 + E2` for `Program[R, A]` with the row inferred, `effect(op)`, `op.perform`, `p.handle(h)`,
  `run(p)`; contextual sugar only here.
- **Scala 2**: the same classes; `Member`, `Sub`, `Removed` found by implicits; no syntax of its own beyond aliases.
- **Java**: the same classes; the proofs are values. A handler's body is a generic method given its evidence, so
  an operation is `st.get()` on an evidence of `State<Integer>` in the body's row, typed, and it cannot leave the
  body.

## 6. The portable subset (layers 1–4)

Classes, traits as interfaces, plain generics, functions as single-abstract-method traits (`Fn`, `Thunk`), `null`
for absence. No context functions, givens in signatures, match types, unions, opaque types, type members, higher
kinds, macros, casts, `Nothing` in a supertype's arguments. A GADT match only on the kernel's own nodes and on
proofs, where it refines types; in Java that refinement is one equality witness (`Same<A, B>`), the only cast of
the Java port, in one place.

## 7. Decided

- **Two artifacts.** Scala 3 is the source of truth, written in the subset Scala 2.13 shares (sealed traits and
  case classes, no `enum` below the fronts), so one source compiles for Scala 3 and Scala 2.13, on the JVM, Scala.js
  and Scala Native: `okay-kernel`. A Java port of it, the same structure and names, no Scala: `okay-kernel-java`. The
  TCK — the same random programs, one digest — keeps the two in step. No bridge between them for now.
- **ATM per effect is optional and decided by the prototype.** A row element is `E` (its answer any, unchanged) or
  `E @ (I, O)`. The kernel's `Cont0[I, O, A]` has ATM anyway; whether rows expose it is decided after PState under a
  Writer under a Choose, a typed printf and the TCK on it. If it costs too much, ATM stays at the nearest delimiter
  (`Control`), the kernel unchanged.
- **okay-core and okay-java stay libraries of their own, compatible**: the same TCK, the same std semantics.
- **okay-kernel is rewritten in place**, on what was learned: `Cont0`'s machine (a small loop, the metacontinuation,
  nesting up to `Deep`), answering in place by evidence, `Returning`, the digest-checked random programs.
- **In version 1**: deep handlers and shallow ones (`k` without the handler, natural on `shift0`); interceptors
  (`local`, `handleWith`); Async with fork/join; exceptions of a program's functions go through as they are.
- **Names**: the root package `okay`; artifacts `okay-kernel` and `okay-kernel-java`.

To check by a prototype before the rest is written: per-effect ATM through the handlers between (above); evidence
by levels as fast as okay-core's paths (the mixed program against its 125 µs); the Java front writable (the TCK's
programs in Java, typed).

## 8. The plan

1. Layer 1: `Cont0` and its machine; the reference (the textbook CPS, recursive); the laws, stack safety.
2. Layer 2: rows, evidence, handlers, ATM per effect, Answering; the TCK's random programs, the machine against the
   reference, a digest.
3. Layer 3: the standard effects; then streams and Async.
4. Fronts: Scala 3, Scala 2, Java; the JavaScript and Native builds from the first commit.
5. okay-core and okay-java onto it (by Q3), then okay.

Each step: tests and the TCK green on every platform, a benchmark against okay-core, a commit.
