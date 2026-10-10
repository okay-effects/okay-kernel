package okay.kernel.std

import okay.kernel.{Translating, Aborting, Answering, Effects, Fn, General, Op, Prog, Program, Returning, Same, Type}

// THE VOCABULARY'S MIRROR: as the Java API's okay.kernel.std, for Scala.js and Scala Native

object State {
  def of(s: Type): Type = Type.of(classOf[State.type], s)
  def narrow[S, X](op: Op[X]): Ops[S, X] =
    if (op.isInstanceOf[Ops[?, ?]]) op.asInstanceOf[Ops[S, X]]
    else throw new IllegalArgumentException("not an operation of State: " + op)
  trait Ops[S, X] extends Op[X] { def fold[R](c: Cases[S, X, R]): R }
  trait Cases[S, X, R] {
    def get(value: Same[S, X]): R
    def put(s: S, value: Same[Void, X]): R
  }
  final case class Get[S](private val e: Type) extends Ops[S, S] {
    def effect(): Type = e
    def fold[R](c: Cases[S, S, R]): R = c.get(new Same.Refl[S]())
  }
  final case class Put[S](private val e: Type, private val v: S) extends Ops[S, Void] {
    def effect(): Type = e
    def s(): S = v
    def fold[R](c: Cases[S, Void, R]): R = c.put(v, new Same.Refl[Void]())
  }
}

object Reader {
  def of(e: Type): Type = Type.of(classOf[Reader.type], e)
  def narrow[E, X](op: Op[X]): Ops[E, X] =
    if (op.isInstanceOf[Ops[?, ?]]) op.asInstanceOf[Ops[E, X]]
    else throw new IllegalArgumentException("not an operation of Reader: " + op)
  trait Ops[E, X] extends Op[X] { def fold[R](c: Cases[E, X, R]): R }
  trait Cases[E, X, R] { def ask(value: Same[E, X]): R }
  final case class Ask[E](private val e: Type) extends Ops[E, E] {
    def effect(): Type = e
    def fold[R](c: Cases[E, E, R]): R = c.ask(new Same.Refl[E]())
  }
}

object Writer {
  def of(w: Type): Type = Type.of(classOf[Writer.type], w)
  def narrow[W, X](op: Op[X]): Ops[W, X] =
    if (op.isInstanceOf[Ops[?, ?]]) op.asInstanceOf[Ops[W, X]]
    else throw new IllegalArgumentException("not an operation of Writer: " + op)
  trait Ops[W, X] extends Op[X] { def fold[R](c: Cases[W, X, R]): R }
  trait Cases[W, X, R] { def tell(w: W, value: Same[Void, X]): R }
  final case class Tell[W](private val e: Type, private val v: W) extends Ops[W, Void] {
    def effect(): Type = e
    def w(): W = v
    def fold[R](c: Cases[W, Void, R]): R = c.tell(v, new Same.Refl[Void]())
  }
}

object Throws {
  def of(e: Type): Type = Type.of(classOf[Throws.type], e)
  def narrow[E, X](op: Op[X]): Ops[E, X] =
    if (op.isInstanceOf[Ops[?, ?]]) op.asInstanceOf[Ops[E, X]]
    else throw new IllegalArgumentException("not an operation of Throws: " + op)
  trait Ops[E, X] extends Op[X] { def fold[R](c: Cases[E, X, R]): R }
  trait Cases[E, X, R] { def raise(error: E): R }
  final case class Raise[E, X](private val e: Type, private val err: E) extends Ops[E, X] {
    def effect(): Type = e
    def error(): E = err
    def fold[R](c: Cases[E, X, R]): R = c.raise(err)
  }
}

object Choose {
  val TYPE: Type = Type.of(classOf[Choose.type])
  def narrow[X](op: Op[X]): Ops[X] =
    if (op.isInstanceOf[Ops[?]]) op.asInstanceOf[Ops[X]]
    else throw new IllegalArgumentException("not an operation of Choose: " + op)
  trait Ops[X] extends Op[X] { def fold[R](c: Cases[X, R]): R }
  trait Cases[X, R] { def among(xs: java.util.List[X]): R }
  final case class Among[X](private val e: Type, private val v: java.util.List[X]) extends Ops[X] {
    def this(xs: java.util.List[X]) = this(TYPE, xs)
    def effect(): Type = e
    def xs(): java.util.List[X] = v
    def fold[R](c: Cases[X, R]): R = c.among(v)
  }
}

object Emit {
  def of(w: Type): Type = Type.of(classOf[Emit.type], w)
  def narrow[W, X](op: Op[X]): Ops[W, X] =
    if (op.isInstanceOf[Ops[?, ?]]) op.asInstanceOf[Ops[W, X]]
    else throw new IllegalArgumentException("not an operation of Emit: " + op)
  trait Ops[W, X] extends Op[X] { def fold[R](c: Cases[W, X, R]): R }
  trait Cases[W, X, R] { def send(w: W, value: Same[Void, X]): R }
  final case class Send[W](private val e: Type, private val v: W) extends Ops[W, Void] {
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
  def narrow[X](op: Op[X]): Ops[X] =
    if (op.isInstanceOf[Ops[?]]) op.asInstanceOf[Ops[X]]
    else throw new IllegalArgumentException("not an operation of Async: " + op)
  trait Ops[X] extends Op[X] { def fold[R](c: Cases[X, R]): R }
  trait Cases[X, R] {
    def await(register: Register[X]): R
    def fork[Y](body: Program[Y], value: Same[Fiber[Y], X]): R
    def join(fiber: Fiber[X]): R
    def cancel[Y](fiber: Fiber[Y], value: Same[Void, X]): R
  }
  final case class Await[X](private val e: Type, private val r: Register[X]) extends Ops[X] {
    def this(r: Register[X]) = this(TYPE, r)
    def effect(): Type = e
    def register(): Register[X] = r
    def fold[R](c: Cases[X, R]): R = c.await(r)
  }
  final case class Fork[Y](private val e: Type, private val b: Program[Y]) extends Ops[Fiber[Y]] {
    def this(b: Program[Y]) = this(TYPE, b)
    def effect(): Type = e
    def body(): Program[Y] = b
    def fold[R](c: Cases[Fiber[Y], R]): R = c.fork(b, new Same.Refl[Fiber[Y]]())
  }
  final case class Join[X](private val e: Type, private val f: Fiber[X]) extends Ops[X] {
    def this(f: Fiber[X]) = this(TYPE, f)
    def effect(): Type = e
    def fiber(): Fiber[X] = f
    def fold[R](c: Cases[X, R]): R = c.join(f)
  }
  final case class Cancel[Y](private val e: Type, private val f: Fiber[Y]) extends Ops[Void] {
    def this(f: Fiber[Y]) = this(TYPE, f)
    def effect(): Type = e
    def fiber(): Fiber[Y] = f
    def fold[R](c: Cases[Void, R]): R = c.cancel(f, new Same.Refl[Void]())
  }
}

object Control {
  def delimiter(): Type = Type.fresh()
  def narrow[X](op: Op[X]): Ops[X] =
    if (op.isInstanceOf[Ops[?]]) op.asInstanceOf[Ops[X]]
    else throw new IllegalArgumentException("not an operation of Control: " + op)
  trait Ops[X] extends Op[X] { def fold[R](c: Cases[X, R]): R }
  trait Cases[X, R] { def capture(body: Fn[Fn[X, Program[Any]], Program[Any]]): R }
  final case class Capture[X](private val e: Type, private val b: Fn[Fn[X, Program[Any]], Program[Any]]) extends Ops[X] {
    def effect(): Type = e
    def body(): Fn[Fn[X, Program[Any]], Program[Any]] = b
    def fold[R](c: Cases[X, R]): R = c.capture(b)
  }
}

/** THE STANDARD HANDLERS' ANSWERS, as data every language reads */
object Results {
  /** a state's final value with the body's */
  final case class Final[S, A](private val s: S, private val v: A) {
    def state(): S = s
    def value(): A = v
  }

  /** the values told, in order, with the body's */
  final case class Logged[W, A](private val l: java.util.List[W], private val v: A) {
    def log(): java.util.List[W] = l
    def value(): A = v
    /** `w` in front of the log */
    def told(w: W): Logged[W, A] = {
      val n = new java.util.ArrayList[W](l.size() + 1)
      n.add(w)
      n.addAll(l)
      new Logged[W, A](java.util.Collections.unmodifiableList(n), v)
    }
  }
  object Logged {
    def of[W, A](value: A): Logged[W, A] = new Logged[W, A](java.util.Collections.emptyList[W](), value)
  }

  /** a body's value, or the error it raised */
  sealed trait Result[E, A] { def fold[R](ok: Fn2[A, R], err: Fn2[E, R]): R }
  final case class Ok[E, A](private val v: A) extends Result[E, A] {
    def value(): A = v
    def fold[R](ok: Fn2[A, R], err: Fn2[E, R]): R = ok.apply(v)
  }
  final case class Err[E, A](private val e: E) extends Result[E, A] {
    def error(): E = e
    def fold[R](ok: Fn2[A, R], err: Fn2[E, R]): R = err.apply(e)
  }
  trait Fn2[A, R] { def apply(a: A): R }

  /** a generator's position: its next value and the rest, or its end with the body's value */
  sealed trait Gen[W, A]
  /** the next value, and the rest — a program of the implementation it was started in */
  final case class Next[W, A](private val v: W, private val r: Program[Gen[W, A]]) extends Gen[W, A] {
    def value(): W = v
    def rest(): Program[Gen[W, A]] = r
  }
  final case class End[W, A](private val v: A) extends Gen[W, A] {
    def value(): A = v
  }
}

/** THE STANDARD HANDLERS, WRITTEN ONCE over `Effects`: as the Java API's okay.kernel.std.Handlers */
object Handlers {
  import Results.{Err, Final, Gen, Logged, Ok, Result}

  /** a handler answering in place and returning a value: one shape-object for a lambda pair */
  private trait Cell[A, R] extends Answering[A, R] with Returning[A, R]

  // -- State

  /** STATE AS A CELL: answered in place, one cell a run of the handler; the final state with the value */
  def state[S, A](t: Type, s0: S, body: Program[A]): Program[Final[S, A]] = (e: Effects) => e.handle(new Cell[A, Final[S, A]] {
    private var s: S = s0
    def effect(): Type = t
    def answer(a: A): Final[S, A] = new Final[S, A](s, a)
    def value[X](op: Op[X]): X = State.narrow[S, X](op).fold(new State.Cases[S, X, X] {
      def get(v: Same[S, X]): X = v.apply(s)
      def put(n: S, v: Same[Void, X]): X = { s = n; v.apply(null) }
    })
  }, body)

  def get[S](t: Type): Program[S] = (e: Effects) => e.perform(new State.Get[S](t))

  def put[S](t: Type, s: S): Program[Void] = (e: Effects) => e.perform(new State.Put[S](t, s))

  def modify[S](t: Type, f: Fn[S, S]): Program[Void] =
    (e: Effects) => e.flatMap[S, Void](e.perform(new State.Get[S](t)), (s: S) => e.perform(new State.Put[S](t, f.apply(s))))

  // -- Reader

  /** READER: the value, answered in place */
  def reader[R, A](t: Type, value: R, body: Program[A]): Program[A] = {
    val r = value // the method `value` below shadows the parameter
    (e: Effects) => e.handle(new Answering[A, A] {
      def effect(): Type = t
      def value[X](op: Op[X]): X = Reader.narrow[R, X](op).fold(new Reader.Cases[R, X, X] { def ask(v: Same[R, X]): X = v.apply(r) })
    }, body)
  }

  def ask[R](t: Type): Program[R] = (e: Effects) => e.perform(new Reader.Ask[R](t))

  /** the body, an ask in it answered by the reader outside, through `f`: an interceptor — its clause asks outward */
  def local[R, A](t: Type, f: Fn[R, R], body: Program[A]): Program[A] = (e: Effects) => e.handle(new General[A, A] {
    def effect(): Type = t
    def ret(a: A, e2: Effects): Prog[A] = e2.pure(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[A]], e2: Effects): Prog[A] = Reader.narrow[R, X](op).fold(new Reader.Cases[R, X, Prog[A]] {
      def ask(v: Same[R, X]): Prog[A] = e2.flatMap[R, A](e2.perform(new Reader.Ask[R](t)), (r: R) => k.apply(v.apply(f.apply(r))))
    })
  }, body)

  // -- Writer

  /** WRITER: the values told, in order, with the body's */
  def writer[W, A](t: Type, body: Program[A]): Program[Logged[W, A]] = (e: Effects) => e.handle(new Collect[W, A](t), body)

  private final class Collect[W, A](t: Type) extends General[A, Logged[W, A]] with Returning[A, Logged[W, A]] {
    def effect(): Type = t
    def answer(a: A): Logged[W, A] = Logged.of[W, A](a)
    def ret(a: A, e: Effects): Prog[Logged[W, A]] = e.pure(answer(a))
    def apply[X](op: Op[X], k: Fn[X, Prog[Logged[W, A]]], e: Effects): Prog[Logged[W, A]] = told(op, k, e)
    private def told[X](op: Op[X], k: Fn[X, Prog[Logged[W, A]]], e: Effects): Prog[Logged[W, A]] =
      if (op.isInstanceOf[Writer.Ops[?, ?]])
        Writer.narrow[W, X](op).fold(new Writer.Cases[W, X, Prog[Logged[W, A]]] {
          def tell(w: W, v: Same[Void, X]): Prog[Logged[W, A]] = e.map[Logged[W, A], Logged[W, A]](k.apply(v.apply(null)), (l: Logged[W, A]) => l.told(w))
        })
      else
        Emit.narrow[W, X](op).fold(new Emit.Cases[W, X, Prog[Logged[W, A]]] {
          def send(w: W, v: Same[Void, X]): Prog[Logged[W, A]] = e.map[Logged[W, A], Logged[W, A]](k.apply(v.apply(null)), (l: Logged[W, A]) => l.told(w))
        })
  }

  def tell[W](t: Type, w: W): Program[Void] = (e: Effects) => e.perform(new Writer.Tell[W](t, w))

  // -- Throws

  /** THROWS: the body's value, or the error it raised — the body ended there, its finalizers run */
  def throwsOf[E, A](t: Type, body: Program[A]): Program[Result[E, A]] = (e: Effects) => e.handle(new AbortingReturning[A, Result[E, A]] {
    def effect(): Type = t
    def answer(a: A): Result[E, A] = new Ok[E, A](a)
    def abort[X](op: Op[X]): Result[E, A] =
      Throws.narrow[E, X](op).fold(new Throws.Cases[E, X, Result[E, A]] { def raise(err: E): Result[E, A] = new Err[E, A](err) })
  }, body)

  private trait AbortingReturning[A, R] extends Aborting[A, R] with Returning[A, R]

  def raise[E, A](t: Type, error: E): Program[A] = (e: Effects) => e.perform(new Throws.Raise[E, A](t, error))

  /** the body, a raise in it answered by `h` — run outside the handler, so a raise in `h` goes outward */
  def handleWith[E, A](t: Type, body: Program[A], h: Fn[E, Program[A]]): Program[A] = (e: Effects) => e.handle(new General[A, A] {
    def effect(): Type = t
    def ret(a: A, e2: Effects): Prog[A] = e2.pure(a)
    def apply[X](op: Op[X], k: Fn[X, Prog[A]], e2: Effects): Prog[A] =
      Throws.narrow[E, X](op).fold(new Throws.Cases[E, X, Prog[A]] { def raise(err: E): Prog[A] = h.apply(err).in(e2) })
  }, body)

  // -- Choose

  /** CHOOSE: every value the body comes to, a path a choice */
  def all[A](body: Program[A]): Program[java.util.List[A]] = (e: Effects) => e.handle(new General[A, java.util.List[A]] {
    def effect(): Type = Choose.TYPE
    def ret(a: A, e2: Effects): Prog[java.util.List[A]] = e2.pure(java.util.Collections.singletonList(a))
    def apply[X](op: Op[X], k: Fn[X, Prog[java.util.List[A]]], e2: Effects): Prog[java.util.List[A]] =
      Choose.narrow[X](op).fold(new Choose.Cases[X, Prog[java.util.List[A]]] {
        def among(xs: java.util.List[X]): Prog[java.util.List[A]] = from(xs, 0, new java.util.ArrayList[A](), k, e2)
      })
    private def from[X](xs: java.util.List[X], i: Int, acc: java.util.List[A], k: Fn[X, Prog[java.util.List[A]]], e2: Effects): Prog[java.util.List[A]] =
      if (i == xs.size()) e2.pure(java.util.Collections.unmodifiableList(acc))
      else e2.flatMap[java.util.List[A], java.util.List[A]](k.apply(xs.get(i)), (r: java.util.List[A]) => {
        val next = new java.util.ArrayList[A](acc)
        next.addAll(r)
        from(xs, i + 1, next, k, e2)
      })
  }, body)

  /** the first value the body comes to, its choices tried in order, the rest not tried */
  def first[A](body: Program[A]): Program[java.util.Optional[A]] = (e: Effects) => e.handle(new General[A, java.util.Optional[A]] {
    def effect(): Type = Choose.TYPE
    def ret(a: A, e2: Effects): Prog[java.util.Optional[A]] = e2.pure(java.util.Optional.of(a))
    def apply[X](op: Op[X], k: Fn[X, Prog[java.util.Optional[A]]], e2: Effects): Prog[java.util.Optional[A]] =
      Choose.narrow[X](op).fold(new Choose.Cases[X, Prog[java.util.Optional[A]]] {
        def among(xs: java.util.List[X]): Prog[java.util.Optional[A]] = from(xs, 0, k, e2)
      })
    private def from[X](xs: java.util.List[X], i: Int, k: Fn[X, Prog[java.util.Optional[A]]], e2: Effects): Prog[java.util.Optional[A]] =
      if (i == xs.size()) e2.pure(java.util.Optional.empty[A]())
      else e2.flatMap[java.util.Optional[A], java.util.Optional[A]](k.apply(xs.get(i)), (r: java.util.Optional[A]) => if (r.isPresent()) e2.pure(r) else from(xs, i + 1, k, e2))
  }, body)

  def among[X](xs: java.util.List[X]): Program[X] = (e: Effects) => e.perform(new Choose.Among[X](xs))

  // -- Emit

  /** EMIT, each value given to `f` in place */
  def forEach[W, A](t: Type, body: Program[A], f: Fn[W, Void]): Program[A] = (e: Effects) => e.handle(new Answering[A, A] {
    def effect(): Type = t
    def value[X](op: Op[X]): X = Emit.narrow[W, X](op).fold(new Emit.Cases[W, X, X] { def send(w: W, v: Same[Void, X]): X = v.apply(f.apply(w)) })
  }, body)

  /** EMIT, the values collected in order with the body's */
  def collect[W, A](t: Type, body: Program[A]): Program[Logged[W, A]] = (e: Effects) => e.handle(new Collect[W, A](t), body)

  /** EMIT AS A GENERATOR: its first position — a value and the rest, pulled when wanted, or the end */
  def generate[W, A](t: Type, body: Program[A]): Program[Gen[W, A]] = (e: Effects) => e.handle(new General[A, Gen[W, A]] {
    def effect(): Type = t
    def ret(a: A, e2: Effects): Prog[Gen[W, A]] = e2.pure[Gen[W, A]](new Results.End[W, A](a))
    def apply[X](op: Op[X], k: Fn[X, Prog[Gen[W, A]]], e2: Effects): Prog[Gen[W, A]] =
      Emit.narrow[W, X](op).fold(new Emit.Cases[W, X, Prog[Gen[W, A]]] {
        def send(w: W, v: Same[Void, X]): Prog[Gen[W, A]] = {
          val rest: Program[Gen[W, A]] = (_: Effects) => k.apply(v.apply(null))
          e2.pure[Gen[W, A]](new Results.Next[W, A](w, rest))
        }
      })
  }, body)

  def send[W](t: Type, w: W): Program[Void] = (e: Effects) => e.perform(new Emit.Send[W](t, w))

  // -- finalizers

  /** `acquire`, then `use` of it, then `release` of it — after a value, a failure or a cancellation alike; the
   * acquiring and the registering of the release one region cancellation waits for */
  trait Translation { def apply[X](op: Op[X]): Program[X] }
  private def translating[A](t: Type, f: Translation): Translating[A, A] = new Translating[A, A] {
    def effect(): Type = t
    def translate[X](op: Op[X]): Program[X] = f(op)
  }
  def translate[A](t: Type, f: Translation, body: Program[A]): Program[A] = new Program[A] {
    def in(e: Effects): Prog[A] = e.handle(translating[A](t, f), body)
  }
  def intercept[A](t: Type, f: Translation, body: Program[A]): Program[A] = new Program[A] {
    def in(e: Effects): Prog[A] = e.intercept(translating[A](t, f), body)
  }

  def bracket[R, A](acquire: Program[R], use: Fn[R, Program[A]], release: Fn[R, Program[Void]]): Program[A] =
    (e: Effects) => e.flatMap[R, A](e.uncancelable(acquire), (r: R) => e.ensuring(use.apply(r), release.apply(r)))
}
