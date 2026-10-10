package okay.jkernel;

import okay.kernel.*;

import java.util.function.Supplier;

/**
 * A MONAD, THE REFERENCE: the textbook — a program evaluates to its value or to an operation with the rest; a handler
 * takes those of its effect, its clause in its place, {@code k} the rest under it again, and passes the others on.
 * Recursive, not in constant stack: what every monad must agree with, on programs small enough
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class Ref implements Monad {
  static final Ref INSTANCE = new Ref();

  private sealed interface N extends Prog {}
  private record Pure(Object a) implements N {}
  private record FlatMap(N m, Fn f) implements N {}
  private record Delay(Thunk p) implements N {}
  private record Perform(Op op) implements N {}
  private record Handle(Handler h, Program body) implements N {}
  private record Resumed(Supplier<Res> r) implements N {}

  private sealed interface Res {}
  private record Done(Object v) implements Res {}
  private record Cap(Op op, Fn<Object, Res> k) implements Res {}

  public <A> Prog<A> pure(A a) { return new Pure(a); }
  public <A, B> Prog<B> flatMap(Prog<A> p, Fn<A, Prog<B>> f) { return new FlatMap((N) p, f); }
  public <A> Prog<A> delay(Thunk<Prog<A>> p) { return new Delay(p); }
  public <X> Prog<X> perform(Op<X> op) { return new Perform(op); }
  public <A, R> Prog<R> handle(Handler<A, R> h, Program<A> body) { return new Handle(h, body); }

  public <A> A run(Prog<A> p) {
    return switch (eval((N) p)) {
      case Done d -> (A) d.v;
      case Cap c -> throw new IllegalStateException("an operation of " + c.op.effect() + " with no handler of it in force");
    };
  }

  private static Res andThen(Res r, Fn<Object, Res> g) {
    return switch (r) {
      case Done d -> g.apply(d.v);
      case Cap c -> new Cap(c.op, x -> andThen(c.k.apply(x), g));
    };
  }

  private Res delimit(Handler h, Res r) {
    return switch (r) {
      case Done d -> eval((N) h.ret(d.v, this));
      case Cap c when c.op.effect() == h.effect() -> {
        Fn<Object, Prog<Object>> kk = x -> new Resumed(() -> delimit(h, c.k.apply(x)));
        yield eval((N) h.apply(c.op, kk, this));
      }
      case Cap c -> new Cap(c.op, x -> delimit(h, c.k.apply(x)));
    };
  }

  private Res eval(N n) {
    return switch (n) {
      case Pure p -> new Done(p.a);
      case FlatMap f -> andThen(eval(f.m), x -> eval((N) f.f.apply(x)));
      case Delay d -> eval((N) d.p.get());
      case Perform p -> new Cap(p.op, Done::new);
      case Handle h -> delimit(h.h, eval((N) h.body.in(this)));
      case Resumed r -> r.r.get();
    };
  }
}
