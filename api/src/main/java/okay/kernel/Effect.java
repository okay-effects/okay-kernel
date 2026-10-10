package okay.kernel;

import java.util.concurrent.ConcurrentHashMap;

/** AN EFFECT: what a handler takes — by identity. {@code Effect.apply(name)} a fresh one each call: a prompt, a
 * delimiter of its own; {@code Effect.of(name, types)} the same one for the same name and types — {@code Writer % String}
 * and {@code Writer % Integer} two effects, {@code Writer % String} one, in every language and implementation */
public final class Effect {
  private final String name;

  private Effect(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  /** a fresh effect: no other is it */
  public static Effect apply(String name) {
    return new Effect(name);
  }

  private static final ConcurrentHashMap<String, Effect> known = new ConcurrentHashMap<>();

  /** the effect of this name over these types (their class names, primitives boxed): one for all who ask */
  public static Effect of(String name, String... types) {
    String key = types.length == 0 ? name : name + "[" + String.join(",", types) + "]";
    return known.computeIfAbsent(key, Effect::new);
  }

  /** a type's name for {@code of}: its class's, a primitive's box's — {@code int} and {@code Integer} one */
  public static String typeName(Class<?> c) {
    if (!c.isPrimitive()) return c.getName();
    return switch (c.getName()) {
      case "int" -> "java.lang.Integer";
      case "long" -> "java.lang.Long";
      case "boolean" -> "java.lang.Boolean";
      case "double" -> "java.lang.Double";
      case "float" -> "java.lang.Float";
      case "short" -> "java.lang.Short";
      case "byte" -> "java.lang.Byte";
      case "char" -> "java.lang.Character";
      case "void" -> "java.lang.Void";
      default -> c.getName();
    };
  }
}
