package okay.kernel;

/**
 * a handler translating each operation to a PROGRAM OUTSIDE IT, the rest of the body then resumed with its value —
 * one's effect on another (an audit to a writer, a ticket on a hidden state), no {@code k} written. Tail-resumptive, so
 * a machine may run it with no capture; its operations go to the handlers outside it. Its return the body's value, or
 * {@link Returning}'s
 */
public interface Translating<A, R> extends Handler<A, R> {
  <X> Program<X> translate(Op<X> op);
}
