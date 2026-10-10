package okay.kernel;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AN EFFECT'S IDENTITY, EXACT: a key applied to arguments — the key a class or any value (an object, a literal, an
 * enum's case; a string at worst, one for the whole process), compared by {@code equals}. Interned: two types are
 * equal if and only if they are the same object. Or fresh: a type no other is — a capability's effect, one a run of
 * its handler. A primitive class is its box: Scala's {@code Int} and Java's {@code Integer} one type.
 */
public final class Type {
  private final Object key;
  private final Type[] args;
  private final int id;

  private Type(Object key, Type[] args, int id) {
    this.key = key;
    this.args = args;
    this.id = id;
  }

  /** the key; {@code null} for a fresh type */
  public Object key() { return key; }

  /** the arguments, a copy */
  public Type[] args() { return args.clone(); }

  public int arity() { return args.length; }

  public Type arg(int i) { return args[i]; }

  /** dense, from 0: a machine's array of handlers in force, by effect */
  public int id() { return id; }

  private record Interned(Object key, int[] args) {
    @Override public boolean equals(Object o) {
      return o instanceof Interned i && key.equals(i.key) && Arrays.equals(args, i.args);
    }
    @Override public int hashCode() { return key.hashCode() * 31 + Arrays.hashCode(args); }
  }

  private static final ConcurrentHashMap<Interned, Type> known = new ConcurrentHashMap<>();
  private static final AtomicInteger next = new AtomicInteger();

  /** the type of {@code key} applied to {@code args}: one object for all who ask */
  public static Type of(Object key, Type... args) {
    if (key == null) throw new IllegalArgumentException("a type's key is not null");
    Object k = key instanceof Class<?> c ? boxed(c) : key;
    Type[] as = args.clone();
    int[] ids = new int[as.length];
    for (int i = 0; i < as.length; i++) ids[i] = as[i].id;
    return known.computeIfAbsent(new Interned(k, ids), i -> new Type(k, as, next.getAndIncrement()));
  }

  /** a type no other is */
  public static Type fresh() {
    return new Type(null, new Type[0], next.getAndIncrement());
  }

  private static Class<?> boxed(Class<?> c) {
    if (!c.isPrimitive()) return c;
    if (c == int.class) return Integer.class;
    if (c == long.class) return Long.class;
    if (c == boolean.class) return Boolean.class;
    if (c == double.class) return Double.class;
    if (c == float.class) return Float.class;
    if (c == short.class) return Short.class;
    if (c == byte.class) return Byte.class;
    if (c == char.class) return Character.class;
    if (c == void.class) return Void.class;
    return c;
  }

  @Override public String toString() {
    String k = key == null ? "fresh#" + id : key instanceof Class<?> c ? c.getSimpleName() : key.toString();
    if (args.length == 0) return k;
    StringBuilder b = new StringBuilder(k).append('[');
    for (int i = 0; i < args.length; i++) b.append(i == 0 ? "" : ", ").append(args[i]);
    return b.append(']').toString();
  }
}
