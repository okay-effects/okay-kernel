package okay.jkernel.lexical;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import okay.jkernel.Monads;
import okay.kernel.Monad;
import okay.kernel.std.Final;
import okay.kernel.std.Logged;
import okay.kernel.std.Result;
import org.junit.jupiter.api.Test;

/** THE KERNEL BY LEVELS: capabilities checked statically, each reaching its own handler; prompts by identity —
 * okay-kernel's suite of its {@code okay.lexical} from Java, on this implementation */
abstract class LexicalTest {
  final Monad m;

  LexicalTest(Monad m) { this.m = m; }

  /** two logs of Strings nested: the outer's capability reaches the outer, past the inner */
  static Prog<Top, Logged<String, Logged<String, Integer>>> twoLogs() {
    return Prog.writer(new WriterBody<Top, String, Logged<String, Integer>>() {
      public <M extends Top> Prog<M, Logged<String, Integer>> in(Writer<M, String> outer) {
        return Prog.writer(new WriterBody<M, String, Integer>() {
          public <N extends M> Prog<N, Integer> in(Writer<N, String> inner) {
            return outer.<N>at().tell("to the outer").andThen(inner.tell("to the inner")).andThen(Prog.pure(7));
          }
        });
      }
    });
  }

  /** a state and a failure: the state counted, the failure raised past it */
  static Prog<Top, Result<String, Final<Integer, Integer>>> counted(int limit) {
    return Prog.attempt(new ThrowsBody<Top, String, Final<Integer, Integer>>() {
      public <M extends Top> Prog<M, Final<Integer, Integer>> in(Throws<M, String> t) {
        return Prog.state(0, new StateBody<M, Integer, Integer>() {
          public <N extends M> Prog<N, Integer> in(State<N, Integer> s) {
            return s.get().flatMap(x -> s.put(x + limit)).andThen(s.get())
                .flatMap(y -> y > 10 ? t.<N>at().<Integer>raise("over " + y) : Prog.pure(y));
          }
        });
      }
    });
  }

  @Test void twoLogsEachCapabilityItsOwnHandler() {
    Logged<String, Logged<String, Integer>> r = Prog.run(twoLogs(), m);
    assertEquals(List.of("to the outer"), r.log());
    assertEquals(List.of("to the inner"), r.value().log());
    assertEquals(7, r.value().value());
  }

  @Test void aStateAndAFailure() {
    assertEquals(Result.ok(new Final<>(5, 5)), Prog.run(counted(5), m));
    assertEquals(Result.failed("over 20"), Prog.run(counted(20), m));
  }

  /** 1 + reset(2 * shift(k -> k(k(10)))): 41 */
  @Test void shiftTwice41() {
    Prog<Top, Integer> p = Prog.reset(new ResetBody<Top, Integer>() {
      public <M extends Top> Prog<M, Integer> in(Prompt<Top, M, Integer> pr) {
        return pr.<Integer>shift(k -> k.apply(10).flatMap(x -> k.apply(x)).<M>at()).map(x -> x * 2);
      }
    }).map(x -> x + 1);
    assertEquals(41, Prog.run(p, m));
  }

  /** a shift to the outer reset, past the inner one: 1001 */
  @Test void shiftPastTheInnerReset() {
    Prog<Top, Integer> p = Prog.reset(new ResetBody<Top, Integer>() {
      public <M extends Top> Prog<M, Integer> in(Prompt<Top, M, Integer> outer) {
        return Prog.reset(new ResetBody<M, Integer>() {
          public <N extends M> Prog<N, Integer> in(Prompt<M, N, Integer> inner) {
            return outer.<Integer>shift(k -> k.apply(1).map(x -> x + 1000).<M>at()).<N>at().map(x -> x * 10);
          }
        }).map(x -> x / 10);
      }
    });
    assertEquals(1001, Prog.run(p, m));
  }

  /** multi-shot: k resumed twice, the answers joined */
  @Test void multiShot() {
    Prog<Top, List<Integer>> p = Prog.reset(new ResetBody<Top, List<Integer>>() {
      public <M extends Top> Prog<M, List<Integer>> in(Prompt<Top, M, List<Integer>> pr) {
        return pr.<Integer>shift(k -> k.apply(1).flatMap(a -> k.apply(2).map(b -> concat(a, b))).<M>at())
            .map(x -> List.of(x, x * 10));
      }
    });
    assertEquals(List.of(1, 10, 2, 20), Prog.run(p, m));
  }

  /** shift0's clause outside its reset: a shift0 in it to the outer, both dropped — -1 */
  @Test void shift0sClauseOutside() {
    Prog<Top, Integer> p = Prog.reset(new ResetBody<Top, Integer>() {
      public <M extends Top> Prog<M, Integer> in(Prompt<Top, M, Integer> outer) {
        return Prog.reset(new ResetBody<M, Integer>() {
          public <N extends M> Prog<N, Integer> in(Prompt<M, N, Integer> pr) {
            return pr.<Integer>shift0(k1 -> outer.<Integer>shift0(k2 -> Prog.<Top, Integer>pure(-1)).<M>at()).map(x -> x + 10);
          }
        }).map(x -> x * 2);
      }
    });
    assertEquals(-1, Prog.run(p, m));
  }

  static List<Integer> concat(List<Integer> a, List<Integer> b) {
    return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
  }
}

class LexicalStepsTest extends LexicalTest {
  LexicalStepsTest() { super(Monads.steps()); }
}

class LexicalRefTest extends LexicalTest {
  LexicalRefTest() { super(okay.jkernel.RefAccess.ref()); }
}
