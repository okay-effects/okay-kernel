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

## 3. The protocol

```java
interface Op<X>           { Type effect(); }
interface Computation<A>  { Step<A> start(Answers host); }       // a program of any implementation, handed out
sealed interface Step<A>  permits Done, Yield {}
record Done<A>(A value)   implements Step<A> {}                   // it came to a value
record Yield<X, A>(Op<X> op, Resume<X, A> k) implements Step<A> {} // it needs op; k the rest
interface Resume<X, A>    { Step<A> resume(X x); }                // multi-shot; later, from another thread, too
interface Answers         { boolean answers(Type effect); <X> X value(Op<X> op); }   // answered in place
```

- **Export.** An implementation runs its program on its own machine; an operation it has no handler of in force is
  first offered to its host in place (`host.value(op)`: no capture, no `Yield` — an `ask`, a `get` across systems
  near free), and only if the host does not answer so, handed out: `Yield(op, k)`.
- **Import.** Another's `Computation` is a program of one's own: one's machine starts it, answers its operations with
  its own handlers, and hands out further what it cannot answer either.
- **Handlers are not in the protocol**: to handle another's program is to run its steps and answer its yields.
- **Cost** only where a program crosses: inside an implementation its own machine, nothing translated.
- **Exceptions** of a program's functions crossing out are an operation: `Raise(e)` of `Throws[Throwable]`, handled
  as any; unhandled at the top, thrown again.
- **Delimited control across systems**: `shift0` to a delimiter of another implementation is an operation of the
  effect `Control`, its delimiter's identity a fresh `Type`.

Proposed, to decide (§8): the program's runtime row, `Computation.effects(): Set<Type>`, checked at the border;
an operation's value type, `Op.value(): Type`, checked in a debug mode.

## 4. The vocabulary: the standard effects

So that `Get` of okay and `Get` of okay-java mean one thing, the API declares the standard effects — their
families, their operations as data, their `Type`s, and their laws, checked by the TCK:

`State`, `Reader`, `Writer`, `Throws`, `Choose`, `Emit`, `Async`, `Control`.

Each operation family is a sealed interface of records with its CASES AS A VISITOR (`Cases`, each case's `fold`,
proofs `Same<A, B>` whose only case is `Refl`): a handler written in Java or Scala 2 is total and typed with no
cast; Scala 3 matches them directly (prototypes: `Exhaustive*.scala`, `Exhaustive.java`). An implementation's own
standard effects are mapped onto these by its bridge, by identity, only at the border.

## 5. The modules

```
okay-kernel/
  api     okay-kernel-api   Type, Op, Computation, Step, Resume, Answers; the vocabulary; Same.
                            A Java jar of no dependency on the JVM; the same API as Scala source on JS and Native
  tck     okay-kernel-tck   the protocol's checks: export, import, multi-shot k, a resume from another thread,
                            nesting through three systems; random programs, one digest
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

## 8. Still to decide

1. The runtime row, `Computation.effects()`: proposed yes — a check at the border, an early error in place of "no
   handler" deep inside.
2. An operation's value type, `Op.value()`: proposed yes, checked in a debug mode only.
3. "A program written once, on any machine": `Program` and `Monad` in the API, so that code meant for every system —
   the standard handlers first — is written once. Proposed yes.

## 9. The plan

1. The API: `Type`, `Op`, the protocol, the vocabulary with its cases; Scala 2 and 3, Java, JS, Native.
2. The TCK.
3. okay-kernel laid out in modules; `OnCore` and `OnJava` back to their repositories as bridges on the protocol.
4. Bridges: okay-core, okay-java, then okay; the TCK across all three in one process.

Each step: tests and the TCK green on every platform, a benchmark where speed is at stake, a commit.
