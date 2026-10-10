package okay.kernel.std;

import okay.kernel.Effect;

/** THE STANDARD EFFECTS: of a name and the types they are over — the same keys as every implementation's, so a
 * program's operations go to any handler of them */
public final class Effects {
  private Effects() {}

  public static Effect writer(String w) { return Effect.of("Writer", w); }
  public static Effect writer(Class<?> w) { return writer(Effect.typeName(w)); }
  public static Effect throwsOf(String e) { return Effect.of("Throws", e); }
  public static Effect throwsOf(Class<?> e) { return throwsOf(Effect.typeName(e)); }
  public static Effect state(String s) { return Effect.of("State", s); }
  public static Effect state(Class<?> s) { return state(Effect.typeName(s)); }
  public static final Effect ASYNC = Effect.of("Async");
  public static final Effect CHOOSE = Effect.of("Choose");
}
