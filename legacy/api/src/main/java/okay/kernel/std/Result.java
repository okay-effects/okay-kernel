package okay.kernel.std;

/** the value, or the failure raised */
public sealed interface Result<E, A> {
  record Ok<E, A>(A value) implements Result<E, A> {}
  record Failed<E, A>(E error) implements Result<E, A> {}

  static <E, A> Result<E, A> ok(A a) { return new Ok<>(a); }
  static <E, A> Result<E, A> failed(E e) { return new Failed<>(e); }
}
