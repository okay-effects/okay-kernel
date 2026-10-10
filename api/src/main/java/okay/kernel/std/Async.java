package okay.kernel.std;

import okay.kernel.Op;
import okay.kernel.Program;
import okay.kernel.Same;
import okay.kernel.Type;

/** ASYNC: a value given later by a callback; a program forked, its fiber joined or cancelled. Threads and timers are
 * the platform's: its handler's */
public final class Async {
  private Async() {}

  public static final Type TYPE = Type.of(Async.class);

  /** a callback: called once, with the value or the failure */
  public interface Callback<X> {
    void done(X x);

    void failed(Throwable e);
  }

  /** what registers a callback */
  @FunctionalInterface
  public interface Register<X> {
    void register(Callback<X> callback);
  }

  /** a forked program, running: its handler's own */
  public interface Fiber<X> {}

  /**
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what a handler found by the effect's identity is given.
   * The one trusted step of the vocabulary: an operation of the effect {@code Async...} is one of {@code Ops}, its
   * type arguments the effect's (its {@code Type} says so). Checked as far as the JVM can: not one of them, a failure
   */
  @SuppressWarnings("unchecked")
  public static <X> Ops<X> narrow(okay.kernel.Op<X> op) {
    if (op instanceof Async.Ops<?>) return (Ops<X>) op;
    throw new IllegalArgumentException("not an operation of Async: " + op);
  }

  public sealed interface Ops<X> extends Op<X> permits Await, Fork, Join, Cancel {
    <R> R fold(Cases<X, R> c);
  }

  public interface Cases<X, R> {
    R await(Register<X> register);

    <Y> R fork(Program<Y> body, Same<Fiber<Y>, X> value);

    R join(Fiber<X> fiber);

    <Y> R cancel(Fiber<Y> fiber, Same<Void, X> value);
  }

  public record Await<X>(Type effect, Register<X> register) implements Ops<X> {
    public Await(Register<X> register) { this(TYPE, register); }
    public <R> R fold(Cases<X, R> c) { return c.await(register); }
  }

  public record Fork<Y>(Type effect, Program<Y> body) implements Ops<Fiber<Y>> {
    public Fork(Program<Y> body) { this(TYPE, body); }
    public <R> R fold(Cases<Fiber<Y>, R> c) { return c.fork(body, new Same.Refl<>()); }
  }

  public record Join<X>(Type effect, Fiber<X> fiber) implements Ops<X> {
    public Join(Fiber<X> fiber) { this(TYPE, fiber); }
    public <R> R fold(Cases<X, R> c) { return c.join(fiber); }
  }

  public record Cancel<Y>(Type effect, Fiber<Y> fiber) implements Ops<Void> {
    public Cancel(Fiber<Y> fiber) { this(TYPE, fiber); }
    public <R> R fold(Cases<Void, R> c) { return c.cancel(fiber, new Same.Refl<>()); }
  }
}
