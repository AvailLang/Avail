/**
 * Interpreter.java Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag.BOUND;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static java.lang.Math.max;
import java.util.*;
import java.util.logging.*;
import com.avail.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.*;
import com.avail.exceptions.*;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.interpreter.primitive.*;
import com.avail.utility.*;

/**
 * This class is used to execute {@linkplain L2ChunkDescriptor Level Two code},
 * which is a translation of the Level One nybblecodes found in
 * {@linkplain CompiledCodeDescriptor compiled code}.
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
 * similar bank yet for {@code double}s (unboxed double-precision floating point
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
	 * If this thread is not an {@link AvailThread}, then answer {@code null}.
	 *
	 * @return The current Level Two interpreter, or {@code null} if the current
	 *         thread is not an {@code AvailThread}.
	 */
	public static Interpreter current ()
	{
		return ((AvailThread) Thread.currentThread()).interpreter;
	}

	/** Whether to print detailed Level One debug information. */
	public final static boolean debugL1 = false;

	/**
	 * Fake slots used to show stack traces in the Eclipse Java debugger.
	 */
	enum FakeStackTraceSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The chain of {@linkplain ContinuationDescriptor continuations} of the
		 * {@linkplain FiberDescriptor fiber} bound to this
		 * {@linkplain Interpreter interpreter}.
		 */
		FRAMES
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
		final List<AvailObject> frames = new ArrayList<AvailObject>(50);
		AvailObject frame = pointers[CALLER.ordinal()];
		if (frame != null)
		{
			while (!frame.equalsNil())
			{
				frames.add(frame);
				frame = frame.caller();
			}
		}
		final AvailObject framesTuple = TupleDescriptor.fromList(frames);
		final AvailObject outerTuple = TupleDescriptor.from(framesTuple);
		final List<AvailObjectFieldHelper> outerList =
			new ArrayList<AvailObjectFieldHelper>();
		assert outerTuple.tupleSize() == FakeStackTraceSlots.values().length;
		for (final FakeStackTraceSlots field : FakeStackTraceSlots.values())
		{
			outerList.add(new AvailObjectFieldHelper(
				outerTuple,
				field,
				-1,
				outerTuple.tupleAt(field.ordinal() + 1)));
		}
		return outerList.toArray(new AvailObjectFieldHelper[outerList.size()]);
	}

	/**
	 * This {@linkplain Interpreter interpreter}'s {@linkplain AvailRuntime
	 * Avail runtime}
	 */
	private final AvailRuntime runtime;

	/**
	 * Construct a new {@link Interpreter}.
	 *
	 * @param runtime
	 *        An {@link AvailRuntime}.
	 */
	public Interpreter (final AvailRuntime runtime)
	{
		this.runtime = runtime;
		pointers[NULL.ordinal()] = NilDescriptor.nil();
	}

	/** A {@linkplain Logger logger}. */
	public static final Logger logger =
		Logger.getLogger(Interpreter.class.getCanonicalName());

	/**
	 * The {@link FiberDescriptor} being executed by this interpreter.
	 */
	public AvailObject fiber;

	/**
	 * Return the current {@linkplain FiberDescriptor fiber}.
	 *
	 * @return The current executing fiber.
	 */
	public AvailObject fiber ()
	{
		assert fiber != null;
		return fiber;
	}

	/**
	 * Bind the specified {@linkplain ExecutionState#RUNNING running}
	 * {@linkplain FiberDescriptor fiber} to the {@linkplain Interpreter
	 * Interpreter}.
	 *
	 * @param newFiber
	 *        The fiber to run.
	 */
	public void fiber (final @Nullable AvailObject newFiber)
	{
		assert fiber == null ^ newFiber == null;
		assert newFiber == null || newFiber.executionState() == RUNNING;
		fiber = newFiber;
	}

	/**
	 * The latest result produced by a {@linkplain Result#SUCCESS successful}
	 * {@linkplain Primitive primitive}, or the latest {@linkplain
	 * AvailErrorCode error code} produced by a {@linkplain Result#FAILURE
	 * failed} primitive.
	 */
	private AvailObject latestResult;

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
		assert latestResult != null;
		return latestResult;
	}

	/**
	 * Set the resulting value of a primitive invocation. Answer primitive
	 * {@linkplain Result#SUCCESS success}.
	 *
	 * @param result
	 *        The result of performing a {@linkplain Primitive primitive}.
	 * @return Primitive {@linkplain Result#SUCCESS success}.
	 */
	public Result primitiveSuccess (final AvailObject result)
	{
		assert fiber.executionState() == RUNNING;
		assert result != null;
		latestResult = result;
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
		assert fiber.executionState() == RUNNING;
		latestResult = code.numericCode();
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
		assert fiber.executionState() == RUNNING;
		latestResult = exception.errorValue();
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
		assert fiber.executionState() == RUNNING;
		latestResult = exception.errorValue();
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
	public Result primitiveFailure (final AvailObject result)
	{
		assert fiber.executionState() == RUNNING;
		assert result != null;
		latestResult = result;
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
	private Continuation0 postExitContinuation;

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
		final AvailObject aFiber = fiber;
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
	 * {@linkplain ExecutionState#JOINING Join} the {@linkplain #fiber()
	 * current} {@linkplain FiberDescriptor fiber} from a {@linkplain Primitive
	 * primitive} invocation. The reified {@linkplain ContinuationDescriptor
	 * continuation} must be available in the architectural {@linkplain
	 * FixedRegister#CALLER caller register}, and will be installed into the
	 * current fiber.
	 *
	 * @return {@link Result#FIBER_SUSPENDED}, for convenience.
	 */
	public Result primitiveJoin ()
	{
		return primitiveSuspend(JOINING);
	}

	/**
	 * A place to store the primitive {@linkplain CompiledCodeDescriptor
	 * compiled code} being attempted.  That allows {@linkplain
	 * P_340_PushConstant} to get to the first literal in order to return it
	 * from the primitive.
	 */
	private AvailObject primitiveCompiledCodeBeingAttempted;

	/**
	 * Answer the {@linkplain Primitive primitive} {@linkplain
	 * CompiledCodeDescriptor compiled code} that is currently being attempted.
	 * This allows {@linkplain P_340_PushConstant} to get to the first literal
	 * in order to return it from the primitive.
	 *
	 * @return The {@linkplain CompiledCodeDescriptor compiled code} whose
	 *         primitive is being attempted.
	 */
	public AvailObject primitiveCompiledCodeBeingAttempted ()
	{
		return primitiveCompiledCodeBeingAttempted;
	}

	/** The lock that synchronizes joins. */
	public static final Object joinLock = new Object();

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
		final AvailObject finalObject,
		final ExecutionState state)
	{
		assert !exitNow;
		assert finalObject != null;
		assert state.indicatesTermination();
		final AvailObject aFiber = fiber;
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
				synchronized (Interpreter.joinLock)
				{
					final AvailObject joining =
						aFiber.joiningFibers().makeShared();
					aFiber.joiningFibers(SetDescriptor.empty());
					// Wake up all fibers trying to join this one.
					for (final AvailObject joiner : joining)
					{
						joiner.lock(new Continuation0()
						{
							@Override
							public void value ()
							{
								// A termination request could have moved the
								// joiner out of the JOINING state.
								if (joiner.executionState() == JOINING)
								{
									assert joiner.joinee().equals(aFiber);
									joiner.joinee(NilDescriptor.nil());
									joiner.executionState(SUSPENDED);
									Interpreter.resumeFromPrimitive(
										joiner,
										SUCCESS,
										NilDescriptor.nil());
								}
								else
								{
									assert joiner.executionState()
										.indicatesTermination();
								}
							}
						});
					}
				}
			}
		});
	}

	/**
	 * {@linkplain ExecutionState#TERMINATED Terminate} the {@linkplain
	 * #fiber() current} {@linkplain FiberDescriptor fiber}, using the specified
	 * {@linkplain AvailObject object} as its final result.
	 *
	 * @param finalObject
	 *        The fiber's result.
	 */
	public void terminateFiber (final AvailObject finalObject)
	{
		exitFiber(finalObject, TERMINATED);
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
	 * Interpreter#primitiveSuccess(AvailObject)} to set the latestResult to
	 * some object indicating what the problem was, and return primitiveFailed
	 * immediately.  If the primitive causes the continuation to change (e.g.,
	 * through block invocation, continuation restart, exception throwing, etc),
	 * answer continuationChanged.  Otherwise the primitive succeeded, and we
	 * simply capture the resulting value with {@link
	 * Interpreter#primitiveSuccess(AvailObject)} and return {@link
	 * Result#SUCCESS}.
	 *
	 * @param primitiveNumber The number of the primitive to invoke.
	 * @param compiledCode The compiled code whose primitive is being attempted.
	 * @param args The list of arguments to supply to the primitive.
	 * @return The resulting status of the primitive attempt.
	 */
	public final Result attemptPrimitive (
		final int primitiveNumber,
		final @Nullable AvailObject compiledCode,
		final List<AvailObject> args)
	{
		final Primitive primitive =
			Primitive.byPrimitiveNumberOrFail(primitiveNumber);
		if (logger.isLoggable(Level.FINER))
		{
			logger.finer(String.format(
				"attempting primitive %d (%s) ...",
				primitiveNumber,
				primitive));
		}

		latestResult = null;
		primitiveCompiledCodeBeingAttempted = compiledCode;
		assert current() == this;
		final Result success = primitive.attempt(args, this);
		assert success != FAILURE || !primitive.hasFlag(Flag.CannotFail);
		primitiveCompiledCodeBeingAttempted = null;
		if (logger.isLoggable(Level.FINER))
		{
			final String failPart = success == FAILURE
				? " (" + AvailErrorCode.byNumericCode(
					latestResult.extractInt())
					+ ")"
				: "";
			logger.finer(String.format(
				"... completed primitive (%s) => %s%s",
				primitive,
				success.name(),
				failPart));
		}
		return success;
	}

	/** The {@link L2ChunkDescriptor} being executed. */
	private AvailObject chunk;

	/**
	 * Return the currently executing {@linkplain L2ChunkDescriptor Level Two
	 * chunk}.
	 *
	 * @return The {@linkplain L2ChunkDescriptor Level Two chunk} that is
	 *         currently being executed.
	 */
	public AvailObject chunk ()
	{
		return chunk;
	}

	/**
	 * The current pointer into {@link #chunkWords}, the Level Two instruction
	 * stream.
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
	 * The L2 instruction stream as a tuple of integers.
	 */
	private AvailObject chunkWords;

	/**
	 * Extract the next word from the Level Two instruction stream.
	 *
	 * @return The word.
	 */
	public int nextWord ()
	{
		final int theOffset = offset;
		final int word = chunkWords.tupleIntAt(theOffset);
		offset = theOffset + 1;
		return word;
	}

	/**
	 * This chunk's register vectors. A register vector is a tuple of integers
	 * that represent {@link #pointers Avail object registers}.
	 */
	private AvailObject chunkVectors;

	/**
	 * Answer the vector in the current chunk which has the given index. A
	 * vector (at runtime) is simply a tuple of integers.
	 *
	 * @param index
	 *        The vector's index.
	 * @return A tuple of integers.
	 */
	public AvailObject vectorAt (final int index)
	{
		return chunkVectors.tupleAt(index);
	}

	/**
	 * Start executing a new chunk. The {@linkplain #offset} at which to execute
	 * must be set separately.
	 *
	 * <p>
	 * Note that the {@linkplain CompiledCodeDescriptor compiled code} is passed
	 * in because the {@linkplain L2ChunkDescriptor#unoptimizedChunk() default
	 * chunk} doesn't inherently know how many registers it needs – the answer
	 * depends on the Level One compiled code being executed.
	 * </p>
	 *
	 * @param chunk
	 *        The {@linkplain L2ChunkDescriptor Level Two chunk} to start
	 *        executing.
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor compiled code} on whose
	 *        behalf to start executing the chunk.
	 * @param newOffset
	 *        The offset at which to begin executing the chunk.
	 */
	private void setChunk (
		final AvailObject chunk,
		final AvailObject code,
		final int newOffset)
	{
		this.chunk = chunk;
		chunkWords = chunk.wordcodes();
		chunkVectors = chunk.vectors();
		makeRoomForChunkRegisters(chunk, code);
		L2ChunkDescriptor.moveToHead(chunk);
		this.offset = newOffset;
		if (logger.isLoggable(Level.FINER))
		{
			logger.finer(String.format(
				"executing new chunk (%d)",
				chunk.index()));
		}
	}

	/** The number of fixed object registers in Level Two. */
	private static final int numberOfFixedRegisters =
		FixedRegister.values().length;

	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain AvailObject#pc() pc}
	 * (program counter).
	 *
	 * @return The subscript to use with {@link Interpreter#integerAt(int)}.
	 */
	public final static int pcRegister ()
	{
		// Reserved.
		return 1;
	}

	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain AvailObject#stackp()
	 * stackp} (stack pointer). While in this register, the value refers to the
	 * exact pointer register number rather than the value that would be stored
	 * in a continuation's stackp slot, so adjustments must be made during
	 * reification and explosion of continuations.
	 *
	 * @return The subscript to use with {@link Interpreter#integerAt(int)}.
	 */
	public final static int stackpRegister ()
	{
		// Reserved.
		return 2;
	}

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
	 * Answer an integer extracted at the current program counter. The program
	 * counter will be adjusted to skip over the integer.
	 *
	 * @return An integer extracted from the instruction stream.
	 */
	public int getInteger ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final AvailObject code = function.code();
		final AvailObject nybbles = code.nybbles();
		int pc = integerAt(pcRegister());
		final byte firstNybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		int value = 0;
		final byte[] counts =
		{
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 4, 8
		};
		for (int count = counts[firstNybble]; count > 0; count--, pc++)
		{
			value = (value << 4) + nybbles.extractNybbleFromTupleAt(pc);
		}
		final byte[] offsets =
		{
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 26, 42, 58, 0, 0
		};
		value += offsets[firstNybble];
		integerAtPut(pcRegister(), pc);
		return value;
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
	public void pointerAtPut (final int index, final AvailObject anAvailObject)
	{
		// assert index > 0;
		assert anAvailObject != null;
		pointers[index] = anAvailObject;
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
		final AvailObject anAvailObject)
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
			Arrays.fill(
				pointers,
				numberOfFixedRegisters,
				pointers.length,
				null);
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
	 * @return The int in the specified register.
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
	 *        The value to write to the register.
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
		// System.out.printf("[#%d] %d -> %d%n", _chunk.index(), _offset,
		// newOffset);
		offset = newOffset;
	}

	/**
	 * A reusable temporary buffer used to hold arguments during method
	 * invocations.
	 */
	public final List<AvailObject> argsBuffer = new ArrayList<AvailObject>();

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
			|| runtime.clock - startTick >= timeSliceTicks;
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
	public void processInterrupt (final AvailObject continuation)
	{
		assert !exitNow;
		final AvailObject aFiber = fiber;
		aFiber.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				assert aFiber.executionState() == RUNNING;
				aFiber.executionState(INTERRUPTED);
				aFiber.continuation(continuation);
				final boolean bound = fiber.getAndSetSynchronizationFlag(
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
	 */
	public void returnToCaller (
		final AvailObject caller,
		final AvailObject value)
	{
		// Wipe out the existing registers for safety. This is technically
		// optional, but not doing so may (1) hide bugs, and (2) leak
		// references to values in registers.
		wipeObjectRegisters();
		if (caller.equalsNil())
		{
			terminateFiber(value);
			return;
		}
		// Return into the caller.
		final int stackp = caller.stackp();
		final AvailObject expectedType = caller.stackAt(stackp);
		if (!value.isInstanceOf(expectedType))
		{
			error(String.format(
				"Return value (%s) does not agree with expected type (%s)",
				value,
				expectedType));
		}
		final AvailObject updatedCaller = caller.ensureMutable();
		updatedCaller.stackAtPut(stackp, value);
		prepareToResumeContinuation(updatedCaller);
	}

	/**
	 * Prepare to resume execution of the passed {@linkplain
	 * ContinuationDescriptor continuation}.
	 *
	 * @param continuation The continuation to resume.
	 */
	public void prepareToResumeContinuation (final AvailObject continuation)
	{
		AvailObject chunkToResume = continuation.levelTwoChunk();
		if (!chunkToResume.isValid())
		{
			// The chunk has become invalid, so use the default chunk and tweak
			// the continuation's chunk information.
			chunkToResume = L2ChunkDescriptor.unoptimizedChunk();
			continuation.levelTwoChunkOffset(
				chunkToResume,
				L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
		}
		pointerAtPut(CALLER, continuation);
		setChunk(
			chunkToResume,
			continuation.function().code(),
			continuation.levelTwoOffset());
	}

	/**
	 * Prepare to restart the given continuation. Its new arguments, if any,
	 * have already been supplied, and all other data has been wiped. Do not
	 * tally this as an invocation of the method.
	 *
	 * @param continuationToRestart
	 */
	public void prepareToRestartContinuation (
		final AvailObject continuationToRestart)
	{
		AvailObject chunkToRestart = continuationToRestart.levelTwoChunk();
		if (!chunkToRestart.isValid())
		{
			// The chunk has become invalid, so use the default chunk and tweak
			// the continuation's chunk information.
			chunkToRestart = L2ChunkDescriptor.unoptimizedChunk();
			continuationToRestart.levelTwoChunkOffset(chunkToRestart, 1);
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
			continuationToRestart.caller());
	}

	/**
	 * Increase the number of registers if necessary to accommodate the new
	 * chunk/code.
	 *
	 * @param theChunk
	 *        The {@linkplain L2ChunkDescriptor L2Chunk} about to be invoked.
	 * @param theCode
	 *        The code about to be invoked.
	 */
	private void makeRoomForChunkRegisters (
		final AvailObject theChunk,
		final AvailObject theCode)
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
		AvailObject continuation = pointerAt(CALLER);
		while (!continuation.equalsNil())
		{
			final AvailObject code = continuation.function().code();
			if (code.primitiveNumber() == primNum)
			{
				assert code.numArgs() == 3;
				final AvailObject failureVariable =
					continuation.argOrLocalOrStackAt(4);
				// Allow scan a currently unmarked frame.
				if (failureVariable.getValue().extractInt() == 0)
				{
					final AvailObject handlerTuple =
						continuation.argOrLocalOrStackAt(2);
					assert handlerTuple.isTuple();
					for (final AvailObject handler : handlerTuple)
					{
						if (exceptionValue.isInstanceOf(
							handler.kind().argsTupleType().typeAtIndex(1)))
						{
							// Mark this frame: we don't want it to handle an
							// exception raised from within one of its handlers.
							failureVariable.setValue(
								E_HANDLER_SENTINEL.numericCode());
							// Run the handler.
							invokePossiblePrimitiveWithReifiedCaller(
								handler,
								continuation);
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
	public Result markNearestGuard (final AvailObject marker)
	{
		final int primNum = P_200_CatchException.instance.primitiveNumber;
		AvailObject continuation = pointerAt(CALLER);
		while (!continuation.equalsNil())
		{
			final AvailObject code = continuation.function().code();
			if (code.primitiveNumber() == primNum)
			{
				assert code.numArgs() == 3;
				final AvailObject failureVariable = continuation
					.argOrLocalOrStackAt(4);
				// Only allow certain state transitions.
				if (marker.equals(E_HANDLER_SENTINEL.numericCode())
					&& failureVariable.getValue().extractInt() != 0)
				{
					return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
				}
				else if (
					marker.equals(
						E_UNWIND_SENTINEL.numericCode())
					&& !failureVariable.getValue().equals(
						E_HANDLER_SENTINEL.numericCode()))
				{
					return primitiveFailure(E_CANNOT_MARK_HANDLER_FRAME);
				}
				// Mark this frame: we don't want it to handle exceptions
				// anymore.
				failureVariable.setValue(marker);
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
	 * @return The {@linkplain Result success state}. If the function was not a
	 *         primitive, always indicate that the current continuation was
	 *         replaced.
	 */
	public Result invokeFunction (
		final AvailObject aFunction,
		final List<AvailObject> args)
	{
		final AvailObject code = aFunction.code();
		assert code.numArgs() == args.size();
		final AvailObject caller = pointerAt(CALLER);
		final int primNum = code.primitiveNumber();
		if (primNum != 0)
		{
			final Result result = attemptPrimitive(primNum, code, args);
			switch (result)
			{
				case FAILURE:
					assert !Primitive.byPrimitiveNumberOrFail(primNum).hasFlag(
						Flag.CannotFail);
					assert latestResult != null;
					pointerAtPut(PRIMITIVE_FAILURE, latestResult);
					break;
				case FIBER_SUSPENDED:
					assert exitNow;
					// $FALL-THROUGH$
				case SUCCESS:
					assert latestResult != null;
					// $FALL-THROUGH$
				case CONTINUATION_CHANGED:
					return result;
			}
		}
		// It wasn't a primitive.
		invokeWithoutPrimitiveFunctionArguments(aFunction, args, caller);
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
	 * @param aFunction The function to invoke.
	 * @param args The arguments to pass to the function.
	 * @param caller The calling continuation.
	 */
	public void invokeWithoutPrimitiveFunctionArguments (
		final AvailObject aFunction,
		final List<AvailObject> args,
		final AvailObject caller)
	{
		final AvailObject code = aFunction.code();
		assert code.primitiveNumber() == 0 || latestResult != null;
		code.tallyInvocation();
		AvailObject chunkToInvoke = code.startingChunk();
		if (!chunkToInvoke.isValid())
		{
			// The chunk is invalid, so use the default chunk and patch up
			// aFunction's code.
			chunkToInvoke = L2ChunkDescriptor.unoptimizedChunk();
			code.setStartingChunkAndReoptimizationCountdown(
				chunkToInvoke,
				L2ChunkDescriptor.countdownForInvalidatedCode());
		}
		wipeObjectRegisters();
		setChunk(chunkToInvoke, code, 1);

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
	}

	/**
	 * Start or complete execution of the specified function. The function is
	 * permitted to be primitive. The current continuation must already have
	 * been reified. Since that's the case, we can clobber all registers, as
	 * long as the {@link FixedRegister#CALLER} is set appropriately afterward.
	 *
	 * @param theFunction
	 *        The function to invoke.
	 * @param caller
	 *        The calling continuation.
	 */
	public void invokePossiblePrimitiveWithReifiedCaller (
		final AvailObject theFunction,
		final AvailObject caller)
	{
		final AvailObject theCode = theFunction.code();
		final int primNum = theCode.primitiveNumber();
		pointerAtPut(CALLER, caller);
		if (primNum != 0)
		{
			final Result primResult = attemptPrimitive(
				primNum,
				theCode,
				argsBuffer);
			switch (primResult)
			{
				case CONTINUATION_CHANGED:
					return;
				case FIBER_SUSPENDED:
					assert exitNow;
					return;
				case SUCCESS:
					assert chunk.isValid();
					final AvailObject updatedCaller = caller.ensureMutable();
					final int stackp = updatedCaller.stackp();
					final AvailObject expectedType =
						updatedCaller.stackAt(stackp);
					assert latestResult != null;
					if (!latestResult.isInstanceOf(expectedType))
					{
						// TODO: [MvG] Remove after debugging.
						latestResult.isInstanceOf(expectedType);
						error(String.format(
							"Return value (%s) does not agree with "
							+ "expected type (%s)",
							latestResult,
							expectedType));
					}
					updatedCaller.stackAtPut(stackp, latestResult);
					pointerAtPut(CALLER, updatedCaller);
					setChunk(
						updatedCaller.levelTwoChunk(),
						updatedCaller.function().code(),
						updatedCaller.levelTwoOffset());
					return;
				case FAILURE:
					assert latestResult != null;
					pointerAtPut(PRIMITIVE_FAILURE, latestResult);
					break;
			}
		}
		invokeWithoutPrimitiveFunctionArguments(
			theFunction, argsBuffer, caller);
	}

	/**
	 * Run the interpreter for a while. Assume the interpreter has already been
	 * set up to run something other than a (successful) primitive function at
	 * the outermost level.
	 */
	@InnerAccess void run ()
	{
		startTick = runtime.clock;
		while (!exitNow)
		{
			/**
			 * This loop is only exited by a return off the end of the outermost
			 * context, a suspend or terminate of the current fiber, or by an
			 * inter-nybblecode interrupt. For now there are no inter-nybblecode
			 * interrupts, but these can be added later. They will probably
			 * happen on non-primitive method invocations, returns, and backward
			 * jumps. At the time of an inter-nybblecode interrupt, the
			 * continuation must be a reflection of the current continuation,
			 * <em>not</em> the caller. That is, only the callerRegister()'s
			 * content is valid.
			 */
			final int wordCode = nextWord();
			final L2Operation operation = L2Operation.values()[wordCode];
			if (logger.isLoggable(Level.FINEST))
			{
				final StringBuilder stackString = new StringBuilder();
				AvailObject chain = pointerAt(CALLER);
				while (!chain.equalsNil())
				{
					stackString.insert(0, "->");
					stackString.insert(
						0,
						argumentOrLocalRegister(chain.stackp()));
					chain = chain.caller();
				}

				logger.finest(String.format(
					"executing %s (chunk#=%d, pc=%d) [stack: %s]",
					operation.name(),
					chunk.index(),
					offset - 1,
					stackString));
			}
			operation.step(this);
		}
	}

	/**
	 * Schedule the specified {@linkplain ExecutionState#indicatesSuspension()
	 * suspended} {@linkplain FiberDescriptor fiber} to execute for a while as a
	 * {@linkplain AvailRuntime#whenLevelOneUnsafeDo(AvailTask) Level One-unsafe
	 * task} for a while. If the fiber completes normally, then call its
	 * {@linkplain AvailObject#resultContinuation() result continuation} with
	 * its final answer. If the fiber terminates abnormally, then call its
	 * {@linkplain AvailObject#failureContinuation() failure continuation} with
	 * the terminal {@linkplain Throwable throwable}.
	 *
	 * @param aFiber
	 *        The fiber to run.
	 * @param continuation
	 *        How to set up the {@linkplain Interpreter interpreter} prior to
	 *        running the fiber for a while. Pass in the interpreter to use.
	 */
	private static void executeFiber (
		final AvailObject aFiber,
		final Continuation1<Interpreter> continuation)
	{
		assert aFiber.executionState().indicatesSuspension();
		// We cannot simply run the specified function, we must queue a task to
		// run when Level One safety is no longer required.
		AvailRuntime.current().whenLevelOneUnsafeDo(
			AvailTask.forFiberResumption(
				aFiber,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						final Interpreter interpreter = current();
						assert aFiber == interpreter.fiber;
						assert aFiber.executionState() == RUNNING;
						// Set up the interpreter.
						continuation.value(interpreter);
						// Run the interpreter for a while.
						interpreter.run();
						final ExecutionState state = aFiber.executionState();
						switch (state)
						{
							case TERMINATED:
								// If the fiber has terminated, then report its
								// result via its result continuation.
								aFiber.resultContinuation().value(
									aFiber.fiberResult());
								break;
							case SUSPENDED:
							case INTERRUPTED:
							case PARKED:
							case JOINING:
							case ASLEEP:
								// No further action is required if the fiber is
								// suspended in some fashion.
								break;
							case UNSTARTED:
							case RUNNING:
							case ABORTED:
							default:
								// These are illegal states at this point.
								assert false;
						}
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
	 * @param aFiber
	 *        The fiber to run.
	 * @param function
	 *        A {@linkplain FunctionDescriptor function} to run.
	 * @param arguments
	 *        The arguments for the function.
	 */
	public static void runOutermostFunction (
		final AvailObject aFiber,
		final AvailObject function,
		final List<AvailObject> arguments)
	{
		assert aFiber.executionState() == UNSTARTED;
		executeFiber(
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
					final Result result =
						interpreter.invokeFunction(function, arguments);
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
	public static void resumeFromInterrupt (final AvailObject aFiber)
	{
		assert aFiber.executionState() == INTERRUPTED;
		assert !aFiber.continuation().equalsNil();
		executeFiber(
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
					// Keep the fiber's current continuation clear
					// during execution.
					aFiber.continuation(NilDescriptor.nil());
					interpreter.exitNow = false;
				}
			});
	}

	/**
	 * Schedule resumption of the specified {@linkplain FiberDescriptor fiber}
	 * following {@linkplain ExecutionState#SUSPENDED suspension} by a
	 * {@linkplain Primitive primitive}. This method is an entry point.
	 *
	 * <p>If the function successfully runs to completion, then the fiber's
	 * "on success" {@linkplain Continuation1 continuation} will be invoked with
	 * the function's result.</p>
	 *
	 * <p>If the function fails for any reason, then the fiber's "on failure"
	 * {@linkplain Continuation1 continuation} will be invoked with the
	 * terminal {@linkplain Throwable throwable}.</p>
	 *
	 * @param aFiber
	 *        The fiber to run.
	 * @param state
	 *        The {@linkplain Result#SUCCESS result state} of the primitive that
	 *        suspended the current fiber.
	 * @param result
	 *        The result of the primitive.
	 */
	public static void resumeFromPrimitive (
		final AvailObject aFiber,
		final Result state,
		final AvailObject result)
	{
		assert state == SUCCESS || state == FAILURE;
		assert !aFiber.continuation().equalsNil();
		assert aFiber.executionState() == SUSPENDED;
		executeFiber(
			aFiber,
			new Continuation1<Interpreter>()
			{
				@Override
				public void value (final @Nullable Interpreter interpreter)
				{
					assert interpreter != null;
					switch (state)
					{
						case SUCCESS:
							assert aFiber == interpreter.fiber;
							assert aFiber.executionState() == RUNNING;
							assert !aFiber.continuation().equalsNil();
							final AvailObject updatedCaller =
								aFiber.continuation().ensureMutable();
							final int stackp = updatedCaller.stackp();
							final AvailObject expectedType =
								updatedCaller.stackAt(stackp);
							if (!result.isInstanceOf(expectedType))
							{
								// TODO: [MvG] Remove after debugging.
								result.isInstanceOf(expectedType);
								error(String.format(
									"Return value (%s) does not agree with "
									+ "expected type (%s)",
									result,
									expectedType));
							}
							updatedCaller.stackAtPut(stackp, result);
							interpreter.prepareToResumeContinuation(
								updatedCaller);
							interpreter.exitNow = false;
							break;
						case FAILURE:
							interpreter.pointerAtPut(
								PRIMITIVE_FAILURE,
								result);
							interpreter.prepareToResumeContinuation(
								aFiber.continuation());
							interpreter.exitNow = false;
							break;
						default:
							assert false;
					}
				}
			});
	}

	/**
	 * Create a list of descriptions of the current stack frames ({@linkplain
	 * ContinuationDescriptor continuations}).
	 *
	 * @return A list of {@linkplain String strings}, starting with the newest
	 *         frame.
	 */
	public List<String> dumpStack ()
	{
		final List<AvailObject> frames = new ArrayList<AvailObject>(20);
		for (
			AvailObject c = currentContinuation();
			!c.equalsNil();
			c = c.caller())
		{
			frames.add(c);
		}
		final List<String> strings = new ArrayList<String>(frames.size());
		int line = frames.size();
		final StringBuilder signatureBuilder = new StringBuilder(1000);
		for (final AvailObject frame : frames)
		{
			final AvailObject code = frame.function().code();
			final AvailObject functionType = code.functionType();
			final AvailObject paramsType = functionType.argsTupleType();
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
			strings.add(String.format(
				"#%d: %s [%s] (%s:%d)",
				line--,
				code.methodName().asNativeString(),
				signatureBuilder.toString(),
				code.module().equalsNil() ? "?" : code.module().name()
					.asNativeString(),
				code.startingLineNumber()));
			signatureBuilder.setLength(0);
		}
		return strings;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(
			String.format(
				"%s [%s]",
				getClass().getSimpleName(),
				fiber().name()));
		final List<String> stack = dumpStack();
		for (final String frame : stack)
		{
			builder.append(
				String.format(
					"%n\t-- %s",
					frame));
		}
		builder.append("\n\n");
		return builder.toString();
	}
}
