package okay.kernel.std;

import okay.kernel.Key;
import okay.kernel.Op;
import okay.kernel.Program;
import okay.kernel.Same;
import okay.kernel.Type;

/** ASYNC: a value given later by a callback; a program forked, its fiber joined or cancelled. Threads and timers are
 * the platform's: its handler's */
public final class Async {
  private Async() {}

  /** its key: one effect, of no parameter */
  public static final Key<Async> KEY = Key.of(Type.of(Async.class));

  /** its exact identity */
  public static final Type TYPE = KEY.type();

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
   * AN OPERATION OF THIS EFFECT, AS ONE OF ITS OPERATIONS — what the handler of {@code key} is given: checked to be of
   * {@code key} (the same key, by identity) and one of {@code Ops}. Not, a failure
   */
  @SuppressWarnings("unchecked")
  public static <X> Ops<X> narrow(Key<Async> key, Op<X> op) {
    if (key.owns(op) && op instanceof Async.Ops<?>) return (Ops<X>) op;
    throw new IllegalArgumentException("not an operation of " + key + ": " + op);
  }

  /** its operations: the kernel's records below, or an implementation's own — implementing it, its {@code fold}
   * calling its own case (the TCK checks it) */
  public interface Ops<X> extends Op<X> {
    <R> R fold(Cases<X, R> c);
  }

  public interface Cases<X, R> {
    R await(Register<X> register);

    <Y> R fork(Program<Y> body, Same<Fiber<Y>, X> value);

    R join(Fiber<X> fiber);

    <Y> R cancel(Fiber<Y> fiber, Same<Void, X> value);
  }

  public record Await<X>(Key<Async> key, Register<X> register) implements Ops<X> {
    public Await(Register<X> register) { this(KEY, register); }
    public <R> R fold(Cases<X, R> c) { return c.await(register); }
  }

  public record Fork<Y>(Key<Async> key, Program<Y> body) implements Ops<Fiber<Y>> {
    public Fork(Program<Y> body) { this(KEY, body); }
    public <R> R fold(Cases<Fiber<Y>, R> c) { return c.fork(body, new Same.Refl<>()); }
  }

  public record Join<X>(Key<Async> key, Fiber<X> fiber) implements Ops<X> {
    public Join(Fiber<X> fiber) { this(KEY, fiber); }
    public <R> R fold(Cases<X, R> c) { return c.join(fiber); }
  }

  public record Cancel<Y>(Key<Async> key, Fiber<Y> fiber) implements Ops<Void> {
    public Cancel(Fiber<Y> fiber) { this(KEY, fiber); }
    public <R> R fold(Cases<Void, R> c) { return c.cancel(fiber, new Same.Refl<>()); }
  }
}
