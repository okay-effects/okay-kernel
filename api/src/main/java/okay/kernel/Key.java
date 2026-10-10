package okay.kernel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AN EFFECT'S KEY, TYPED: {@code Key<F>}, {@code F} the effect as a type ({@code State<Integer>}) — what operations and
 * handlers carry, so a handler found by an operation's key is the operation's own, its types the operation's. The
 * Java of {@code Class<T>} in a typesafe heterogeneous container: a key cannot be had at a type it is not.
 *
 * <p>One a {@link Type}, interned: {@code State<Integer>}'s key is the same object wherever it is asked for, in any
 * language. Made only by the families' typed factories ({@code State.key(s)}) or fresh. THE ONE TRUSTED STEP of the
 * kernel's types is here, in {@link #of}: a key asked for at {@code F} by its type is the one made for that type —
 * sound as long as a {@code Type} names one type exactly (the exact types of {@code TypeOf}; a name of one's own, a
 * string, must not be given to two types).
 */
public final class Key<F> {
  private final Type type;

  private Key(Type type) { this.type = type; }

  /** the effect's exact identity */
  public Type type() { return type; }

  /** whether {@code op} is of this key's effect: the same key, by identity */
  public boolean owns(Op<?> op) { return op.key() == this; }

  private static final ConcurrentHashMap<Type, Key<?>> known = new ConcurrentHashMap<>();

  /** the key of {@code type}, at the effect's type {@code F}: one object for all who ask — for a family's typed factory
   * ({@code State.key}), which alone knows that {@code type} is {@code F}'s */
  @SuppressWarnings("unchecked")
  public static <F> Key<F> of(Type type) {
    return (Key<F>) known.computeIfAbsent(type, Key::new);
  }

  /** a key no other is: a capability's effect, an effect hidden inside a handler, a delimiter's */
  public static <F> Key<F> fresh() { return new Key<>(Type.fresh()); }

  @Override public String toString() { return type.toString(); }
}
