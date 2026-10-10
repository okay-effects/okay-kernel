package okay.kernel;

import okay.lexical.*;
import okay.lexical.Prog;
import okay.kernel.std.Logged;
import okay.kernel.std.Final;
import okay.kernel.std.Result;

/** THE KERNEL BY LEVELS, FROM JAVA: capabilities checked statically, each reaching its own handler */
public final class LexicalFromJava {
  private LexicalFromJava() {}

  /** two logs of Strings nested: the outer's capability reaches the outer, past the inner */
  public static Prog<Top, Logged<String, Logged<String, Integer>>> twoLogs() {
    return Prog.writer(new WriterBody<Top, String, Logged<String, Integer>>() {
      public <M extends Top> Prog<M, Logged<String, Integer>> in(Writer<M, String> outer) {
        return Prog.writer(new WriterBody<M, String, Integer>() {
          public <N extends M> Prog<N, Integer> in(Writer<N, String> inner) {
            return outer.<N>at().tell("to the outer")
                .andThen(inner.tell("to the inner"))
                .andThen(Prog.pure(7));
          }
        });
      }
    });
  }

  /** a state and a failure: the state counted, the failure raised past it */
  public static Prog<Top, Result<String, Final<Integer, Integer>>> counted(int limit) {
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

  /** Scala 3's program under a Java log by type: the row's handler, from Java */
  public static Object scalaUnderJava(Monad m) {
    Program<Object> p = okay.demo.Programs.p(3);
    Program<Object> withAsync = mm -> mm.handle(okay.std.Handlers.async(), p);
    Program<scala.Tuple2<scala.collection.immutable.List<String>, Object>> withLog =
        mm -> mm.handle(okay.std.Handlers.<String, Object>writer(okay.kernel.std.Effects.writer(String.class)), withAsync);
    return Prog.run(Prog.<Top, scala.Tuple2<scala.collection.immutable.List<String>, Object>>of(withLog), m);
  }
}
