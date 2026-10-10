package okay.jkernel;

import okay.kernel.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okay.kernel.std.*;
import okay.jkernel.std.*;
import org.junit.jupiter.api.Test;

/** THE KERNEL'S BEHAVIOUR, one suite, every monad: the same programs, the same values — okay-kernel's suite, in Java */
abstract class KernelTest {
  final Monad m;
  final int big;

  KernelTest(Monad m, int big) {
    this.m = m;
    this.big = big;
  }

  static final Effect STR = Effects.writer(String.class);
  static final Effect INT = Effects.writer(Integer.class);
  static final Effect ERR = Effects.throwsOf(String.class);
  static final Effect CELL = Effects.state(Integer.class);

  static <W> Program<Void> tell(W w, Effect e) { return mm -> mm.perform(new Tell<>(w, e)); }
  static <A> Program<A> raise(String err) { return mm -> mm.perform(new Raise<String, A>(err, ERR)); }
  static <A, B> Program<B> then(Program<A> p, Fn<A, Program<B>> f) { return mm -> mm.flatMap(p.in(mm), a -> f.apply(a).in(mm)); }
  static <A, B> Program<B> map(Program<A> p, Fn<A, B> f) { return mm -> mm.map(p.in(mm), f); }
  static <A> Program<A> pure(A a) { return mm -> mm.pure(a); }
  static <A, R> Program<R> handle(Handler<A, R> h, Program<A> body) { return mm -> mm.handle(h, body); }

  <A> A run(Program<A> p) { return Program.run(p, m); }

  @Test void aRaiseDropsTheRestTheLogKeptOutside() {
    Program<Integer> q = then(tell("before", STR), u -> then(KernelTest.<Void>raise("no"), v -> map(tell("after", STR), w -> 1)));
    Logged<String, Result<String, Integer>> l = run(handle(Handlers.writer(STR), handle(Handlers.throwsOf(ERR), q)));
    assertEquals(List.of("before"), l.log());
    assertEquals(Result.failed("no"), l.value());
    // the other way round: the raise's handler outside, the log dropped with the rest
    assertEquals(Result.failed("no"), run(handle(Handlers.<String, Logged<String, Integer>>throwsOf(ERR), handle(Handlers.writer(STR), q))));
  }

  Program<Void> loop(int i) {
    return i == 0 ? pure(null) : then(tell(i, INT), u -> mm -> mm.delay(() -> loop(i - 1).in(mm)));
  }

  @Test void bindsInConstantStackTheNearestHandler() {
    Logged<Integer, Void> l = run(handle(Handlers.writer(INT), loop(big)));
    assertEquals(big, l.log().size());
    assertEquals(big, l.log().get(0));
  }

  @Test void aHandlerOfTheSameEffectNearerTakesIt() {
    Program<Void> q = then(handle(Handlers.<String, Void>writer(STR), tell("inner", STR)), l -> tell("outer saw " + l.log(), STR));
    assertEquals(List.of("outer saw [inner]"), run(handle(Handlers.writer(STR), q)).log());
  }

  record Flip() implements Op<Boolean> {
    static final Effect E = Effect.apply("Flip");
    public Effect effect() { return E; }
  }

  @SuppressWarnings("unchecked")
  @Test void multiShotAClauseResumingTwice() {
    Handler<String, List<String>> all = new Handler<>() {
      public Effect effect() { return Flip.E; }
      public Prog<List<String>> ret(String a, Monad mm) { return mm.pure(List.of(a)); }
      public <X> Prog<List<String>> apply(Op<X> op, Fn<X, Prog<List<String>>> k, Monad mm) {
        Fn<Boolean, Prog<List<String>>> kb = b -> k.apply((X) b);
        return mm.flatMap(kb.apply(true), a -> mm.map(kb.apply(false), b -> concat(a, b)));
      }
    };
    Program<String> coins = mm -> mm.flatMap(mm.perform(new Flip()), x -> mm.map(mm.perform(new Flip()), y -> x + "" + y));
    assertEquals(List.of("truetrue", "truefalse", "falsetrue", "falsefalse"), run(handle(all, coins)));
  }

  static <A> List<A> concat(List<A> a, List<A> b) {
    var l = new java.util.ArrayList<>(a);
    l.addAll(b);
    return l;
  }

  @Test void anOperationWithNoHandlerAnError() {
    assertThrows(IllegalStateException.class, () -> run(tell("x", STR)));
  }

  @Test void writerOfStringAndOfIntegerTwoEffects() {
    Program<Void> q = then(tell("a", STR), u -> then(tell(1, INT), v -> then(tell("b", STR), w -> tell(2, INT))));
    Logged<String, Logged<Integer, Void>> l = run(handle(Handlers.writer(STR), handle(Handlers.writer(INT), q)));
    assertEquals(List.of("a", "b"), l.log());
    assertEquals(List.of(1, 2), l.value().log());
  }

  @Test void aStateOfACellAChoiceEveryPathTheFirst() {
    Program<Integer> get = mm -> mm.perform(new Get<Integer>(CELL));
    Program<Integer> counted = then(get, s -> then(mm -> mm.perform(new Put<>(s + 10, CELL)), u -> get));
    assertEquals(new Final<>(15, 15), run(handle(Handlers.state(CELL, 5), counted)));
    Program<String> pairs = then(mm -> mm.perform(new Among<>(List.of(1, 2))), x -> map(mm -> mm.perform(new Among<>(List.of("a", "b"))), y -> x + y));
    assertEquals(List.of("1a", "1b", "2a", "2b"), run(handle(Handlers.choose(), pairs)));
    Program<Integer> odd = then(mm -> mm.perform(new Among<>(List.of(2, 4, 5, 7))), x -> x % 2 == 1 ? pure(x) : mm -> mm.perform(new Among<Integer>(List.of())));
    assertEquals(Optional.of(5), run(handle(Handlers.first(), odd)));
  }

  // DELIMITED CONTROL, BY PROMPTS: FRESH EFFECTS

  @Test void shiftOnePlusResetTwoTimesShiftKK10Is41() {
    Program<Integer> p = map(Control.<Integer>reset(pr -> map(pr.<Integer>shift(k -> mm -> mm.flatMap(k.apply(10), k)), x -> x * 2)), x -> x + 1);
    assertEquals(41, run(p));
  }

  @Test void shiftToAResetFurtherOutCrossingANearerOne() {
    Program<Integer> p = Control.<Integer>reset(outer -> map(Control.<Integer>reset(inner ->
        map(outer.<Integer>shift(k -> mm -> mm.map(k.apply(1), x -> x + 1000)), x -> x * 10)), x -> x / 10));
    assertEquals(1001, run(p));
  }

  @Test void shiftsClauseUnderTheResetAgainShift0sOutside() {
    // reset(reset(shift k1. shift k2. k2(k2(k1 1))) + 10) * 2): k2 the empty rest under the reset — 22
    Program<Integer> p = Control.<Integer>reset(o -> map(Control.<Integer>reset(pr -> map(pr.<Integer>shift(k1 ->
        pr.<Integer>shift(k2 -> mm -> mm.flatMap(mm.flatMap(k1.apply(1), k2), k2))), x -> x + 10)), x -> x * 2));
    assertEquals(22, run(p));
    // shift0's clause outside its delimiter: a shift0 of the same prompt there has none; the outer's, its rest * 2
    Program<Integer> q = Control.<Integer>reset(o -> map(Control.<Integer>reset(pr -> map(pr.<Integer>shift0(k1 ->
        o.<Integer>shift0(k2 -> mm -> mm.flatMap(mm.flatMap(k1.apply(1), k2), k2))), x -> x + 10)), x -> x * 2));
    assertEquals(44, run(q));
    Program<Integer> none = Control.<Integer>reset(pr -> pr.<Integer>shift0(k1 -> pr.<Integer>shift0(k2 -> mm -> k1.apply(1))));
    assertThrows(IllegalStateException.class, () -> run(none));
  }

  // ASYNC

  Program<Integer> awaits(int i, int acc) {
    return i == 0 ? pure(acc) : then(mm -> mm.perform(new Await<Integer>(cb -> cb.ok(1))), x -> mm -> mm.delay(() -> awaits(i - 1, acc + x).in(mm)));
  }

  @Test void asyncAtTheTopCallbacksAnsweredAtOnceInConstantStack() {
    AtomicReference<Integer> r = new AtomicReference<>();
    Async.start(awaits(big, 0), m, r::set, e -> fail(e));
    assertEquals(big, r.get());
    assertEquals(big, run(handle(Handlers.async(), awaits(big, 0))));
  }

  @Test void asyncAtTheTopAFailureAtACallbackTheRuns() {
    AtomicReference<Throwable> e = new AtomicReference<>();
    Async.start(mm -> mm.perform(new Await<Integer>(cb -> cb.fail(new IllegalStateException("cb")))), m, x -> {}, e::set);
    assertEquals("cb", e.get().getMessage());
  }

  @Test void asyncAtTheTopACallbackCalledLaterFromAnotherThread() throws Exception {
    CompletableFuture<Logged<String, Integer>> done = new CompletableFuture<>();
    Program<Integer> later = then(tell("start", STR), u -> then(mm -> mm.perform(new Await<Integer>(cb ->
        CompletableFuture.runAsync(() -> cb.ok(41)))), x -> map(tell("got " + x, STR), v -> x + 1)));
    Async.start(handle(Handlers.writer(STR), later), m, done::complete, done::completeExceptionally);
    Logged<String, Integer> l = done.get(5, TimeUnit.SECONDS);
    assertEquals(List.of("start", "got 41"), l.log());
    assertEquals(42, l.value());
  }
}

class KernelStepsTest extends KernelTest {
  KernelStepsTest() { super(Monads.steps(), 100_000); }
}

/** the reference, recursive: programs as deep as the stack lets it */
class KernelRefTest extends KernelTest {
  KernelRefTest() { super(Ref.INSTANCE, 300); }
}
