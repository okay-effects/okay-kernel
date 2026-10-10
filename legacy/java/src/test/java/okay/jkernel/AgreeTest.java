package okay.jkernel;

import okay.kernel.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import okay.kernel.std.*;
import okay.jkernel.std.*;
import org.junit.jupiter.api.Test;

/**
 * EVERY MONAD AGREES: programs made at random — handlers of logs, states, failures and choices nested in any order,
 * the same effect again nearer, their operations, binds — each run on every monad, the values compared, or the
 * failures (an operation with no handler). The kernel's claim: the monad changes nothing a program sees
 */
class AgreeTest {
  sealed interface T {}
  record Pure(int n) implements T {}
  record Tel(int e, int n) implements T {}
  record Gt(int e) implements T {}
  record Pt(int e, int n) implements T {}
  record Amg(List<Integer> ns) implements T {}
  record Rse(int e, int n) implements T {}
  record Seq(T a, T b) implements T {}
  record Add(T a, T b) implements T {}
  record Wri(int e, T body) implements T {}
  record Sta(int e, int s0, T body) implements T {}
  record Thr(int e, T body) implements T {}
  record Cho(T body) implements T {}

  // two effects of each kind, told apart by their types: an Integer's and a String's
  static String type(int e) { return e == 0 ? "java.lang.Integer" : "java.lang.String"; }
  static Effect writerE(int e) { return Effects.writer(type(e)); }
  static Effect stateE(int e) { return Effects.state(type(e)); }
  static Effect throwsE(int e) { return Effects.throwsOf(type(e)); }

  static T gen(Random r, int depth) {
    if (depth <= 0 || r.nextInt(4) == 0) return switch (r.nextInt(6)) {
      case 0 -> new Pure(r.nextInt(10));
      case 1 -> new Tel(r.nextInt(2), r.nextInt(10));
      case 2 -> new Gt(r.nextInt(2));
      case 3 -> new Pt(r.nextInt(2), r.nextInt(10));
      case 4 -> {
        List<Integer> ns = new ArrayList<>();
        for (int i = 1 + r.nextInt(2); i > 0; i--) ns.add(r.nextInt(10));
        yield new Amg(ns);
      }
      default -> new Rse(r.nextInt(2), r.nextInt(10));
    };
    return switch (r.nextInt(6)) {
      case 0 -> new Seq(gen(r, depth - 1), gen(r, depth - 1));
      case 1 -> new Add(gen(r, depth - 1), gen(r, depth - 1));
      case 2 -> new Wri(r.nextInt(2), gen(r, depth - 1));
      case 3 -> new Sta(r.nextInt(2), r.nextInt(10), gen(r, depth - 1));
      case 4 -> new Thr(r.nextInt(2), gen(r, depth - 1));
      default -> new Cho(gen(r, depth - 1));
    };
  }

  /** the program in the monad: every value an Integer, a handler's answer folded into one */
  static Prog<Integer> build(T p, Monad m) {
    return switch (p) {
      case Pure x -> m.pure(x.n());
      case Tel x -> m.map(m.perform(new Tell<>(x.n(), writerE(x.e()))), u -> x.n());
      case Gt x -> m.perform(new Get<Integer>(stateE(x.e())));
      case Pt x -> m.map(m.perform(new Put<>(x.n(), stateE(x.e()))), u -> x.n());
      case Amg x -> m.perform(new Among<>(x.ns()));
      case Rse x -> m.perform(new Raise<Integer, Integer>(x.n(), throwsE(x.e())));
      case Seq x -> m.flatMap(build(x.a(), m), u -> build(x.b(), m));
      case Add x -> m.flatMap(build(x.a(), m), a -> m.map(build(x.b(), m), b -> a + b));
      case Wri x -> m.map(m.handle(Handlers.<Integer, Integer>writer(writerE(x.e())), mm -> build(x.body(), mm)),
          l -> l.value() * 100 + l.log().stream().mapToInt(i -> i).sum());
      case Sta x -> m.map(m.handle(Handlers.<Integer, Integer>state(stateE(x.e()), x.s0()), mm -> build(x.body(), mm)),
          f -> f.value() * 10 + f.state());
      case Thr x -> m.map(m.handle(Handlers.<Integer, Integer>throwsOf(throwsE(x.e())), mm -> build(x.body(), mm)),
          res -> switch (res) {
            case Result.Ok<Integer, Integer> ok -> ok.value();
            case Result.Failed<Integer, Integer> f -> -1 - f.error();
          });
      case Cho x -> m.map(m.handle(Handlers.<Integer>choose(), mm -> build(x.body(), mm)),
          l -> l.stream().mapToInt(i -> i).sum());
    };
  }

  static String outcome(T p, Monad m) {
    try {
      return "value " + Program.run(mm -> build(p, mm), m);
    } catch (IllegalStateException e) {
      return "no handler";
    }
  }

  @Test void aThousandProgramsAtRandomEveryMonadAgrees() {
    Random r = new Random(20261010);
    List<String> differ = new ArrayList<>();
    int values = 0;
    long digest = 0;
    for (int i = 1; i <= 1000; i++) {
      // a handler of each kind around, so most programs come to a value
      T p = new Cho(new Thr(0, new Thr(1, new Sta(0, 0, new Sta(1, 0, new Wri(0, new Wri(1, gen(r, 6))))))));
      String steps = outcome(p, Monads.steps()), ref = outcome(p, Ref.INSTANCE);
      if (steps.startsWith("value")) values++;
      digest = digest * 31 + (steps.startsWith("value") ? Integer.parseInt(steps.substring(6)) : -1_000_000);
      if (!Objects.equals(steps, ref)) differ.add("#" + i + " " + p + ": Steps " + steps + ", Ref " + ref);
    }
    assertTrue(differ.isEmpty(), String.join("\n", differ.subList(0, Math.min(3, differ.size()))));
    assertTrue(values > 900, values + " of 1000 came to a value");
    // okay-kernel's TestAgree: the same programs (java.util.Random, the same seed), the same outcomes — two
    // implementations of the kernel, one behaviour
    assertEquals(-8623788747993822161L, digest);
  }
}
