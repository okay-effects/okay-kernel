package okay.kernel;

import okay.demo.Programs;
import okay.std.Await;
import okay.std.Handlers;
import okay.std.Tell;

/** THE KERNEL FROM JAVA: a program written in Java, and Scala 3's run from Java under handlers Java installs */
public final class FromJava {
  private FromJava() {}

  /** a tell and an await, as Scala's `p`: written in Java, its operations the same data */
  public static Program<Integer> q(int a) {
    return m -> m.flatMap(m.perform(new Tell<>("java " + a)),
        u -> m.map(m.perform(new Await<Integer>(() -> a * 10)), x -> (Integer) x + 1));
  }

  /** Scala 3's `p(1)`, its handlers installed from Java, on the monad Java chose */
  public static Object scalasUnderJavas(Monad m) {
    Program<Object> p = Programs.p(1);
    Program<Object> withAsync = mm -> mm.handle(Handlers.async(), p);
    Program<Object> withLog = mm -> mm.handle(Handlers.writer(), withAsync);
    return Program.run(withLog, m);
  }
}
