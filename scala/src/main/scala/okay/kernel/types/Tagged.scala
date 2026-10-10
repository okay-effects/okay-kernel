package okay.kernel.types

/** `A @@ T`: a value of `A` tagged by `T` — an object, a literal, a class: a type of its own, apart from `A` and from
 * `A` under any other tag, so an effect over it is an effect of its own (`State[Long @@ UserId.type]`). A wrapper, no
 * cast */
final case class @@[+A, T](value: A)
