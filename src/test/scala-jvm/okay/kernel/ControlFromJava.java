package okay.kernel;

import java.util.List;
import okay.lexical.Prog;
import okay.lexical.Prompt;
import okay.lexical.ResetBody;
import okay.lexical.Top;

/** SHIFT AND RESET FROM JAVA: prompts by identity, levels fresh — a prompt does not leave its reset */
public final class ControlFromJava {
  private ControlFromJava() {}

  /** 1 + reset(2 * shift(k -> k(k(10)))): 41 */
  public static Prog<Top, Integer> twice() {
    return Prog.reset(new ResetBody<Top, Integer>() {
      public <M extends Top> Prog<M, Integer> in(Prompt<Top, M, Integer> p) {
        return p.<Integer>shift(k -> k.apply(10).flatMap(x -> k.apply(x)).<M>at()).map(x -> x * 2);
      }
    }).map(x -> x + 1);
  }

  /** a shift to the outer reset, past the inner one: 1001 */
  public static Prog<Top, Integer> past() {
    return Prog.reset(new ResetBody<Top, Integer>() {
      public <M extends Top> Prog<M, Integer> in(Prompt<Top, M, Integer> outer) {
        return Prog.reset(new ResetBody<M, Integer>() {
          public <N extends M> Prog<N, Integer> in(Prompt<M, N, Integer> inner) {
            return outer.<Integer>shift(k -> k.apply(1).map(x -> x + 1000).<M>at()).<N>at().map(x -> x * 10);
          }
        }).map(x -> x / 10);
      }
    });
  }

  /** multi-shot: k resumed twice, the answers joined */
  public static Prog<Top, List<Integer>> both() {
    return Prog.reset(new ResetBody<Top, List<Integer>>() {
      public <M extends Top> Prog<M, List<Integer>> in(Prompt<Top, M, List<Integer>> p) {
        return p.<Integer>shift(k -> k.apply(1).flatMap(a -> k.apply(2).map(b -> concat(a, b))).<M>at())
            .map(x -> List.of(x, x * 10));
      }
    });
  }

  /** shift0: the clause outside the reset, a shift0 in it reaching the next out — reset(reset(shift0 k1. shift0 k2. ...)) */
  public static Prog<Top, Integer> outside() {
    return Prog.reset(new ResetBody<Top, Integer>() {
      public <M extends Top> Prog<M, Integer> in(Prompt<Top, M, Integer> outer) {
        return Prog.reset(new ResetBody<M, Integer>() {
          public <N extends M> Prog<N, Integer> in(Prompt<M, N, Integer> p) {
            // dropping both: the inner shift0's clause at the outer reset, its k never resumed
            return p.<Integer>shift0(k1 -> outer.<Integer>shift0(k2 -> Prog.<Top, Integer>pure(-1)).<M>at()).map(x -> x + 10);
          }
        }).map(x -> x * 2);
      }
    });
  }

  private static List<Integer> concat(List<Integer> a, List<Integer> b) {
    return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
  }
}
