/**
 * L2Chunk.java
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

package com.avail.interpreter.levelTwo;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.avail.descriptor.*;
import com.avail.optimizer.L2Translator;
import com.avail.interpreter.levelTwo.operation.L2_LABEL;
import com.avail.interpreter.levelTwo.register.*;
import javax.annotation.Nullable;

/**
 * A Level Two chunk represents an optimized implementation of a {@linkplain
 * CompiledCodeDescriptor compiled code object}.
 *
 * <p>
 * The chunks are held onto by weak references in a list (keyed by chunk index).
 * When a chunk expires due to not being referred to by any code or
 * continuations, its weak reference is added to a queue from which chunk index
 * recycling takes place.  The weak references also keep track of the contingent
 * methods.  The methods maintain the reverse relation
 * by keeping track of the indices of all chunks that depend on them.  When an
 * method changes (due to a method being added or removed), the
 * dependent chunks can be marked as invalid and eviscerated (to reclaim
 * memory).  When an attempt is made to use an invalidated chunk by invoking a
 * compiled code object or returning into a continuation, the reference to the
 * chunk is replaced by a reference to the default chunk (and a continuation
 * gets its offset set to the level one dispatch loop).  When all references to
 * the chunk have been so replaced, the chunk's weak reference will appear on
 * the ReferenceQueue, allowing the index to be recycled.
 * </p>
 *
 * <p>
 * Eventually we can limit the number of valid chunks by linking the weak
 * references together into an LRU ring.  When adding a new chunk to a "full"
 * ring, the oldest element can simply be invalidated, removing it from the
 * ring.  The level two interpreter is already instrumented to call
 * moveToHead() at appropriate times.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2Chunk
{
	/**
	 * The number of {@linkplain L2ObjectRegister object registers} that
	 * this chunk uses (including the fixed registers).  Having the number of
	 * needed object registers stored separately allows the register list to be
	 * dynamically expanded as needed only when starting or resuming a
	 * {@link ContinuationDescriptor continuation}.
	 */
	final int numObjects;

	/**
	 * The number of {@linkplain L2IntegerRegister integer registers} that
	 * are used by this chunk. Having this recorded separately allows the
	 * register list to be dynamically expanded as needed only when starting
	 * or resuming a continuation.
	 */
	final int numIntegers;

	/**
	 * The number of {@linkplain L2FloatRegister floating point registers}
	 * that are used by this chunk. Having this recorded separately allows
	 * the register list to be dynamically expanded as needed only when
	 * starting or resuming a continuation.
	 */
	final int numDoubles;

	/**
	 * A flag indicating whether this chunk is valid or if it has been
	 * invalidated by the addition or removal of a method signature.
	 */
	boolean valid;

	/**
	 * The set of {@linkplain A_ChunkDependable contingent values} on which
	 * this chunk depends. If one of these changes significantly, this chunk
	 * must be invalidated (at which time this set will be emptied).
	 */
	A_Set contingentValues;

	/**
	 * The sequence of {@link L2Instruction}s that make up this L2Chunk.
	 */
	public L2Instruction[] instructions;

	/**
	 * The sequence of {@link L2Instruction}s that should be <em>executed</em>
	 * for this L2Chunk.  Non-executable instructions like {@link L2_LABEL}s
	 * have been stripped out.  The original instruction sequence is still
	 * present in {@link #instructions}, which is suitable for inlining into
	 * callers.
	 */
	public final L2Instruction[] executableInstructions;

	/**
	 * Answer the Avail {@linkplain PojoDescriptor pojo} associated with this
	 * L2Chunk.
	 */
	public final AvailObject chunkPojo =
		RawPojoDescriptor.identityWrap(this).makeShared();

	/**
	 * Answer the number of floating point registers used by this chunk.
	 *
	 * @return The count of float registers.
	 */
	public int numDoubles ()
	{
		return numDoubles;
	}

	/**
	 * Answer the number of integer registers used by this chunk.
	 *
	 * @return The count of integer registers.
	 */
	public int numIntegers ()
	{
		return numIntegers;
	}

	/**
	 * Answer the number of object registers used by this chunk.
	 *
	 * @return The count of object registers.
	 */
	public int numObjects ()
	{
		return numObjects;
	}

	/**
	 * Answer whether this chunk is still valid.  A {@linkplain
	 * ContinuationDescriptor continuation} or {@linkplain
	 * CompiledCodeDescriptor raw function} may refer to an invalid chunk, but
	 * attempts to resume or invoke (respectively) such a chunk are detected and
	 * cause the {@link #unoptimizedChunk()} to be substituted instead.  We
	 * don't have to worry about an Interpreter holding onto a chunk when it
	 * becomes invalid, because invalidation can only happen when the runtime
	 * temporarily inhibits running Avail code, and all fibers have had their
	 * continuation reified to a level-one-coherent state.
	 *
	 * @return Whether this chunk is still valid.
	 */
	public boolean isValid ()
	{
		return valid;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		if (this == unoptimizedChunk)
		{
			return "Default chunk";
		}
		builder.append(String.format(
			"Chunk #%08x%n",
			System.identityHashCode(this)));
		if (!isValid())
		{
			builder.append("\t(INVALID)\n");
		}
		final L2InstructionDescriber describer =
			new L2InstructionDescriber();
		for (final L2Instruction instruction : instructions)
		{
			builder.append(String.format("\t#%-3d ", instruction.offset()));
			final StringBuilder tempStream = new StringBuilder(100);
			describer.describe(instruction, this, tempStream);
			builder.append(tempStream.toString().replace("\n", "\n\t\t"));
			builder.append("\n");
		}
		return builder.toString();
	}

	/**
	 * The level two wordcode offset to which to jump when returning into a
	 * continuation that's running the {@linkplain #unoptimizedChunk unoptimized
	 * chunk}.
	 *
	 * @return A level two wordcode offset.
	 */
	public static int offsetToContinueUnoptimizedChunk ()
	{
		// This is hard-coded, but cross-checked by
		// L2Translator#createChunkForFirstInvocation().
		return 6;
	}

	/**
	 * Return the number of times to invoke a {@linkplain CompiledCodeDescriptor
	 * compiled code} object, <em>after an invalidation</em>, before attempting
	 * to optimize it again.
	 *
	 * @return The number of invocations before post-invalidate reoptimization.
	 */
	public static int countdownForInvalidatedCode ()
	{
		return 10;
	}

	/**
	 * Return the number of times to invoke a {@linkplain CompiledCodeDescriptor
	 * compiled code} object, <em>after creation</em>, before attempting to
	 * optimize it for the first time.
	 *
	 * @return The number of invocations before initial optimization.
	 */
	public static int countdownForNewCode ()
	{
		return 10;
	}

	/**
	 * Return the number of times to invoke a {@linkplain CompiledCodeDescriptor
	 * compiled code} object, <em>after optimization</em>, before attempting to
	 * optimize it again with more effort.
	 *
	 * @return The number of invocations before attempting to improve the
	 *         optimization.
	 */
	public static int countdownForNewlyOptimizedCode ()
	{
		// TODO: [MvG] Set this to something sensible when optimization levels
		// are implemented.
		return 1_000_000_000;
	}

	/**
	 * The {@linkplain ReentrantLock lock} that protects invalidation of chunks
	 * due to {@linkplain MethodDescriptor method} changes from interfering
	 * with each other.  The alternative to a global lock seems to imply
	 * deadlock conditions.
	 */
	public static final ReentrantLock invalidationLock = new ReentrantLock();

	/**
	 * Allocate and set up a new {@linkplain L2Chunk level two chunk} with the
	 * given information. If {@code code} is non-null, set it up to use the new
	 * chunk for subsequent invocations.
	 *
	 * @param code
	 *        The {@linkplain CompiledCodeDescriptor code} for which to use the
	 *        new level two chunk, or null for the initial unoptimized chunk.
	 * @param numObjects
	 *        The number of {@linkplain L2ObjectRegister object registers} that
	 *        this chunk will require.
	 * @param numIntegers
	 *        The number of {@linkplain L2IntegerRegister integer registers}
	 *        that this chunk will require.
	 * @param numFloats
	 *        The number of {@linkplain L2FloatRegister floating point
	 *        registers} that this chunk will require.
	 * @param theInstructions
	 *        A {@link List} of {@link L2Instruction}s that prescribe what to do
	 *        in place of the level one nybblecodes.  These are not normally
	 *        executed, but they're suitable for inlining.
	 * @param executableInstructions
	 *        A {@link List} of {@link L2Instruction}s that can be executed in
	 *        place of the level one nybblecodes.
	 * @param contingentValues
	 *        A {@link Set} of {@linkplain MethodDescriptor methods} on which
	 *        the level two chunk depends.
	 * @return The new level two chunk.
	 */
	public static L2Chunk allocate (
		final @Nullable A_RawFunction code,
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final List<L2Instruction> theInstructions,
		final List<L2Instruction> executableInstructions,
		final A_Set contingentValues)
	{
		final L2Chunk chunk = new L2Chunk(
			numObjects,
			numIntegers,
			numFloats,
			theInstructions,
			executableInstructions,
			contingentValues);
		if (code != null)
		{
			code.setStartingChunkAndReoptimizationCountdown(
				chunk,
				L2Chunk.countdownForNewlyOptimizedCode());
		}
		for (final A_ChunkDependable value : contingentValues)
		{
			value.addDependentChunk(chunk);
		}
		return chunk;
	}

	/**
	 * Create a new {@linkplain L2Chunk level two chunk} with the given
	 * information.
	 *
	 * @param numObjects The number of object registers needed.
	 * @param numIntegers The number of integer registers needed.
	 * @param numFloats The number of float registers needed.
	 * @param theInstructions The instructions that can be inlined into callers.
	 * @param executableInstructions The actual instructions to execute.
	 * @param contingentValues The set of contingent {@link A_ChunkDependable}.
	 */
	private L2Chunk (
		final int numObjects,
		final int numIntegers,
		final int numFloats,
		final List<L2Instruction> theInstructions,
		final List<L2Instruction> executableInstructions,
		final A_Set contingentValues)
	{
		// A new chunk starts out valid.
		this.valid = true;
		this.numObjects = numObjects;
		this.numIntegers = numIntegers;
		this.numDoubles = numFloats;
		this.instructions = theInstructions.toArray(
			new L2Instruction[theInstructions.size()]);
		this.executableInstructions = executableInstructions.toArray(
			new L2Instruction[executableInstructions.size()]);
		this.contingentValues = contingentValues;
	}

	/**
	 * Something that this {@linkplain L2Chunk Level Two chunk} depended on has
	 * changed. This must have been because it was optimized in a way that
	 * relied on some aspect of the available definitions (e.g., monomorphic
	 * inlining), so we need to invalidate the chunk now, so that an attempt to
	 * invoke it or return into it will be detected and converted into using the
	 * {@linkplain #unoptimizedChunk unoptimized chunk}. Also remove this
	 * chunk from the contingent set of each object on which it was depending.
	 *
	 * <p>
	 * This can only happen when L2 execution is suspended, due to a method
	 * changing (TODO[MvG] - we'll have to consider "observed" variables
	 * changing at some point).  The {@link #invalidationLock} must be acquired
	 * by the caller to ensure safe manipulation of the dependency information.
	 * </p>
	 */
	public void invalidate ()
	{
		assert invalidationLock.isHeldByCurrentThread();
		valid = false;
		instructions = new L2Instruction[0];
		final A_Set contingents = contingentValues.makeImmutable();
		contingentValues = SetDescriptor.empty();
		for (final A_ChunkDependable value : contingents)
		{
			value.removeDependentChunk(this);
		}
	}

	/**
	 * The special {@linkplain L2Chunk level two chunk} that is used to
	 * interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some number of
	 * times.
	 */
	private static final L2Chunk unoptimizedChunk =
		L2Translator.createChunkForFirstInvocation();

	/**
	 * Return the special {@linkplain L2Chunk level two chunk} that is used to
	 * interpret level one nybblecodes until a piece of {@linkplain
	 * CompiledCodeDescriptor compiled code} has been executed some threshold
	 * number of times.
	 *
	 * @return The special {@linkplain #unoptimizedChunk unoptimized chunk}.
	 */
	public static L2Chunk unoptimizedChunk ()
	{
		return unoptimizedChunk;
	}
}
