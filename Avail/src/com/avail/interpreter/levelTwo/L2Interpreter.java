/**
 * L2Interpreter.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.levelTwo;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static java.lang.Math.max;
import java.util.*;
import java.util.logging.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;

/**
 * This class is used to execute {@linkplain L2ChunkDescriptor level two code},
 * which is a translation of the level one nybblecodes found in {@linkplain
 * CompiledCodeDescriptor compiled code}.
 *
 * <p>
 * Level one nybblecodes are designed to be compact and very simple, but not
 * particularly efficiently executable.  Level two is designed for a clean model
 * for optimization, including:
 * <ul>
 * <li>primitive folding.</li>
 * <li>register coloring/allocation.</li>
 * <li>inlining.</li>
 * <li>common sub-expression elimination.</li>
 * <li>side-effect analysis.</li>
 * <li>object escape analysis.</li>
 * <li>a variant of keyhole optimization that involves building the loosest
 * possible level two instruction dependency graph, then "pulling" eligible
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
 * To accomplish these goals, the stack-oriented architecture of level one maps
 * onto a register transfer language for level two.  At runtime the idealized
 * {@linkplain L2Interpreter interpreter} has an arbitrarily large bank of
 * pointer registers (that point to {@linkplain AvailObject Avail objects}),
 * plus a separate bank for {@code int}s (unboxed 32-bit signed integers), and
 * a similar bank yet for {@code double}s (unboxed double-precision floating
 * point numbers).  Ideally these will map to machine registers, but more likely
 * they will spill into physical arrays of the appropriate type.  Register
 * spilling is a well studied art, and essentially a solved problem.  Better
 * yet, the Java HotSpot optimizer should be able to do at least as good a job
 * as we can, so we should be able to just generate Java bytecodes and leave it
 * at that.
 * </p>
 *
 * <p>
 * One of the less intuitive aspects of the level one / level two mapping is how
 * to handle the call stack.  The level one view is of a chain of continuations,
 * but level two doesn't even have a stack!  We bridge this disconnect by
 * reserving a register to hold the level one continuation of the
 * <em>caller</em> of the current method.  This is at least vaguely analogous to
 * the way that high level languages typically implement their calling
 * conventions using stack frames and such.
 * </p>
 *
 * <p>
 * However, our target is not assembly language (nor something that purports to
 * operate at that level in some platform-neutral way).  Instead, our target
 * language, level two, is designed for representing and performing
 * optimization.  With this in mind, the level two instruction set includes an
 * instruction that constructs a new continuation from a list of registers.  A
 * corresponding instruction "explodes" a continuation into registers reserved
 * as part of the calling convention (strictly enforced).  During transition
 * from caller to callee (and vice-versa), the only registers that hold usable
 * state are the "architectural" registers – those that hold the state of a
 * continuation being constructed or deconstructed.  This sounds brutally
 * inefficient, but time will tell.  Also, I have devised and implemented
 * mechanisms to allow deeper inlining than would normally be possible in a
 * traditional system, the explicit construction and deconstruction of
 * continuations being one such mechanism.
 * </p>
 *
 * <p>
 * Note that unlike languages like C and C++, optimizations below level one are
 * always transparent – other than observations about performance and memory
 * use.  Also note that this was a design constraint for Avail as far back as
 * 1993, after <span style="font-variant: small-caps;">Self</span>, but before
 * its technological successor Java.  The way in which this is accomplished (or
 * will be more fully accomplished) in Avail is by allowing the generated level
 * two code itself to define how to maintain the "accurate fiction" of a level
 * one interpreter.  If a method is inlined ten layers deep inside an outer
 * method, a non-inlined call from that inner method requires ten layers of
 * continuations to be constructed prior to the call (to accurately maintain the
 * fiction that it was always simply interpreting level one nybblecodes).  There
 * are ways to avoid or at least postpone this phase transition, but I don't
 * have any solid plans for introducing such a mechanism any time soon.
 * </p>
 *
 * <p>
 * Finally, note that the Avail control structures are defined in terms of
 * multimethod dispatch and continuation resumption.  As of 2011.05.09 they
 * are also <em>implemented</em> that way, but a goal is to perform object
 * escape analysis in such a way that it deeply favors chasing continuations.
 * If successful, a continuation resumption can basically be rewritten as a
 * jump, leading to a more traditional control flow in the typical case, which
 * should be much easier to further optimize (say with SSA) than code which
 * literally passes and resumes continuations.  In those cases that the
 * continuation actually escapes (say, if the continuations are used for
 * backtracking) then it can't dissolve into a simple jump – but it will still
 * execute correctly, just not as quickly.
 * </p>
 *
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
final public class L2Interpreter
extends Interpreter
{
	/** Whether to print detailed level one debug information. */
	public final static boolean debugL1 = false;

	/** Whether to print detailed level two debug information. */
	final static boolean debugL2 = false;

	/** Whether to print detailed level two method results. */
	final static boolean debugL2Results = false;

	/**
	 * Fake slots used to show stack traces in the Eclipse Java debugger.
	 */
	enum FakeStackTraceSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The chain of {@linkplain ContinuationDescriptor continuations} of the
		 * {@linkplain FiberDescriptor fiber} bound to this {@linkplain
		 * L2Interpreter interpreter}.
		 */
		FRAMES
	}

	/**
	 * Utility method for decomposing this object in the debugger.  See {@link
	 * AvailObjectFieldHelper} for instructions to enable this functionality in
	 * Eclipse.
	 *
	 * <p>In particular, an L2Interpreter should present (possible among other
	 * things) a complete stack trace of the current fiber, converting the
	 * deep continuation structure into a list of continuation substitutes
	 * that <em>do not</em> recursively print the caller chain.</p>
	 *
	 * @return An array of {@link AvailObjectFieldHelper} objects that help
	 *         describe the logical structure of the receiver to the debugger.
	 */
	public AvailObjectFieldHelper[] describeForDebugger()
	{
		final List<AvailObject> frames = new ArrayList<AvailObject>(50);
		AvailObject frame = pointers[CALLER.ordinal()];
		if (frame != null)
		{
			while (!frame.equalsNull())
			{
				frames.add(frame);
				frame = frame.caller();
			}
		}
		final AvailObject framesTuple =
			TupleDescriptor.fromCollection(frames);
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
	 * The {@link L2ChunkDescriptor} being executed.
	 */
	private AvailObject chunk;

	/**
	 * Return the currently executing {@linkplain L2ChunkDescriptor level two
	 * chunk}.
	 *
	 * @return
	 *            The {@linkplain L2ChunkDescriptor level two chunk} that is
	 *            currently being executed.
	 */
	@InnerAccess
	public AvailObject chunk ()
	{
		return chunk;
	}

	/**
	 * Start executing a new chunk.  The {@linkplain #offset} at which to
	 * execute must be set separately.
	 *
	 * <p>
	 * Note that the {@linkplain CompiledCodeDescriptor compiled code} is passed
	 * in because the {@linkplain L2ChunkDescriptor#unoptimizedChunk()
	 * default chunk} doesn't inherently know how many registers it needs
	 * – the answer depends on the level one compiled code being executed.
	 * </p>
	 *
	 * @param chunk
	 *            The {@linkplain L2ChunkDescriptor level two chunk} to start
	 *            executing.
	 * @param code
	 *            The {@linkplain CompiledCodeDescriptor compiled code} on whose
	 *            behalf to start executing the chunk.
	 */
	private void setChunk (
		final @NotNull AvailObject chunk,
		final @NotNull AvailObject code)
	{
		this.chunk = chunk;
		chunkWords = chunk.wordcodes();
		chunkVectors = chunk.vectors();
		makeRoomForChunkRegisters(chunk, code);
		L2ChunkDescriptor.moveToHead(chunk);

		if (logger.isLoggable(Level.FINER))
		{
			logger.finer(String.format(
				"executing new chunk (%d)", chunk.index()));
		}
	}

	/**
	 * The L2 instruction stream as a tuple of integers.
	 */
	private AvailObject chunkWords;

	/**
	 * This chunk's register vectors.  A register vector is a tuple of integers
	 * that represent {@link #pointers Avail object registers}.
	 */
	private AvailObject chunkVectors;

	/**
	 * The registers that hold {@linkplain AvailObject Avail objects}.
	 */
	private AvailObject[] pointers = new AvailObject[10];

	/**
	 * The 32-bit signed integer registers.
	 */
	private int[] integers = new int[10];

	/**
	 * The double-precision floating point registers.
	 */
	private double[] doubles = new double[10];

	/**
	 * The current pointer into {@link #chunkWords}, the level two instruction
	 * stream.
	 */
	private int offset;

	/**
	 * A reusable temporary buffer used to hold arguments during method
	 * invocations.
	 */
	public final List<AvailObject> argsBuffer = new ArrayList<AvailObject>();

	/**
	 * The {@link L1InstructionStepper} used to simulate execution of level
	 * one nybblecodes.
	 */
	public final L1InstructionStepper levelOneStepper =
		new L1InstructionStepper(this);

	/**
	 * Whether or not execution has completed.
	 */
	private boolean exitNow = false;

	/**
	 * The value returned by the outermost continuation.
	 */
	private AvailObject exitValue;

	@Override
	public void exitProcessWith (final AvailObject finalObject)
	{
		fiber.executionState(ExecutionState.TERMINATED);
		fiber.continuation(NullDescriptor.nullObject());
		exitNow = true;
		exitValue = finalObject;
		wipeObjectRegisters();
	}

	/**
	 * The current fiber has been asked to pause for an inter-nybblecode
	 * interrupt for some reason. It has possibly executed several more
	 * wordcodes since that time, to place the fiber into a state that's
	 * consistent with naive Level One execution semantics. That is, a naive
	 * Level One interpreter should be able to resume the fiber later. The
	 * continuation to use can be found in pointerAt(continuationIndex).
	 */
	public void processInterrupt ()
	{
		final int continuationIndex = nextWord();
		fiber.continuation(pointerAt(continuationIndex));
		// TODO[MvG/TLS]: Really process the interrupt, possibly causing a
		// context switch to another fiber.  The simplest approach is to queue
		// this fiber, then allow the thread pool to grab the highest priority
		// available fiber to run.
		fiber.clearInterruptRequestFlags();
		exitValue = NullDescriptor.nullObject();
		exitNow = true;
	}

	/**
	 * Construct a new {@link L2Interpreter}.
	 *
	 * @param runtime
	 *            An {@link AvailRuntime}.
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	public L2Interpreter (final @NotNull AvailRuntime runtime)
	{
		super(runtime);
		pointers[0] = NullDescriptor.nullObject();
	}

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
	 * Jump to a new position in the L2 wordcode stream.
	 *
	 * @param newOffset The new position in the L2 wordcode stream.
	 */
	public void offset (final int newOffset)
	{
		// System.out.printf("[#%d] %d -> %d%n", _chunk.index(), _offset,
		// newOffset);
		offset = newOffset;
	}

	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain AvailObject#pc() pc}
	 * (program counter).
	 *
	 * @return The subscript to use with {@link L2Interpreter#integerAt(int)}.
	 */
	public final static int pcRegister ()
	{
		// reserved
		return 1;
	}


	/**
	 * Answer the subscript of the integer register reserved for holding the
	 * current (virtualized) continuation's {@linkplain AvailObject#stackp()
	 * stackp} (stack pointer).  While in this register, the value refers to
	 * the exact pointer register number rather than the value that would be
	 * stored in a continuation's stackp slot, so adjustments must be made
	 * during reification and explosion of continuations.
	 *
	 * @return The subscript to use with {@link L2Interpreter#integerAt(int)}.
	 */
	public final static int stackpRegister ()
	{
		// reserved
		return 2;
	}


	/**
	 * Answer the subscript of the register holding the argument or local with
	 * the given index (e.g., the first argument is in register 4).
	 * The arguments come first, then the locals.
	 *
	 * @param argumentOrLocalNumber The one-based argument/local number.
	 * @return The subscript to use with {@link L2Interpreter#pointerAt(int)}.
	 */
	public final static int argumentOrLocalRegister (
		final int argumentOrLocalNumber)
	{
		// Skip the fixed registers.
		return FixedRegister.values().length + argumentOrLocalNumber - 1;
	}


	/**
	 * Answer the current continuation.
	 *
	 * @return The current continuation.
	 */
	public final AvailObject currentContinuation ()
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
	 * Read from an object register.  Register zero is reserved for read-only
	 * use, and always contains the {@linkplain NullDescriptor#nullObject() null
	 * object}.
	 *
	 * @param index The object register index.
	 * @return The object in the specified register.
	 */
	public AvailObject pointerAt (final int index)
	{
		// assert index >= 0;
		assert pointers[index] != null;
		return pointers[index];
	}

	/**
	 * Write to an object register.  Register zero is reserved for read-only
	 * use, and always contains the {@linkplain NullDescriptor#nullObject() null
	 * object}.
	 *
	 * @param index The object register index.
	 * @param anAvailObject The object to write to the specified register.
	 */
	public void pointerAtPut (
		final int index,
		final @NotNull AvailObject anAvailObject)
	{
		assert index > 0;
		assert anAvailObject != null;
		pointers[index] = anAvailObject;
	}

	/**
	 * Read from a {@linkplain FixedRegister fixed object register}.  Register
	 * zero is reserved for read-only use, and always contains the {@linkplain
	 * NullDescriptor#nullObject() null object}.
	 *
	 * @param fixedObjectRegister The fixed object register.
	 * @return The object in the specified register.
	 */
	public AvailObject pointerAt (
		final @NotNull FixedRegister fixedObjectRegister)
	{
		return pointerAt(fixedObjectRegister.ordinal());
	}

	/**
	 * Write to a fixed object register.  Register zero is reserved for
	 * read-only use, and always contains the {@linkplain
	 * NullDescriptor#nullObject() null object}.
	 *
	 * @param fixedObjectRegister The fixed object register.
	 * @param anAvailObject The object to write to the specified register.
	 */
	public void pointerAtPut (
		final @NotNull FixedRegister fixedObjectRegister,
		final @NotNull AvailObject anAvailObject)
	{
		pointerAtPut(fixedObjectRegister.ordinal(), anAvailObject);
	}

	/**
	 * Write a Java null to an object register.  Register zero is reserved for
	 * read-only use, and always contains the Avail {@linkplain
	 * NullDescriptor#nullObject() null object}.
	 *
	 * @param index The object register index to overwrite.
	 */
	public void clearPointerAt (final int index)
	{
		assert index > 0;
		pointers[index] = null;
	}

	/**
	 * Write {@code null} into each object register except the constant {@link
	 * FixedRegister#NULL} register.
	 */
	public void wipeObjectRegisters ()
	{
		if (chunk != null)
		{
			Arrays.fill(
				pointers,
				1,
				chunk().numObjects(),
				null);
		}
	}

	/**
	 * Read from an integer register.  The index is one-based.  Entry [0] is
	 * unused.
	 *
	 * @param index The one-based integer-register index.
	 * @return The int in the specified register.
	 */
	public int integerAt (final int index)
	{
		assert index > 0;
		return integers[index];
	}


	/**
	 * Write to an integer register.  The index is one-based.  Entry [0] is
	 * unused.
	 *
	 * @param index The one-based integer-register index.
	 * @param value The value to write to the register.
	 */
	public void integerAtPut (final int index, final int value)
	{
		assert index > 0;
		integers[index] = value;
	}

	/**
	 * Answer the vector in the current chunk which has the given index.  A
	 * vector (at runtime) is simply a tuple of integers.
	 *
	 * @param index The vector's index.
	 * @return A tuple of integers.
	 */
	public AvailObject vectorAt (final int index)
	{
		return chunkVectors.tupleAt(index);
	}


	/**
	 * Return into the specified continuation with the given return value.
	 * Verify that the return value matches the expected type already pushed
	 * on the continuation's stack.  If the continuation is {@linkplain
	 * NullDescriptor#nullObject() the null object} then make sure the value
	 * gets returned from the main interpreter invocation.
	 *
	 * @param caller
	 *            The {@linkplain ContinuationDescriptor continuation} to
	 *            resume, or the null object.
	 * @param value
	 *            The {@link AvailObject} to return.
	 */
	public void returnToCaller (
		final AvailObject caller,
		final AvailObject value)
	{
		// Wipe out the existing registers for safety.  This is technically
		// optional, but not doing so may (1) hide bugs, and (2) leak
		// references to values in registers.
		wipeObjectRegisters();
		if (caller.equalsNull())
		{
			exitProcessWith(value);
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

	@Override
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
		setChunk(chunkToResume, continuation.function().code());
		offset(continuation.levelTwoOffset());
	}

	@Override
	public void fixupForPotentiallyInvalidCurrentChunk ()
	{
		prepareToResumeContinuation(pointerAt(CALLER));
	}

	@Override
	public void prepareToRestartContinuation (
		final AvailObject continuationToRestart)
	{
		AvailObject chunkToRestart = continuationToRestart.levelTwoChunk();
		if (!chunkToRestart.isValid())
		{
			// The chunk has become invalid, so use the default chunk and tweak
			// the continuation's chunk information.
			chunkToRestart = L2ChunkDescriptor.unoptimizedChunk();
			continuationToRestart.levelTwoChunkOffset(
				chunkToRestart,
				1);
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
	 * @param theChunk The {@linkplain L2ChunkDescriptor L2Chunk} about to be
	 *                 invoked.
	 * @param theCode The code about to be invoked.
	 */
	private void makeRoomForChunkRegisters (
		final AvailObject theChunk,
		final AvailObject theCode)
	{
		final int neededObjectCount = max(
			theChunk.numObjects(),
			FixedRegister.values().length + theCode.numArgsAndLocalsAndStack());
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

	@Override
	public Result searchForExceptionHandler (
		final @NotNull AvailObject exceptionValue)
	{
		AvailObject continuation = pointerAt(CALLER);
		while (!continuation.equalsNull())
		{
			if (continuation.function().code().primitiveNumber() == 200)
			{
				final AvailObject handlerTuple =
					continuation.argOrLocalOrStackAt(2);
				assert handlerTuple.isTuple();
				for (final AvailObject handler : handlerTuple)
				{
					if (exceptionValue.isInstanceOf(
						handler.kind().argsTupleType().typeAtIndex(1)))
					{
						prepareToResumeContinuation(continuation);
						invokeFunctionArguments(
							handler,
							Collections.singletonList(exceptionValue));
						// Catching an exception *always* changes the
						// continuation.
						return CONTINUATION_CHANGED;
					}
				}
			}
			continuation = continuation.caller();
		}
		return primitiveFailure(exceptionValue);
	}

	@Override
	public Result invokeFunctionArguments (
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
			if (result == SUCCESS || result == CONTINUATION_CHANGED)
			{
				return result;
			}
			// The primitive failed.
			assert !Primitive.byPrimitiveNumber(primNum).hasFlag(
				Flag.CannotFail);
			invokeWithoutPrimitiveFunctionArguments(
				aFunction,
				args,
				caller);
			pointerAtPut(PRIMITIVE_FAILURE, primitiveResult);
			return CONTINUATION_CHANGED;
		}
		// It wasn't a primitive.
		invokeWithoutPrimitiveFunctionArguments(
			aFunction,
			args,
			caller);
		return CONTINUATION_CHANGED;
	}

	@Override
	public void invokeWithoutPrimitiveFunctionArguments (
		final @NotNull AvailObject aFunction,
		final List<AvailObject> args,
		final @NotNull AvailObject caller)
	{
		final AvailObject code = aFunction.code();
		code.tallyInvocation();
		AvailObject chunkToInvoke = code.startingChunk();
		if (!chunkToInvoke.isValid())
		{
			// The chunk is invalid, so use the default chunk and patch up
			// aFunction's code.
			chunkToInvoke = L2ChunkDescriptor.unoptimizedChunk();
			code.startingChunk(chunkToInvoke);
			code.countdownToReoptimize(
				L2ChunkDescriptor.countdownForInvalidatedCode());
		}
		wipeObjectRegisters();
		setChunk(chunkToInvoke, code);
		offset(1);

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
	 * Start or complete execution of the specified function.  The function is
	 * permitted to be primitive.  The current continuation must already have
	 * been reified.  Since that's the case, we can clobber all registers, as
	 * long as the {@link FixedRegister#CALLER} is set appropriately afterward.
	 *
	 * @param theFunction
	 *            The function to invoke.
	 * @param caller
	 *            The calling continuation.
	 */
	public void invokePossiblePrimitiveWithReifiedCaller (
		final @NotNull AvailObject theFunction,
		final @NotNull AvailObject caller)
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
				case SUSPENDED:
					return;
				case SUCCESS:
					assert chunk.isValid();
					final AvailObject updatedCaller = caller.ensureMutable();
					final int stackp = updatedCaller.stackp();
					final AvailObject expectedType =
						updatedCaller.stackAt(stackp);
					if (!primitiveResult.isInstanceOf(expectedType))
					{
						// TODO: [MvG] Remove after debugging.
						primitiveResult.isInstanceOf(expectedType);
						error(String.format(
							"Return value (%s) does not agree with "
							+ "expected type (%s)",
							primitiveResult,
							expectedType));
					}
					updatedCaller.stackAtPut(stackp, primitiveResult);
					pointerAtPut(CALLER, updatedCaller);
					setChunk(
						updatedCaller.levelTwoChunk(),
						updatedCaller.function().code());
					offset(updatedCaller.levelTwoOffset());
					return;
				case FAILURE:
					invokeWithoutPrimitiveFunctionArguments(
						theFunction,
						argsBuffer,
						caller);
					pointerAtPut(PRIMITIVE_FAILURE, primitiveResult);
					return;
			}
		}
		invokeWithoutPrimitiveFunctionArguments(
			theFunction,
			argsBuffer,
			caller);
	}

	/**
	 * Run the interpreter until the outermost function returns, answering the
	 * result it returns.  Assume the interpreter has already been set up to run
	 * something other than a (successful) primitive function at the outermost
	 * level.
	 *
	 * @return The final result produced by outermost function.
	 */
	private AvailObject run ()
	{
		exitNow = false;
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
				while (!chain.equalsNull())
				{
					stackString.insert(0, "->");
					stackString.insert(
						0,
						argumentOrLocalRegister(chain.stackp()));
					chain = chain.caller();
				}

				logger.finest(String.format(
					"executing %s (pc = %d) [stack: %s]",
					operation,
					offset - 1,
					stackString));
			}

			if (debugL2)
			{
				int depth = 0;
				for (
					AvailObject c = pointerAt(CALLER);
					!c.equalsNull();
					c = c.caller())
				{
					depth++;
				}
				System.out.printf(
					"%nStep L2 (d=%d, #%d, off=%d): %s",
					depth,
					chunk.index(),
					offset,
					operation.name());
			}
			operation.step(this);
		}
		if (debugL2Results)
		{
			System.out.printf("%nOutput --> %s%n", exitValue);
		}
		return exitValue;
	}

	@Override
	public AvailObject runFunctionArguments (
		final AvailObject function,
		final List<AvailObject> arguments)
	{
		pointerAtPut(CALLER, NullDescriptor.nullObject());
		clearPointerAt(FUNCTION.ordinal());
		// Keep the fiber's current continuation clear during execution.
		fiber.continuation(NullDescriptor.nullObject());

		final Result result = invokeFunctionArguments(function, arguments);
		if (result == SUCCESS)
		{
			// Outermost call was a primitive invocation.
			return primitiveResult;
		}
		return run();
	}

	/**
	 * Extract the next word from the level two instruction stream.
	 *
	 * @return The word.
	 */
	public int nextWord ()
	{
		final int theOffset = offset();
		final int word = chunkWords.tupleIntAt(theOffset);
		offset(theOffset + 1);
		return word;
	}
}
