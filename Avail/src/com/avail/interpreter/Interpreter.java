/**
 * Interpreter.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.FiberDescriptor.ExecutionState.*;
import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag.*;
import static com.avail.descriptor.FiberDescriptor.InterruptRequestFlag.*;
import static com.avail.descriptor.FiberDescriptor.TraceFlag.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static java.lang.Math.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.AvailThread;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.*;
import com.avail.exceptions.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L1InstructionStepper;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.interpreter.primitive.*;
import com.avail.io.TextInterface;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import com.avail.utility.evaluation.*;
import com.avail.utility.*;

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
 * {@linkplain Interpreter interpreter} has an arbitrarily large bank of
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
	/**
	 * Answer the {@linkplain Interpreter Level Two interpreter} associated
	 * with {@linkplain Thread#currentThread() this} {@linkplain Thread thread}.
	 * If this thread is not an {@link AvailThread}, then fail.
	 *
	 * @return The current Level Two interpreter.
	 */
	public static Interpreter current ()
	{
		return ((AvailThread) Thread.currentThread()).interpreter;
	}

	/**
	 * Answer the {@linkplain Interpreter Level Two interpreter} associated
	 * with {@linkplain Thread#currentThread() this} {@linkplain Thread thread}.
	 * If this thread is not an {@link AvailThread}, then answer {@code null}.
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

	/** Whether to print detailed Level One debug information. */
	public static boolean debugL1 = false;

	/** Whether to print detailed Level Two debug information. */
	public static boolean debugL2 = false;

	/** Whether to print detailed Primitive debug information. */
	public static boolean debugPrimitives = false;

	/**
	 * Whether to print debug information related to a specific problem being
	 * debugged with a custom VM.  This is a convenience flag and will be
	 * inaccessible in a production VM.
	 */
	public static boolean debugCustom = false;

	/** A {@linkplain Logger logger}. */
	private static final Logger logger =
		Logger.getLogger(Interpreter.class.getCanonicalName());

	/**
	 * Set the current logging level for interpreters.
	 *
	 * @param level The new logging {@link Level}.
	 */
	public static void setLoggerLevel (final Level level)
	{
		logger.setLevel(level);
	}

	/**
	 * Log a message.
	 *
	 * @param level The verbosity level at which to log.
	 * @param message The message pattern to log.
	 * @param arguments The arguments to fill into the message pattern.
	 */
	public static void log (
		final Level level,
		final String message,
		final Object... arguments)
	{
		if (logger.isLoggable(level))
		{
			final Thread thread = Thread.currentThread();
			log(
				thread instanceof AvailThread
					? ((AvailThread)thread).interpreter.fiber
					: null,
				level,
				message,
				arguments);
		}
	}

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
		final Level level,
		final String message,
		final Object... arguments)
	{
		if (logger.isLoggable(level))
		{
			final @Nullable A_Fiber runningFiber =
				FiberDescriptor.currentOrNull();
			final StringBuilder builder = new StringBuilder();
			builder.append(
				runningFiber != null
					? String.format(
						"%6d ",
						runningFiber.traversed().slot(
							FiberDescriptor.IntegerSlots.DEBUG_UNIQUE_ID))
					: "?????? ");
			builder.append('→');
			builder.append(
				affectedFiber != null
					? String.format(
						"%6d ",
						affectedFiber.traversed().slot(
							FiberDescriptor.IntegerSlots.DEBUG_UNIQUE_ID))
					: "?????? ");
			logger.log(level, builder.toString() + message, arguments);
		}
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
		INTEGERS
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
		outerArray[FakeStackTraceSlots.L2_OFFSET.ordinal()] =
			new AvailIntegerValueHelper(offset());

		// Produce the current chunk's L2 instructions...
		outerArray[FakeStackTraceSlots.L2_INSTRUCTIONS.ordinal()] =
			chunk().instructions;

		// Produce the current chunk's L2 instructions...
		outerArray[FakeStackTraceSlots.CURRENT_FUNCTION.ordinal()] =
			pointers[FUNCTION.ordinal()];

		// Build the stack frames...
		final List<A_Continuation> frames = new ArrayList<>(50);
		A_Continuation frame = pointers[CALLER.ordinal()];
		if (frame != null)
		{
			while (!frame.equalsNil())
			{
				frames.add(frame);
				frame = frame.caller();
			}
		}
		outerArray[FakeStackTraceSlots.FRAMES.ordinal()] =
			TupleDescriptor.fromList(frames);

		// Now collect the pointer register values, wrapped in 1-tuples to
		// suppress printing by the Eclipse debugger...
		outerArray[FakeStackTraceSlots.POINTERS.ordinal()] =
			TupleDescriptor.fromList(
				Arrays.asList(pointers).subList(0, chunk().numObjects()));

		// May as well show the integer registers too...
		final int numInts = chunk().numIntegers();
		final List<Integer> integersList = new ArrayList<>(numInts);
		for (int i = 0; i < numInts; i++)
		{
			integersList.add(integers[i]);
		}
		outerArray[FakeStackTraceSlots.INTEGERS.ordinal()] =
			TupleDescriptor.fromIntegerList(integersList);

		// Now put all the top level constructs together...
		final AvailObjectFieldHelper[] helpers =
			new AvailObjectFieldHelper[FakeStackTraceSlots.values().length];
		for (final FakeStackTraceSlots field : FakeStackTraceSlots.values())
		{
			helpers[field.ordinal()] = new AvailObjectFieldHelper(
				NilDescriptor.nil(),
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

	/**
	 * Construct a new {@link Interpreter}.
	 *
	 * @param runtime
	 *        An {@link AvailRuntime}.
	 */
	public Interpreter (final AvailRuntime runtime)
	{
		this.runtime = runtime;
		interpreterIndex = runtime.allocateInterpreterIndex();
		pointers[NULL.ordinal()] = NilDescriptor.nil();
	}

	/**
	 * The {@link FiberDescriptor} being executed by this interpreter.
	 */
	public @Nullable A_Fiber fiber;

	/**
	 * Return the current {@linkplain FiberDescriptor fiber}.
	 *
	 * @return The current executing fiber.
	 */
	public A_Fiber fiber ()
	{
		final A_Fiber f = fiber;
		assert f != null;
		return f;
	}

	/**
	 * Bind the specified {@linkplain ExecutionState#RUNNING running}
	 * {@linkplain FiberDescriptor fiber} to the {@linkplain Interpreter
	 * Interpreter}.
	 *
	 * @param newFiber
	 *        The fiber to run.
	 */
	public void fiber (final @Nullable A_Fiber newFiber)
	{
		assert fiber == null ^ newFiber == null;
		assert newFiber == null || newFiber.executionState() == RUNNING;
		fiber = newFiber;
		if (newFiber != null)
		{
			final boolean readsBeforeWrites =
				newFiber.traceFlag(TRACE_VARIABLE_READS_BEFORE_WRITES);
			if (readsBeforeWrites)
			{
				traceVariableReadsBeforeWrites = readsBeforeWrites;
				traceVariableWrites = true;
			}
			else
			{
				traceVariableReadsBeforeWrites = false;
				traceVariableWrites = newFiber.traceFlag(TRACE_VARIABLE_WRITES);
			}
		}
		else
		{
			traceVariableReadsBeforeWrites = false;
			traceVariableWrites = false;
		}
	}

	/**
	 * Should the {@linkplain Interpreter interpreter} record which
	 * {@linkplain VariableDescriptor variables} are read before written while
	 * running its current {@linkplain FiberDescriptor fiber}?
	 */
	private boolean traceVariableReadsBeforeWrites = false;

	/**
	 * Should the {@linkplain Interpreter interpreter} record which
	 * {@linkplain VariableDescriptor variables} are read before written while
	 * running its current {@linkplain FiberDescriptor fiber}?
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
	 *        {@code true} if the {@linkplain Interpreter interpreter} should
	 *        record which {@linkplain VariableDescriptor variables} are
	 *        read before written while running its current {@linkplain
	 *        FiberDescriptor fiber}, {@code false} otherwise.
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
	 * Should the {@linkplain Interpreter interpreter} record which
	 * {@linkplain VariableDescriptor variables} are written while running its
	 * current {@linkplain FiberDescriptor fiber}?
	 */
	private boolean traceVariableWrites = false;

	/**
	 * Should the {@linkplain Interpreter interpreter} record which
	 * {@linkplain VariableDescriptor variables} are written while running its
	 * current {@linkplain FiberDescriptor fiber}?
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
	 *        {@code true} if the {@linkplain Interpreter interpreter} should
	 *        record which {@linkplain VariableDescriptor variables} are
	 *        written while running its current {@linkplain FiberDescriptor
	 *        fiber}, {@code false} otherwise.
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
	 * Answer the {@linkplain ModuleDescriptor module} being loaded by this
	 * interpreter's loader. If there is no {@linkplain AvailLoader loader} then
	 * answer {@link NilDescriptor#nil() nil}.
	 *
	 * @return The current loader's module under definition, or {@code nil} if
	 *         loading is not taking via in this interpreter.
	 */
	public A_Module module()
	{
		final AvailLoader loader = fiber().availLoader();
		if (loader == null)
		{
			return NilDescriptor.nil();
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
	 * failed} primitive.  The value must not be Java's {@code null}.
	 *
	 * @param newResult The latest result to record.
	 */
	public void latestResult (final A_BasicObject newResult)
	{
		assert newResult != null;
		latestResult = (AvailObject)newResult;
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
		final AvailObject result = latestResult;
		assert result != null;
		return result;
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
		assert result != null;
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
		assert latestResult != null;
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
		assert latestResult != null;
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
		assert latestResult != null;
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
		assert result != null;
		latestResult(result);
		return FAILURE;
	}

	/**
	 * Should the {@linkplain Interpreter interpreter} exit its {@linkplain
	 * #run() run loop}?
	 */
	@InnerAccess boolean exitNow = true;

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
	 * Suspend the {@linkplain #fiber() current} {@linkplain FiberDescriptor
	 * fiber} from a {@linkplain Primitive primitive} invocation. The reified
	 * {@linkplain ContinuationDescriptor continuation} must be available in the
	 * architectural {@linkplain FixedRegister#CALLER caller register}, and will
	 * be installed into the current fiber.
	 *
	 * @param state
	 *        The suspension {@linkplain ExecutionState state}.
	 * @return {@link Result#FIBER_SUSPENDED}, for convenience.
	 */
	private Result primitiveSuspend (final ExecutionState state)
	{
		assert !exitNow;
		assert state.indicatesSuspension();
		final A_Fiber aFiber = fiber();
		aFiber.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				assert aFiber.executionState() == RUNNING;
				aFiber.executionState(state);
				aFiber.continuation(pointerAt(CALLER));
				final boolean bound = aFiber.getAndSetSynchronizationFlag(
					BOUND, false);
				assert bound;
				fiber = null;
			}
		});
		exitNow = true;
		startTick = -1L;
		latestResult = null;
		wipeObjectRegisters();
		return FIBER_SUSPENDED;
	}

	/**
	 * {@linkplain ExecutionState#SUSPENDED Suspend} the {@linkplain #fiber()
	 * current} {@linkplain FiberDescriptor fiber} from a {@linkplain Primitive
	 * primitive} invocation. The reified {@linkplain ContinuationDescriptor
	 * continuation} must be available in the architectural {@linkplain
	 * FixedRegister#CALLER caller register}, and will be installed into the
	 * current fiber.
	 *
	 * @return {@link Result#FIBER_SUSPENDED}, for convenience.
	 */
	public Result primitiveSuspend ()
	{
		return primitiveSuspend(SUSPENDED);
	}

	/**
	 * {@linkplain ExecutionState#PARKED Park} the {@linkplain #fiber()
	 * current} {@linkplain FiberDescriptor fiber} from a {@linkplain Primitive
	 * primitive} invocation. The reified {@linkplain ContinuationDescriptor
	 * continuation} must be available in the architectural {@linkplain
	 * FixedRegister#CALLER caller register}, and will be installed into the
	 * current fiber.
	 *
	 * @return {@link Result#FIBER_SUSPENDED}, for convenience.
	 */
	public Result primitivePark ()
	{
		return primitiveSuspend(PARKED);
	}

	/**
	 * A place to store the primitive {@linkplain CompiledCodeDescriptor
	 * compiled code} being attempted.  That allows {@linkplain
	 * P_340_PushConstant} to get to the first literal in order to return it
	 * from the primitive.
	 */
	private @Nullable A_Function primitiveFunctionBeingAttempted;

	/**
	 * Answer the {@linkplain Primitive primitive} {@linkplain
	 * FunctionDescriptor function} that is currently being attempted.
	 *
	 * @return The requested function.
	 */
	public A_Function primitiveFunctionBeingAttempted ()
	{
		final A_Function function = primitiveFunctionBeingAttempted;
		assert function != null;
		return function;
	}

	/**
	 * Terminate the {@linkplain #fiber() current} {@linkplain FiberDescriptor
	 * fiber}, using the specified {@linkplain AvailObject object} as its final
	 * result.
	 *
	 * @param finalObject
	 *        The fiber's result, or {@linkplain NilDescriptor#nil() nil} if
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
		assert finalObject != null;
		assert state.indicatesTermination();
		final A_Fiber aFiber = fiber();
		aFiber.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				assert aFiber.executionState() == RUNNING;
				aFiber.executionState(state);
				aFiber.continuation(NilDescriptor.nil());
				aFiber.fiberResult(finalObject);
				final boolean bound = aFiber.getAndSetSynchronizationFlag(
					BOUND, false);
				assert bound;
				fiber = null;
			}
		});
		exitNow = true;
		startTick = -1L;
		latestResult = null;
		wipeObjectRegisters();
		postExitContinuation(new Continuation0()
		{
			@Override
			public void value ()
			{
				final A_Set joining = aFiber.joiningFibers().makeShared();
				aFiber.joiningFibers(NilDescriptor.nil());
				// Wake up all fibers trying to join this one.
				for (final A_Fiber joiner : joining)
				{
					joiner.lock(new Continuation0()
					{
						@Override
						public void value ()
						{
							// Restore the permit. Resume the fiber if it was
							// parked.
							joiner.getAndSetSynchronizationFlag(
								PERMIT_UNAVAILABLE, false);
							if (joiner.executionState() == PARKED)
							{
								// Wake it up.
								joiner.executionState(SUSPENDED);
								Interpreter.resumeFromSuccessfulPrimitive(
									AvailRuntime.current(),
									joiner,
									NilDescriptor.nil(),
									true);
							}
						}
					});
				}
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
		exitFiber(NilDescriptor.nil(), ABORTED);
	}

	/**
	 * Invoke an Avail primitive.  The primitive number and arguments are
	 * passed.  If the primitive fails, use {@link
	 * Interpreter#primitiveSuccess(A_BasicObject)} to set the primitiveResult
	 * to some object indicating what the problem was, and return
	 * primitiveFailed immediately.  If the primitive causes the continuation to
	 * change (e.g., through block invocation, continuation restart, exception
	 * throwing, etc), answer continuationChanged.  Otherwise the primitive
	 * succeeded, and we simply capture the resulting value with {@link
	 * Interpreter#primitiveSuccess(A_BasicObject)} and return {@link
	 * Result#SUCCESS}.
	 *
	 * @param primitiveNumber
	 *            The number of the primitive to invoke.
	 * @param function
	 *            The function whose primitive is being attempted.
	 * @param args
	 *            The list of arguments to supply to the primitive.
	 * @param skipReturnCheck
	 *            Whether to skip checking the return result if the primitive
	 *            attempt succeeds.  It should only skip the check if the VM
	 *            guarantees the type produced at the current call site will
	 *            satisfy the expected type at the call site.  To simplify the
	 *            design, the primitive {@linkplain FunctionDescriptor
	 *            function}'s Avail backup code, if any, must also satisfy the
	 *            call site.  This is usually the case anyhow, because most
	 *            primitive backup Avail code produces type ⊥.
	 * @return The resulting status of the primitive attempt.
	 */
	public final Result attemptPrimitive (
		final int primitiveNumber,
		final @Nullable A_Function function,
		final List<AvailObject> args,
		final boolean skipReturnCheck)
	{
		final Primitive primitive =
			Primitive.byPrimitiveNumberOrFail(primitiveNumber);
		if (debugPrimitives)
		{
			log(
				Level.FINER,
				"attempt {0}",
				primitive.name());
		}
		if (FiberDescriptor.debugFibers)
		{
			fiber().recordLatestPrimitive((short)primitiveNumber);
		}
		latestResult = null;
		primitiveFunctionBeingAttempted = function;
		assert current() == this;
		final long timeBefore = System.nanoTime();
		final Result success = primitive.attempt(args, this, skipReturnCheck);
		final long timeAfter = System.nanoTime();
		primitive.addNanosecondsRunning(
			timeAfter - timeBefore,
			interpreterIndex);
		assert success != FAILURE || !primitive.hasFlag(Flag.CannotFail);
		primitiveFunctionBeingAttempted = null;
		if (debugPrimitives && logger.isLoggable(Level.FINER))
		{
			AvailErrorCode errorCode = null;
			if (success == FAILURE)
			{
				if (latestResult().isInt())
				{
					final int errorInt = latestResult().extractInt();
					errorCode = AvailErrorCode.byNumericCode(errorInt);
				}
			}
			final String failPart = errorCode != null
				? " (" + errorCode + ")"
				: "";
			log(
				Level.FINER,
				"... completed primitive {0} => {1}{2}",
				primitive.getClass().getSimpleName(),
				success.name(),
				failPart);
		}
		return success;
	}

	/** The {@link L2Chunk} being executed. */
	private @Nullable L2Chunk chunk;

	/**
	 * Return the currently executing {@linkplain L2Chunk Level Two chunk}.
	 *
	 * @return The {@linkplain L2Chunk Level Two chunk} that is currently being
	 *         executed.
	 */
	public L2Chunk chunk ()
	{
		final L2Chunk c = chunk;
		assert c != null;
		return c;
	}

	/**
	 * The current pointer into {@link #chunkInstructions}, the Level Two
	 * instruction stream.
	 */
	private int offset;

	/**
	 * Return the current position in the L2 wordcode stream.
	 *
	 * @return The position in the L2 wordcode stream.
	 */
	public int offset ()
	{
		return offset;
	}

	/**
	 * The level two instruction stream as an array of {@link L2Instruction}s.
	 */
	private @Nullable L2Instruction [] chunkInstructions;

	/**
	 * Start executing a new chunk. The {@linkplain #offset} at which to execute
	 * must be set separately.
	 *
	 * <p>
	 * Note that the {@linkplain CompiledCodeDescriptor compiled code} is passed
	 * in because the {@linkplain L2Chunk#unoptimizedChunk() default chunk}
	 * doesn't inherently know how many registers it needs – the answer depends
	 * on the Level One compiled code being executed.
	 * </p>
	 *
	 * @param chunkToResume
	 *        The {@linkplain L2Chunk Level Two chunk} to start executing.
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor compiled code} on whose
	 *        behalf to start executing the chunk.
	 * @param newOffset
	 *        The offset at which to begin executing the chunk.
	 */
	private void setChunk (
		final L2Chunk chunkToResume,
		final A_RawFunction code,
		final int newOffset)
	{
		this.chunk = chunkToResume;
		this.chunkInstructions = chunkToResume.executableInstructions;
		makeRoomForChunkRegisters(chunkToResume, code);
		this.offset = newOffset;
		if (debugL2)
		{
			log(
				Level.FINER,
				"starting new chunk ({0})",
				chunkToResume);
		}
	}

	/** The number of fixed object registers in Level Two. */
	private static final int numberOfFixedRegisters =
		FixedRegister.all().length;

	/**
	 * Answer the subscript of the register holding the argument or local with
	 * the given index (e.g., the first argument is in register 4). The
	 * arguments come first, then the locals.
	 *
	 * @param argumentOrLocalNumber
	 *            The one-based argument/local number.
	 * @return The subscript to use with {@link Interpreter#pointerAt(int)}.
	 */
	public final static int argumentOrLocalRegister (
		final int argumentOrLocalNumber)
	{
		// Skip the fixed registers.
		return numberOfFixedRegisters + argumentOrLocalNumber - 1;
	}

	/**
	 * The registers that hold {@linkplain AvailObject Avail objects}.
	 */
	private AvailObject[] pointers = new AvailObject[10];

	/**
	 * Answer the current continuation.
	 *
	 * @return The current continuation.
	 */
	public AvailObject currentContinuation ()
	{
		return pointerAt(CALLER);
	}

	/**
	 * Read from an object register. Register zero is reserved for read-only
	 * use, and always contains the {@linkplain NilDescriptor#nil() null
	 * object}.
	 *
	 * @param index
	 *        The object register index.
	 * @return The object in the specified register.
	 */
	public AvailObject pointerAt (final int index)
	{
		// assert index >= 0;
		assert pointers[index] != null;
		return pointers[index];
	}

	/**
	 * Write to an object register. Register zero is reserved for read-only use,
	 * and always contains the {@linkplain NilDescriptor#nil() null
	 * object}.
	 *
	 * @param index
	 *        The object register index.
	 * @param anAvailObject
	 *        The object to write to the specified register.
	 */
	public void pointerAtPut (final int index, final A_BasicObject anAvailObject)
	{
		// assert index > 0;
		assert anAvailObject != null;
		pointers[index] = (AvailObject)anAvailObject;
	}

	/**
	 * Read from a {@linkplain FixedRegister fixed object register}. Register
	 * zero is reserved for read-only use, and always contains
	 * {@linkplain NilDescriptor#nil() nil}.
	 *
	 * @param fixedObjectRegister
	 *        The fixed object register.
	 * @return The object in the specified register.
	 */
	public AvailObject pointerAt (final FixedRegister fixedObjectRegister)
	{
		return pointerAt(fixedObjectRegister.ordinal());
	}

	/**
	 * Write to a fixed object register. Register zero is reserved for read-only
	 * use, and always contains the {@linkplain NilDescriptor#nil() null
	 * object}.
	 *
	 * @param fixedObjectRegister
	 *        The fixed object register.
	 * @param anAvailObject
	 *        The object to write to the specified register.
	 */
	public void pointerAtPut (
		final FixedRegister fixedObjectRegister,
		final A_BasicObject anAvailObject)
	{
		pointerAtPut(fixedObjectRegister.ordinal(), anAvailObject);
	}

	/**
	 * Write a Java null to an object register. Register zero is reserved for
	 * read-only use, and always contains the Avail
	 * {@linkplain NilDescriptor#nil() nil}.
	 *
	 * @param index
	 *        The object register index to overwrite.
	 */
	public void clearPointerAt (final int index)
	{
		assert index > 0;
		pointers[index] = null;
	}

	/**
	 * Write {@code null} into each object register except the constant
	 * {@link FixedRegister#NULL} register.
	 */
	public void wipeObjectRegisters ()
	{
		if (chunk != null)
		{
			pointers[CALLER.ordinal()] = null;
			pointers[FUNCTION.ordinal()] = null;
			// Explicitly *do not* clear the primitive failure value register.

			//TODO[MvG] - Eventually we'll be running Java code and storing all
			//variables in stack slots.
//			Arrays.fill(
//				pointers,
//				numberOfFixedRegisters,
//				pointers.length,
//				null);
		}
	}

	/**
	 * The 32-bit signed integer registers.
	 */
	private int[] integers = new int[10];

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
		assert index > 0;
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
		assert index > 0;
		integers[index] = value;
	}

	/**
	 * The double-precision floating point registers.
	 */
	private double[] doubles = new double[10];

	/**
	 * Jump to a new position in the L2 wordcode stream.
	 *
	 * @param newOffset
	 *        The new position in the L2 wordcode stream.
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
	 * The {@link L1InstructionStepper} used to simulate execution of Level One
	 * nybblecodes.
	 */
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
	private static final int timeSliceTicks = 10;

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
	 * has possibly executed several more wordcodes since that time, to place
	 * the fiber into a state that's consistent with naive Level One execution
	 * semantics. That is, a naive Level One interpreter should be able to
	 * resume the fiber later.
	 *
	 * @param continuation
	 *        The reified continuation to save into the current fiber.
	 */
	public void processInterrupt (final A_Continuation continuation)
	{
		assert !exitNow;
		final A_Fiber aFiber = fiber();
		final MutableOrNull<A_Set> waiters = new MutableOrNull<>();
		aFiber.lock(new Continuation0()
		{
			@Override
			public void value ()
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
					fiber = null;
				}
			}
		});
		exitNow = true;
		startTick = -1L;
		latestResult = null;
		wipeObjectRegisters();
		postExitContinuation(new Continuation0()
		{
			@Override
			public void value ()
			{
				if (waiters.value != null)
				{
					for (final A_BasicObject pojo : waiters.value())
					{
						@SuppressWarnings("unchecked")
						final Continuation1<A_Continuation> waiter =
							(Continuation1<A_Continuation>)
								(pojo.javaObjectNotNull());
						waiter.value(continuation);
					}
				}
				resumeFromInterrupt(aFiber);
			}
		});
	}

	/**
	 * Return into the specified continuation with the given return value.
	 * Verify that the return value matches the expected type already pushed on
	 * the continuation's stack. If the continuation is
	 * {@linkplain NilDescriptor#nil() nil} then make sure
	 * the value gets returned from the main interpreter invocation.
	 *
	 * @param caller
	 *        The {@linkplain ContinuationDescriptor continuation} to resume, or
	 *        {@linkplain NilDescriptor#nil() nil}.
	 * @param value
	 *        The {@link AvailObject} to return.
	 * @param skipReturnCheck
	 *        Whether to skip checking that the return value is of the required
	 *        type.
	 */
	public void returnToCaller (
		final A_Continuation caller,
		final A_BasicObject value,
		final boolean skipReturnCheck)
	{
		// Wipe out the existing registers for safety. This is technically
		// optional, but not doing so may (1) hide bugs, and (2) leak
		// references to values in registers.
		if (caller.equalsNil())
		{
			wipeObjectRegisters();
			terminateFiber(value);
			return;
		}
		// Return into the caller.
		final int stackp = caller.stackp();
		if (!skipReturnCheck)
		{
			final A_Type expectedType = caller.stackAt(stackp);
			if (!value.isInstanceOf(expectedType))
			{
				wipeObjectRegisters();
				pointerAtPut(CALLER, caller);
				invokeFunction(
					runtime.resultDisagreedWithExpectedTypeFunction(),
					Collections.<A_BasicObject>emptyList(),
					true);
				return;
			}
		}
		wipeObjectRegisters();
		final A_Continuation updatedCaller = caller.ensureMutable();
		updatedCaller.stackAtPut(stackp, value);
		prepareToResumeContinuation(updatedCaller);
	}

	/**
	 * Prepare to resume execution of the passed {@linkplain
	 * ContinuationDescriptor continuation}.
	 *
	 * @param updatedCaller The continuation to resume.
	 */
	public void prepareToResumeContinuation (final A_Continuation updatedCaller)
	{
		L2Chunk chunkToResume = updatedCaller.levelTwoChunk();
		if (!chunkToResume.isValid())
		{
			// The chunk has become invalid, so use the default chunk and tweak
			// the continuation's chunk information.
			chunkToResume = L2Chunk.unoptimizedChunk();
			updatedCaller.levelTwoChunkOffset(
				chunkToResume,
				L2Chunk.offsetToContinueUnoptimizedChunk());
		}
		pointerAtPut(CALLER, updatedCaller);
		setChunk(
			chunkToResume,
			updatedCaller.function().code(),
			updatedCaller.levelTwoOffset());
	}

	/**
	 * Prepare to restart the given continuation. Its new arguments, if any,
	 * have already been supplied, and all other data has been wiped. Do not
	 * tally this as an invocation of the method.
	 *
	 * @param continuationToRestart
	 */
	public void prepareToRestartContinuation (
		final A_Continuation continuationToRestart)
	{
		L2Chunk chunkToRestart = continuationToRestart.levelTwoChunk();
		if (!chunkToRestart.isValid())
		{
			// The chunk has become invalid, so use the default chunk and tweak
			// the continuation's chunk information.
			chunkToRestart = L2Chunk.unoptimizedChunk();
			continuationToRestart.levelTwoChunkOffset(chunkToRestart, 0);
		}
		final int numArgs = continuationToRestart.function().code().numArgs();
		argsBuffer.clear();
		for (int i = 1; i <= numArgs; i++)
		{
			argsBuffer.add(continuationToRestart.argOrLocalOrStackAt(i));
		}
		wipeObjectRegisters();
		invokeWithoutPrimitiveFunctionArguments(
			continuationToRestart.function(),
			argsBuffer,
			continuationToRestart.caller(),
			continuationToRestart.skipReturnFlag());
	}

	/**
	 * Increase the number of registers if necessary to accommodate the new
	 * chunk/code.
	 *
	 * @param theChunk
	 *        The {@link L2Chunk} about to be invoked.
	 * @param theCode
	 *        The code about to be invoked.
	 */
	private void makeRoomForChunkRegisters (
		final L2Chunk theChunk,
		final A_RawFunction theCode)
	{
		final int neededObjectCount = max(
			theChunk.numObjects(),
			numberOfFixedRegisters + theCode.numArgsAndLocalsAndStack());
		if (neededObjectCount > pointers.length)
		{
			final AvailObject[] newPointers =
				new AvailObject[neededObjectCount * 2 + 10];
			System.arraycopy(pointers, 0, newPointers, 0, pointers.length);
			pointers = newPointers;
		}
		if (theChunk.numIntegers() > integers.length)
		{
			final int[] newIntegers = new int[theChunk.numIntegers() * 2 + 10];
			System.arraycopy(integers, 0, newIntegers, 0, integers.length);
			integers = newIntegers;
		}
		if (theChunk.numDoubles() > doubles.length)
		{
			final double[] newDoubles =
				new double[theChunk.numDoubles() * 2 + 10];
			System.arraycopy(doubles, 0, newDoubles, 0, doubles.length);
			doubles = newDoubles;
		}
	}

	/**
	 * Raise an exception. Scan the stack of continuations until one is found
	 * for a function whose code specifies {@linkplain P_200_CatchException}.
	 * Get that continuation's second argument (a handler block of one
	 * argument), and check if that handler block will accept the
	 * exceptionValue. If not, keep looking. If it will accept it, unwind the
	 * stack so that the primitive 200 method is the top entry, and invoke the
	 * handler block with exceptionValue. If there is no suitable handler block,
	 * fail the primitive.
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
		final int primNum = P_200_CatchException.instance.primitiveNumber;
		A_Continuation continuation = pointerAt(CALLER);
		while (!continuation.equalsNil())
		{
			final A_RawFunction code = continuation.function().code();
			if (code.primitiveNumber() == primNum)
			{
				assert code.numArgs() == 3;
				final A_Variable failureVariable =
					continuation.argOrLocalOrStackAt(4);
				// Scan a currently unmarked frame.
				if (failureVariable.value().extractInt() == 0)
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
							failureVariable.setValueNoCheck(
								E_HANDLER_SENTINEL.numericCode());
							// Run the handler.
							invokePossiblePrimitiveWithReifiedCaller(
								handler,
								continuation,
								false);
							// Catching an exception *always* changes the
							// continuation.
							return CONTINUATION_CHANGED;
						}
					}
				}
			}
			continuation = continuation.caller();
		}
		// If no handler was found, then return the unhandled exception.
		return primitiveFailure(exceptionValue);
	}

	/**
	 * Scan the stack of continuations until one is found for a function whose
	 * code specifies {@linkplain P_200_CatchException}. Write the specified
	 * marker into its primitive failure variable to indicate the current
	 * exception handling state.
	 *
	 * @param marker An exception handling state marker.
	 * @return The {@linkplain Result success state}.
	 */
	public Result markNearestGuard (final A_Number marker)
	{
		final int primNum = P_200_CatchException.instance.primitiveNumber;
		A_Continuation continuation = pointerAt(CALLER);
		while (!continuation.equalsNil())
		{
			final A_RawFunction code = continuation.function().code();
			if (code.primitiveNumber() == primNum)
			{
				assert code.numArgs() == 3;
				final A_Variable failureVariable =
					continuation.argOrLocalOrStackAt(4);
				// Only allow certain state transitions.
				if (marker.equals(E_HANDLER_SENTINEL.numericCode())
					&& failureVariable.value().extractInt() != 0)
				{
					return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
				}
				else if (
					marker.equals(
						E_UNWIND_SENTINEL.numericCode())
					&& !failureVariable.value().equals(
						E_HANDLER_SENTINEL.numericCode()))
				{
					return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
				}
				// Mark this frame: we don't want it to handle exceptions
				// anymore.
				failureVariable.setValueNoCheck(marker);
				return primitiveSuccess(NilDescriptor.nil());
			}
			continuation = continuation.caller();
		}
		return primitiveFailure(E_NO_HANDLER_FRAME);
	}

	/**
	 * Prepare the {@linkplain Interpreter interpreter} to execute the given
	 * {@linkplain FunctionDescriptor function} with the given arguments.
	 *
	 * @param aFunction
	 *        The function to begin executing.
	 * @param args
	 *        The arguments to pass to the function.
	 * @param skipReturnCheck
	 *        Whether the return type check can be safely skipped upon eventual
	 *        completion of the function.
	 * @return The {@linkplain Result success state}. If the function was not a
	 *         primitive, always indicate that the current continuation was
	 *         replaced.
	 */
	public Result invokeFunction (
		final A_Function aFunction,
		final List<? extends A_BasicObject> args,
		final boolean skipReturnCheck)
	{
		final A_RawFunction code = aFunction.code();
		assert code.numArgs() == args.size();
		final A_Continuation caller = pointerAt(CALLER);
		final int primNum = code.primitiveNumber();
		if (primNum != 0)
		{
			final List<AvailObject> strongArgs =
				new ArrayList<>(args.size());
			for (final A_BasicObject arg : args)
			{
				strongArgs.add((AvailObject)arg);
			}
			final Result result = attemptPrimitive(
				primNum, aFunction, strongArgs, skipReturnCheck);
			switch (result)
			{
				case FAILURE:
					assert !Primitive.byPrimitiveNumberOrFail(primNum).hasFlag(
						Flag.CannotFail);
					assert latestResult != null;
					pointerAtPut(PRIMITIVE_FAILURE, latestResult());
					break;
				case FIBER_SUSPENDED:
					assert exitNow;
					return result;
				case SUCCESS:
					assert latestResult != null;
					return result;
				case CONTINUATION_CHANGED:
					return result;
			}
		}
		// It wasn't a primitive.
		invokeWithoutPrimitiveFunctionArguments(
			aFunction, args, caller, skipReturnCheck);
		return CONTINUATION_CHANGED;
	}

	/**
	 * Prepare the interpreter to deal with executing the given function, using
	 * the given arguments. <em>Do not</em> set up the new function's locals.
	 * In the case of Level One code simulated in Level Two, a preamble level
	 * two instruction will set them up. In the case of Level Two, the code to
	 * initialize them is a sequence of variable creation instructions that can
	 * be interleaved, hoisted or even elided, just like any other instructions.
	 *
	 * <p>Assume the current {@linkplain ContinuationDescriptor continuation}
	 * has already been reified. If the function is a primitive, then it was
	 * already attempted and must have failed, so the failure value must be in
	 * {@link #latestResult}. Move it somewhere more appropriate for the
	 * specialization of {@link Interpreter}, but not into the primitive
	 * failure variable (since that local has not been created yet).</p>
	 *
	 * @param aFunction
	 *            The function to invoke.
	 * @param args
	 *            The arguments to pass to the function.
	 * @param caller
	 *            The calling continuation.
	 * @param skipReturnCheck
	 *            Whether this invocation can skip checking its return result
	 *            upon eventual completion.
	 */
	public void invokeWithoutPrimitiveFunctionArguments (
		final A_Function aFunction,
		final List<? extends A_BasicObject> args,
		final A_BasicObject caller,
		final boolean skipReturnCheck)
	{
		final A_RawFunction code = aFunction.code();
		assert code.primitiveNumber() == 0
			|| pointers[PRIMITIVE_FAILURE.ordinal()] != null;
		code.tallyInvocation();
		L2Chunk chunkToInvoke = code.startingChunk();
		if (!chunkToInvoke.isValid())
		{
			// The chunk is invalid, so use the default chunk and patch up
			// aFunction's code.
			chunkToInvoke = L2Chunk.unoptimizedChunk();
			code.setStartingChunkAndReoptimizationCountdown(
				chunkToInvoke,
				L2Chunk.countdownForInvalidatedCode());
		}
		wipeObjectRegisters();
		setChunk(chunkToInvoke, code, 0);

		pointerAtPut(CALLER, caller);
		pointerAtPut(FUNCTION, aFunction);
		// Transfer arguments...
		final int numArgs = code.numArgs();
		int dest = argumentOrLocalRegister(1);
		for (int i = 1; i <= numArgs; i++)
		{
			pointerAtPut(dest, args.get(i - 1));
			dest++;
		}
		// Store skipReturnCheck into its *architectural* register.  It will be
		// retrieved from there by the L2 code, whether it's the default
		// unoptimized chunk or an optimized chunk.
		integerAtPut(
			L1InstructionStepper.skipReturnCheckRegister(),
			skipReturnCheck ? 1 : 0);
	}

	/**
	 * Start or complete execution of the specified function. The function is
	 * permitted to be primitive. The current continuation must already have
	 * been reified. Since that's the case, we can clobber all registers, as
	 * long as the {@link FixedRegister#CALLER} is set appropriately afterward.
	 *
	 * @param function
	 *        The function to invoke.
	 * @param continuation
	 *        The calling continuation.
	 * @param skipReturnCheck
	 *        Whether the function being invoked can safely skip checking its
	 *        return type when it completes.
	 */
	public void invokePossiblePrimitiveWithReifiedCaller (
		final A_Function function,
		final A_Continuation continuation,
		final boolean skipReturnCheck)
	{
		final int primNum = function.code().primitiveNumber();
		pointerAtPut(CALLER, continuation);
		if (primNum != 0)
		{
			final Result primResult = attemptPrimitive(
				primNum,
				function,
				argsBuffer,
				skipReturnCheck);
			switch (primResult)
			{
				case CONTINUATION_CHANGED:
					return;
				case FIBER_SUSPENDED:
					assert exitNow;
					return;
				case SUCCESS:
					assert chunk().isValid();
					final int stackp = continuation.stackp();
					final AvailObject result = latestResult();
					if (!skipReturnCheck)
					{
						final A_Type expectedType =
							continuation.stackAt(stackp);
						if (!result.isInstanceOf(expectedType))
						{
							invokeFunction(
								runtime
									.resultDisagreedWithExpectedTypeFunction(),
								Collections.<A_BasicObject>emptyList(),
								true);
							return;
						}
					}
					final A_Continuation updatedCaller =
						continuation.ensureMutable();
					pointerAtPut(CALLER, updatedCaller);
					updatedCaller.stackAtPut(stackp, result);
					setChunk(
						updatedCaller.levelTwoChunk(),
						updatedCaller.function().code(),
						updatedCaller.levelTwoOffset());
					return;
				case FAILURE:
					pointerAtPut(PRIMITIVE_FAILURE, latestResult());
					break;
			}
		}
		invokeWithoutPrimitiveFunctionArguments(
			function, argsBuffer, continuation, skipReturnCheck);
	}

	/**
	 * Run the interpreter for a while. Assume the interpreter has already been
	 * set up to run something other than a (successful) primitive function at
	 * the outermost level.
	 */
	@SuppressWarnings("null")
	@InnerAccess void run ()
	{
		startTick = runtime.clock.get();
		while (!exitNow)
		{
			/* This loop is only exited by a return off the end of the outermost
			 * context, a suspend or terminate of the current fiber, or by an
			 * inter-nybblecode interrupt. At the time of an inter-nybblecode
			 * interrupt, the continuation must be a reflection of the current
			 * continuation, <em>not</em> the caller. That is, only the
			 * callerRegister()'s content is valid.
			 */
			final L2Instruction instruction = chunkInstructions[offset];
			final L2Operation operation = instruction.operation;
			if (debugL2)
			{
				int depth = 0;
				for (
					A_Continuation c = pointerAt(CALLER);
					!c.equalsNil();
					c = c.caller())
				{
					depth++;
				}
				log(
					Level.FINE,
					"d={0}: {1} (chunk={2}, off={3})",
					depth,
					operation.name(),
					String.format("#%08x", System.identityHashCode(chunk())),
					offset);
			}
			offset++;
			final long timeBefore = System.nanoTime();
			try
			{
				operation.step(instruction, this);
			}
			finally
			{
				// Even though some primitives may suspend the current fiber,
				// the code still returns here after suspending.  Close enough.
				final long timeAfter = System.nanoTime();
				operation.statisticInNanoseconds.record(
					timeAfter - timeBefore,
					interpreterIndex);
			}
		}
	}

	/**
	 * Schedule the specified {@linkplain ExecutionState#indicatesSuspension()
	 * suspended} {@linkplain FiberDescriptor fiber} to execute for a while as a
	 * {@linkplain AvailRuntime#whenLevelOneUnsafeDo(AvailTask) Level One-unsafe
	 * task} for a while. If the fiber completes normally, then call its
	 * {@linkplain A_Fiber#resultContinuation() result continuation} with
	 * its final answer. If the fiber terminates abnormally, then call its
	 * {@linkplain A_Fiber#failureContinuation() failure continuation} with
	 * the terminal {@linkplain Throwable throwable}.
	 *
	 * @param runtime
	 *        An {@linkplain AvailRuntime Avail runtime}.
	 * @param aFiber
	 *        The fiber to run.
	 * @param continuation
	 *        How to set up the {@linkplain Interpreter interpreter} prior to
	 *        running the fiber for a while. Pass in the interpreter to use.
	 */
	private static void executeFiber (
		final AvailRuntime runtime,
		final A_Fiber aFiber,
		final Continuation1<Interpreter> continuation)
	{
		assert aFiber.executionState().indicatesSuspension();
		// We cannot simply run the specified function, we must queue a task to
		// run when Level One safety is no longer required.
		runtime.whenLevelOneUnsafeDo(
			AvailTask.forFiberResumption(
				aFiber,
				new Transformer0<ExecutionState>()
				{
					@Override
					public ExecutionState value ()
					{
						final Interpreter interpreter = current();
						assert aFiber == interpreter.fiber;
						assert aFiber.executionState() == RUNNING;
						// Set up the interpreter.
						continuation.value(interpreter);
						// Run the interpreter for a while.
						interpreter.run();
						return aFiber.executionState();
					}
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
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					final A_RawFunction code = function.code();
					return StringDescriptor.format(
						"Outermost %s @ %s:%d",
						code.methodName().asNativeString(),
						code.module().moduleName().asNativeString(),
						code.startingLineNumber());
				}
			});
		executeFiber(
			runtime,
			aFiber,
			new Continuation1<Interpreter>()
			{
				@Override
				public void value (final @Nullable Interpreter interpreter)
				{
					assert interpreter != null;
					assert aFiber == interpreter.fiber;
					assert aFiber.executionState() == RUNNING;
					assert aFiber.continuation().equalsNil();
					// Invoke the function. If it's a primitive and it
					// succeeds, then immediately invoke the fiber's
					// result continuation with the primitive's result.
					interpreter.exitNow = false;
					interpreter.pointerAtPut(CALLER, NilDescriptor.nil());
					interpreter.clearPointerAt(FUNCTION.ordinal());
					// Always check the type of the outermost continuation's
					// return value.
					final Result result =
						interpreter.invokeFunction(function, arguments, false);
					if (result == SUCCESS)
					{
						interpreter.terminateFiber(interpreter.latestResult());
						assert interpreter.exitNow;
					}
				}
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
			AvailRuntime.current(),
			aFiber,
			new Continuation1<Interpreter>()
			{
				@Override
				public void value (final @Nullable Interpreter interpreter)
				{
					assert interpreter != null;
					assert aFiber == interpreter.fiber;
					assert aFiber.executionState() == RUNNING;
					assert !aFiber.continuation().equalsNil();
					interpreter.prepareToResumeContinuation(
						aFiber.continuation());
					aFiber.continuation(NilDescriptor.nil());
					interpreter.exitNow = false;
				}
			});
	}

	/**
	 * Schedule resumption of the specified {@linkplain FiberDescriptor fiber}
	 * following {@linkplain ExecutionState#SUSPENDED suspension} by a
	 * {@linkplain Result#SUCCESS successful} {@linkplain Primitive primitive}.
	 * This method is an entry point.
	 *
	 * @param runtime
	 *        An {@linkplain AvailRuntime Avail runtime}.
	 * @param aFiber
	 *        The fiber to run.
	 * @param result
	 *        The result of the primitive.
	 * @param skipReturnCheck
	 *        Whether successful completion of the primitive will always produce
	 *        something of the expected type, allowing us to elide the check of
	 *        the returned value's type.
	 */
	public static void resumeFromSuccessfulPrimitive (
		final AvailRuntime runtime,
		final A_Fiber aFiber,
		final A_BasicObject result,
		final boolean skipReturnCheck)
	{
		assert !aFiber.continuation().equalsNil();
		assert aFiber.executionState() == SUSPENDED;
		executeFiber(
			runtime,
			aFiber,
			new Continuation1<Interpreter>()
			{
				@Override
				public void value (final @Nullable Interpreter interpreter)
				{
					assert interpreter != null;
					assert aFiber == interpreter.fiber;
					assert aFiber.executionState() == RUNNING;
					assert !aFiber.continuation().equalsNil();
					final A_Continuation updatedCaller =
						aFiber.continuation().ensureMutable();
					final int stackp = updatedCaller.stackp();
					if (!skipReturnCheck)
					{
						final A_Type expectedType =
							updatedCaller.stackAt(stackp);
						if (!result.isInstanceOf(expectedType))
						{
							// Breakpoint this to trace the failed type test.
							result.isInstanceOf(expectedType);
							error(String.format(
								"Return value (%s) does not agree with "
								+ "expected type (%s)",
								result,
								expectedType));
						}
					}
					updatedCaller.stackAtPut(stackp, result);
					interpreter.prepareToResumeContinuation(
						updatedCaller);
					aFiber.continuation(NilDescriptor.nil());
					interpreter.exitNow = false;
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
		executeFiber(
			runtime,
			aFiber,
			new Continuation1<Interpreter>()
			{
				@Override
				public void value (final @Nullable Interpreter interpreter)
				{
					assert interpreter != null;
					interpreter.pointerAtPut(
						PRIMITIVE_FAILURE,
						failureValue);
					interpreter.invokeWithoutPrimitiveFunctionArguments(
						failureFunction,
						args,
						aFiber.continuation(),
						skipReturnCheck);
					aFiber.continuation(NilDescriptor.nil());
					interpreter.exitNow = false;
				}
			});
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
		final Continuation1<String> continuation)
	{
		final A_Function stringifierFunction =
			runtime.stringificationFunction();
		// If the stringifier function is not defined, then use the basic
		// mechanism for stringification.
		if (stringifierFunction == null)
		{
			continuation.value(String.format(
				"(stringifier undefined) %s",
				value.toString()));
			return;
		}
		// Create the fiber that will execute the function.
		final A_Fiber fiber = FiberDescriptor.newFiber(
			TupleTypeDescriptor.stringType(),
			FiberDescriptor.stringificationPriority,
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					return StringDescriptor.from("Stringification");
				}
			});
		fiber.textInterface(textInterface);
		fiber.resultContinuation(new Continuation1<AvailObject>()
		{
			@Override
			public void value (final @Nullable AvailObject string)
			{
				assert string != null;
				continuation.value(string.asNativeString());
			}
		});
		fiber.failureContinuation(new Continuation1<Throwable>()
		{
			@Override
			public void value (final @Nullable Throwable e)
			{
				assert e != null;
				continuation.value(String.format(
					"(stringification failed [%s]) %s",
					e.getClass().getSimpleName(),
					value.toString()));
			}
		});
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
		final Continuation1<List<String>> continuation)
	{
		final int valuesCount = values.size();
		if (valuesCount == 0)
		{
			continuation.value(Collections.<String>emptyList());
			return;
		}
		// Deduplicate the list of values for performance…
		final Map<A_BasicObject, List<Integer>> map =
			new HashMap<>(valuesCount);
		for (int i = 0; i < values.size(); i++)
		{
			final A_BasicObject value = values.get(i);
			List<Integer> indices = map.get(value);
			if (indices == null)
			{
				indices = new ArrayList<>();
				map.put(value, indices);
			}
			indices.add(i);
		}
		final AtomicInteger outstanding = new AtomicInteger(map.size());
		final String[] strings = new String[valuesCount];
		for (final Map.Entry<A_BasicObject, List<Integer>> entry
			: map.entrySet())
		{
			final List<Integer> indicesToWrite = entry.getValue();
			stringifyThen(
				runtime,
				textInterface,
				entry.getKey(),
				new Continuation1<String>()
				{
					@Override
					public void value (final @Nullable String arg)
					{
						assert arg != null;
						for (final int indexToWrite : indicesToWrite)
						{
							strings[indexToWrite] = arg;
						}
						if (outstanding.decrementAndGet() == 0)
						{
							final List<String> stringList =
								Arrays.asList(strings);
							continuation.value(stringList);
						}
					}
				});
		}
	}

	/**
	 * Create a list of descriptions of the current stack frames ({@linkplain
	 * ContinuationDescriptor continuations}).
	 *
	 * @return A list of {@linkplain String strings}, starting with the newest
	 *         frame.
	 */
	private List<String> builtinDumpStack ()
	{
		final List<A_Continuation> frames = new ArrayList<>(20);
		final AvailObject currentFunction =
			pointers[FixedRegister.FUNCTION.ordinal()];
		if (currentFunction != null
			&& !currentFunction.equalsNil())
		{
			final A_Continuation syntheticFrame =
				ContinuationDescriptor.createExceptFrame (
					currentFunction,
					NilDescriptor.nil(),
					0,
					0,
					integerAt(
						// Best guess, but the L2Translator is free to put this
						// flag anywhere.
						L1InstructionStepper.skipReturnCheckRegister()) != 0,
					chunk(),
					offset);
			frames.add(syntheticFrame);
		}
		A_Continuation c = currentContinuation();
		// Sometimes the CALLER register contains the current continuation and
		// sometimes it's really the caller.  Compensate with a good heuristic.
		if (!c.equalsNil() && c.function() == currentFunction)
		{
			c = c.caller();
		}
		while (!c.equalsNil())
		{
			frames.add(c);
			c = c.caller();
		}
		final List<String> strings = new ArrayList<>(frames.size());
		int line = frames.size();
		final StringBuilder signatureBuilder = new StringBuilder(1000);
		for (final A_Continuation frame : frames)
		{
			final A_Function function = frame.function();
			final A_RawFunction code = function.code();
			final A_Type functionType = code.functionType();
			final A_Type paramsType = functionType.argsTupleType();
			for (int i = 1,
				limit = paramsType.sizeRange().lowerBound().extractInt();
				i <= limit;
				i++)
			{
				if (i != 1)
				{
					signatureBuilder.append(", ");
				}
				signatureBuilder.append(paramsType.typeAtIndex(i));
			}
			final String entry = String.format(
				"#%d (^%s)[%s]: %s [%s] (%s:%d)",
				line--,
				frame.skipReturnFlag() ? "skip" : "check",
				code.startingChunk(),
				code.methodName().asNativeString(),
				signatureBuilder.toString(),
				code.module().equalsNil()
					? "?"
					: code.module().moduleName().asNativeString(),
				code.startingLineNumber());
			strings.add(entry.replace("\n", "\n\t"));
			signatureBuilder.setLength(0);
		}
		return strings;
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
			builder.append(String.format(" [%s]", fiber().fiberName()));
			if (pointers[CALLER.ordinal()] == null)
			{
				builder.append(String.format("%n\t«no stack»"));
			}
			else
			{
//				final List<String> stack = builtinDumpStack();
//				for (final String frame : stack)
//				{
//					builder.append(String.format("%n\t-- %s", frame));
//				}
			}
			builder.append("\n\n");
		}
		return builder.toString();
	}

	/**
	 * Statistics about dynamic lookups, keyed by the message bundle's message's
	 * print representation (an Avail string).
	 */
	private static final Map<A_String, Statistic>
		dynamicLookupStatsByString = new HashMap<>();

	/**
	 * Statistics about dynamic lookups, keyed by message bundle.  It's a
	 * <em>weak</em> keyed collection, so it doesn't force the bundles to stick
	 * around.  That's ok, since subsequent runs will produce the same names for
	 * the statistics that should be aggregated with data from previous runs,
	 * which can be found in the {@link #dynamicLookupStatsByString}.
	 *
	 * <p>We don't need to worry about indirection objects here, because a lost
	 * entry just means we'll find it by name in the {@link
	 * #dynamicLookupStatsByString} map.</p>
	 */
	private static final Map<A_Bundle, Statistic>
		dynamicLookupStatsByBundle = new WeakHashMap<>();

	/**
	 * Record the fact that a lookup in the specified {@link
	 * MessageBundleDescriptor message bundle} has just taken place, and that it
	 * took the given time in nanoseconds.
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
	public synchronized void recordDynamicLookup (
		final A_Bundle bundle,
		final double nanos)
	{
		Statistic stat = dynamicLookupStatsByBundle.get(bundle);
		if (stat == null)
		{
			// Instead, look it up by name.  Every statistic is stored under
			// both its bundle (for speed) and its bundle's message's print
			// representation (for aggregating between runs).
			final String nameString = bundle.message().toString();
			final A_String name = StringDescriptor.from(nameString);
			stat = dynamicLookupStatsByString.get(name);
			if (stat == null)
			{
				// First time -- create a new statistic.
				stat = new Statistic(
					"Lookup " + nameString,
					StatisticReport.DYNAMIC_LOOKUPS);
				dynamicLookupStatsByString.put(name, stat);
			}
			// Reuse the statistic from a previous run (or just created).
			dynamicLookupStatsByBundle.put(bundle, stat);
		}
		stat.record(nanos, interpreterIndex);
	}

	/**
	 * Detailed statistics about checked non-primitive returns.  This is a map
	 * whose keys are raw functions doing the returning, and whose values are
	 * maps from the raw functions being returned into to a statistic.
	 */
	private static final Map<A_RawFunction, Map<A_RawFunction, Statistic>>
		checkedReturnMaps = new WeakHashMap<>();

	/**
	 * Statistics about checked non-primitive returns, keyed by the {@link
	 * Statistic}'s name (which includes information about both the returner and
	 * the returnee).  This holds the statistics strongly to allow the same
	 * statistics to be used after unloading/loading.
	 */
	private static final Map<A_String, Statistic>
		checkedReturnMapsByString = new HashMap<>();

	/**
	 * Record the fact that when returning from the specified returner into the
	 * returnee, the return value was checked against the expected type, and it
	 * took the specified number of nanoseconds.
	 *
	 * @param returner The {@link A_RawFunction} which was returning.
	 * @param returnee The {@link A_RawFunction} being returned into.
	 * @param nanos The number of nanoseconds it took to check the result type.
	 */
	public void recordCheckedReturnFromTo (
		final A_RawFunction returner,
		final A_RawFunction returnee,
		final double nanos)
	{
		Statistic statistic;
		synchronized (checkedReturnMaps)
		{
			Map<A_RawFunction, Statistic> submap =
				checkedReturnMaps.get(returner);
			if (submap == null)
			{
				submap = new WeakHashMap<>();
				checkedReturnMaps.put(returner, submap);
			}
			statistic = submap.get(returnee);
			if (statistic == null)
			{
				final StringBuilder builder = new StringBuilder();
				builder.append("Return from ");
				builder.append(returner.methodName().asNativeString());
				builder.append(" to ");
				builder.append(returnee.methodName().asNativeString());
				final String nameString = builder.toString();
				final A_String availString = StringDescriptor.from(nameString);
				statistic = checkedReturnMapsByString.get(availString);
				if (statistic == null)
				{
					statistic = new Statistic(
						nameString,
						StatisticReport.NON_PRIMITIVE_RETURN_TYPE_CHECKS);
					checkedReturnMapsByString.put(availString, statistic);
					submap.put(returnee, statistic);
				}
			}
		}
		statistic.record(nanos, interpreterIndex);
	}

	/**
	 * The bootstrapped {@linkplain P_011_SetValue assignment function} used to
	 * restart implicitly observed assignments.
	 */
	private static final A_Function assignmentFunction =
		FunctionDescriptor.newPrimitiveFunction(
			P_011_SetValue.instance,
			NilDescriptor.nil(),
			0);

	/**
	 * Answer the bootstrapped {@linkplain P_011_SetValue assignment function}
	 * used to restart implicitly observed assignments.
	 *
	 * @return The assignment function.
	 */
	public static final A_Function assignmentFunction ()
	{
		return assignmentFunction;
	}
}
