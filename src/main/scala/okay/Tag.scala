package okay

import scala.reflect.ClassTag

/** a type's name at run time, for an effect's identity: `Writer % String` and `Writer % Int` two effects */
final class Tag[T](val name: String)

object Tag:
  given [T](using c: ClassTag[T]): Tag[T] = Tag(okay.kernel.Effect.typeName(c.runtimeClass))
