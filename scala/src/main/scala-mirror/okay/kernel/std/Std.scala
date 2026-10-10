package okay.kernel.std

import okay.kernel.{Fn, Op, Program, Same, Type}

// THE VOCABULARY'S MIRROR: as the Java API's okay.kernel.std, for Scala.js and Scala Native

object State {
  def of(s: Type): Type = Type.of(classOf[State.type], s)
  sealed trait Ops[S, X] extends Op[X] { def fold[R](c: Cases[S, X, R]): R }
  trait Cases[S, X, R] {
    def get(value: Same[S, X]): R
    def put(s: S, value: Same[Void, X]): R
  }
  final class Get[S](e: Type) extends Ops[S, S] {
    def effect(): Type = e
    def fold[R](c: Cases[S, S, R]): R = c.get(new Same.Refl[S]())
  }
  final class Put[S](e: Type, v: S) extends Ops[S, Void] {
    def effect(): Type = e
    def s(): S = v
    def fold[R](c: Cases[S, Void, R]): R = c.put(v, new Same.Refl[Void]())
  }
}

object Reader {
  def of(e: Type): Type = Type.of(classOf[Reader.type], e)
  sealed trait Ops[E, X] extends Op[X] { def fold[R](c: Cases[E, X, R]): R }
  trait Cases[E, X, R] { def ask(value: Same[E, X]): R }
  final class Ask[E](e: Type) extends Ops[E, E] {
    def effect(): Type = e
    def fold[R](c: Cases[E, E, R]): R = c.ask(new Same.Refl[E]())
  }
}

object Writer {
  def of(w: Type): Type = Type.of(classOf[Writer.type], w)
  sealed trait Ops[W, X] extends Op[X] { def fold[R](c: Cases[W, X, R]): R }
  trait Cases[W, X, R] { def tell(w: W, value: Same[Void, X]): R }
  final class Tell[W](e: Type, v: W) extends Ops[W, Void] {
    def effect(): Type = e
    def w(): W = v
    def fold[R](c: Cases[W, Void, R]): R = c.tell(v, new Same.Refl[Void]())
  }
}

object Throws {
  def of(e: Type): Type = Type.of(classOf[Throws.type], e)
  sealed trait Ops[E, X] extends Op[X] { def fold[R](c: Cases[E, X, R]): R }
  trait Cases[E, X, R] { def raise(error: E): R }
  final class Raise[E, X](e: Type, err: E) extends Ops[E, X] {
    def effect(): Type = e
    def error(): E = err
    def fold[R](c: Cases[E, X, R]): R = c.raise(err)
  }
}

object Choose {
  val TYPE: Type = Type.of(classOf[Choose.type])
  sealed trait Ops[X] extends Op[X] { def fold[R](c: Cases[X, R]): R }
  trait Cases[X, R] { def among(xs: java.util.List[X]): R }
  final class Among[X](e: Type, v: java.util.List[X]) extends Ops[X] {
    def this(xs: java.util.List[X]) = this(TYPE, xs)
    def effect(): Type = e
    def xs(): java.util.List[X] = v
    def fold[R](c: Cases[X, R]): R = c.among(v)
  }
}

object Emit {
  def of(w: Type): Type = Type.of(classOf[Emit.type], w)
  sealed trait Ops[W, X] extends Op[X] { def fold[R](c: Cases[W, X, R]): R }
  trait Cases[W, X, R] { def send(w: W, value: Same[Void, X]): R }
  final class Send[W](e: Type, v: W) extends Ops[W, Void] {
    def effect(): Type = e
    def w(): W = v
    def fold[R](c: Cases[W, Void, R]): R = c.send(v, new Same.Refl[Void]())
  }
}

object Async {
  val TYPE: Type = Type.of(classOf[Async.type])
  trait Callback[X] { def done(x: X): Unit; def failed(e: Throwable): Unit }
  trait Register[X] { def register(callback: Callback[X]): Unit }
  trait Fiber[X]
  sealed trait Ops[X] extends Op[X] { def fold[R](c: Cases[X, R]): R }
  trait Cases[X, R] {
    def await(register: Register[X]): R
    def fork[Y](body: Program[Y], value: Same[Fiber[Y], X]): R
    def join(fiber: Fiber[X]): R
    def cancel[Y](fiber: Fiber[Y], value: Same[Void, X]): R
  }
  final class Await[X](e: Type, r: Register[X]) extends Ops[X] {
    def this(r: Register[X]) = this(TYPE, r)
    def effect(): Type = e
    def register(): Register[X] = r
    def fold[R](c: Cases[X, R]): R = c.await(r)
  }
  final class Fork[Y](e: Type, b: Program[Y]) extends Ops[Fiber[Y]] {
    def this(b: Program[Y]) = this(TYPE, b)
    def effect(): Type = e
    def body(): Program[Y] = b
    def fold[R](c: Cases[Fiber[Y], R]): R = c.fork(b, new Same.Refl[Fiber[Y]]())
  }
  final class Join[X](e: Type, f: Fiber[X]) extends Ops[X] {
    def this(f: Fiber[X]) = this(TYPE, f)
    def effect(): Type = e
    def fiber(): Fiber[X] = f
    def fold[R](c: Cases[X, R]): R = c.join(f)
  }
  final class Cancel[Y](e: Type, f: Fiber[Y]) extends Ops[Void] {
    def this(f: Fiber[Y]) = this(TYPE, f)
    def effect(): Type = e
    def fiber(): Fiber[Y] = f
    def fold[R](c: Cases[Void, R]): R = c.cancel(f, new Same.Refl[Void]())
  }
}

object Control {
  def delimiter(): Type = Type.fresh()
  sealed trait Ops[X] extends Op[X] { def fold[R](c: Cases[X, R]): R }
  trait Cases[X, R] { def capture(body: Fn[Fn[X, Program[Any]], Program[Any]]): R }
  final class Capture[X](e: Type, b: Fn[Fn[X, Program[Any]], Program[Any]]) extends Ops[X] {
    def effect(): Type = e
    def body(): Fn[Fn[X, Program[Any]], Program[Any]] = b
    def fold[R](c: Cases[X, R]): R = c.capture(b)
  }
}
