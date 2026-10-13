package okay.kernel.steps;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okay.kernel.Control;
import okay.kernel.Key;
import okay.kernel.Answers;
import okay.kernel.Computation;
import okay.kernel.Effects;
import okay.kernel.Fn;
import okay.kernel.Op;
import okay.kernel.Prog;
import okay.kernel.Program;
import okay.kernel.Step;
import okay.kernel.Type;
import okay.kernel.std.Choose;
import okay.kernel.std.Emit;
import okay.kernel.std.Handlers;
import okay.kernel.std.Reader;
import okay.kernel.std.Results;
import okay.kernel.std.State;
import okay.kernel.std.Throws;
import okay.kernel.std.Writer;
import org.junit.jupiter.api.Test;

/** the reference machine in Java, the same checks as {@code steps}' TestSteps: the standard handlers written once,
 * control, failures, finalizers, cancellation, the protocol — export, import, multi-shot k. No cast: an operation
 * handed out is answered by its cases */
class StepsJavaTest {
  static final StepsJava M = StepsJava.INSTANCE;
  static final Type INT = Type.of(Integer.class);
  static final Key<State<Integer>> ST = State.key(INT);
  static final Key<Writer<String>> LOG = Writer.key(Type.of(String.class));
  static final Key<Throws<String>> ERR = Throws.key(Type.of(String.class));
  static final Key<Reader<Integer>> RD = Reader.key(INT);

  static <A> A run(Program<A> p) { return M.run(p); }

  static <A> A done(Step<A> s) {
    if (s instanceof Step.Done<A> d) return d.value();
    return fail("expected a value: " + s);
  }

  static <A> Step.Yield<?, A> yielded(Step<A> s) {
    if (s instanceof Step.Yield<?, A> y) return y;
    return fail("expected a yield: " + s);
  }

  /** an ask handed out, answered {@code n} through its cases: its value typed by the operation */
  static <X, A> Step<A> answer(Step.Yield<X, A> y, int n) {
    return y.k().resume(Reader.narrow(RD, y.op()).fold(v -> v.apply(n)));
  }

  static Program<Integer> count(int n) {
    return e -> n == 0
        ? Handlers.<Integer>get(ST).in(e)
        : e.flatMap(Handlers.<Integer>modify(ST, s -> s + 1).in(e), u -> count(n - 1).in(e));
  }

  @Test void stateAsACellInConstantStack() {
    assertEquals(100000, run(Handlers.state(ST, 0, count(100000))).state());
  }

  @Test void writerAndStateReaderInPlace() {
    Program<Integer> body = e ->
        e.flatMap(Handlers.<Integer>ask(RD).in(e), n ->
            e.flatMap(Handlers.tell(LOG, "n=" + n).in(e), u ->
                e.flatMap(Handlers.put(ST, n).in(e), u2 -> Handlers.<Integer>get(ST).in(e))));
    Results.Logged<String, Results.Final<Integer, Integer>> r =
        run(Handlers.reader(RD, 7, Handlers.<String, Results.Final<Integer, Integer>>writer(LOG, Handlers.state(ST, 0, body))));
    assertEquals(List.of("n=7"), r.log());
    assertEquals(7, r.value().state());
  }

  @Test void raiseEndsTheBodyFinalizerRunHandleWithAnswers() {
    AtomicBoolean finalized = new AtomicBoolean();
    Program<Integer> body = e -> e.ensuring(Handlers.<String, Integer>raise(ERR, "boom"), e2 -> {
      finalized.set(true);
      return e2.pure(null);
    });
    Results.Result<String, Integer> r = run(Handlers.throwsOf(ERR, body));
    assertTrue(r instanceof Results.Err<String, Integer> && finalized.get());
    assertEquals(4, run(Handlers.<String, Integer>handleWith(ERR, body, m -> e -> e.pure(m.length()))));
  }

  @Test void chooseEveryPathTheFirst() {
    Program<Integer> pair = e ->
        e.flatMap(Handlers.among(List.of(1, 2)).in(e), a ->
            e.map(Handlers.among(List.of(10, 20)).in(e), b -> a * 100 + b));
    assertEquals(List.of(110, 120, 210, 220), run(Handlers.all(pair)));
    assertEquals(Optional.of(110), run(Handlers.first(pair)));
  }

  @Test void shift0AndDollarTheLaws() {
    Fn<Integer, Prog<Integer>> k = x -> M.pure(x * 2);
    assertEquals(42, run((Program<Integer>) e -> Control.of(e).<Integer, Integer, Integer>dollar(e2 -> e2.pure(21), k)));
    Program<Integer> shifted = e -> Control.of(e).<Integer, Integer, Integer>shift0(kk -> e.flatMap(kk.apply(1), kk));
    assertEquals(21, run((Program<Integer>) e -> Control.of(e).<Integer, Integer, Integer>dollar(shifted, x -> e.pure(x + 10))));
  }

  @Test void aGeneratorPulledOneByOne() {
    Key<Emit<Integer>> em = Emit.key(INT);
    Program<String> three = e ->
        e.flatMap(Handlers.send(em, 1).in(e), u -> e.flatMap(Handlers.send(em, 2).in(e), u2 -> e.pure("done")));
    Results.Gen<Integer, String> g = run(Handlers.generate(em, three));
    StringBuilder pulled = new StringBuilder();
    while (g instanceof Results.Next<Integer, String> n) {
      pulled.append(n.value()).append(' ');
      g = run(n.rest());
    }
    assertEquals("1 2 ", pulled.toString());
    assertEquals(new Results.End<Integer, String>("done"), g);
  }

  @Test void aFailureIsDataRecoverCatchesIt() {
    Computation<Integer> c = M.computation(e -> e.map(e.pure(1), x -> {
      throw new IllegalStateException("x");
    }), Set.of());
    assertInstanceOf(Step.Failed.class, c.start(Answers.NONE));
    assertEquals(2, run((Program<Integer>) e -> e.<Integer>recover(e2 -> e2.failed(new RuntimeException("no")), t -> e.pure(t.getMessage().length()))));
  }

  /** an operation nothing here handles: handed out */
  static final Program<Integer> ASK = Handlers.ask(RD);

  @Test void exportMultiShotK() {
    Computation<Integer> c = M.computation(e -> e.map(ASK.in(e), n -> n + 1), Set.of(RD.type()));
    Step.Yield<?, Integer> y = yielded(c.start(Answers.NONE));
    assertEquals(42, done(answer(y, 41)));
    assertEquals(2, done(answer(y, 1)));
    assertEquals(Set.of(RD.type()), c.effects());
    assertThrows(UnsupportedOperationException.class, () -> c.effects().clear());
  }

  @Test void exportAnsweredInPlaceByTheHost() {
    Answers host = new Answers() {
      public boolean answers(Type t) { return t == RD.type(); }
      public <X> X value(Op<X> op) { return Reader.narrow(RD, op).fold(v -> v.apply(41)); }
    };
    Computation<Integer> c = M.computation(e -> e.map(ASK.in(e), n -> n + 1), Set.of(RD.type()));
    assertEquals(42, done(c.start(host)));
  }

  @Test void kFailCaughtWhereTheOperationWas() {
    Computation<Integer> c = M.computation(e -> e.recover(ASK, t -> e.pure(-1)), Set.of(RD.type()));
    assertEquals(-1, done(yielded(c.start(Answers.NONE)).k().fail(new RuntimeException("x"))));
  }

  @Test void kCancelFinalizersRunUncancelableWaits() {
    AtomicInteger finalized = new AtomicInteger();
    Program<Void> fin = e -> {
      finalized.incrementAndGet();
      return e.pure(null);
    };
    Computation<Integer> c = M.computation(e -> e.recover(e2 -> e2.ensuring(ASK, fin), t -> e.pure(0)), Set.of(RD.type()));
    Step<Integer> cancelled = yielded(c.start(Answers.NONE)).k().cancel();
    assertInstanceOf(Step.Cancelled.class, cancelled, "cancelled, recover not taken");
    assertEquals(1, finalized.get(), "the finalizer once");
    Computation<Integer> u = M.computation(e -> e.uncancelable(ASK), Set.of(RD.type()));
    Step.Yield<?, Integer> waiting = yielded(yielded(u.start(Answers.NONE)).k().cancel());
    assertInstanceOf(Step.Cancelled.class, answer(waiting, 5), "the region waits; its end cancels");
  }

  @Test void importAnotherProgramUnderThisOnesHandlers() {
    Computation<Integer> inner = M.computation(e ->
        e.flatMap(ASK.in(e), n -> e.map(Handlers.among(List.of(n, n + 1)).in(e), x -> x * 10)), Set.of(RD.type(), Choose.TYPE));
    Program<List<Integer>> outer = Handlers.reader(RD, 4, Handlers.all(e -> e.foreign(inner)));
    assertEquals(List.of(40, 50), run(outer));
  }

  /** the same, the inner one answered in place by a cell here: its yields none, its state this machine's */
  @Test void importAnsweredInPlaceHere() {
    Computation<Integer> inner = M.computation(e ->
        e.flatMap(Handlers.<Integer>modify(ST, s -> s + 1).in(e), u -> Handlers.<Integer>get(ST).in(e)), Set.of(ST.type()));
    Results.Final<Integer, Integer> r = run(Handlers.state(ST, 41, (Effects e) -> e.foreign(inner)));
    assertEquals(new Results.Final<>(42, 42), r);
  }
}
