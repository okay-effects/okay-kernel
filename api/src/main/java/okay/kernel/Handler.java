package okay.kernel;

/**
 * A HANDLER of an effect: a delimiter, of one of four shapes — {@link Answering} (answered in place),
 * {@link Aborting} ({@code k} never called), {@link Returning} (its return a value), {@link General} (a clause with
 * {@code k}). The first three are data: an implementation makes them its own handlers at no cost; a handler may be two
 * at once. The body's value {@code A}, the handler's answer {@code R}
 */
public interface Handler<A, R> {
  Type effect();
}
