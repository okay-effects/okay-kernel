package okay.jkernel.std;

import okay.kernel.std.*;

import okay.kernel.Effect;
import okay.kernel.Fn;
import okay.kernel.Handler;
import okay.kernel.Monad;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;
import okay.kernel.Sink;

/** ASYNC AT THE TOP: a run that awaits callbacks called later, from any thread — the run stops at the await, the
 * callback goes on with it, a run of its own, to the end: the value to {@code done}. Its handler the outermost */
@SuppressWarnings("unchecked")
public final class Async {
  private Async() {}

  static final class Cell<X> implements Callback<X> {
    volatile boolean answered;
    volatile boolean registered;
    X value;
    Throwable failure;
    volatile Callback<X> later;

    public synchronized void ok(X a) {
      if (registered) later.ok(a);
      else {
        value = a;
        answered = true;
      }
    }

    public synchronized void fail(Throwable e) {
      if (registered) later.fail(e);
      else {
        failure = e;
        answered = true;
      }
    }
  }

  static <T extends Throwable> RuntimeException sneaky(Throwable e) throws T {
    throw (T) e;
  }

  /** the run's answer: its value, or nothing yet — suspended at an await */
  private record Answer<A>(boolean done, A value) {}

  public static <A> void start(Program<A> p, Monad m, Sink<A> done, Sink<Throwable> failed) {
    new Object() {
      void go(Prog<Answer<A>> c) {
        Answer<A> r;
        try {
          r = m.run(c);
        } catch (Throwable e) {
          failed.accept(e);
          return;
        }
        if (r.done) done.accept(r.value);
      }

      final Handler<A, Answer<A>> top = new Handler<>() {
        public Effect effect() { return Effects.ASYNC; }
        public Prog<Answer<A>> ret(A a, Monad mm) { return mm.pure(new Answer<>(true, a)); }
        public <X> Prog<Answer<A>> apply(Op<X> op, Fn<X, Prog<Answer<A>>> k, Monad mm) {
          Cell<X> cell = new Cell<>();
          ((Await<X>) op).register().accept(cell);
          synchronized (cell) {
            if (cell.answered) {
              if (cell.failure != null) throw sneaky(cell.failure);
              return k.apply(cell.value);
            }
            // answered later: from the callback, the rest a run of its own
            cell.later = new Callback<>() {
              public void ok(X a) { go(k.apply(a)); }
              public void fail(Throwable e) { failed.accept(e); }
            };
            cell.registered = true;
            return mm.pure(new Answer<>(false, null));
          }
        }
      };

      void start() { go(m.handle(top, p)); }
    }.start();
  }
}
