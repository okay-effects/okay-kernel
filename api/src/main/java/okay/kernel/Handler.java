package okay.kernel;

/**
 * A HANDLER of an effect: a delimiter, of one of five shapes — {@link Answering} (answered in place),
 * {@link Aborting} ({@code k} never called), {@link Returning} (its return a value), {@link Translating} (each
 * operation a program outside, the rest resumed with its value), {@link General} (a clause with {@code k}). The first
 * four are data: an implementation makes them its own handlers at no cost; a handler may be two
 * at once. The body's value {@code A}, the handler's answer {@code R}
 */
public interface Handler<A, R> {
  Type effect();
}
