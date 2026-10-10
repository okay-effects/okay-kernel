package okay

import scala.reflect.ClassTag
import okay.kernel.Effect

/** A TYPE AT RUN TIME, for an effect's identity: `Writer % String` and `Writer % Int` two effects. One a class,
 * cached: its effects of each kind made once — an operation finds its effect with no string built, no lock taken */
final class Tag[T] private (val name: String):
  private val effects = new Array[Effect](Tag.Kinds)
  private val handlers = new Array[AnyRef](Tag.Kinds)

  /** a handler of kind `kind` over this type, of no state: made once */
  def handler[H <: AnyRef](kind: Int, make: => H): H =
    val h = handlers(kind)
    if h != null then h.asInstanceOf[H]
    else
      val made = make
      handlers(kind) = made
      made

  /** the effect of kind `kind` (a `Tag.Writer`…) over this type: made once */
  def effect(kind: Int, make: String => Effect): Effect =
    val e = effects(kind)
    if e != null then e
    else
      val made = make(name)
      effects(kind) = made
      made

object Tag:
  inline val Writer = 0
  inline val Throws = 1
  inline val State = 2
  inline val Kinds = 3

  // the tags known, by class: a map replaced whole when one is added, read with no lock
  @volatile private var known = Map.empty[Class[?], Tag[?]]

  def of[T](c: Class[?]): Tag[T] =
    known.get(c) match
      case Some(t) => t.asInstanceOf[Tag[T]]
      case None => synchronized {
        known.get(c) match
          case Some(t) => t.asInstanceOf[Tag[T]]
          case None =>
            val t = Tag[Any](Effect.typeName(c))
            known = known.updated(c, t)
            t.asInstanceOf[Tag[T]]
      }

  given [T](using c: ClassTag[T]): Tag[T] = of[T](c.runtimeClass)
