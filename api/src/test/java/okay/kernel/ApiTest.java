package okay.kernel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import okay.kernel.std.Choose;
import okay.kernel.std.State;
import okay.kernel.std.Throws;
import org.junit.jupiter.api.Test;

/** the API's own: identities exact and interned; the vocabulary's cases total and typed, with no cast */
class ApiTest {
  static final Type INT = Type.of(Integer.class);
  static final Type STR = Type.of(String.class);

  @Test void typesAreInternedAndExact() {
    assertSame(Type.of(java.util.List.class, INT), Type.of(java.util.List.class, INT));
    assertNotSame(Type.of(java.util.List.class, INT), Type.of(java.util.List.class, STR));
    assertSame(Type.of(int.class), INT, "a primitive is its box");
    assertSame(Type.of("com.acme.Sku"), Type.of("com.acme.Sku"), "a key any value, by equals");
    assertNotSame(Type.fresh(), Type.fresh());
    assertEquals("State[Integer]", State.of(INT).toString());
  }

  /** a cell's clause, as a handler written in Java writes it: k typed by the operation's value, no cast */
  static int cell = 1;

  static <X, R> R clause(State.Ops<Integer, X> op, Fn<X, R> k) {
    return op.fold(new State.Cases<Integer, X, R>() {
      public R get(Same<Integer, X> value) { return k.apply(value.apply(cell)); }
      public R put(Integer s, Same<Void, X> value) { cell = s; return k.apply(value.apply(null)); }
    });
  }

  @Test void casesTotalAndTyped() {
    Type st = State.of(INT);
    assertEquals("got 1", clause(new State.Get<Integer>(st), (Integer x) -> "got " + x));
    assertEquals("put", clause(new State.Put<>(st, 5), (Void u) -> "put"));
    assertEquals("got 5", clause(new State.Get<Integer>(st), (Integer x) -> "got " + x));
    assertSame(st, new State.Get<Integer>(st).effect());
  }

  @Test void aRaiseHasNoValue() {
    Throws.Ops<String, Integer> raise = new Throws.Raise<>(Throws.of(STR), "boom");
    assertEquals("caught boom", raise.fold(new Throws.Cases<String, Integer, String>() {
      public String raise(String error) { return "caught " + error; }
    }));
  }

  @Test void aChoiceItsValues() {
    Choose.Ops<Integer> among = new Choose.Among<>(List.of(1, 2, 3));
    assertEquals(6, (int) among.fold(new Choose.Cases<Integer, Integer>() {
      public Integer among(List<Integer> xs) { return xs.stream().mapToInt(Integer::intValue).sum(); }
    }));
    assertSame(Choose.TYPE, among.effect());
  }

  @Test void stepsAreData() {
    Step<Integer> done = new Step.Done<>(42);
    Step<Integer> failed = new Step.Failed<>(new IllegalStateException("x"));
    String seen = switch (done) {
      case Step.Done<Integer> d -> "done " + d.value();
      case Step.Yield<?, Integer> y -> "yield";
      case Step.Failed<Integer> f -> "failed";
      case Step.Cancelled<Integer> c -> "cancelled";
    };
    assertEquals("done 42", seen);
    assertInstanceOf(Step.Failed.class, failed);
  }
}
