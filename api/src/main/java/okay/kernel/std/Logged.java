package okay.kernel.std;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** a log and the value: {@code log()} first told first. Told in front of the rest's, a cons inside: no copy a tell */
public final class Logged<W, A> {
  private record Cons(Object head, Cons tail) {}

  private final Cons items;
  private final A value;

  private Logged(Cons items, A value) {
    this.items = items;
    this.value = value;
  }

  /** the value, no log */
  public static <W, A> Logged<W, A> of(A value) { return new Logged<>(null, value); }

  /** {@code w} told in front of the log */
  public Logged<W, A> told(W w) { return new Logged<>(new Cons(w, items), value); }

  public A value() { return value; }

  @SuppressWarnings("unchecked")
  public List<W> log() {
    List<W> l = new ArrayList<>();
    for (Cons c = items; c != null; c = c.tail) l.add((W) c.head);
    return Collections.unmodifiableList(l);
  }

  @Override public boolean equals(Object o) {
    return o instanceof Logged<?, ?> l && l.log().equals(log()) && Objects.equals(l.value, value);
  }
  @Override public int hashCode() { return Objects.hash(log(), value); }
  @Override public String toString() { return "Logged(" + log() + ", " + value + ")"; }
}
