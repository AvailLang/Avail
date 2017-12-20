/**
 * Interpreter.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.interpreter;

import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.AvailThread;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.AvailException;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L1InstructionStepper;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operation.L2_INVOKE;
import com.avail.interpreter.primitive.controlflow.P_CatchException;
import com.avail.interpreter.primitive.fibers.P_AttemptJoinFiber;
import com.avail.interpreter.primitive.variables.P_SetValue;
import com.avail.io.TextInterface;
import com.avail.optimizer.StackReifier;
import com.avail.performance.PerInterpreterStatistic;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.MutableOrNull;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2NotNull;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.FiberDescriptor.*;
import static com.avail.descriptor.FiberDescriptor.ExecutionState.*;
import static com.avail.descriptor.FiberDescriptor.InterruptRequestFlag
	.REIFICATION_REQUESTED;
import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag.BOUND;
import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag
	.PERMIT_UNAVAILABLE;
import static com.avail.descriptor.FiberDescriptor.TraceFlag
	.TRACE_VARIABLE_READS_BEFORE_WRITES;
import static com.avail.descriptor.FiberDescriptor.TraceFlag
	.TRACE_VARIABLE_WRITES;
import static com.avail.descriptor.FunctionDescriptor.newPrimitiveFunction;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.VariableDescriptor
	.newVariableWithContentType;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Interpreter.FakeStackTraceSlots.*;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.primitive.variables.P_SetValue.instance;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * This class is used to execute {@linkplain L2Chunk Level Two code}, which is a
 * translation of the Level One nybblecodes found in {@linkplain
 * CompiledCodeDescriptor compiled code}.
 *
 * <p>
 * Level One nybblecodes are designed to be compact and very simple, but not
 * particularly efficiently executable. Level Two is designed for a clean model
 * for optimization, including:
 * <ul>
 * <li>primitive folding.</li>
 * <li>register coloring/allocation.</li>
 * <li>inlining.</li>
 * <li>common sub-expression elimination.</li>
 * <li>side-effect analysis.</li>
 * <li>object escape analysis.</li>
 * <li>a variant of keyhole optimization that involves building the loosest
 * possible Level Two instruction dependency graph, then "pulling" eligible
 * instruction sequences that are profitably rewritten.</li>
 * <li>further translation to native code – although the current plan is to
 * generate Java bytecodes to leverage the enormous amount of effort that went
 * into the bytecode verifier, concurrency semantics, and HotSpot's low-level
 * optimizations.</li>
 * </ul>
 * As of 2011.05.09, only the first of these optimizations has been implemented,
 * although a translation into Smalltalk blocks was implemented experimentally
 * by Mark van Gulik in the mid-1990s.
 * </p>
 *
 * <p>
 * To accomplish these goals, the stack-oriented architecture of Level One maps
 * onto a register transfer language for Level Two. At runtime the idealized
 * {@code Interpreter interpreter} has an arbitrarily large bank of
 * pointer registers (that point to {@linkplain AvailObject Avail objects}),
 * plus a separate bank for {@code int}s (unboxed 32-bit signed integers), and a
 * similar bank for {@code double}s (unboxed double-precision floating point
 * numbers). Ideally these will map to machine registers, but more likely they
 * will spill into physical arrays of the appropriate type. Register spilling is
 * a well studied art, and essentially a solved problem. Better yet, the Java
 * HotSpot optimizer should be able to do at least as good a job as we can, so
 * we should be able to just generate Java bytecodes and leave it at that.
 * </p>
 *
 * <p>
 * One of the less intuitive aspects of the Level One / Level Two mapping is how
 * to handle the call stack. The Level One view is of a chain of continuations,
 * but Level Two doesn't even have a stack! We bridge this disconnect by
 * reserving a register to hold the Level One continuation of the
 * <em>caller</em> of the current method. This is at least vaguely analogous to
 * the way that high level languages typically implement their calling
 * conventions using stack frames and such.
 * </p>
 *
 * <p>
 * However, our target is not assembly language (nor something that purports to
 * operate at that level in some platform-neutral way). Instead, our target
 * language, Level Two, is designed for representing and performing
 * optimization. With this in mind, the Level Two instruction set includes an
 * instruction that constructs a new continuation from a list of registers. A
 * corresponding instruction "explodes" a continuation into registers reserved
 * as part of the calling convention (strictly enforced). During transition from
 * caller to callee (and vice-versa), the only registers that hold usable state
 * are the "architectural" registers – those that hold the state of a
 * continuation being constructed or deconstructed. This sounds brutally
 * inefficient, but time will tell. Also, I have devised and implemented
 * mechanisms to allow deeper inlining than would normally be possible in a
 * traditional system, the explicit construction and deconstruction of
 * continuations being one such mechanism.
 * </p>
 *
 * <p>
 * Note that unlike languages like C and C++, optimizations below Level One are
 * always transparent – other than observations about performance and memory
 * use. Also note that this was a design constraint for Avail as far back as
 * 1993, after <span style="font-variant: small-caps;">Self</span>, but before
 * its technological successor Java. The way in which this is accomplished (or
 * will be more fully accomplished) in Avail is by allowing the generated level
 * two code itself to define how to maintain the "accurate fiction" of a level
 * one interpreter. If a method is inlined ten layers deep inside an outer
 * method, a non-inlined call from that inner method requires ten layers of
 * continuations to be constructed prior to the call (to accurately maintain the
 * fiction that it was always simply interpreting Level One nybblecodes). There
 * are ways to avoid or at least postpone this phase transition, but I don't
 * have any solid plans for introducing such a mechanism any time soon.
 * </p>
 *
 * <p>
 * Finally, note that the Avail control structures are defined in terms of
 * multimethod dispatch and continuation resumption. As of 2011.05.09 they are
 * also <em>implemented</em> that way, but a goal is to perform object escape
 * analysis in such a way that it deeply favors chasing continuations. If
 * successful, a continuation resumption can basically be rewritten as a jump,
 * leading to a more traditional control flow in the typical case, which should
 * be much easier to further optimize (say with SSA) than code which literally
 * passes and resumes continuations. In those cases that the continuation
 * actually escapes (say, if the continuations are used for backtracking) then
 * it can't dissolve into a simple jump – but it will still execute correctly,
 * just not as quickly.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class Interpreter
{
	/** Whether to print detailed Level One debug information. */
	public static boolean debugL1 = false;

	/** Whether to print detailed Level Two debug information. */
	public static boolean debugL2 = false;

	/** Whether to print detailed Primitive debug information. */
	public static boolean debugPrimitives = false;

	/**
	 * Whether to print detailed debug information related to compiler/lexer
	 * work unit tracking.
	 * */
	public static boolean debugWorkUnits = false;

	/**
	 * Whether to divert logging into fibers' {@link A_Fiber#debugLog()}, which
	 * is simply a length-bounded StringBuilder.  This is <em>by far</em> the
	 * fastest available way to log, although message pattern substitution is
	 * still unnecessarily slow.
	 *
	 * <p>Note that this only has an effect if one of the above debug flags is
	 * set.</p>
	 */
	public static boolean debugIntoFiberDebugLog = true;

	/**
	 * Whether to print debug information related to a specific problem being
	 * debugged with a custom VM.  This is a convenience flag and will be
	 * inaccessible in a production VM.
	 */
	public static boolean debugCustom = false;

	/** A {@linkplain Logger logger}. */
	private static final Logger mainLogger =
		Logger.getLogger(Interpreter.class.getCanonicalName());

	/** A {@linkplain Logger logger}. */
	public  static final Logger loggerDebugL1 =
		Logger.getLogger(Interpreter.class.getCanonicalName() + ".debugL1");

	/** A {@linkplain Logger logger}. */
	public static final Logger loggerDebugL2 =
		Logger.getLogger(Interpreter.class.getCanonicalName() + ".debugL2");

	/** A {@linkplain Logger logger}. */
	private static final Logger loggerDebugPrimitives =
		Logger.getLogger(
			Interpreter.class.getCanonicalName() + ".debugPrimitives");

	/**
	 * Set the current logging level for interpreters.
	 *
	 * @param level The new logging {@link Level}.
	 */
	public static void setLoggerLevel (final Level level)
	{
		mainLogger.setLevel(level);
		loggerDebugL1.setLevel(level);
		loggerDebugL2.setLevel(level);
		loggerDebugPrimitives.setLevel(level);
	}

	/**
	 * Log a message.
	 *
	 * @param level The verbosity level at which to log.
	 * @param message The message pattern to log.
	 * @param arguments The arguments to fill into the message pattern.
	 */
	public static void log (
		final Logger logger,
		final Level level,
		final String message,
		final Object... arguments)
	{
		if (logger.isLoggable(level))
		{
			final Thread thread = Thread.currentThread();
			log(
				thread instanceof AvailThread
					? ((AvailThread) thread).interpreter.fiber
					: null,
				logger,
				level,
				message,
				arguments);
		}
	}

	/**
	 * The approximate maximum number of bytes to log per fiber before throwing
	 * away the earliest 25%.
	 */
	private static final int maxFiberLogLength = 1_000_000;

	/**
	 * Log a message.
	 *
	 * @param affectedFiber The affected fiber or null.
	 * @param level The verbosity level at which to log.
	 * @param message The message pattern to log.
	 * @param arguments The arguments to fill into the message pattern.
	 */
	public static void log (
		final @Nullable A_Fiber affectedFiber,
		final Logger logger,
		final Level level,
		final String message,
		final Object... arguments)
	{
		if (logger.isLoggable(level))
		{
			final @Nullable A_Fiber runningFiber = currentFiberOrNull();
			if (debugIntoFiberDebugLog)
			{
				// Write into a StringBuilder in each fiber's debugLog().
				if (runningFiber != null)
				{
					// Log to the fiber.
					final StringBuilder log = runningFiber.debugLog();
					synchronized (log)
					{
						log.append(MessageFormat.format(message, arguments));
						log.append('\n');
						if (log.length() > maxFiberLogLength)
						{
							log.delete(0, maxFiberLogLength >> 4);
						}
					}
				}
				// Ignore the bit of logging not tied to a specific fiber.
				return;
			}
			final StringBuilder builder = new StringBuilder();
			builder.append(
				runningFiber != null
					? format("%6d ", runningFiber.uniqueId())
					: "?????? ");
			builder.append("→ ");
			builder.append(
				affectedFiber != null
					? format("%6d ", affectedFiber.uniqueId())
					: "?????? ");
			//noinspection StringConcatenationMissingWhitespace
			logger.log(level, builder + message, arguments);
		}
	}

	/**
	 * Answer the Avail interpreter associated with the {@linkplain
	 * Thread#currentThread() current thread}.  If this thread is not an {@link
	 * AvailThread}, then fail.
	 *
	 * @return The current Level Two interpreter.
	 */
	public static Interpreter current ()
	{
		return ((AvailThread) Thread.currentThread()).interpreter;
	}

	/**
	 * Answer the Avail interpreter associated with the {@linkplain
	 * Thread#currentThread() current thread}.  If this thread is not an {@link
	 * AvailThread}, then answer {@code null}.
	 *
	 * @return The current Level Two interpreter, or {@code null} if the current
	 *         thread is not an {@code AvailThread}.
	 */
	public static @Nullable Interpreter currentOrNull ()
	{
		final Thread current = Thread.currentThread();
		if (current instanceof AvailThread)
		{
			return ((AvailThread) current).interpreter;
		}
		return null;
	}

	/**
	 * Answer how many continuations would be created from Java stack frames at
	 * the current execution point (or the nearest place reification may be
	 * triggered).
	 */
	public int unreifiedCallDepth ()
	{
		return unreifiedCallDepth;
	}

	/**
	 * Add the delta to the current count of how many frames would be reified
	 * into continuations at the current execution point.
	 *
	 * @param delta How much to add.
	 */
	public void adjustUnreifiedCallDepthBy (final int delta)
	{
		if (debugL1 || debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}Depth: {1} → {2}",
				debugModeString,
				unreifiedCallDepth,
				unreifiedCallDepth + delta);
		}
		unreifiedCallDepth += delta;
	}

	/**
	 * Fake slots used to show stack traces in the Eclipse Java debugger.
	 */
	enum FakeStackTraceSlots
	implements ObjectSlotsEnum, IntegerSlotsEnum
	{
		/**
		 * The offset of the current L2 instruction.
		 */
		L2_OFFSET,

		/**
		 * The current frame's L2 instructions.
		 */
		L2_INSTRUCTIONS,

		/**
		 * The function that was being executed.
		 */
		CURRENT_FUNCTION,

		/**
		 * The chain of {@linkplain ContinuationDescriptor continuations} of the
		 * {@linkplain FiberDescriptor fiber} bound to this
		 * {@linkplain Interpreter interpreter}.
		 */
		FRAMES,

		/**
		 * The pointer register values.  These are wrapped an extra layer deep
		 * in a 1-tuple to ensure Eclipse won't spend years repeatedly producing
		 * the print representations of things you would never want to see and
		 * didn't ask for.
		 */
		POINTERS,

		/**
		 * The integer register values.
		 */
		INTEGERS,

		/** The current {@link AvailLoader}, if any. */
		LOADER
	}

	/**
	 * Utility method for decomposing this object in the debugger. See
	 * {@link AvailObjectFieldHelper} for instructions to enable this
	 * functionality in Eclipse.
	 *
	 * <p>
	 * In particular, an Interpreter should present (possible among other
	 * things) a complete stack trace of the current fiber, converting the deep
	 * continuation structure into a list of continuation substitutes that
	 * <em>do not</em> recursively print the caller chain.
	 * </p>
	 *
	 * @return An array of {@link AvailObjectFieldHelper} objects that help
	 *         describe the logical structure of the receiver to the debugger.
	 */
	public AvailObjectFieldHelper[] describeForDebugger ()
	{
		final Object[] outerArray =
			new Object[FakeStackTraceSlots.values().length];

		// Extract the current L2 offset...
		outerArray[L2_OFFSET.ordinal()] = new AvailIntegerValueHelper(offset);

		// Produce the current chunk's L2 instructions...
		outerArray[L2_INSTRUCTIONS.ordinal()] = chunk != null
			? chunk.instructions
			: emptyList();

		// Produce the current function being executed...
		outerArray[CURRENT_FUNCTION.ordinal()] = function;

		// Build the stack frames...
		@Nullable A_Continuation frame = reifiedContinuation;
		if (frame != null)
		{
			final List<A_Continuation> frames = new ArrayList<>(50);
			while (!frame.equalsNil())
			{
				frames.add(frame);
				frame = frame.caller();
			}
			outerArray[FRAMES.ordinal()] = tupleFromList(frames);
		}

		// Now collect the pointer register values.
		outerArray[POINTERS.ordinal()] = pointers;

		// May as well show the integer registers too...
		outerArray[INTEGERS.ordinal()] = integers;

		outerArray[LOADER.ordinal()] = availLoaderOrNull();

		// Now put all the top level constructs together...
		final AvailObjectFieldHelper[] helpers =
			new AvailObjectFieldHelper[FakeStackTraceSlots.values().length];
		for (final FakeStackTraceSlots field : FakeStackTraceSlots.values())
		{
			helpers[field.ordinal()] = new AvailObjectFieldHelper(
				nil,
				field,
				-1,
				outerArray[field.ordinal()]);
		}
		return helpers;
	}

	/**
	 * This {@linkplain Interpreter interpreter}'s {@linkplain AvailRuntime
	 * Avail runtime}
	 */
	private final AvailRuntime runtime;

	/**
	 * Answer the {@link AvailRuntime} permanently used by this interpreter.
	 *
	 * @return This interpreter's runtime.
	 */
	public AvailRuntime runtime ()
	{
		return runtime;
	}

	/**
	 * Capture a unique ID between 0 and the {@link #runtime()}'s {@link
	 * AvailRuntime#maxInterpreters}.
	 */
	public final int interpreterIndex;

	public String debugModeString = "";

	/**
	 * Construct a new {@code Interpreter}.
	 *
	 * @param runtime
	 *        An {@link AvailRuntime}.
	 */
	public Interpreter (final AvailRuntime runtime)
	{
		this.runtime = runtime;
		interpreterIndex = runtime.allocateInterpreterIndex();
	}

	/**
	 * The {@link AvailLoader} associated with the {@link A_Fiber fiber}
	 * currently running on this interpreter.  This is {@code null} if there is
	 * no fiber, or if it is not associated with an AvailLoader.
	 *
	 * <p>This field is a consistent cache of the AvailLoader found in the
	 * fiber, which is authoritative.  Multiple fibers may share the same
	 * AvailLoader.</p>
	 */
	private @Nullable AvailLoader availLoader;

	/**
	 * Answer the {@link AvailLoader} associated with the {@link A_Fiber fiber}
	 * currently running on this interpreter.  This interpreter must be bound
	 * to a fiber having an AvailLoader.
	 *
	 * @return The current fiber's {@link AvailLoader}.
	 */
	public AvailLoader availLoader ()
	{
		return stripNull(availLoader);
	}

	/**
	 * Answer the {@link AvailLoader} associated with the {@link A_Fiber fiber}
	 * currently running on this interpreter.  Answer {@code null} if there is
	 * no AvailLoader for the current fiber.
	 *
	 * @return The current fiber's {@link AvailLoader}.
	 */
	public @Nullable AvailLoader availLoaderOrNull ()
	{
		return availLoader;
	}

	/**
	 * The {@link FiberDescriptor} being executed by this interpreter.
	 */
	private @Nullable A_Fiber fiber;

	/**
	 * Answer the current {@link A_Fiber fiber} bound to this interpreter, or
	 * {@code null} if there is none.
	 *
	 * @return The current fiber or null.
	 */
	public @Nullable A_Fiber fiberOrNull ()
	{
		return fiber;
	}

	/**
	 * Return the current {@linkplain FiberDescriptor fiber}.
	 *
	 * @return The current executing fiber.
	 */
	public A_Fiber fiber ()
	{
		return stripNull(fiber);
	}

	/**
	 * Bind the specified {@linkplain ExecutionState#RUNNING running}
	 * {@linkplain FiberDescriptor fiber} to the {@code Interpreter}.
	 *
	 * @param newFiber
	 *        The fiber to run.
	 */
	public void fiber (final @Nullable A_Fiber newFiber, final String tempDebug)
	{
		if (debugPrimitives)
		{
			@SuppressWarnings("StringBufferReplaceableByString")
			final StringBuilder builder = new StringBuilder();
			builder
				.append("[")
				.append(interpreterIndex)
				.append("] fiber: ")
				.append(fiber == null
					? "null"
					: fiber.uniqueId() + "[" + fiber.executionState() + "]")
				.append(" -> ")
				.append(newFiber == null
					? "null"
					: newFiber.uniqueId()
						+ "[" + newFiber.executionState() + "]")
				.append(" (").append(tempDebug).append(")");
			log(
				loggerDebugPrimitives,
				Level.INFO,
				"{0}",
				builder.toString());
		}

		assert fiber == null ^ newFiber == null;
		assert newFiber == null || newFiber.executionState() == RUNNING;
		fiber = newFiber;
		reifiedContinuation = null;
		if (newFiber != null)
		{
			availLoader = newFiber.availLoader();
			final boolean readsBeforeWrites =
				newFiber.traceFlag(TRACE_VARIABLE_READS_BEFORE_WRITES);
			traceVariableReadsBeforeWrites = readsBeforeWrites;
			traceVariableWrites =
				readsBeforeWrites || newFiber.traceFlag(TRACE_VARIABLE_WRITES);
		}
		else
		{
			availLoader = null;
			traceVariableReadsBeforeWrites = false;
			traceVariableWrites = false;
		}
	}

	/**
	 * Should the {@code Interpreter} record which {@link A_Variable}s are read
	 * before written while running its current {@link A_Fiber}?
	 */
	private boolean traceVariableReadsBeforeWrites = false;

	/**
	 * Should the {@code Interpreter} record which {@link A_Variable}s are read
	 * before written while running its current {@link A_Fiber}?
	 *
	 * @return {@code true} if the interpreter should record variable accesses,
	 *         {@code false} otherwise.
	 */
	public boolean traceVariableReadsBeforeWrites ()
	{
		return traceVariableReadsBeforeWrites;
	}

	/**
	 * Set the variable trace flag.
	 *
	 * @param traceVariableReadsBeforeWrites
	 *        {@code true} if the {@code Interpreter} should record which {@link
	 *        A_Variable}s are read before written while running its current
	 *        {@link A_Fiber}, {@code false} otherwise.
	 */
	public void setTraceVariableReadsBeforeWrites (
		final boolean traceVariableReadsBeforeWrites)
	{
		if (traceVariableReadsBeforeWrites)
		{
			fiber().setTraceFlag(TRACE_VARIABLE_READS_BEFORE_WRITES);
		}
		else
		{
			fiber().clearTraceFlag(TRACE_VARIABLE_READS_BEFORE_WRITES);
		}
		this.traceVariableReadsBeforeWrites = traceVariableReadsBeforeWrites;
		this.traceVariableWrites = traceVariableReadsBeforeWrites;
	}

	/**
	 * Should the {@code Interpreter} record which {@link A_Variable}s are
	 * written while running its current {@link A_Fiber}?
	 */
	private boolean traceVariableWrites = false;

	/**
	 * Should the {@code Interpreter} record which {@link A_Variable}s are
	 * written while running its current {@link A_Fiber}?
	 *
	 * @return {@code true} if the interpreter should record variable accesses,
	 *         {@code false} otherwise.
	 */
	public boolean traceVariableWrites ()
	{
		return traceVariableWrites;
	}

	/**
	 * Set the variable trace flag.
	 *
	 * @param traceVariableWrites
	 *        {@code true} if the {@code Interpreter} should record which {@link
	 *        A_Variable}s are written while running its current {@link
	 *        A_Fiber}, {@code false} otherwise.
	 */
	public void setTraceVariableWrites (final boolean traceVariableWrites)
	{
		if (traceVariableWrites)
		{
			fiber().setTraceFlag(TRACE_VARIABLE_WRITES);
		}
		else
		{
			fiber().clearTraceFlag(TRACE_VARIABLE_WRITES);
		}
		this.traceVariableWrites = traceVariableWrites;
	}

	/**
	 * Answer the {@link A_Module} being loaded by this interpreter's loader. If
	 * there is no {@linkplain AvailLoader loader} then answer {@code nil}.
	 *
	 * @return The current loader's module under definition, or {@code nil} if
	 *         loading is not taking place via this interpreter.
	 */
	public A_Module module()
	{
		final @Nullable AvailLoader loader = fiber().availLoader();
		if (loader == null)
		{
			return nil;
		}
		return loader.module();
	}

	/**
	 * The latest result produced by a {@linkplain Result#SUCCESS successful}
	 * {@linkplain Primitive primitive}, or the latest {@linkplain
	 * AvailErrorCode error code} produced by a {@linkplain Result#FAILURE
	 * failed} primitive.
	 */
	private @Nullable AvailObject latestResult;

	/**
	 * Set the latest result due to a {@linkplain Result#SUCCESS successful}
	 * {@linkplain Primitive primitive}, or the latest {@linkplain
	 * AvailErrorCode error code} produced by a {@linkplain Result#FAILURE
	 * failed} primitive.
	 *
	 * <p>The value may be Java's {@code null} to indicate this field should be
	 * cleared, to detect accidental use.</p>
	 *
	 * @param newResult The latest result to record.
	 */
	public void latestResult (final @Nullable A_BasicObject newResult)
	{
		latestResult = (AvailObject) newResult;
		if (debugL2)
		{
			//noinspection StringConcatenationMissingWhitespace
			log(
				loggerDebugL2,
				Level.INFO,
				debugModeString + "Set latestResult: " +
					(latestResult == null
						 ? "null"
						 : latestResult.typeTag().name()));
		}
	}

	/**
	 * Answer the latest result produced by a {@linkplain Result#SUCCESS
	 * successful} {@linkplain Primitive primitive}, or the latest {@linkplain
	 * AvailErrorCode error code} produced by a
	 * {@linkplain Result#FAILURE failed} primitive.
	 *
	 * @return The latest result.
	 */
	public AvailObject latestResult ()
	{
		return stripNull(latestResult);
	}

	/**
	 * Answer the latest result produced by a {@linkplain Result#SUCCESS
	 * successful} {@linkplain Primitive primitive}, or the latest {@linkplain
	 * AvailErrorCode error code} produced by a {@linkplain Result#FAILURE
	 * failed} primitive.  Answer null if no such value is available.  This is
	 * useful for saving/restoring without knowing whether the value is valid.
	 *
	 * @return The latest result (or primitive failure value) or {@code null}.
	 */
	public @Nullable AvailObject latestResultOrNull ()
	{
		return latestResult;
	}

	/**
	 * A field that captures which {@link A_Function} is returning.  This is
	 * used for statistics collection and reporting errors when returning a
	 * value that disagrees with semantic restrictions.
	 */
	public @Nullable A_Function returningFunction;

	/**
	 * Some operations like {@link L2_INVOKE} instructions have statistics that
	 * shouldn't include the {@link L2Instruction}s executed while the invoked
	 * function is running (e.g., other L2_INVOKE instructions).  Accumulate
	 * those here.  When an L2_INVOKE completes its invocation, replace the
	 * portion representing the sub-tasks accumulated during the call with a
	 * value representing the actual elapsed time for the call, but exclude the
	 * prior value from the reported L2_INVOKE.
	 */
	public long nanosToExclude = 0L;

	/**
	 * Suspend the current fiber, evaluating the provided action.  The action is
	 * passed two additional actions, one indicating how to resume from the
	 * suspension in the future (taking the result of the primitive), and the
	 * other indicating how to cause the primitive to fail (taking an
	 * AvailErrorCode).
	 *
	 * @param args
	 *        The {@link List} of arguments to the primitive.
	 * @param skipCheck
	 *        Whether the result will need to be dynamically type-checked.
	 * @param action
	 *        The action supplied by the client that itself takes two actions
	 *        for succeeding and failing the primitive at a later time.
	 * @return The value FIBER_SUSPENDED.
	 */
	public Result suspendAndDo (
		final List<AvailObject> args,
		final boolean skipCheck,
		final Continuation2NotNull<
				Continuation1NotNull<A_BasicObject>,
				Continuation1NotNull<AvailErrorCode>>
			action)
	{
		final List<AvailObject> copiedArgs = new ArrayList<>(args);
		final AvailRuntime theRuntime = currentRuntime();
		final A_Function primitiveFunction = stripNull(function);
		final @Nullable Primitive prim = primitiveFunction.code().primitive();
		assert prim != null && prim.hasFlag(CanSuspend);
		final A_Fiber currentFiber = fiber();
		final AtomicBoolean once = new AtomicBoolean(false);
		postExitContinuation(() ->
			action.value(
				result -> {
					assert !once.getAndSet(true);
					resumeFromSuccessfulPrimitive(
						theRuntime,
						currentFiber,
						prim,
						result,
						skipCheck);
				},
				failureCode ->
				{
					assert !once.getAndSet(true);
					resumeFromFailedPrimitive(
						theRuntime,
						currentFiber,
						failureCode.numericCode(),
						primitiveFunction,
						copiedArgs,
						skipCheck);
				}));
		return primitiveSuspend(primitiveFunction);
	}

	/**
	 * Set the resulting value of a primitive invocation. Answer primitive
	 * {@linkplain Result#SUCCESS success}.
	 *
	 * @param result
	 *        The result of performing a {@linkplain Primitive primitive}.
	 * @return Primitive {@linkplain Result#SUCCESS success}.
	 */
	public Result primitiveSuccess (final A_BasicObject result)
	{
		assert fiber().executionState() == RUNNING;
		latestResult(result);
		return SUCCESS;
	}

	/**
	 * Set the resulting value of a primitive invocation to the {@linkplain
	 * AvailErrorCode#numericCode() numeric code} of the specified {@link
	 * AvailErrorCode}. Answer primitive {@linkplain Result#FAILURE failure}.
	 *
	 * @param code
	 *        An {@link AvailErrorCode}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public Result primitiveFailure (final AvailErrorCode code)
	{
		assert fiber().executionState() == RUNNING;
		latestResult(code.numericCode());
		return FAILURE;
	}

	/**
	 * Set the resulting value of a primitive invocation to the {@linkplain
	 * AvailErrorCode#numericCode() numeric code} of the {@link AvailErrorCode}
	 * embedded within the specified {@linkplain AvailException exception}.
	 * Answer primitive {@linkplain Result#FAILURE failure}.
	 *
	 * @param exception
	 *        An {@linkplain AvailException exception}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public Result primitiveFailure (final AvailException exception)
	{
		assert fiber().executionState() == RUNNING;
		latestResult(exception.numericCode());
		return FAILURE;
	}

	/**
	 * Set the resulting value of a primitive invocation to the {@linkplain
	 * AvailErrorCode#numericCode() numeric code} of the {@link AvailErrorCode}
	 * embedded within the specified {@linkplain AvailRuntimeException
	 * runtime exception}.  Answer primitive {@linkplain Result#FAILURE
	 * failure}.
	 *
	 * @param exception
	 *        A {@linkplain AvailRuntimeException runtime exception}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public Result primitiveFailure (final AvailRuntimeException exception)
	{
		assert fiber().executionState() == RUNNING;
		latestResult(exception.numericCode());
		return FAILURE;
	}

	/**
	 * Set the resulting value of a primitive invocation. Answer primitive
	 * {@linkplain Result#FAILURE failure}.
	 *
	 * @param result
	 *        The result of performing a {@linkplain Primitive primitive}.
	 * @return Primitive {@linkplain Result#FAILURE failure}.
	 */
	public Result primitiveFailure (final A_BasicObject result)
	{
		assert fiber().executionState() == RUNNING;
		latestResult(result);
		return FAILURE;
	}

	/**
	 * Should the current executing chunk return to its caller?  The value to
	 * return is in {@link #latestResult}.  If the outer interpreter loop
	 * detects this, it should resume the top reified continuation's chunk,
	 * giving it an opportunity to accept the return value and de-reify.
	 */
	public boolean returnNow = false;

	/**
	 * Should the {@linkplain Interpreter interpreter} exit its {@linkplain
	 * #run() run loop}?  This can happen when the fiber has completed, failed,
	 * or been suspended.
	 */
	public boolean exitNow = true;

	/**
	 * A {@linkplain Continuation0 continuation} to run after a {@linkplain
	 * FiberDescriptor fiber} exits and is unbound.
	 */
	private @Nullable Continuation0 postExitContinuation;

	/**
	 * Answer the {@linkplain Continuation0 continuation}, if any, to run after
	 * a {@linkplain FiberDescriptor fiber} exits and is unbound.
	 *
	 * @return A continuation, or {@code null} if no such continuation has been
	 *         established.
	 */
	public @Nullable Continuation0 postExitContinuation ()
	{
		return postExitContinuation;
	}

	/**
	 * Set the post-exit {@linkplain Continuation0 continuation}. The affected
	 * fiber will be locked around the evaluation of this continuation.
	 *
	 * @param continuation
	 *        What to do after a {@linkplain FiberDescriptor fiber} has exited
	 *        and been unbound, or {@code null} if nothing should be done.
	 */
	public void postExitContinuation (
		final @Nullable Continuation0 continuation)
	{
		assert postExitContinuation == null || continuation == null;
		postExitContinuation = continuation;
	}

	/**
	 * Suspend the current {@link A_Fiber} within a {@link Primitive}
	 * invocation.  The reified {@link A_Continuation} will be available in
	 * {@link #reifiedContinuation}, and will be installed into the current
	 * fiber.
	 *
	 * @param state
	 *        The suspension {@linkplain ExecutionState state}.
	 * @return {@link Result#FIBER_SUSPENDED}, for convenience.
	 */
	private Result primitiveSuspend (final ExecutionState state)
	{
		assert !exitNow;
		assert state.indicatesSuspension();
		assert unreifiedCallDepth() == 0;
		final A_Fiber aFiber = fiber();
		aFiber.lock(() ->
		{
			assert aFiber.executionState() == RUNNING;
			aFiber.executionState(state);
			aFiber.continuation(stripNull(reifiedContinuation));
			reifiedContinuation = null;
			final boolean bound = aFiber.getAndSetSynchronizationFlag(
				BOUND, false);
			assert bound;
			fiber(null, "primitiveSuspend");
		});
		startTick = -1L;
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.INFO,
				"{0}Set exitNow (primitiveSuspend), clear latestResult",
				debugModeString);
		}
		exitNow = true;
		latestResult(null);
		wipeRegisters();
		return FIBER_SUSPENDED;
	}

	/**
	 * {@linkplain ExecutionState#SUSPENDED Suspend} the current {@link A_Fiber}
	 * from within a {@link Primitive} invocation.  The reified {@link
	 * A_Continuation} will be available in {@link #reifiedContinuation}, and
	 * will be installed into the current fiber.
	 *
	 * @param suspendingFunction
	 *        The primitive {@link A_Function} causing the fiber suspension.
	 * @return {@link Result#FIBER_SUSPENDED}, for convenience.
	 */
	public Result primitiveSuspend (final A_Function suspendingFunction)
	{
		final Primitive prim = stripNull(suspendingFunction.code().primitive());
		assert prim.hasFlag(CanSuspend);
		fiber().suspendingFunction(suspendingFunction);
		function = null;  // Safety
		return primitiveSuspend(SUSPENDED);
	}

	/**
	 * {@linkplain ExecutionState#PARKED Park} the current {@link A_Fiber}
	 * from within a {@link Primitive} invocation.  The reified {@link
	 * A_Continuation} will be available in {@link #reifiedContinuation}, and
	 * will be installed into the current fiber.
	 *
	 * @param suspendingFunction
	 *        The primitive {@link A_Function} parking the fiber.
	 * @return {@link Result#FIBER_SUSPENDED}, for convenience.
	 */
	public Result primitivePark (final A_Function suspendingFunction)
	{
		fiber().suspendingFunction(suspendingFunction);
		return primitiveSuspend(PARKED);
	}

	/**
	 * Terminate the {@linkplain #fiber() current} {@linkplain FiberDescriptor
	 * fiber}, using the specified {@linkplain AvailObject object} as its final
	 * result.
	 *
	 * @param finalObject
	 *        The fiber's result, or {@linkplain NilDescriptor#nil nil} if
	 *        none.
	 * @param state
	 *        An {@linkplain ExecutionState execution state} that {@linkplain
	 *        ExecutionState#indicatesTermination() indicates termination}.
	 */
	private void exitFiber (
		final A_BasicObject finalObject,
		final ExecutionState state)
	{
		assert !exitNow;
		assert state.indicatesTermination();
		final A_Fiber aFiber = fiber();
		aFiber.lock(() ->
		{
			assert aFiber.executionState() == RUNNING;
			aFiber.executionState(state);
			aFiber.continuation(nil);
			aFiber.fiberResult(finalObject);
			final boolean bound = aFiber.getAndSetSynchronizationFlag(
				BOUND, false);
			assert bound;
			fiber(null, "exitFiber");
		});
		startTick = -1L;
		exitNow = true;
		if (debugL2)
		{
			//noinspection StringConcatenationMissingWhitespace
			log(
				loggerDebugL2,
				Level.INFO,
				debugModeString
					+ "Set exitNow and clear latestResult (exitFiber)");
		}
		latestResult(null);
		wipeRegisters();
		postExitContinuation(() ->
		{
			final A_Set joining = aFiber.joiningFibers().makeShared();
			aFiber.joiningFibers(nil);
			// Wake up all fibers trying to join this one.
			for (final A_Fiber joiner : joining)
			{
				joiner.lock(() ->
				{
					// Restore the permit. Resume the fiber if it was parked.
					joiner.getAndSetSynchronizationFlag(
						PERMIT_UNAVAILABLE, false);
					if (joiner.executionState() == PARKED)
					{
						// Wake it up.
						joiner.executionState(SUSPENDED);
						Interpreter.resumeFromSuccessfulPrimitive(
							currentRuntime(),
							joiner,
							P_AttemptJoinFiber.instance,
							nil,
							true);
					}
				});
			}
		});
	}

	/**
	 * {@linkplain ExecutionState#TERMINATED Terminate} the {@linkplain
	 * #fiber() current} {@linkplain FiberDescriptor fiber}, using the specified
	 * {@linkplain AvailObject object} as its final result.
	 *
	 * @param value
	 *        The fiber's result.
	 */
	public void terminateFiber (final A_BasicObject value)
	{
		exitFiber(value, TERMINATED);
	}

	/**
	 * {@linkplain ExecutionState#ABORTED Abort} the {@linkplain #fiber()
	 * current} {@linkplain FiberDescriptor fiber}.
	 */
	public void abortFiber ()
	{
		exitFiber(nil, ABORTED);
	}

	/**
	 * Invoke an Avail primitive.  The primitive is passed, and the arguments
	 * are provided in {@link #argsBuffer}.  If the primitive fails, use {@link
	 * Interpreter#primitiveFailure(A_BasicObject)} to set the primitiveResult
	 * to some object indicating what the problem was, and return
	 * primitiveFailed immediately.  If the primitive causes the continuation to
	 * change (e.g., through block invocation, continuation restart, exception
	 * throwing, etc), answer continuationChanged.  Otherwise the primitive
	 * succeeded, and we simply capture the resulting value with {@link
	 * Interpreter#primitiveSuccess(A_BasicObject)} and return {@link
	 * Result#SUCCESS}.
	 *
	 * @param primitive
	 *        The {@link Primitive} to invoke.
	 * @param skipCheck
	 *        Whether to skip checking the return result if the primitive
	 *        attempt succeeds.  It should only skip the check if the VM
	 *        guarantees the type produced at the current call site will satisfy
	 *        the expected type at the call site.  To simplify the design, the
	 *        primitive {@link A_Function}'s Avail backup code, if any, must
	 *        also satisfy the call site.  This is usually the case anyhow,
	 *        because most primitive backup Avail code produces type ⊥.
	 * @return The resulting status of the primitive attempt.
	 */
	public Result attemptPrimitive (
		final Primitive primitive,
		final boolean skipCheck)
	{
		if (debugPrimitives)
		{
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"{0}attempt {1} (and clear latestResult)",
				debugModeString,
				primitive.name());
		}
		latestResult(null);
		assert current() == this;
		final long timeBefore = AvailRuntime.captureNanos();
		final Result success =
			primitive.attempt(argsBuffer, this, skipCheck);
		final long timeAfter = AvailRuntime.captureNanos();
		primitive.addNanosecondsRunning(
			timeAfter - timeBefore, interpreterIndex);
		assert success != FAILURE || !primitive.hasFlag(CannotFail);
		if (debugPrimitives)
		{
			if (loggerDebugPrimitives.isLoggable(Level.FINER))
			{
				@Nullable AvailErrorCode errorCode = null;
				if (success == FAILURE)
				{
					if (latestResult().isInt())
					{
						final int errorInt = latestResult().extractInt();
						errorCode = byNumericCode(errorInt);
					}
				}
				final String failPart = errorCode != null
					? " (" + errorCode + ")"
					: "";
				log(
					loggerDebugPrimitives,
					Level.FINER,
					"{0}... completed primitive {1} => {2}{3}",
					debugModeString,
					primitive.getClass().getSimpleName(),
					success.name(),
					failPart);
				if (success != SUCCESS)
				{
					log(
						loggerDebugPrimitives,
						Level.FINER,
						"{0}      ({1})",
						debugModeString,
						success.name());
				}
			}
		}
		return success;
	}

	/** The (bottom) portion of the call stack that has been reified. */
	public @Nullable A_Continuation reifiedContinuation = null;

	/**
	 * The number of stack frames that reification would transform into
	 * continuations.
	 */
	private int unreifiedCallDepth = 0;

	/** The {@link A_Function} being executed. */
	public @Nullable A_Function function;

	/** The {@link L2Chunk} being executed. */
	public @Nullable L2Chunk chunk;

	/**
	 * The current zero-based L2 offset within the current L2Chunk's
	 * instructions.
	 */
	public int offset;

	/** An empty array used for clearing the pointers quickly. */
	public static final AvailObject[] emptyPointersArray = new AvailObject[0];

	/**
	 * The registers that hold {@linkplain AvailObject Avail objects}.
	 */
	public AvailObject[] pointers = emptyPointersArray;

	/**
	 * Read from an object register. Register zero is reserved for read-only
	 * use, and always contains the {@linkplain NilDescriptor#nil null
	 * object}.
	 *
	 * @param index
	 *        The object register index.
	 * @return The object in the specified register.
	 */
	public AvailObject pointerAt (final int index)
	{
		return pointers[index];
	}

	/**
	 * Write to an object register. Register zero is reserved for read-only use,
	 * and always contains the {@linkplain NilDescriptor#nil null
	 * object}.
	 *
	 * @param index
	 *        The object register index.
	 * @param anAvailObject
	 *        The object to write to the specified register.
	 */
	public void pointerAtPut (
		final int index,
		final A_BasicObject anAvailObject)
	{
		// assert index > 0;
		// assert anAvailObject != null;
		pointers[index] = (AvailObject) anAvailObject;
	}

	/**
	 * Write a Java null to an object register. Register zero is reserved for
	 * read-only use, and always contains the Avail
	 * {@linkplain NilDescriptor#nil nil}.
	 *
	 * @param index
	 *        The object register index to overwrite.
	 */
	public void clearPointerAt (final int index)
	{
		// assert index > 0;
		pointers[index] = null;
	}

	/**
	 * Restore the array of pointer registers, discarding the current array.
	 *
	 * @param replacementPointers The pointer registers to restore.
	 */
	public void restorePointers (final AvailObject[] replacementPointers)
	{
		pointers = replacementPointers;
	}

	/** An empty array used for clearing the integers quickly. */
	public static final int[] emptyIntArray = new int[0];

	/**
	 * The 32-bit signed integer registers.
	 */
	public int[] integers = emptyIntArray;

	/**
	 * Read from an integer register. The index is one-based. Entry [0] is
	 * unused.
	 *
	 * @param index
	 *        The one-based integer-register index.
	 * @return The {@code int} in the specified register.
	 */
	public int integerAt (final int index)
	{
		// assert index > 0;
		return integers[index];
	}

	/**
	 * Write to an integer register. The index is one-based. Entry [0] is
	 * unused.
	 *
	 * @param index
	 *        The one-based integer-register index.
	 * @param value
	 *        The {@code int} value to write to the register.
	 */
	public void integerAtPut (final int index, final int value)
	{
		// assert index > 0;
		integers[index] = value;
	}

	/**
	 * Wipe out the existing register set for safety.
	 */
	public void wipeRegisters ()
	{
		pointers = emptyPointersArray;
		integers = emptyIntArray;
	}

	/**
	 * Jump to a new position in the L2 instruction stream.
	 *
	 * @param newOffset
	 *        The new position in the L2 instruction stream.
	 */
	public void offset (final int newOffset)
	{
		offset = newOffset;
	}

	/**
	 * A reusable temporary buffer used to hold arguments during method
	 * invocations.
	 */
	public final List<AvailObject> argsBuffer = new ArrayList<>();

	/**
	 * An indicator that the current {@link #function} can safely skip checking
	 * the type of its result when it returns to its caller.
	 */
	public boolean skipReturnCheck;

	/**
	 * The {@link L1InstructionStepper} used to simulate execution of Level One
	 * nybblecodes.
	 */
	@SuppressWarnings("ThisEscapedInObjectConstruction")
	public final L1InstructionStepper levelOneStepper =
		new L1InstructionStepper(this);

	/**
	 * The value of the {@linkplain AvailRuntime#clock clock} when the
	 * {@linkplain #run() interpreter loop} started running.
	 */
	public long startTick = -1L;

	/**
	 * The size of a {@linkplain FiberDescriptor fiber}'s time slice, in ticks.
	 */
	private static final int timeSliceTicks = 20;

	/**
	 * Answer true if an interrupt has been requested. The interrupt may be
	 * specific to the {@linkplain #fiber() current} {@linkplain FiberDescriptor
	 * fiber} or global to the {@linkplain AvailRuntime runtime}.
	 *
	 * @return {@code true} if an interrupt is pending, {@code false} otherwise.
	 */
	public boolean isInterruptRequested ()
	{
		return runtime.levelOneSafetyRequested()
			|| runtime.clock.get() - startTick >= timeSliceTicks
			|| fiber().interruptRequestFlag(REIFICATION_REQUESTED);
	}

	/**
	 * The {@linkplain #fiber() current} {@linkplain FiberDescriptor fiber} has
	 * been asked to pause for an inter-nybblecode interrupt for some reason. It
	 * has possibly executed several more L2 instructions since that time, to
	 * place the fiber into a state that's consistent with naive Level One
	 * execution semantics. That is, a naive Level One interpreter should be
	 * able to resume the fiber later (although most of the time the Level Two
	 * interpreter will kick in).
	 *
	 * @param continuation
	 *        The reified continuation to save into the current fiber.
	 */
	public void processInterrupt (final A_Continuation continuation)
	{
		assert !exitNow;
		final A_Fiber aFiber = fiber();
		final MutableOrNull<A_Set> waiters = new MutableOrNull<>();
		aFiber.lock(() ->
		{
			synchronized (aFiber)
			{
				assert aFiber.executionState() == RUNNING;
				aFiber.executionState(INTERRUPTED);
				aFiber.continuation(continuation);
				if (aFiber.getAndClearInterruptRequestFlag(
					REIFICATION_REQUESTED))
				{
					continuation.makeShared();
					waiters.value = aFiber.getAndClearReificationWaiters();
					assert waiters.value().setSize() > 0;
				}
				final boolean bound = fiber().getAndSetSynchronizationFlag(
					BOUND, false);
				assert bound;
				fiber(null, "processInterrupt");
			}
		});
		exitNow = true;
		offset = Integer.MAX_VALUE;
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}Set exitNow (processInterrupt)",
				debugModeString);
		}
		startTick = -1L;
		latestResult(null);
		wipeRegisters();
		postExitContinuation(() ->
		{
			if (waiters.value != null)
			{
				for (final A_BasicObject pojo : waiters.value)
				{
					final Continuation1<A_Continuation> waiter =
						pojo.javaObjectNotNull();
					waiter.value(continuation);
				}
			}
			resumeFromInterrupt(aFiber);
		});
	}

	/**
	 * Raise an exception. Scan the stack of continuations (which must have been
	 * reified already) until one is found for a function whose code specifies
	 * {@linkplain P_CatchException}. Get that continuation's second argument
	 * (a handler block of one argument), and check if that handler block will
	 * accept the exceptionValue. If not, keep looking. If it will accept it,
	 * unwind the continuation stack so that the primitive catch method is the
	 * top entry, and invoke the handler block with exceptionValue. If there is
	 * no suitable handler block, fail the primitive.
	 *
	 * @param exceptionValue The exception object being raised.
	 * @return The {@linkplain Result success state}.
	 */
	public Result searchForExceptionHandler (final AvailObject exceptionValue)
	{
		// Replace the contents of the argument buffer with "exceptionValue",
		// an exception augmented with stack information.
		assert argsBuffer.size() == 1;
		argsBuffer.set(0, exceptionValue);
		final int primNum = P_CatchException.instance.primitiveNumber;
		A_Continuation continuation = stripNull(reifiedContinuation);
		int depth = 0;
		while (!continuation.equalsNil())
		{
			final A_RawFunction code = continuation.function().code();
			if (code.primitiveNumber() == primNum)
			{
				assert code.numArgs() == 3;
				final A_Variable failureVariable =
					continuation.argOrLocalOrStackAt(4);
				// Scan a currently unmarked frame.
				if (failureVariable.value().value().equalsInt(0))
				{
					final A_Tuple handlerTuple =
						continuation.argOrLocalOrStackAt(2);
					assert handlerTuple.isTuple();
					for (final A_Function handler : handlerTuple)
					{
						if (exceptionValue.isInstanceOf(
							handler.kind().argsTupleType().typeAtIndex(1)))
						{
							// Mark this frame: we don't want it to handle an
							// exception raised from within one of its handlers.
							if (debugL2)
							{
								log(
									loggerDebugPrimitives,
									Level.FINER,
									"{0}Raised (->handler) at depth {1}",
									debugModeString,
									depth);
							}
							failureVariable.value().setValueNoCheck(
								E_HANDLER_SENTINEL.numericCode());
							// Run the handler.  Since the Java stack has been
							// fully reified, simply jump into the chunk.  Note
							// that the argsBuffer was already set up with just
							// the exceptionValue.
							reifiedContinuation = continuation;
							function = handler;
							chunk = handler.code().startingChunk();
							offset = 0;  // Invocation
							wipeRegisters();
							returnNow = false;
							latestResult(null);
							return CONTINUATION_CHANGED;
						}
					}
				}
			}
			continuation = continuation.caller();
			depth++;
		}
		// If no handler was found, then return the unhandled exception.
		return primitiveFailure(exceptionValue);
	}

	/**
	 * Assume the entire stack has been reified.  Scan the stack of
	 * continuations until one is found for a function whose code specifies
	 * {@link P_CatchException}. Write the specified marker into its primitive
	 * failure variable to indicate the current exception handling state.
	 *
	 * @param marker An exception handling state marker.
	 * @return The {@link Result success state}.
	 */
	public Result markGuardVariable (
		final A_Variable guardVariable,
		final A_Number marker)
	{
		// Only allow certain state transitions.
		final int oldState = guardVariable.value().extractInt();
		if (marker.equals(E_HANDLER_SENTINEL.numericCode())
			&& oldState != 0)
		{
			return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
		}
		if (marker.equals(E_UNWIND_SENTINEL.numericCode())
			&& oldState != E_HANDLER_SENTINEL.nativeCode())
		{
			return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
		}
		// Mark this frame.  Depending on the marker, we don't want it to handle
		// exceptions or unwinds anymore.
		if (debugL2)
		{
			log(
				loggerDebugL2,
				Level.FINER,
				"{0}Marked guard var {1}",
				debugModeString,
				marker);
		}
		guardVariable.setValueNoCheck(marker);
		return primitiveSuccess(nil);
	}

	/**
	 * Assume the entire stack has been reified.  Scan the stack of
	 * continuations until one is found for a function whose code specifies
	 * {@link P_CatchException}. Write the specified marker into its primitive
	 * failure variable to indicate the current exception handling state.
	 *
	 * @param marker An exception handling state marker.
	 * @return The {@link Result success state}.
	 */
	public Result markNearestGuard (final A_Number marker)
	{
		final int primNum = P_CatchException.instance.primitiveNumber;
		A_Continuation continuation = stripNull(reifiedContinuation);
		int depth = 0;
		while (!continuation.equalsNil())
		{
			final A_RawFunction code = continuation.function().code();
			if (code.primitiveNumber() == primNum)
			{
				assert code.numArgs() == 3;
				final A_Variable failureVariable =
					continuation.argOrLocalOrStackAt(4);
				final A_Variable guardVariable = failureVariable.value();
				final int oldState = guardVariable.value().extractInt();
				// Only allow certain state transitions.
				if (marker.equals(E_HANDLER_SENTINEL.numericCode())
					&& oldState != 0)
				{
					return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
				}
				if (marker.equals(E_UNWIND_SENTINEL.numericCode())
					&& oldState != E_HANDLER_SENTINEL.nativeCode())
				{
					return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
				}
				// Mark this frame: we don't want it to handle exceptions
				// anymore.
				guardVariable.setValueNoCheck(marker);
				if (debugL2)
				{
					log(
						loggerDebugL2,
						Level.FINER,
						"{0}Marked {1} at depth {2}",
						debugModeString,
						marker,
						depth);
				}
				return primitiveSuccess(nil);
			}
			continuation = continuation.caller();
			depth++;
		}
		return primitiveFailure(E_NO_HANDLER_FRAME);
	}

	/**
	 * Prepare the {@code Interpreter} to execute the given {@link
	 * FunctionDescriptor function} with the arguments provided in {@link
	 * #argsBuffer}.  The {@link #skipReturnCheck} should also have been set,
	 * based on the call site.
	 *
	 * @param aFunction
	 *        The function to begin executing.
	 * @return Either {@code null} to indicate the function returned normally,
	 *         leaving its result in the interpreter's latestResult field, or
	 *         a {@link StackReifier} used to indicate the stack is being
	 *         unwound (and the Avail function is <em>not</em> returning).
	 */
	public @Nullable StackReifier invokeFunction (
		final A_Function aFunction)
	{
		assert !exitNow;
		function = aFunction;
		final A_RawFunction code = aFunction.code();
		assert code.numArgs() == argsBuffer.size();
		chunk = code.startingChunk();
		// Note that a chunk can only be invalidated by a method change, which
		// can only happen when all fibers are suspended (level-one safe zone),
		// so this test is entirely stable.  Also, the chunk will be
		// disconnected from the L1 code during invalidation, although
		// existing continuations will still refer to it.  Re-entry into those
		// continuations always checks for validity.
		assert chunk.isValid();
		offset = 0;
		returnNow = false;
		return runChunk();
	}

	/**
	 * Run the interpreter until it completes the fiber, is suspended, or is
	 * interrupted, perhaps by exceeding its time-slice.
	 */
	@InnerAccess void run ()
	{
		assert unreifiedCallDepth() == 0;
		assert fiber != null;
		assert !exitNow;
		nanosToExclude = 0L;
		startTick = runtime.clock.get();
		if (debugL2)
		{
			debugModeString = "Fib=" + fiber.uniqueId() + " ";
			log(
				loggerDebugPrimitives,
				Level.FINER,
				"\n{0}Run: ({1})",
				debugModeString,
				fiber.fiberName());
		}
		while (true)
		{
			// Run the chunk to completion (dealing with reification).
			// The chunk will do its own invalidation checks and off-ramp
			// to L1 if needed.
			final A_Function calledFunction = stripNull(function);
			final @Nullable StackReifier reifier = runChunk();
			assert unreifiedCallDepth() <= 1;
			returningFunction = calledFunction;
			if (reifier != null)
			{
				// Reification has been requested, and the exception has already
				// collected all the continuations.
				reifiedContinuation =
					reifier.actuallyReify()
						? reifier.assembleContinuation(
							stripNull(reifiedContinuation))
						: nil;
				chunk = null; // The postReificationAction should set this up.
				reifier.postReificationAction().value();
				if (exitNow)
				{
					// The fiber has been dealt with. Exit the interpreter loop.
					assert fiber == null;
					if (debugL2)
					{
						log(
							loggerDebugL2,
							Level.FINER,
							"{0}Exit1 run\n",
							debugModeString);
					}
					return;
				}
				if (!returnNow)
				{
					continue;
				}
				// Fall through to accomplish the return.
			}
			// We're returning from the outermost non-reified frame, either into
			// the top reified frame or right out of the fiber.
			assert returnNow;
			assert latestResult != null;
			returnNow = false;
			if (stripNull(reifiedContinuation).equalsNil())
			{
				// The reified stack is empty, too.  We must have returned from
				// the outermost frame.  The fiber runner will deal with it.
				terminateFiber(latestResult());
				exitNow = true;
				if (debugL2)
				{
					log(
						loggerDebugL2,
						Level.FINER,
						"{0}Exit2 run and set exitNow "
							+ "(fall off Interpreter.run)\n",
						debugModeString);
				}
				return;
			}
			// Resume the top reified frame.  It should be at an on-ramp that
			// expects nothing of the current registers, but is able to create
			// them and explode the current reified continuation into them
			// (popping the continuation as it does so).
			final A_Continuation frame = reifiedContinuation;
			function = frame.function();
			chunk = frame.levelTwoChunk();
			offset = frame.levelTwoOffset();
		}
	}

	/**
	 * Run the current L2Chunk to completion.  Note that a reification request
	 * may cut this short.  Also note that this interpreter indicates the offset
	 * at which to start executing.  For an initial invocation, the argsBuffer
	 * will have been set up for the call.  For a return into this continuation,
	 * the offset will refer to code that will rebuild the register set from the
	 * top reified continuation, using the {@link Interpreter#latestResult()}.
	 * For resuming the continuation, the offset will point to code that also
	 * rebuilds the register set from the top reified continuation, but it won't
	 * expect a return value.  These re-entry points should perform validity
	 * checks on the chunk, allowing an orderly off-ramp into the {@link
	 * L2Chunk#unoptimizedChunk()} (which simply interprets the L1 nybblecodes).
	 *
	 * @return {@code null} if returning normally, otherwise a {@link
	 *          StackReifier} to effect reification.
	 */
	public @Nullable StackReifier runChunk ()
	{
		adjustUnreifiedCallDepthBy(1);
		assert !exitNow;
		while (!returnNow)
		{
			final L2Instruction instruction =
				stripNull(chunk).instructions[offset++];
			if (Interpreter.debugL2)
			{
				log(
					loggerDebugL2,
					Level.FINER,
					"{0}L2 start[#{1}]: {2}",
					debugModeString,
					offset - 1,
					instruction.operation.debugNameIn(instruction));
			}

			final long nanosToExcludeBeforeStep = nanosToExclude;
			final long timeBefore = System.nanoTime();
			final @Nullable StackReifier reifier =
				instruction.action.value(this);
			// Even though some primitives may suspend the current fiber, the
			// code still returns here after suspending.  Close enough.  Also,
			// this chunk may call other chunks (on the Java stack), so we have
			// to subtract out the cost of other instructions executed during
			// this one... and count this instruction's *total* execution time
			// as something to be subtracted from any outer instructions.
			final long deltaTime = System.nanoTime() - timeBefore;
			final long exclude = nanosToExclude - nanosToExcludeBeforeStep;
			instruction.operation.statisticInNanoseconds.record(
				deltaTime - exclude, interpreterIndex);
			nanosToExclude = nanosToExcludeBeforeStep + deltaTime;
			final boolean isReifying = reifier != null;
			if (Interpreter.debugL2)
			{
				log(
					loggerDebugL2,
					Level.FINER,
					"{0}L2 end{1}: {2}",
					debugModeString,
					isReifying ? "-for-reify" : "",
					instruction.operation.debugNameIn(instruction));
			}
			if (isReifying)
			{
				adjustUnreifiedCallDepthBy(-1);
				return reifier;
			}
		}
		adjustUnreifiedCallDepthBy(-1);
		return null;
	}

	/**
	 * Throw a {@link StackReifier} to reify the Java stack into {@link
	 * A_Continuation}s, then invoke the given {@link A_Function} with no
	 * arguments.
	 *
	 * @param functionToCall
	 *        What zero-argument function to invoke after reification.
	 * @param skipReturnCheckFlag
	 *        Whether when the function completes it can skip checking the
	 *        result's type.
	 * @return The {@link StackReifier} that collects reified continuations on
	 *         the way out to {@link #run()}.
	 */
	public StackReifier reifyThenCall0 (
		final A_Function functionToCall,
		final boolean skipReturnCheckFlag)
	{
		return reifyThen(() ->
		{
			argsBuffer.clear();
			skipReturnCheck = skipReturnCheckFlag;
			function = functionToCall;
			chunk = functionToCall.code().startingChunk();
			offset = 0;
		});
	}

	/**
	 * Throw a {@link StackReifier} to reify the Java stack into {@link
	 * A_Continuation}s, then invoke the given {@link A_Function} with the given
	 * three arguments.
	 *
	 * @param functionToCall
	 *        What three-argument function to invoke after reification.
	 * @param skipReturnCheckFlag
	 *        Whether when the function completes it can skip checking the
	 *        result's type.
	 * @param arg1
	 *        The first argument of the function.
	 * @param arg2
	 *        The second argument of the function.
	 * @param arg3
	 *        The third argument of the function.
	 * @return The {@link StackReifier} that collects reified continuations on
	 *         the way out to {@link #run()}.
	 */
	public StackReifier reifyThenCall3 (
		final A_Function functionToCall,
		final boolean skipReturnCheckFlag,
		final A_BasicObject arg1,
		final A_BasicObject arg2,
		final A_BasicObject arg3)
	{
		return reifyThen(() ->
		{
			argsBuffer.clear();
			argsBuffer.add((AvailObject) arg1);
			argsBuffer.add((AvailObject) arg2);
			argsBuffer.add((AvailObject) arg3);
			skipReturnCheck = skipReturnCheckFlag;
			function = functionToCall;
			chunk = functionToCall.code().startingChunk();
			offset = 0;
		});
	}

	/**
	 * Immediately throw a {@link StackReifier}.  Various Java stack
	 * frames will catch and rethrow it, accumulating reified {@link
	 * A_Continuation}s along the way.  The outer interpreter loop should catch
	 * this, then run the provided {@link Continuation0}.
	 *
	 * @param postReificationAction
	 *        The action to perform (in the outer interpreter loop) after the
	 *        entire stack is reified.
	 * @return The {@link StackReifier} that collects reified continuations on
	 *         the way out to {@link #run()}.
	 */
	public StackReifier reifyThen (
		final Continuation0 postReificationAction)
	{
		// Note that the *current* frame isn't reified, so subtract one.
		return new StackReifier(
			true, unreifiedCallDepth() - 1, postReificationAction);
	}

	/**
	 * Immediately throw a {@link StackReifier} with its {@link
	 * StackReifier#actuallyReify} flag set to false.  This abandons the
	 * Java stack (out to {@link #run()}) before running the
	 * postReificationAction, which should set up the interpreter to continue
	 * running.
	 *
	 * @param postReificationAction
	 *        The action to perform (in the outer interpreter loop) after the
	 *        entire stack is reified.
	 * @return The {@link StackReifier} that <em>abandons</em> stack frames on
	 *         the way out to {@link #run()}.
	 */
	@SuppressWarnings("MethodMayBeStatic")
	public StackReifier abandonStackThen (
		final Continuation0 postReificationAction)
	{
		return new StackReifier(false, 0, postReificationAction);
	}

	/**
	 * Schedule the specified {@linkplain ExecutionState#indicatesSuspension()
	 * suspended} {@linkplain FiberDescriptor fiber} to execute for a while as a
	 * {@linkplain AvailRuntime#whenLevelOneUnsafeDo(int, Continuation0)} Level
	 * One-unsafe task}. If the fiber completes normally, then call its
	 * {@linkplain A_Fiber#resultContinuation() result continuation} with its
	 * final answer. If the fiber terminates abnormally, then call its
	 * {@linkplain A_Fiber#failureContinuation() failure continuation} with the
	 * terminal {@linkplain Throwable throwable}.
	 *
	 * @param runtime
	 *        An {@linkplain AvailRuntime Avail runtime}.
	 * @param aFiber
	 *        The fiber to run.
	 * @param continuation
	 *        How to set up the {@code Interpreter interpreter} prior to running
	 *        the fiber for a while. Pass in the interpreter to use.
	 */
	private static void executeFiber (
		final AvailRuntime runtime,
		final A_Fiber aFiber,
		final Continuation1NotNull<Interpreter> continuation)
	{
		assert aFiber.executionState().indicatesSuspension();
		// We cannot simply run the specified function, we must queue a task to
		// run when Level One safety is no longer required.
		runtime.whenLevelOneUnsafeDo(
			aFiber.priority(),
			AvailTask.forFiberResumption(
				aFiber,
				() ->
				{
					final Interpreter interpreter = current();
					assert aFiber == interpreter.fiberOrNull();
					assert aFiber.executionState() == RUNNING;
					continuation.value(interpreter);
					if (interpreter.exitNow)
					{
						assert stripNull(interpreter.reifiedContinuation)
							.equalsNil();
						interpreter.terminateFiber(interpreter.latestResult());
					}
					else
					{
						// Run the interpreter for a while.
						interpreter.run();
					}
					assert interpreter.fiber == null;
				}));
	}

	/**
	 * Schedule the specified {@linkplain FiberDescriptor fiber} to run the
	 * given {@linkplain FunctionDescriptor function}. This function is run as
	 * an outermost function, and must correspond to a top-level action. The
	 * fiber must be in the {@linkplain ExecutionState#UNSTARTED unstarted}
	 * state. This method is an entry point.
	 *
	 * <p>If the function successfully runs to completion, then the fiber's
	 * "on success" {@linkplain Continuation1 continuation} will be invoked with
	 * the function's result.</p>
	 *
	 * <p>If the function fails for any reason, then the fiber's "on failure"
	 * {@linkplain Continuation1 continuation} will be invoked with the
	 * terminal {@linkplain Throwable throwable}.</p>
	 *
	 * @param runtime
	 *        An {@linkplain AvailRuntime Avail runtime}.
	 * @param aFiber
	 *        The fiber to run.
	 * @param function
	 *        A {@linkplain FunctionDescriptor function} to run.
	 * @param arguments
	 *        The arguments for the function.
	 */
	public static void runOutermostFunction (
		final AvailRuntime runtime,
		final A_Fiber aFiber,
		final A_Function function,
		final List<? extends A_BasicObject> arguments)
	{
		assert aFiber.executionState() == UNSTARTED;
		aFiber.fiberNameGenerator(
			() ->
			{
				final A_RawFunction code = function.code();
				return formatString("Outermost %s @ %s:%d",
					code.methodName().asNativeString(),
					code.module().equalsNil()
						? "«vm»"
						: code.module().moduleName().asNativeString(),
					code.startingLineNumber());
			});
		executeFiber(
			runtime,
			aFiber,
			interpreter ->
			{
				assert aFiber == interpreter.fiberOrNull();
				assert aFiber.executionState() == RUNNING;
				assert aFiber.continuation().equalsNil();
				// Invoke the function. If it's a primitive and it
				// succeeds, then immediately invoke the fiber's
				// result continuation with the primitive's result.
				interpreter.exitNow = false;
				interpreter.returnNow = false;
				interpreter.reifiedContinuation = nil;
				interpreter.function = function;
				interpreter.argsBuffer.clear();
				for (final A_BasicObject arg : arguments)
				{
					interpreter.argsBuffer.add((AvailObject) arg);
				}
				interpreter.chunk = function.code().startingChunk();
				interpreter.offset = 0;
				// Always check the type of the outermost return value.
				interpreter.skipReturnCheck = false;
			});
	}

	/**
	 * Schedule resumption of the specified {@linkplain FiberDescriptor fiber}
	 * following {@linkplain ExecutionState#INTERRUPTED suspension} due to an
	 * interrupt. This method is an entry point.
	 *
	 * <p>If the function successfully runs to completion, then the fiber's
	 * "on success" {@linkplain Continuation1 continuation} will be invoked with
	 * the function's result.</p>
	 *
	 * <p>If the function fails for any reason, then the fiber's "on failure"
	 * {@linkplain Continuation1 continuation} will be invoked with the
	 * terminal {@linkplain Throwable throwable}.</p>
	 *
	 * @param aFiber The fiber to run.
	 */
	public static void resumeFromInterrupt (final A_Fiber aFiber)
	{
		assert aFiber.executionState() == INTERRUPTED;
		assert !aFiber.continuation().equalsNil();
		executeFiber(
			currentRuntime(),
			aFiber,
			interpreter ->
			{
				assert aFiber == interpreter.fiberOrNull();
				assert aFiber.executionState() == RUNNING;
				final A_Continuation con = aFiber.continuation();
				assert !con.equalsNil();
				interpreter.exitNow = false;
				interpreter.returnNow = false;
				interpreter.reifiedContinuation = con;
				interpreter.function = con.function();
				interpreter.latestResult(null);
				interpreter.chunk = con.levelTwoChunk();
				interpreter.offset = con.levelTwoOffset();
				interpreter.wipeRegisters();
				aFiber.continuation(nil);
			});
	}

	/**
	 * Schedule resumption of the specified {@linkplain FiberDescriptor fiber}
	 * following {@linkplain ExecutionState#SUSPENDED suspension} by a
	 * {@linkplain Result#SUCCESS successful} {@linkplain Primitive primitive}.
	 * This method is an entry point.
	 *  @param runtime
	 *        An {@linkplain AvailRuntime Avail runtime}.
	 * @param aFiber
	 *        The fiber to run.
	 * @param resumingPrimitive
	 *        The suspended primitive that is resuming.  This must agree with
	 *        the fiber's {@link A_Fiber#suspendingFunction}'s raw function's
	 *        primitive.
	 * @param result
	 *        The result of the primitive.
	 * @param skipReturnCheck
	 *        Whether successful completion of the primitive will always produce
	 *        something of the expected type, allowing us to elide the check of
	 */
	public static void resumeFromSuccessfulPrimitive (
		final AvailRuntime runtime,
		final A_Fiber aFiber,
		final Primitive resumingPrimitive,
		final A_BasicObject result,
		final boolean skipReturnCheck)
	{
		assert !aFiber.continuation().equalsNil();
		assert aFiber.executionState() == SUSPENDED;
		assert aFiber.suspendingFunction().code().primitive()
			== resumingPrimitive;
		executeFiber(
			runtime,
			aFiber,
			interpreter ->
			{
				assert aFiber == interpreter.fiberOrNull();
				assert aFiber.executionState() == RUNNING;

				final A_Continuation continuation = aFiber.continuation();
				interpreter.reifiedContinuation = continuation;
				interpreter.latestResult(result);
				interpreter.returningFunction = aFiber.suspendingFunction();
				interpreter.exitNow = false;
				if (continuation.equalsNil())
				{
					// Return from outer function, which was the (successful)
					// suspendable primitive itself.
					interpreter.returnNow = true;
					interpreter.function = null;
					interpreter.chunk = null;
					interpreter.offset = Integer.MAX_VALUE;
					interpreter.skipReturnCheck = skipReturnCheck;
				}
				else
				{
					interpreter.returnNow = false;
					interpreter.function = continuation.function();
					interpreter.chunk = continuation.levelTwoChunk();
					interpreter.offset = continuation.levelTwoOffset();
					interpreter.skipReturnCheck = skipReturnCheck;
					// Clear the fiber's continuation slot while it's active.
					aFiber.continuation(nil);
				}
			});
	}

	/**
	 * Schedule resumption of the specified {@linkplain FiberDescriptor fiber}
	 * following {@linkplain ExecutionState#SUSPENDED suspension} by a
	 * {@linkplain Result#FAILURE failed} {@linkplain Primitive primitive}. This
	 * method is an entry point.
	 *
	 * @param runtime
	 *        An {@linkplain AvailRuntime Avail runtime}.
	 * @param aFiber
	 *        The fiber to run.
	 * @param failureValue
	 *        The failure value produced by the failed primitive attempt.
	 * @param failureFunction
	 *        The primitive failure {@linkplain FunctionDescriptor function}.
	 * @param args
	 *        The arguments to the primitive.
	 * @param skipReturnCheck
	 *        Whether this failed primitive's backup Avail code will always
	 *        produce something of the expected type, allowing us to elide the
	 *        return check when the non-primitive part of this function
	 *        eventually completes.
	 */
	public static void resumeFromFailedPrimitive (
		final AvailRuntime runtime,
		final A_Fiber aFiber,
		final A_BasicObject failureValue,
		final A_Function failureFunction,
		final List<AvailObject> args,
		final boolean skipReturnCheck)
	{
		assert !aFiber.continuation().equalsNil();
		assert aFiber.executionState() == SUSPENDED;
		assert aFiber.suspendingFunction().equals(failureFunction);

		executeFiber(
			runtime,
			aFiber,
			interpreter ->
			{
				final A_RawFunction code = failureFunction.code();
				final @Nullable Primitive prim = code.primitive();
				assert prim != null;
				assert !prim.hasFlag(CannotFail);
				assert prim.hasFlag(CanSuspend);
				assert args.size() == code.numArgs();
				assert interpreter.reifiedContinuation == null;
				interpreter.reifiedContinuation = aFiber.continuation();
				aFiber.continuation(nil);
				interpreter.function = failureFunction;
				interpreter.argsBuffer.clear();
				interpreter.argsBuffer.addAll(args);
				interpreter.latestResult(failureValue);
				interpreter.skipReturnCheck = skipReturnCheck;
				final L2Chunk chunk = code.startingChunk();
				interpreter.chunk = chunk;
				interpreter.offset = chunk.offsetAfterInitialTryPrimitive();
				interpreter.exitNow = false;
				interpreter.returnNow = false;
			});
	}

	/**
	 * Check that the result is an instance of the expected type.  If it is,
	 * return.  If not, invoke the resultDisagreedWithExpectedTypeFunction.
	 * Also accumulate statistics related to the return type check.  The {@link
	 * Interpreter#returningFunction} must have been set by the client.
	 *
	 * @param result
	 *        The value that was just returned.
	 * @param expectedReturnType
	 *        The expected type to check the value against.
	 * @param returnee
	 *        The {@link A_Function} that we're returning into.
	 * @return A {@link StackReifier} if reification is needed, otherwise {@code
	 *         null}.
	 */
	public @Nullable StackReifier checkReturnType (
		final AvailObject result,
		final A_Type expectedReturnType,
		final A_Function returnee)
	{
		final long before = AvailRuntime.captureNanos();
		final boolean checkOk = result.isInstanceOf(expectedReturnType);
		final long after = AvailRuntime.captureNanos();
		final A_Function returner = stripNull(returningFunction);
		final @Nullable Primitive calledPrimitive = returner.code().primitive();
		if (calledPrimitive != null)
		{
			calledPrimitive.addNanosecondsCheckingResultType(
				after - before, interpreterIndex);
		}
		else
		{
			returner.code().returnerCheckStat().record(
				after - before, interpreterIndex);
			returnee.code().returneeCheckStat().record(
				after - before, interpreterIndex);
		}
		if (!checkOk)
		{
			final A_Variable reportedResult =
				newVariableWithContentType(Types.ANY.o());
			reportedResult.setValueNoCheck(result);
			argsBuffer.clear();
			argsBuffer.add((AvailObject) returner);
			argsBuffer.add((AvailObject) expectedReturnType);
			argsBuffer.add((AvailObject) reportedResult);
			final @Nullable StackReifier reifier =
				invokeFunction(
					runtime.resultDisagreedWithExpectedTypeFunction());
			// The function has to be bottom-valued, so it can't ever actually
			// return.  However, it's reifiable.  Note that the original callee
			// is not part of the stack.  No point, since it was returning and
			// is probably mostly evacuated.
			assert reifier != null;
			return reifier;
		}
		// Check was ok.
		return null;
	}

	/**
	 * Stringify an {@linkplain AvailObject Avail value}, using the
	 * {@linkplain AvailRuntime#stringificationFunction() stringification
	 * function} associated with the specified {@linkplain AvailRuntime
	 * runtime}. Stringification will run in a new {@linkplain FiberDescriptor
	 * fiber}. If stringification fails for any reason, then the built-in
	 * mechanism, available via {@link AvailObject#toString()} will be used.
	 * Invoke the specified continuation with the result.
	 *
	 * @param runtime
	 *        An Avail runtime.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for {@linkplain
	 *        A_Fiber fibers} started due to stringification. This need not be
	 *        the {@linkplain AvailRuntime#textInterface() default text
	 *        interface}.
	 * @param value
	 *        An Avail value.
	 * @param continuation
	 *        What to do with the stringification of {@code value}.
	 */
	public static void stringifyThen (
		final AvailRuntime runtime,
		final TextInterface textInterface,
		final A_BasicObject value,
		final Continuation1NotNull<String> continuation)
	{
		final @Nullable A_Function stringifierFunction =
			runtime.stringificationFunction();
		// If the stringifier function is not defined, then use the basic
		// mechanism for stringification.
		if (stringifierFunction == null)
		{
			continuation.value(format(
				"(stringifier undefined) %s",
				value.toString()));
			return;
		}
		// Create the fiber that will execute the function.
		final A_Fiber fiber = newFiber(
			stringType(),
			stringificationPriority,
			() -> stringFrom("Stringification"));
		fiber.textInterface(textInterface);
		fiber.resultContinuation(
			string -> continuation.value(string.asNativeString()));
		fiber.failureContinuation(
			e -> continuation.value(format(
				"(stringification failed [%s]) %s",
				e.getClass().getSimpleName(),
				value.toString())));
		// Stringify!
		Interpreter.runOutermostFunction(
			runtime,
			fiber,
			stringifierFunction,
			Collections.singletonList(value));
	}

	/**
	 * Stringify a {@linkplain List list} of {@linkplain AvailObject Avail
	 * values}, using the {@linkplain AvailRuntime#stringificationFunction()
	 * stringification function} associated with the specified {@linkplain
	 * AvailRuntime runtime}. Stringification will run in parallel, with each
	 * value being processed by its own new {@linkplain FiberDescriptor fiber}.
	 * If stringification fails for a value for any reason, then the built-in
	 * mechanism, available via {@link AvailObject#toString()} will be used for
	 * that value. Invoke the specified continuation with the resulting list,
	 * preserving the original order.
	 *
	 * @param runtime
	 *        An Avail runtime.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for {@linkplain
	 *        A_Fiber fibers} started due to stringification. This need not be
	 *        the {@linkplain AvailRuntime#textInterface() default text
	 *        interface}.
	 * @param values
	 *        Some Avail values.
	 * @param continuation
	 *        What to do with the resulting list.
	 */
	public static void stringifyThen (
		final AvailRuntime runtime,
		final TextInterface textInterface,
		final List<? extends A_BasicObject> values,
		final Continuation1NotNull<List<String>> continuation)
	{
		final int valuesCount = values.size();
		if (valuesCount == 0)
		{
			continuation.value(emptyList());
			return;
		}
		// Deduplicate the list of values for performance…
		final Map<A_BasicObject, List<Integer>> map =
			new HashMap<>(valuesCount);
		for (int i = 0; i < values.size(); i++)
		{
			final A_BasicObject value = values.get(i);
			final List<Integer> indices = map.computeIfAbsent(
				value,
				k -> new ArrayList<>());
			indices.add(i);
		}
		final AtomicInteger outstanding = new AtomicInteger(map.size());
		final String[] strings = new String[valuesCount];
		for (final Entry<A_BasicObject, List<Integer>> entry
			: map.entrySet())
		{
			final List<Integer> indicesToWrite = entry.getValue();
			stringifyThen(
				runtime,
				textInterface,
				entry.getKey(),
				arg ->
				{
					for (final int indexToWrite : indicesToWrite)
					{
						strings[indexToWrite] = arg;
					}
					if (outstanding.decrementAndGet() == 0)
					{
						final List<String> stringList = asList(strings);
						continuation.value(stringList);
					}
				});
		}
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(getClass().getSimpleName());
		builder.append(" #");
		builder.append(interpreterIndex);
		if (fiber == null)
		{
			builder.append(" [«unbound»]");
		}
		else
		{
			builder.append(formatString(" [%s]", fiber.fiberName()));
			if (reifiedContinuation == null)
			{
				builder.append(formatString("%n\t«null stack»"));
			}
			else if (reifiedContinuation.equalsNil())
			{
				builder.append(formatString("%n\t«empty call stack»"));
			}
			builder.append("\n\n");
		}
		return builder.toString();
	}

	/**
	 * {@link Statistic} measuring the performance of dynamic lookups, keyed by
	 * the number of definitions in the method being looked up.  Tho name of the
	 * statistic includes this count, as well as the name of the first bundle
	 * encountered which had that count.
	 */
	@GuardedBy("dynamicLookupStatsLock")
	private static final Map<Integer, Statistic>
		dynamicLookupStatsByCount = new HashMap<>();

	/**
	 * The lock that protects access to {@link #dynamicLookupStatsByCount}.
	 */
	private static final ReadWriteLock dynamicLookupStatsLock =
		new ReentrantReadWriteLock();

	/**
	 * A <em>non-static</em> field of this interpreter that holds a mapping from
	 * the number of definitions considered in a lookup, to a {@link
	 * PerInterpreterStatistic} specific to this interpreter.  This is accessed
	 * without a lock, and only by the thread accessing this interpreter (other
	 * than to view the momentary statistics).
	 *
	 * <p>If the desired key is not found, acquire the {@link
	 * #dynamicLookupStatsLock} with read access, extracting the {@link
	 * PerInterpreterStatistic} from the {@link Statistic} in {@link
	 * #dynamicLookupStatsByCount}.  If the key was not present, release the
	 * lock, acquire it for write access, try looking it up again (in case the
	 * map changed while the lock wasn't held), and if necessary add a new entry
	 * for that size, including the bundle name as an example in the name of the
	 * statistic.</p>
	 */
	private final Map<Integer, PerInterpreterStatistic>
		dynamicLookupPerInterpreterStat = new HashMap<>();

	/**
	 * Record the fact that a lookup in the specified {@link
	 * MessageBundleDescriptor message bundle} has just taken place, and that it
	 * took the given time in nanoseconds.
	 *
	 * <p>At the moment, we only record the duration of the lookup, and we do so
	 * under a statistic tied to the number of definitions in the bundle's
	 * method.  We do, however, record the name of the first looked up bundle
	 * having that number of definitions.</p>
	 *
	 * <p>Before 2017.12, we used to do this:</p>
	 *
	 * <p>Multiple runs will create distinct message bundles, but we'd like them
	 * aggregated.  Therefore we store each statistic not only under the bundle
	 * but under the bundle's message's print representation.  We first look for
	 * an exact match by bundle, then fall back by the slower string search,
	 * making the same statistic available under the new bundle for the next
	 * time it occurs.</p>
	 *
	 * @param bundle A message bundle in which a lookup has just taken place.
	 * @param nanos A {@code double} indicating how many nanoseconds it took.
	 */
	public void recordDynamicLookup (
		final A_Bundle bundle,
		final double nanos)
	{
		final int size = bundle.bundleMethod().definitionsTuple().tupleSize();
		@Nullable PerInterpreterStatistic perInterpreterStat =
			dynamicLookupPerInterpreterStat.get(size);
		if (perInterpreterStat == null)
		{
			// See if we can find it in the global map.
			dynamicLookupStatsLock.readLock().lock();
			@Nullable Statistic globalStat;
			try
			{
				globalStat = dynamicLookupStatsByCount.get(size);
			}
			finally
			{
				dynamicLookupStatsLock.readLock().unlock();
			}

			if (globalStat == null)
			{
				// It didn't exist when we looked for it while holding the read
				// lock.  Having released the read lock, grab the write lock,
				// double-check for the element, then if necessary create it.
				dynamicLookupStatsLock.writeLock().lock();
				try
				{
					globalStat = dynamicLookupStatsByCount.get(size);
					if (globalStat == null)
					{
						// Create it.
						globalStat = new Statistic(
							"Dynamic lookup time for size "
								+ size
								+ " (example: "
								+ bundle.message().atomName()
								+ ")",
							StatisticReport.DYNAMIC_LOOKUP_TIME);
						dynamicLookupStatsByCount.put(size, globalStat);
					}
				}
				finally
				{
					dynamicLookupStatsLock.writeLock().unlock();
				}
			}
			perInterpreterStat =
				stripNull(globalStat).statistics[interpreterIndex];
			dynamicLookupPerInterpreterStat.put(size, perInterpreterStat);
		}
		perInterpreterStat.record(nanos);
	}

	/**
	 * Top-level statement evaluation statistics, keyed by module.
	 */
	private static final Map<A_Module, Statistic>
		topStatementEvaluationStats = new WeakHashMap<>();

	/**
	 * Record the fact that a statement starting at the given line number in the
	 * given module just took some number of nanoseconds to run.
	 *
	 * <p>As of 2017.12, we no longer record a separate statistic for each
	 * top-level statement, so the line number is ignored.</p>
	 *
	 * @param sample The number of nanoseconds.
	 * @param module The module containing the top-level statement that ran.
	 * @param lineNumber The line number of the statement that ran.  Ignored.
	 */
	public void recordTopStatementEvaluation (
		final double sample,
		final A_Module module,
		final int lineNumber)
	{
		final Statistic statistic;
		//noinspection SynchronizationOnStaticField
		synchronized (topStatementEvaluationStats)
		{
			final A_Module moduleTraversed = module.traversed();
			statistic = topStatementEvaluationStats.computeIfAbsent(
				moduleTraversed,
				mod -> new Statistic(
					mod.moduleName().asNativeString(),
					StatisticReport.TOP_LEVEL_STATEMENTS));
		}
		statistic.record(sample, interpreterIndex);
	}

	/**
	 * The bootstrapped {@linkplain P_SetValue assignment function} used to
	 * restart implicitly observed assignments.
	 */
	private static final A_Function assignmentFunction =
		newPrimitiveFunction(instance, nil, 0);

	/**
	 * Answer the bootstrapped {@linkplain P_SetValue assignment function}
	 * used to restart implicitly observed assignments.
	 *
	 * @return The assignment function.
	 */
	public static A_Function assignmentFunction ()
	{
		return assignmentFunction;
	}
}
