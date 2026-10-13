package okay.kernel.steps;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import okay.kernel.Aborting;
import okay.kernel.Answering;
import okay.kernel.Answers;
import okay.kernel.Computation;
import okay.kernel.Control;
import okay.kernel.Effects;
import okay.kernel.Fn;
import okay.kernel.General;
import okay.kernel.Handler;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;
import okay.kernel.Resume;
import okay.kernel.Returning;
import okay.kernel.Translating;
import okay.kernel.Step;
import okay.kernel.Thunk;
import okay.kernel.Type;

/**
 * THE KERNEL'S REFERENCE MACHINE IN JAVA: the same as {@code steps}' {@code Steps}, node for node and frame for frame,
 * on the API alone. {@code Effects} and the protocol, simple and exact rather than fast. A program is nodes; the run
 * one loop over them with the frames as an immutable singly linked list, the innermost first — so a captured {@code k}
 * is a prefix of it, multi-shot for free, resumable from any thread. Every delimiter a frame: a handler's, a
 * {@code $}'s; and the frames of failures, finalizers and regions cancellation waits for. An operation goes to the
 * nearest handler of its effect: answered in place ({@code Answering}), ended ({@code Aborting}, its finalizers run),
 * or with {@code k} ({@code General}); with none, to the host in place ({@link Answers}), else handed out
 * ({@link Step.Yield}). Another implementation's program comes in as a node ({@link #foreign}), its yields answered
 * here.
 *
 * <p>One machine, stateless: {@link #INSTANCE}, its methods instance methods (it is an {@code Effects}, given to
 * handlers' clauses as one). Values flow through the frames untyped: the one trusted step is {@link #trust}, where a
 * value is given to the function the program bound it to — the types agree because the program was built typed and an
 * operation's handler is found by its effect's exact identity.
 */
public final class StepsJava implements Effects, Control {
  /** the machine */
  public static final StepsJava INSTANCE = new StepsJava();

  private StepsJava() {}

  // -- the programs: nodes, the value's type a phantom

  private sealed interface Node<A> extends Prog<A>
      permits PureN, BindN, DelayN, PerformN, HandleN, Shift0N, DollarN, FailedN, RecoverN, EnsuringN, UncancelableN,
      ForeignN, ResumeN {}
  private record PureN<A>(A a) implements Node<A> {}
  private record BindN<X, A>(Node<X> p, Fn<X, Prog<A>> f) implements Node<A> {}
  private record DelayN<A>(Thunk<Prog<A>> t) implements Node<A> {}
  private record PerformN<X>(Op<X> op) implements Node<X> {}
  private record HandleN<A, R>(Handler<A, R> h, Program<A> body) implements Node<R> {}
  private record Shift0N<A, I, O>(Fn<Fn<A, Prog<I>>, Prog<O>> body) implements Node<A> {}
  private record DollarN<A, I, O>(Program<A> body, Fn<A, Prog<I>> k) implements Node<O> {}
  private record FailedN<A>(Throwable e) implements Node<A> {}
  private record RecoverN<A>(Program<A> body, Fn<Throwable, Prog<A>> h) implements Node<A> {}
  private record EnsuringN<A>(Program<A> body, Program<Void> fin) implements Node<A> {}
  private record UncancelableN<A>(Program<A> body) implements Node<A> {}
  private record ForeignN<A>(Computation<A> c) implements Node<A> {}
  /** a captured rest resumed: its frames back in front, the value given to them */
  private record ResumeN<A>(Frames frames, Object value) implements Node<A> {}

  @Override public <A> Prog<A> pure(A a) { return new PureN<>(a); }

  @Override public <A, B> Prog<B> flatMap(Prog<A> p, Fn<A, Prog<B>> f) { return new BindN<>(node(p), f); }

  @Override public <A> Prog<A> delay(Thunk<Prog<A>> p) { return new DelayN<>(p); }

  @Override public <X> Prog<X> perform(Op<X> op) { return new PerformN<>(op); }

  @Override public <A, R> Prog<R> handle(Handler<A, R> h, Program<A> body) { return new HandleN<>(h, body); }

  @Override public <A, I, O> Prog<A> shift0(Fn<Fn<A, Prog<I>>, Prog<O>> body) { return new Shift0N<>(body); }

  @Override public <A, I, O> Prog<O> dollar(Program<A> body, Fn<A, Prog<I>> k) { return new DollarN<A, I, O>(body, k); }

  @Override public <A> Prog<A> failed(Throwable e) { return new FailedN<>(e); }

  @Override public <A> Prog<A> recover(Program<A> body, Fn<Throwable, Prog<A>> h) { return new RecoverN<>(body, h); }

  @Override public <A> Prog<A> ensuring(Program<A> body, Program<Void> finalizer) {
    return new EnsuringN<>(body, finalizer);
  }

  @Override public <A> Prog<A> uncancelable(Program<A> body) { return new UncancelableN<>(body); }

  /** another implementation's program, as one of this machine: its yields answered here */
  @Override public <A> Prog<A> foreign(Computation<A> c) { return new ForeignN<>(c); }

  private static <A> Node<A> node(Prog<A> p) {
    if (p instanceof Node<A> n) return n;
    throw new IllegalArgumentException("not a program of this machine: " + p);
  }

  /** THE TRUSTED STEP: a value given where the program, built typed, expects it */
  @SuppressWarnings("unchecked")
  private static <T> T trust(Object v) { return (T) v; }

  // -- the frames: the rest of the run, innermost first

  private sealed interface Frame permits FBind, FHandler, FDollar, FRecover, FEnsuring, FUncancelable, FRestore, FForeign, FAfter, FResumeWith {}
  private record FBind(Fn<Object, Prog<Object>> f) implements Frame {}
  /** compared by identity: an abort unwinds to this very frame */
  private static final class FHandler implements Frame {
    final Handler<Object, Object> h;
    FHandler(Handler<Object, Object> h) { this.h = h; }
  }
  private record FDollar(Fn<Object, Prog<Object>> k) implements Frame {}
  private record FRecover(Fn<Throwable, Prog<Object>> h) implements Frame {}
  private record FEnsuring(Program<Void> fin) implements Frame {}
  private enum FUncancelable implements Frame { INSTANCE }
  /** a finalizer running: when it ends, the outcome it interrupted goes on */
  private record FRestore(Outcome outcome) implements Frame {}
  /** another implementation's program waiting on an operation answered here: its {@code k} */
  private record FForeign(Resume<Object, Object> k) implements Frame {}
  /** a foreign program being cancelled, its finalizers' steps answered here: when it ends, {@code outcome} goes on */
  private record FAfter(Outcome outcome) implements Frame {}
  /** a translating handler's program running outside it: its value given to the body, {@code frames} back in front —
   * no {@code k} made; a failure or a cancellation passes it as a clause's does */
  private record FResumeWith(Frames frames) implements Frame {}

  /** the frames, an immutable singly linked list; {@code null} the empty one */
  private record Frames(Frame head, Frames tail) {}

  private static Frames push(Frame f, Frames s) { return new Frames(f, s); }

  /** the first {@code n} frames, as a list of their own */
  private static Frames take(Frames s, int n) {
    Frame[] fs = new Frame[n];
    for (int i = 0; i < n; i++, s = s.tail) fs[i] = s.head;
    return prependAll(fs, null);
  }

  private static Frames drop(Frames s, int n) {
    for (int i = 0; i < n; i++) s = s.tail;
    return s;
  }

  /** {@code prefix ::: s} */
  private static Frames prependAll(Frames prefix, Frames s) {
    int n = 0;
    for (Frames p = prefix; p != null; p = p.tail) n++;
    Frame[] fs = new Frame[n];
    int i = 0;
    for (Frames p = prefix; p != null; p = p.tail) fs[i++] = p.head;
    return prependAll(fs, s);
  }

  private static Frames prependAll(Frame[] fs, Frames s) {
    for (int i = fs.length - 1; i >= 0; i--) s = push(fs[i], s);
    return s;
  }

  /** the index of the nearest delimiter — a handler's frame or a {@code $}'s — or -1 */
  private static int nearestDelimiter(Frames s) {
    for (int i = 0; s != null; i++, s = s.tail) if (s.head instanceof FHandler || s.head instanceof FDollar) return i;
    return -1;
  }

  private static boolean uncancelable(Frames s) {
    for (; s != null; s = s.tail) if (s.head == FUncancelable.INSTANCE) return true;
    return false;
  }

  /** where the loop is: a node to run, a value, a failure, a cancellation */
  private sealed interface Outcome permits Eval, Ret, Thrown, Cancelling {}
  private record Eval(Node<?> n) implements Outcome {}
  private record Ret(Object v) implements Outcome {}
  private record Thrown(Throwable e) implements Outcome {}
  private enum Cancelling implements Outcome { INSTANCE }

  /** a handler's abort, unwinding to it — its finalizers run, no {@code recover} catches it */
  private static final class Abort extends RuntimeException {
    final FHandler to;
    final Object answer;

    Abort(FHandler to, Object answer) {
      super(null, null, false, false);
      this.to = to;
      this.answer = answer;
    }
  }

  private Node<?> build(Program<?> p) { return node(p.in(this)); }

  /** a user's function, its failure the program's */
  private static Outcome guarded(Thunk<Node<?>> f) {
    try {
      return new Eval(f.get());
    } catch (Throwable t) {
      if (!nonFatal(t)) throw t;
      return new Thrown(t);
    }
  }

  /** as Scala's {@code NonFatal}: a failure of the JVM itself is not a program's */
  private static boolean nonFatal(Throwable e) {
    return !(e instanceof VirtualMachineError || e instanceof LinkageError || e instanceof InterruptedException);
  }

  // -- the loop

  /** run from {@code start} with the frames {@code stack0}, the host answering in place what nothing here does */
  private Step<Object> loop(Outcome start, Frames stack0, Answers host, boolean cancelPending0) {
    Outcome out = start;
    Frames stack = stack0;
    boolean cancelPending = cancelPending0;
    while (true) {
      switch (out) {
        case Eval ev -> {
          switch (ev.n()) {
            case PureN<?> p -> out = new Ret(p.a());
            case BindN<?, ?> b -> {
              stack = push(new FBind(trust(b.f())), stack);
              out = new Eval(b.p());
            }
            case DelayN<?> d -> out = guarded(() -> node(d.t().get()));
            case PerformN<?> p -> {
              Op<Object> op = trust(p.op());
              switch (perform(op, stack, host)) {
                case Performed r -> {
                  stack = r.stack();
                  out = r.out();
                }
                case Suspend y -> {
                  return new Step.Yield<>(y.op(), resumption(y.op(), stack, host, cancelPending));
                }
              }
            }
            case HandleN<?, ?> h -> {
              stack = push(new FHandler(trust(h.h())), stack);
              out = guarded(() -> build(h.body()));
            }
            case Shift0N<?, ?, ?> s -> {
              int i = nearestDelimiter(stack);
              if (i < 0) out = new Thrown(new IllegalStateException("shift0 with no delimiter around it"));
              else {
                Frames captured = take(stack, i + 1);
                stack = drop(stack, i + 1);
                Fn<Object, Prog<Object>> k = x -> new ResumeN<>(captured, x);
                Fn<Fn<Object, Prog<Object>>, Prog<Object>> body = trust(s.body());
                out = guarded(() -> node(body.apply(k)));
              }
            }
            case DollarN<?, ?, ?> d -> {
              stack = push(new FDollar(trust(d.k())), stack);
              out = guarded(() -> build(d.body()));
            }
            case FailedN<?> f -> out = new Thrown(f.e());
            case RecoverN<?> r -> {
              stack = push(new FRecover(trust(r.h())), stack);
              out = guarded(() -> build(r.body()));
            }
            case EnsuringN<?> e -> {
              stack = push(new FEnsuring(e.fin()), stack);
              out = guarded(() -> build(e.body()));
            }
            case UncancelableN<?> u -> {
              stack = push(FUncancelable.INSTANCE, stack);
              out = guarded(() -> build(u.body()));
            }
            case ResumeN<?> r -> {
              stack = prependAll(r.frames(), stack);
              out = new Ret(r.value());
            }
            case ForeignN<?> f -> {
              ForeignNext fs = foreignStep(f.c().start(answersHere(stack, host)), stack);
              stack = fs.stack();
              out = fs.out();
            }
          }
        }

        case Ret r -> {
          if (stack == null) return new Step.Done<>(r.v());
          Frame top = stack.head;
          stack = stack.tail;
          switch (top) {
            case FBind b -> out = guarded(() -> node(b.f().apply(r.v())));
            case FHandler h -> {
              if (h.h instanceof Returning<Object, Object> ret) {
                Outcome o;
                try { o = new Ret(ret.answer(r.v())); } catch (Throwable x) { if (!nonFatal(x)) throw x; o = new Thrown(x); }
                out = o;
              }
              else if (h.h instanceof General<Object, Object> g) out = guarded(() -> node(g.ret(r.v(), this)));
              else out = r;
            }
            case FDollar d -> out = guarded(() -> node(d.k().apply(r.v())));
            case FRecover ignored -> out = r;
            case FEnsuring e -> {
              stack = push(new FRestore(r), stack);
              out = guarded(() -> build(e.fin()));
            }
            case FUncancelable ignored -> {
              if (cancelPending) {
                cancelPending = false;
                out = Cancelling.INSTANCE;
              } else out = r;
            }
            case FRestore fr -> out = fr.outcome();
            case FAfter fa -> out = fa.outcome();
            case FResumeWith rw -> {
              stack = prependAll(rw.frames(), stack);
              out = r;
            }
            case FForeign f -> {
              ForeignNext fs = foreignStep(f.k().resume(r.v()), stack);
              stack = fs.stack();
              out = fs.out();
            }
          }
        }

        case Thrown t -> {
          if (stack == null) {
            if (t.e() instanceof Abort a) return new Step.Failed<>(new IllegalStateException("an abort with its handler gone", a));
            return new Step.Failed<>(t.e());
          }
          Frame top = stack.head;
          stack = stack.tail;
          Throwable e = t.e();
          boolean abort = e instanceof Abort;
          switch (top) {
            case FHandler h when e instanceof Abort a && a.to == h -> out = new Ret(a.answer);
            case FRecover r when !abort -> out = guarded(() -> node(r.h().apply(e)));
            case FEnsuring en -> {
              stack = push(new FRestore(t), stack);
              out = guarded(() -> build(en.fin()));
            }
            case FRestore ignored -> out = t; // a finalizer failed: its failure goes on, the one it interrupted lost
            case FAfter fa -> out = fa.outcome(); // a foreign program's cancellation failed: the outcome it was for goes on
            case FUncancelable ignored -> {
              // the region ended, failed: a cancellation waiting goes on
              if (cancelPending) { cancelPending = false; out = Cancelling.INSTANCE; } else out = t;
            }
            case FForeign f when !abort -> {
              ForeignNext fs = foreignStep(f.k().fail(e), stack);
              stack = fs.stack();
              out = fs.out();
            }
            case FForeign f -> {
              // an abort crossing it: it is cancelled, its finalizers' operations answered here, then the abort goes on
              ForeignNext fs = foreignStep(f.k().cancel(), push(new FAfter(t), stack));
              stack = fs.stack();
              out = fs.out();
            }
            default -> out = t;
          }
        }

        case Cancelling c -> {
          if (stack == null) return new Step.Cancelled<>();
          Frame top = stack.head;
          stack = stack.tail;
          switch (top) {
            case FEnsuring e -> {
              stack = push(new FRestore(c), stack);
              out = guarded(() -> build(e.fin()));
            }
            case FForeign f -> {
              // it is cancelled too, its finalizers' operations answered here, then this cancellation goes on
              ForeignNext fs = foreignStep(f.k().cancel(), push(new FAfter(c), stack));
              stack = fs.stack();
              out = fs.out();
            }
            case FAfter fa -> out = fa.outcome();
            default -> out = c;
          }
        }
      }
    }
  }

  // -- an operation

  private sealed interface PerformResult permits Performed, Suspend {}
  private record Performed(Frames stack, Outcome out) implements PerformResult {}
  private record Suspend(Op<Object> op) implements PerformResult {}

  private PerformResult perform(Op<Object> op, Frames stack, Answers host) {
    Type e = op.effect();
    int i = 0;
    FHandler frame = null;
    for (Frames s = stack; s != null; s = s.tail, i++) {
      if (s.head instanceof FHandler h && h.h.effect() == e) {
        frame = h;
        break;
      }
    }
    if (frame == null) {
      if (host.answers(e)) return new Performed(stack, inPlace(() -> new Ret(host.value(op))));
      return new Suspend(op);
    }
    FHandler to = frame;
    if (to.h instanceof Answering<Object, Object> a) return new Performed(stack, inPlace(() -> new Ret(a.value(op))));
    if (to.h instanceof Aborting<Object, Object> a) return new Performed(stack, inPlace(() -> new Thrown(new Abort(to, a.abort(op)))));
    if (to.h instanceof Translating<Object, Object> t) {
      // the program outside the handler, the rest of the body then resumed with its value: no capture of k
      return new Performed(push(new FResumeWith(take(stack, i + 1)), drop(stack, i + 1)), guarded(() -> build(t.translate(op))));
    }
    if (to.h instanceof General<Object, Object> g) {
      Frames captured = take(stack, i + 1);
      Fn<Object, Prog<Object>> k = x -> new ResumeN<>(captured, x);
      return new Performed(drop(stack, i + 1), guarded(() -> node(g.apply(op, k, this))));
    }
    return new Performed(stack, new Thrown(new IllegalStateException("a handler of no shape: " + to.h)));
  }

  /** an answer in place, its failure where the operation was */
  private static Outcome inPlace(Thunk<Outcome> f) {
    try {
      return f.get();
    } catch (Throwable t) {
      if (!nonFatal(t)) throw t;
      return new Thrown(t);
    }
  }

  // -- the protocol

  private static Handler<Object, Object> nearest(Frames s, Type e) {
    for (; s != null; s = s.tail) if (s.head instanceof FHandler h && h.h.effect() == e) return h.h;
    return null;
  }

  /** what a foreign program's operation is answered in place by here: the nearest handler of its effect answering in
   * place, or — none here — the host */
  private static Answers answersHere(Frames stack, Answers host) {
    return new Answers() {
      @Override public boolean answers(Type e) {
        Handler<Object, Object> h = nearest(stack, e);
        return h == null ? host.answers(e) : h instanceof Answering<?, ?>;
      }

      @Override public <X> X value(Op<X> op) {
        Handler<Object, Object> h = nearest(stack, op.effect());
        if (h == null) return host.value(op);
        if (h instanceof Answering<Object, Object> a) return a.value(op);
        throw new IllegalStateException("not answered in place: " + op);
      }
    };
  }

  private record ForeignNext(Frames stack, Outcome out) {}

  /** a foreign program's step, taken here: a value goes on, a failure is thrown here, a cancellation cancels this
   * path, an operation is performed here — its answer to the foreign {@code k} (the frame {@code FForeign}) */
  private static ForeignNext foreignStep(Step<?> s, Frames stack) {
    return switch (s) {
      case Step.Done<?> d -> new ForeignNext(stack, new Ret(d.value()));
      case Step.Failed<?> f -> new ForeignNext(stack, new Thrown(f.error()));
      case Step.Cancelled<?> c -> new ForeignNext(stack, Cancelling.INSTANCE);
      case Step.Yield<?, ?> y -> new ForeignNext(push(new FForeign(trust(y.k())), stack), new Eval(new PerformN<>(y.op())));
    };
  }

  /** the rest of a run suspended on {@code op}, handed out. A cancellation inside a region it waits for: the same wait
   * again, the cancellation pending — the region's end cancels */
  private Resume<Object, Object> resumption(Op<Object> op, Frames stack, Answers host, boolean cancelPending) {
    return new Resume<>() {
      @Override public Step<Object> resume(Object x) { return loop(new Ret(x), stack, host, cancelPending); }

      @Override public Step<Object> fail(Throwable e) { return loop(new Thrown(e), stack, host, cancelPending); }

      @Override public Step<Object> cancel() {
        if (uncancelable(stack)) return new Step.Yield<>(op, resumption(op, stack, host, true));
        return loop(Cancelling.INSTANCE, stack, host, false);
      }
    };
  }

  /** A PROGRAM OF THIS MACHINE, HANDED OUT: its row given, as it is known statically where it was written */
  @Override public <A> Computation<A> computation(Program<A> p, Set<Type> row) {
    Set<Type> fixed = Collections.unmodifiableSet(new HashSet<>(row));
    return new Computation<>() {
      @Override public Step<A> start(Answers host) { return trust(loop(guarded(() -> build(p)), null, host, false)); }

      @Override public Set<Type> effects() { return fixed; }
    };
  }

  /** run a program with nothing left to perform: its value, or its failure thrown (a checked one wrapped in an
   * {@link UndeclaredThrowableException}) */
  public <A> A run(Program<A> p) {
    return switch (computation(p, Collections.<Type>emptySet()).start(Answers.NONE)) {
      case Step.Done<A> d -> d.value();
      case Step.Failed<A> f -> {
        if (f.error() instanceof RuntimeException e) throw e;
        if (f.error() instanceof Error e) throw e;
        throw new UndeclaredThrowableException(f.error());
      }
      case Step.Cancelled<A> c -> throw new IllegalStateException("cancelled");
      case Step.Yield<?, A> y -> throw new IllegalStateException("an operation with no handler: " + y.op());
    };
  }
}
