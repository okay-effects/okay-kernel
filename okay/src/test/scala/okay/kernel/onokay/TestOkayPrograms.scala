package okay.kernel.onokay

import okay.cont.{Choose, Every, Free, Pure, State, StateCell, Throws, Writer, WriterLog, +:, %}
import okay.kernel.{Answers, Effects, Program, Step, Type}
import okay.kernel.std.{Handlers, Results, State as KState, Throws as KThrows, Writer as KWriter}
import okay.kernel.steps.{Steps, StepsJava}
import okay.kernel.tck.Tck.{fn, list, prog, set}

/** okay's own programs, in okay's words, under the kernel's handlers of any implementation; the kernel's programs
 * under okay's handlers */
class TestOkayPrograms extends munit.FunSuite:
  private val stT = Type.of("okay.kernel.onokay.s")
  // one effect, seen at Scala's Int by okay and at Integer by the kernel's std: one key at run time (Int is boxed)
  private val st = KState.key[Integer](stT)
  private val log = KWriter.key[String](Type.of("okay.kernel.onokay.w"))
  private val err = KThrows.key[String](Type.of("okay.kernel.onokay.t"))
  given stateM: Mapping[State % Int] = Mapping.state[Int](KState.key[Int](stT))
  given writerM: Mapping[Writer % String] = Mapping.writer[String](log)
  given chooseM: Mapping[Choose] = Mapping.choose
  given throwsM: Mapping[Throws % String] = Mapping.throws[String](err)

  type R = State % Int +: Writer % String +: Choose +: Pure

  /** IN OKAY'S WORDS: a choice, the state read and moved, a word told */
  val okayProgram: Free[R, Int] =
    import okay.*
    for
      x <- effect(Choose.Among(Seq(1, 2))).at[R]
      s <- effect(State.Get[Int]()).at[R]
      _ <- effect(State.Put(s + x)).at[R]
      _ <- effect(Writer.Tell(s"x=$x")).at[R]
    yield s + x

  /** THE SAME, WRITTEN ONCE over the kernel's `Effects` */
  val kernelProgram: Program[Integer] = prog { e =>
    e.flatMap(Handlers.among[Integer](list(Integer.valueOf(1), Integer.valueOf(2))).in(e), fn((x: Integer) =>
      e.flatMap(Handlers.get[Integer](st).in(e), fn((s: Integer) =>
        e.flatMap(Handlers.put[Integer](st, Integer.valueOf(s + x)).in(e), fn((_: Void) =>
          e.map(Handlers.tell[String](log, s"x=$x").in(e), fn((_: Void) => Integer.valueOf(s + x)))))))))
  }

  /** the kernel's standard handlers around a program: state (a cell, shared by the paths), writer, every path */
  def underKernel[A](body: Program[A]): Program[Results.Final[Integer, Results.Logged[String, java.util.List[A]]]] =
    Handlers.state(st, Integer.valueOf(0), Handlers.writer(log, Handlers.all(body)))
  val kernelAnswer = Results.Final(Integer.valueOf(3), Results.Logged(list("x=1", "x=2"), list(1, 3)))

  /** okay's handlers around a program: the same, okay's */
  def underOkay(p: Free[R, Int]): (Int, (List[String], Seq[Int])) =
    import okay.*
    p.handle(Every[Int]()).handle(WriterLog[String, Seq[Int]]()).handle(StateCell[Int, (List[String], Seq[Int])](0)).value
  val okayAnswer = (3, (List("x=1", "x=2"), Seq(1, 3)))

  private def run[A](e: Effects, p: Program[A]): A = okay.kernel.tck.Tck.run(e, p)

  test("okay's program under okay's handlers: the answer to compare") {
    assertEquals(underOkay(okayProgram), okayAnswer)
  }

  test("EXPORT: okay's program, a computation, under the reference machines' standard handlers") {
    val c = OkayPrograms.exported(okayProgram)
    assertEquals(c.effects(), set(st, log, okay.kernel.std.Choose.KEY))
    for e <- List[Effects](Steps, StepsJava.INSTANCE) do
      assertEquals(run(e, underKernel(prog(_.foreign(c)))).toString, kernelAnswer.toString)
  }

  test("okay's program as a kernel program on okay's bridge, under the kernel's handlers: no computation crossed") {
    assertEquals(run(OnOkay, underKernel(OkayPrograms.lift(okayProgram))).toString, kernelAnswer.toString)
    // and on another implementation, imported there
    assertEquals(run(Steps, underKernel(OkayPrograms.lift(okayProgram))).toString, kernelAnswer.toString)
  }

  test("export: handed out step by step, k multi-shot") {
    val c = OkayPrograms.exported(okayProgram)
    val among = c.start(Answers.NONE).asInstanceOf[Step.Yield[Any, Int]]
    def drive(s: Step[Int], state: Int): Int = s match
      case d: Step.Done[Int] => d.value()
      case y: Step.Yield[?, Int] @unchecked => y.op() match
        case _: KState.Get[?] => drive(y.k().asInstanceOf[okay.kernel.Resume[Any, Int]].resume(Integer.valueOf(state)), state)
        case p: KState.Put[?] => drive(y.k().asInstanceOf[okay.kernel.Resume[Any, Int]].resume(null), p.s().asInstanceOf[Integer])
        case _ => drive(y.k().asInstanceOf[okay.kernel.Resume[Any, Int]].resume(null), state)
      case other => fail(s"$other")
    assertEquals(drive(among.k().resume(Integer.valueOf(1)), 10), 11)
    assertEquals(drive(among.k().resume(Integer.valueOf(2)), 10), 12)
  }

  test("IMPORT: the kernel's program, computed by the reference machine, under okay's handlers") {
    for e <- List[Effects](Steps, StepsJava.INSTANCE, OnOkay) do
      val c = e.computation(prog(x => x.map(kernelProgram.in(x), fn((i: Integer) => i.intValue))), set(st, log, okay.kernel.std.Choose.KEY))
      assertEquals(underOkay(OkayPrograms.imported[R, Int](c)), okayAnswer)
  }

  test("okay's program there and back: exported, imported, under okay's handlers") {
    assertEquals(underOkay(OkayPrograms.imported[R, Int](OkayPrograms.exported(okayProgram))), okayAnswer)
  }

  test("okay's raise under the kernel's throws: an abort through the border, the kernel's finalizer run") {
    type T = Throws % String +: Writer % String +: Pure
    val raising: Free[T, Int] =
      import okay.*
      effect(Writer.Tell("before")).at[T].flatMap(_ => effect(Throws.Raise("boom")).at[T]).map(_ => 1)
    val p = Handlers.writer[String, Results.Result[String, Int]](log, Handlers.throwsOf[String, Int](err,
      prog(x => x.ensuring(OkayPrograms.lift(raising), Handlers.tell[String](log, "fin")))))
    for e <- List[Effects](OnOkay, Steps) do
      val r = run(e, p)
      assertEquals((r.log(), r.value().toString), (list("before", "fin"), "Err[error=boom]"))
  }

  test("100 000 operations of okay's program under the kernel's state, in constant stack") {
    type S = State % Int +: Pure
    def loop(n: Int): Free[S, Int] =
      import okay.*
      if n == 0 then effect(State.Get[Int]()).at[S]
      else effect(State.Get[Int]()).at[S].flatMap(s => effect(State.Put(s + 1)).at[S]).flatMap(_ => Free.delay(() => loop(n - 1)))
    assertEquals(run(OnOkay, Handlers.state(st, Integer.valueOf(0), OkayPrograms.lift(loop(100000)))).state().intValue, 100000)
  }

  test("okay's own handlers inside, the rest outside under the kernel's: okay's reach crosses them to the border") {
    type S = State % Int +: Pure
    // Choose and Reader handled by okay inside (one with k, multi-shot; one in place), State by the kernel outside
    val inner: Free[okay.cont.Reader % Int +: Choose +: S, Int] =
      import okay.*
      for
        r <- effect(okay.cont.Reader.Ask[Int]()).at[okay.cont.Reader % Int +: Choose +: S]
        x <- effect(Choose.Among(Seq(1, 2))).at[okay.cont.Reader % Int +: Choose +: S]
        s <- effect(State.Get[Int]()).at[okay.cont.Reader % Int +: Choose +: S]
        _ <- effect(State.Put(s + r * x)).at[okay.cont.Reader % Int +: Choose +: S]
      yield s
    val handled: Free[S, Seq[Int]] =
      import okay.*
      inner.handle(okay.cont.ReaderOf[Int, Int](10)).handle(Every[Int]())
    for e <- List[Effects](OnOkay, Steps, StepsJava.INSTANCE) do
      val r = run(e, Handlers.state(st, Integer.valueOf(0), OkayPrograms.lift(handled)))
      assertEquals((r.state().intValue, r.value()), (30, Seq(0, 10)))
  }
