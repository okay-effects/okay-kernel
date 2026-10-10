package okay.jkernel;

import okay.kernel.*;

/**
 * A MONAD: the machine of steps — programs as nodes, run over an explicit stack of handlers on the heap, so handlers
 * nest as deep as the heap allows. One step a kind of node, a method of its own (the loop's call to it a virtual one):
 * each compiled apart, small. A handler is found by its effect, walked to; a capture crosses the handlers between with
 * no allocation; resumed where it was captured, a continuation keeps the stack as it was; a clause's {@code k(x)} bound
 * puts its function in the handler, no frame of its own. okay-kernel's {@code Steps}, in Java
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class Steps implements Monad {
  public static final Steps INSTANCE = new Steps();

  private Steps() {}

  // THE NODES: this monad's programs

  private abstract static class Node implements Prog {
    abstract Node step(Run r);
  }

  private static final class Return extends Node {
    final Object value;
    Return(Object value) { this.value = value; }
    Node step(Run r) { return r.value(value); }
  }

  private static final class Bind extends Node {
    final Node m;
    final Fn f;
    Bind(Node m, Fn f) { this.m = m; this.f = f; }
    Node step(Run r) { return r.bind(this); }
  }

  private static final class MapN extends Node {
    final Node m;
    final Fn f;
    MapN(Node m, Fn f) { this.m = m; this.f = f; }
    Node step(Run r) { return r.map(this); }
  }

  private static final class Delay extends Node {
    final Thunk c;
    Delay(Thunk c) { this.c = c; }
    Node step(Run r) { return (Node) c.get(); }
  }

  private static final class Perform extends Node {
    final Op op;
    Perform(Op op) { this.op = op; }
    Node step(Run r) { return r.perform(op, null); }
  }

  /** an operation bound: one node */
  private static final class Suspend extends Node {
    final Op op;
    final Fn k;
    Suspend(Op op, Fn k) { this.op = op; this.k = k; }
    Node step(Run r) { return r.perform(op, k); }
  }

  private static final class Handle extends Node {
    final Handler h;
    final Program body;
    Handle(Handler h, Program body) { this.h = h; this.body = body; }
    Node step(Run r) { return r.handle(this, null); }
  }

  /** a captured rest resumed with {@code x}: {@code k} frames or a function, {@code upTo} its handler if none crossed,
   * else the Kont */
  private static final class Resume extends Node {
    final Object x;
    final Object k;
    final Object upTo;
    Resume(Object x, Object k, Object upTo) { this.x = x; this.k = k; this.upTo = upTo; }
    Node step(Run r) { return r.resume(this, null); }
  }

  public <A> Prog<A> pure(A a) {
    return new Return(a);
  }

  public <A, B> Prog<B> flatMap(Prog<A> p, Fn<A, Prog<B>> f) {
    return p instanceof Perform o ? new Suspend(o.op, f) : new Bind((Node) p, f);
  }

  @Override
  public <A, B> Prog<B> map(Prog<A> p, Fn<A, B> f) {
    return new MapN((Node) p, f);
  }

  public <A> Prog<A> delay(Thunk<Prog<A>> p) {
    return new Delay(p);
  }

  public <X> Prog<X> perform(Op<X> op) {
    return new Perform(op);
  }

  public <A, R> Prog<R> handle(Handler<A, R> h, Program<A> body) {
    return new Handle(h, body);
  }

  private static final Node DONE = new Return(null);

  public <A> A run(Prog<A> p) {
    Run r = new Run();
    Node c = (Node) p;
    while (c != DONE) c = c.step(r);
    return (A) r.result;
  }

  // THE STACK

  private abstract static class Frames {}
  private static final Frames END = new Frames() {};
  private static final class Then extends Frames {
    final Fn f;
    final Frames next;
    Then(Fn f, Frames next) { this.f = f; this.next = next; }
  }
  private static final class Mapped extends Frames {
    final Fn f;
    final Frames next;
    Mapped(Fn f, Frames next) { this.f = f; this.next = next; }
  }

  private abstract static class Meta {}
  private static final Meta TOP = new Meta() {};
  /** a handler in force: the function its answer is bound to if any, the frames outside it, the handlers further out */
  private static final class Seg extends Meta {
    final Handler h;
    final Fn bound;
    final Frames outer;
    final Meta next;
    Seg(Handler h, Fn bound, Frames outer, Meta next) { this.h = h; this.bound = bound; this.outer = outer; this.next = next; }
  }
  /** the first {@code n} handlers of {@code m}, then {@code tail}: a resumed continuation's, shared, not copied */
  private static final class Take extends Meta {
    final Meta m;
    final int n;
    final Meta tail;
    Take(Meta m, int n, Meta tail) { this.m = m; this.n = n; this.tail = tail; }
  }

  private static Meta take(Meta m, int n, Meta tail) {
    if (n == 0) return tail;
    while (m instanceof Take t && n <= t.n) m = t.m;
    return new Take(m, n, tail);
  }

  private static Seg head(Meta m) {
    while (true) {
      if (m instanceof Seg s) return s;
      if (m instanceof Take t) m = t.n > 0 ? t.m : t.tail;
      else throw new IllegalStateException("no handler");
    }
  }

  private static Meta drop(Meta m, int d) {
    while (d > 0) {
      if (m instanceof Seg s) {
        m = s.next;
        d--;
      } else if (m instanceof Take t) {
        if (d < t.n) return take(drop(t.m, d), t.n - d, t.tail);
        d -= t.n;
        m = t.tail;
      } else throw new IllegalStateException("no handler");
    }
    return m;
  }

  /** the index of the nearest handler of {@code e} among the first {@code limit} of {@code m}: {@code -1 - n} if none */
  private static int indexOf(Meta m, int limit, Effect e) {
    int seen = 0;
    while (seen < limit) {
      if (m instanceof Seg s) {
        if (s.h.effect() == e) return seen;
        seen++;
        m = s.next;
      } else if (m instanceof Take t) {
        int n = Math.min(t.n, limit - seen);
        int i = indexOf(t.m, n, e);
        if (i >= 0) return seen + i;
        seen += n;
        m = t.tail;
      } else return -1 - seen;
    }
    return -1 - seen;
  }

  private static int index(Meta ms, Effect e) {
    int i = indexOf(ms, Integer.MAX_VALUE, e);
    if (i < 0) throw new IllegalStateException("an operation of " + e + " with no handler of it in force");
    return i;
  }

  /** a captured continuation: the rest (frames or a function), the handlers crossed and the one captured up to */
  private static final class Kont implements Fn {
    final Object k;
    final Meta ms;
    final int n;
    Kont(Object k, Meta ms, int n) { this.k = k; this.ms = ms; this.n = n; }
    public Object apply(Object x) { return new Resume(x, k, n == 0 && ms instanceof Seg ? ms : this); }
    Seg up() { return n == 0 && ms instanceof Seg s ? s : head(drop(ms, n)); }
  }

  private static final class Run {
    Frames k = END;
    Meta ms = TOP;
    Object result;

    Node bind(Bind b) {
      Node m = b.m;
      if (m instanceof Return r) return (Node) b.f.apply(r.value);
      // a handler or a resumption bound: the function to its handler, no frame made
      if (m instanceof Resume r) return resume(r, b.f);
      if (m instanceof Handle h) return handle(h, b.f);
      k = new Then(b.f, k);
      return m instanceof Delay d ? (Node) d.c.get() : m;
    }

    Node map(MapN m) {
      k = new Mapped(m.f, k);
      return m.m instanceof Delay d ? (Node) d.c.get() : m.m;
    }

    Node handle(Handle h, Fn bound) {
      ms = new Seg(h.h, bound, k, ms);
      k = END;
      return (Node) h.body.in(INSTANCE);
    }

    /** an operation: to the nearest handler of its effect — answered in place if it answers so, else its clause in
     * the handler's place, {@code k} the rest, {@code bound} the function bound to the operation's value if any */
    Node perform(Op op, Fn bound) {
      Effect e = op.effect();
      Seg d;
      int i = 0;
      if (ms instanceof Seg near && near.h.effect() == e) d = near;
      else {
        i = index(ms, e);
        d = head(drop(ms, i));
      }
      if (d.h instanceof Answering a) {
        Object v = a.value(op);
        return bound == null ? value(v) : (Node) bound.apply(v);
      }
      Object kk = bound == null ? k : k == END ? bound : new Then(bound, k);
      Kont kont = new Kont(kk, ms, i);
      Node c = (Node) d.h.apply(op, kont, INSTANCE);
      ms = i == 0 && ms == d ? d.next : drop(ms, i + 1);
      k = d.outer;
      if (d.bound != null) {
        if (c instanceof Return a) c = (Node) d.bound.apply(a.value);
        else k = new Then(d.bound, k);
      }
      // the usual clauses, k(x), bound or mapped: resumed right here, no step of the loop between
      if (c instanceof Resume r) return resume(r, null);
      if (c instanceof Bind bb && bb.m instanceof Resume r) return resume(r, bb.f);
      if (c instanceof MapN mp && mp.m instanceof Resume r) {
        k = new Mapped(mp.f, k);
        return resume(r, null);
      }
      return c;
    }

    /** a continuation resumed here: x through its rest, then its handlers again, the frames here outside them */
    Node resume(Resume r, Fn bound) {
      Object x = r.x;
      if (!(r.k instanceof Frames fr)) {
        ms = again(r, bound, k, ms);
        k = END;
        return (Node) ((Fn) r.k).apply(x);
      }
      Node next = null;
      if (fr instanceof Mapped m) {
        x = m.f.apply(x);
        fr = m.next;
        while (fr instanceof Mapped mm) {
          x = mm.f.apply(x);
          fr = mm.next;
        }
        if (fr instanceof Then t) {
          next = (Node) t.f.apply(x);
          fr = t.next;
        }
      } else if (fr instanceof Then t) {
        next = (Node) t.f.apply(x);
        fr = t.next;
      }
      if (next != null) {
        ms = again(r, bound, k, ms);
        k = fr;
        return next;
      }
      if (r.upTo instanceof Seg up) {
        // its rest done, its handler next: the frames and handlers here as they are, its return applied
        Node c = (Node) up.h.ret(x, INSTANCE);
        if (bound == null) return c;
        if (c instanceof Return a) return (Node) bound.apply(a.value);
        k = new Then(bound, k);
        return c;
      }
      ms = again(r, bound, k, ms);
      k = END;
      return value(x);
    }

    /** a value: through the maps to the first bind, or out of the handler — its return, outside it; a value again,
     * on here, no step of the loop for it */
    Node value(Object v) {
      while (true) {
        while (k instanceof Mapped m) {
          v = m.f.apply(v);
          k = m.next;
        }
        Node c;
        if (k instanceof Then t) {
          k = t.next;
          c = (Node) t.f.apply(v);
        } else {
          Seg seg;
          if (ms instanceof Seg plain) {
            seg = plain;
            ms = plain.next;
          } else if (ms == TOP) {
            result = v;
            return DONE;
          } else {
            seg = head(ms);
            ms = drop(ms, 1);
          }
          k = seg.outer;
          c = (Node) seg.h.ret(v, INSTANCE);
          if (seg.bound != null) {
            if (c instanceof Return a) c = (Node) seg.bound.apply(a.value);
            else k = new Then(seg.bound, k);
          }
        }
        if (!(c instanceof Return r)) return c;
        v = r.value;
      }
    }
  }

  private static Meta again(Resume r, Fn bound, Frames k, Meta ms) {
    if (r.upTo instanceof Seg up)
      return bound == null && up.bound == null && k == up.outer && ms == up.next ? up : new Seg(up.h, bound, k, ms);
    Kont kont = (Kont) r.upTo;
    Meta at = drop(kont.ms, kont.n);
    Seg up = head(at);
    if (bound == null && up.bound == null && k == up.outer && ms == up.next)
      return at == up ? kont.ms : take(kont.ms, kont.n, up);
    return take(kont.ms, kont.n, new Seg(up.h, bound, k, ms));
  }
}
