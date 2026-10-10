package okay.kernel

/** Scala's own over the kernel's API: exact types found by the compiler */
package object types {
  /** the exact type of `T` */
  def typeOf[T](implicit t: TypeOf[T]): Type = t.get
}
