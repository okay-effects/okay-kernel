# okay-kernel: the design

## 0. What it is

okay-kernel is a PROTOCOL, minimal and portable: what lets every implementation of effects and delimited control —
okay's two, okay-core's two, okay-java's two, systems to come — work with each other in one process. It is not an
implementation of its own. Each implementation keeps its machine, its rows, its syntax; with the protocol its
programs, effects and handlers meet another's.

Requirements behind it:

- delimited continuations with answer-type modification, and algebraic effects with rows in their types — each
  implementation's business, checked by its own types;
- Scala 3, Scala 2, Java; the JVM, JavaScript, Native;
- full interoperability: a program written in one language runs under handlers written in another, in one process;
  machines work with each other;
- compatibility with okay is a goal of the kernel too.

## 1. What an effect is, to the kernel

The kernel does not know what an effect MEANS: that is its handlers', and they are each implementation's. To the
kernel an effect is two things:

- an IDENTITY: a `Type` (§2), as `State[Int]`;
- its OPERATIONS: values, each knowing its effect and of a value type `X` — `Op<X> { Type effect(); }`.

An implementation that cannot answer an operation itself hands it out, and that is all the protocol is (§3).

## 2. Identity: `Type`

```java
Type.of(key, args...)   // a key — a class or ANY value: an object, a literal, an enum's case, at worst a string —
                        // applied to arguments; keys compared by equals; interned, so types are compared by ==
Type.fresh()            // a type no other is: a capability's effect, one a run of its handler
int id()                // dense: a machine's array of handlers in force, by effect, O(1)
```

- A primitive is its box: Scala's `Int` and Java's `Integer` one type.
- Found by the compiler, in Scala 3 (givens) and Scala 2 (implicits), with no macro: a class, a class applied
  (`State[Map[String, Option[Int]]]`, by the constructor's arity, its class by `ClassTag`), a tagged type
  (`Long @@ UserId.type`, `String @@ "mail"`, `Long @@ OrderTag`: the tag's key its value or its class). Anything else
  in one line: `given TypeOf[Sku] = TypeOf.named("Sku")`. Java writes the same calls.
- A string key is one for the whole process: an object or a class is the better key, a string a last resort
  (`"com.acme.Sku"`). An opaque type or a tag is declared in an object of its own (inside its own file it is
  transparent).
- Prototypes: `docs/prototypes/Types3.scala`, `Types2.scala`.

### The typed key

`Key<F>` is what operations and handlers carry: the effect as a type (`Key<State<Integer>>`), one a `Type`, interned.
A handler found by an operation's key is the operation's own: `State.narrow(key, op)` checks the key by identity, so
the operation's types are the key's. The Java of `Class<T>` in a typesafe heterogeneous container: a key cannot be had
at a type it is not, without a cast in one's own code.

Why a key and not a proof at compile time: an operation and its handler compiled apart — two languages, two
libraries — meet only at run time, by a value; a compiler cannot turn the equality of two values into the equality of
two types (Java's `Class.cast`, Haskell's `Typeable`/`eqT` meet the same). So the kernel's one trusted step is in
`Key.of`: a key asked for at `F` by its type is the one made for that type — sound as long as a `Type` names one type
(the exact types of `TypeOf`; a name of one's own, a string, given to one type only). Inside one implementation there
is no cast at all (rows and paths, okay-zero); at the border a foreign program's row is checked once (`effects()`).

## 3. The protocol

```java
interface Op<X>           { Type effect(); }
interface Computation<A>  { Step<A> start(Answers host); }       // a program of any implementation, handed out
sealed interface Step<A>  permits Done, Yield, Failed, Cancelled {}
record Done<A>(A value)                      implements Step<A> {}   // it came to a value
record Yield<X, A>(Op<X> op, Resume<X, A> k) implements Step<A> {}   // it needs op; k the rest
record Failed<A>(Throwable error)            implements Step<A> {}   // it failed: an exception, as data
record Cancelled<A>()                        implements Step<A> {}   // cancelled from outside, its finalizers run
interface Resume<X, A> {                                             // multi-shot; later, from another thread, too
  Step<A> resume(X x);        // on, with the operation's value
  Step<A> fail(Throwable e);  // on, the operation having thrown e where it was performed: catchable inside
  Step<A> cancel();           // ended: its finalizers run, not catchable
}
interface Answers         { boolean answers(Type effect); <X> X value(Op<X> op); }   // answered in place
```

- **Export.** An implementation runs its program on its own machine; an operation it has no handler of in force is
  first offered to its host in place (`host.value(op)`: no capture, no `Yield` — an `ask`, a `get` across systems
  near free; if `value` throws, it is a `fail` where the operation was), and only if the host does not answer so,
  handed out: `Yield(op, k)`.
- **Import.** Another's `Computation` is a program of one's own: one's machine starts it, answers its operations with
  its own handlers, and hands out further what it cannot answer either.
- **Handlers are not in the protocol**: to handle another's program is to run its steps and answer its yields.
- **Cost** only where a program crosses: inside an implementation its own machine, nothing translated.
- **Failures are data.** An exception of a program's functions, not caught inside, comes out as `Failed(e)`, not
  thrown through another's machine; its host decides. `k.fail(e)` goes on as if the operation threw `e` where it was
  performed (a generator's `throw`): an `Async` callback's error, a handler that cannot answer, a resource's
  finalizers. Typed errors stay the effect `Throws[E]`.
- **Cancellation is cooperative**: only where a program is suspended (a `Yield`), never in the middle of its code.
  `k.cancel()` runs the program's finalizers — programs themselves, so it answers a `Step`: `Yield`s while they need
  operations, `Cancelled` at the end, `Failed` if one failed. It cannot be caught (a `catch` swallowing a
  cancellation is how cancellation breaks). A multi-shot `k`: cancelling one resumption cancels that path, its
  finalizers once a path. An implementation with no finalizers answers `Cancelled` at once. Regions where
  cancellation waits (`uncancelable`, so that `bracket` is safe): in version 1.

| what | as | caught |
|---|---|---|
| a typed error | the effect `Throws[E]` | yes |
| a failure | `Failed`, `k.fail` | yes |
| a cancellation | `Cancelled`, `k.cancel` | no: finalizers only |

Decided (§8): the program's runtime row, `Computation.effects(): Set<Type>`, checked at the border; an operation's
value type, `Op.value(): Type`, checked in a debug mode.

## 3a. Written once: `Effects` and the shapes of handlers

Code meant for every implementation — the standard handlers first — is written once over `Effects`: what an
implementation offers it. Each implementation's bridge implements `Effects` on its machine and takes the shapes of
handlers as its own, so the standard handlers of the kernel work in every implementation.

```java
interface Effects {
  <A> Prog<A> pure(A a);
  <A, B> Prog<B> flatMap(Prog<A> p, Fn<A, Prog<B>> f);
  <A> Prog<A> delay(Thunk<Prog<A>> p);
  <X> Prog<X> perform(Op<X> op);
  <A, R> Prog<R> handle(Handler<A, R> h, Program<A> body);
  // control, the minimal pair: the answer types at the binary level only parameters, checked by the fronts
  <A, I, O> Prog<A> shift0(Fn<Fn<A, Prog<I>>, Prog<O>> body);   // k the rest up to the nearest delimiter
  <A, I, O> Prog<O> dollar(Program<A> body, Fn<A, Prog<I>> k);   // body under a fresh delimiter, its value to k
}
interface Program<A> { Prog<A> in(Effects e); }

interface Handler<A, R>   { Type effect(); }                       // a handler is a delimiter too
interface Answering<A, R> extends Handler<A, R> { <X> X value(Op<X> op); }   // answered in place
interface Aborting<A, R>  extends Handler<A, R> { <X> R abort(Op<X> op); }   // k never called
interface Returning<A, R> extends Handler<A, R> { R answer(A a); }           // its return a value
interface General<A, R>   extends Handler<A, R> {                            // a clause with k
  Prog<R> ret(A a, Effects e);
  <X> Prog<R> apply(Op<X> op, Fn<X, Prog<R>> k, Effects e);
}
```

| shape | needs `Effects` | the standard handlers |
|---|---|---|
| `Answering` | no | State (a cell), Reader, Emit's `forEach` |
| `Aborting` | no | Throws |
| `Returning` | no | almost all: Writer, Throws, Choose, State |
| `General` | yes | Choose (all, first), Writer (functional), Emit's generators, Async, Control, `ensuring`, `bracket` |

A handler may be two at once: a cell is `Answering` and `Returning`, `Choose` `General` and `Returning`. The first
three are data, no `Effects`: an implementation's bridge makes them its own handlers once, at no cost (`Returning`
under okay-core: handlers 40% faster). An interceptor (`local`, `handleWith`) is a `handle` whose clause performs
the same operation outward (`e.perform(op)`): a clause runs outside its handler.

**No wrapper: implementations implement the API.** okay-core and okay-java depend on `okay-kernel-api` (a jar of
interfaces and a few records, no dependency of its own; on JS and Native its Scala mirror): their own nodes are
`Prog`s, their own operations implement the vocabulary's families (`State.Ops`, … — open interfaces, each case's
`fold` calling its own case; the TCK checks it). So code written once is built in an implementation's nodes as they
are, and an operation crosses the border as it is. `Direct`, the fast path of the bridges' time, is gone. `Type` and
`Same` stay concrete (an identity, a proof); `Step` stays data.

Not written once, by design: the fronts (`A ! E1 + E2`, enums and matches, Java's capabilities), the machines, the
platform (threads, timers: a hook), and programs where speed matters — they run on their implementation's own machine.

## 4. The vocabulary: the standard effects

So that `Get` of okay and `Get` of okay-java mean one thing, the API declares the standard effects — their
families, their operations as data, their `Type`s, and their laws, checked by the TCK:

`State`, `Reader`, `Writer`, `Throws`, `Choose`, `Emit`, `Async`, `Control` — and finalizers: `ensuring`, `bracket`.

Each operation family is a sealed interface of records with its CASES AS A VISITOR (`Cases`, each case's `fold`,
proofs `Same<A, B>` whose only case is `Refl`): a handler written in Java or Scala 2 is total and typed with no
cast; Scala 3 matches them directly (prototypes: `Exhaustive*.scala`, `Exhaustive.java`). An implementation's own
standard effects are mapped onto these by its bridge, by identity, only at the border.

## 5. The modules

```
okay-kernel/
  api     okay-kernel-api   Type, Op, Computation, Step, Resume, Answers; the vocabulary; Same.
                            A Java jar of no dependency on the JVM; the same API as Scala source on JS and Native
  scala   okay-kernel-scala Scala's own over the API: TypeOf (exact types found by the compiler), @@; on JS and
                            Native the API's mirror. One source for Scala 3 and 2.13, on the JVM, JS and Native
  tck     okay-kernel-tck   the protocol's checks: export, import, multi-shot k, a resume from another thread,
                            nesting through three systems; random programs, one digest
  legacy/ the kernel before the protocol, unchanged, until each part moves onto the API or to its own repository
  …       what has no other home, each a module of its own (§7)
```

Each implementation and its bridge live in their own repositories: okay-core, okay-java, okay. Scala 2 wherever it
makes sense: the kernel's Scala modules are written in the subset Scala 2.13 and Scala 3 share and built for both,
on the JVM, and on JavaScript and Native where the module has no JVM dependency.

## 6. Compatibility with okay

okay's `Effects[M]` over rows of effects `E[+_]` maps onto the protocol: a row element `E[?]` is a `Type`, an
operation an `Op`. A module of the kernel (or okay's own bridge) makes okay's programs `Computation`s and runs others'
under okay's handlers.

## 7. What is in okay-kernel now, and where it goes

Moved back where it belongs: `OnCore` to okay-core's bridge, `OnJava` to okay-java's. What has no other home stays,
each a module of its own beside the API: the kernel's machine of steps (Scala, and its Java one), the typed view of
Scala 3, `lexical`, `Cont` in Java.

## 8. Decided

`Effects` its name; control (`shift0`, `$`) its methods; `Aborting` a shape; failures and cancellation outcomes of a
step; the runtime row `Computation.effects()`, checked at the border; an operation's value type `Op.value()`,
checked in a debug mode; implementations depend on the API and implement it directly (no `Direct`); `uncancelable` regions in
version 1.

## 9. The plan

1. The API: `Type`, `Op`, the protocol, the vocabulary with its cases; Scala 2 and 3, Java, JS, Native.
2. The TCK.
3. okay-kernel laid out in modules; `OnCore` and `OnJava` back to their repositories as bridges on the protocol.
4. Bridges: okay-core, okay-java, then okay; the TCK across all three in one process.

Each step: tests and the TCK green on every platform, a benchmark where speed is at stake, a commit.
