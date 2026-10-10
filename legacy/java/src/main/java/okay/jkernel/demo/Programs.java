package okay.jkernel.demo;

import okay.jkernel.Cont;
import okay.jkernel.std.Control;
import okay.jkernel.std.Handlers;
import okay.kernel.Program;
import okay.kernel.std.Await;
import okay.kernel.std.Effects;
import okay.kernel.std.Logged;
import okay.kernel.std.Tell;

/** PROGRAMS OF JAVA on the kernel API, binary: run by okay-kernel's tests on its machines, under its handlers */
public final class Programs {
  private Programs() {}

  /** okay-kernel's demo {@code p}, in Java: a log of Strings and an await, its value */
  public static Program<Integer> p(int a) {
    return m -> m.flatMap(m.perform(new Tell<>("java got " + a, Effects.writer(String.class))),
        u -> m.flatMap(m.perform(new Await<Integer>(cb -> cb.ok(a + 1))),
            x -> m.map(m.perform(new Tell<>("java awaited " + x, Effects.writer(String.class))), v -> x * 2)));
  }

  /** this implementation's handlers around any program of a log of Strings and awaits: the log a Java list */
  public static <A> Program<Logged<String, A>> logged(Program<A> body) {
    return m -> m.handle(Handlers.<String, A>writer(Effects.writer(String.class)), mm -> mm.handle(Handlers.<A>async(), body));
  }

  /** 1 + reset(2 * shift(k -> k(k(10)))): 41 */
  public static Program<Integer> shifted() {
    return m -> m.map(Control.<Integer>reset(p -> mm -> mm.map(p.<Integer>shift(k -> m3 -> m3.flatMap(k.apply(10), k)).in(mm), x -> x * 2)).in(m), x -> x + 1);
  }

  /** shift0(k -> k(k(10))), open: its delimiter wherever it is run — Scala's {@code reset0} among them */
  public static Cont<Cont<Integer, Integer, Integer>, Cont<Integer, Integer, Integer>, Integer> shift0Twice() {
    return Cont.<Integer, Integer, Integer, Cont<Integer, Integer, Integer>, Integer>shift0(k -> k.apply(10).flatMap(k::apply));
  }

  /** reset0(body + 1), the body of any language's {@code Cont} */
  public static Integer plusOneReset0(Program<Integer> body) {
    return Cont.value(Cont.<Integer, Integer, Integer, Integer, Integer>reset0(
        Cont.<Cont<Integer, Integer, Integer>, Cont<Integer, Integer, Integer>, Integer>from(body).map(x -> x + 1)));
  }
}
