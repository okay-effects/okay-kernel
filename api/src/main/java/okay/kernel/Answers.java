package okay.kernel;

/**
 * WHAT A HOST ANSWERS IN PLACE: an operation it can answer with no capture — a reader's value, a cell's — asked by
 * the computation where it is performed, no step handed out. If {@code value} throws, it is a failure where the
 * operation was
 */
public interface Answers {
  boolean answers(Type effect);

  <X> X value(Op<X> op);

  /** a host that answers nothing in place */
  Answers NONE = new Answers() {
    public boolean answers(Type effect) { return false; }
    public <X> X value(Op<X> op) { throw new IllegalStateException("not answered in place: " + op); }
  };
}
