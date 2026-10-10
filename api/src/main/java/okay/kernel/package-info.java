/**
 * THE KERNEL: a protocol, minimal and portable, by which implementations of effects and delimited control — of any
 * language, on any machine — work with each other in one process (docs/design.md). An effect is an identity
 * ({@link okay.kernel.Type}) and operations ({@link okay.kernel.Op}); a program of any implementation, handed out, is a
 * {@link okay.kernel.Computation}, run by its {@link okay.kernel.Step}s; code meant for every implementation is
 * written once over {@link okay.kernel.Effects}.
 */
package okay.kernel;
