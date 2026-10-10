package okay.kernel.std;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okay.kernel.Program;

/** THE STANDARD HANDLERS' ANSWERS, as data every language reads */
public final class Results {
  private Results() {}

  /** a state's final value with the body's */
  public record Final<S, A>(S state, A value) {}

  /** the values told, in order, with the body's */
  public record Logged<W, A>(List<W> log, A value) {
    public static <W, A> Logged<W, A> of(A value) { return new Logged<>(List.of(), value); }

    /** {@code w} in front of the log */
    public Logged<W, A> told(W w) {
      List<W> l = new ArrayList<>(log.size() + 1);
      l.add(w);
      l.addAll(log);
      return new Logged<>(Collections.unmodifiableList(l), value);
    }
  }

  /** a body's value, or the error it raised */
  public sealed interface Result<E, A> permits Ok, Err {
    <R> R fold(Fn2<A, R> ok, Fn2<E, R> err);
  }
  public record Ok<E, A>(A value) implements Result<E, A> {
    public <R> R fold(Fn2<A, R> ok, Fn2<E, R> err) { return ok.apply(value); }
  }
  public record Err<E, A>(E error) implements Result<E, A> {
    public <R> R fold(Fn2<A, R> ok, Fn2<E, R> err) { return err.apply(error); }
  }
  @FunctionalInterface
  public interface Fn2<A, R> { R apply(A a); }

  /** a generator's position: its next value and the rest, or its end with the body's value */
  public sealed interface Gen<W, A> permits Next, End {}
  /** the next value, and the rest — a program of the implementation it was started in */
  public record Next<W, A>(W value, Program<Gen<W, A>> rest) implements Gen<W, A> {}
  public record End<W, A>(A value) implements Gen<W, A> {}
}
