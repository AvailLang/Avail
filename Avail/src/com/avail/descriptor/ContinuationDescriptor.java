/**
 * ContinuationDescriptor.java
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

package com.avail.descriptor;

import java.util.*;
import static com.avail.descriptor.ContinuationDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ContinuationDescriptor.ObjectSlots.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Interpreter;

/**
 * A {@linkplain ContinuationDescriptor continuation} acts as an immutable
 * execution stack.  A running {@linkplain ProcessDescriptor process}
 * conceptually operates by repeatedly replacing its continuation with a new one
 * (i.e., one derived from the previous state by nybblecode execution rules),
 * performing necessary side-effects as it does so.
 *
 * <p>
 * A continuation can be {@linkplain
 * Primitive#prim57_ExitContinuationWithResult exited}, which causes
 * the current process's continuation to be replaced by the specified
 * continuation's caller.  A return value is supplied to this caller.  A
 * continuation can also be {@linkplain
 * Primitive#prim56_RestartContinuationWithArguments restarted},
 * either with a specified tuple of arguments or {@linkplain
 * Primitive#prim58_RestartContinuation with the original arguments}.
 * </p>
 *
 * @author Mark van Gulik&lt;ghoul137@gmail.com&gt;
 */
public class ContinuationDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * A composite field containing the {@linkplain #PROGRAM_COUNTER program
		 * counter} and {@linkplain #STACK_POINTER stack pointer}.
		 */
		PROGRAM_COUNTER_AND_STACK_POINTER,

		/**
		 * The Level Two {@linkplain L2ChunkDescriptor.ObjectSlots#WORDCODES
		 * wordcode} index at which to resume.
		 */
		LEVEL_TWO_OFFSET;


		/**
		 * The index into the current continuation's {@linkplain
		 * ObjectSlots#FUNCTION function's} compiled code's tuple of nybblecodes
		 * at which execution will next occur.
		 */
		static BitField PROGRAM_COUNTER = bitField(
			PROGRAM_COUNTER_AND_STACK_POINTER,
			16,
			16);

		/**
		 * An index into this continuation's {@linkplain ObjectSlots#FRAME_AT_
		 * frame slots}.  It grows from the top + 1 (empty stack), and at its
		 * deepest it just abuts the last local variable.
		 */
		static BitField STACK_POINTER = bitField(
			PROGRAM_COUNTER_AND_STACK_POINTER,
			0,
			16);

	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The continuation that invoked this one, or the {@linkplain
		 * NullDescriptor#nullObject() null object} for the outermost
		 * continuation. When a continuation is not directly created by a
		 * {@linkplain L1Operation#L1Ext_doPushLabel push-label instruction}, it
		 * will have a type pushed on it.  This type is checked against any
		 * value that the callee attempts to return to it. This supports
		 * link-time type strengthening at call sites.
		 */
		CALLER,

		/**
		 * The {@linkplain FunctionDescriptor function} being executed via this
		 * continuation.
		 */
		FUNCTION,

		/**
		 * The {@linkplain L2ChunkDescriptor Level Two chunk} which can be
		 * resumed directly by the {@link L2Interpreter} to effect continued
		 * execution.
		 */
		LEVEL_TWO_CHUNK,

		/**
		 * The slots allocated for locals, arguments, and stack entries.  The
		 * arguments are first, then the locals, and finally the stack entries
		 * (growing downwards from the top).  At its deepest, the stack slots
		 * will abut the last local.
		 */
		FRAME_AT_
	}


	@Override @AvailMethod
	void o_Caller (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(CALLER, value);
	}

	@Override @AvailMethod
	void o_Function (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(FUNCTION, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ArgOrLocalOrStackAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(FRAME_AT_, subscript);
	}

	@Override @AvailMethod
	void o_ArgOrLocalOrStackAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		object.setSlot(FRAME_AT_, subscript, value);
	}

	@Override @AvailMethod
	void o_Pc (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(PROGRAM_COUNTER, value);
	}

	@Override @AvailMethod
	void o_Stackp (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(STACK_POINTER, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Caller (
		final @NotNull AvailObject object)
	{
		return object.slot(CALLER);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Function (
		final @NotNull AvailObject object)
	{
		return object.slot(FUNCTION);
	}

	@Override @AvailMethod
	int o_Pc (
		final @NotNull AvailObject object)
	{
		return object.slot(PROGRAM_COUNTER);
	}

	@Override @AvailMethod
	int o_Stackp (
		final @NotNull AvailObject object)
	{
		return object.slot(STACK_POINTER);
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsContinuation(object);
	}

	@Override @AvailMethod
	boolean o_EqualsContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuation)
	{
		if (object.sameAddressAs(aContinuation))
		{
			return true;
		}
		if (!object.caller().equals(aContinuation.caller()))
		{
			return false;
		}
		if (!object.function().equals(aContinuation.function()))
		{
			return false;
		}
		if (object.pc() != aContinuation.pc())
		{
			return false;
		}
		if (object.stackp() != aContinuation.stackp())
		{
			return false;
		}
		for (int i = object.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			if (!object.argOrLocalOrStackAt(i)
					.equals(aContinuation.argOrLocalOrStackAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		int h = 0x593599A;
		h ^= object.caller().hash();
		h = h + object.function().hash() + object.pc() * object.stackp();
		for (int i = object.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			h = h * 23 + 0x221C9 ^ object.argOrLocalOrStackAt(i).hash();
		}
		return h;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return ContinuationTypeDescriptor.forFunctionType(
			object.function().kind());
	}

	/**
	 * Set both my chunk index and the offset into it.
	 */
	@Override @AvailMethod
	void o_LevelTwoChunkOffset (
		final @NotNull AvailObject object,
		final @NotNull AvailObject chunk,
		final int offset)
	{
		object.setSlot(LEVEL_TWO_CHUNK, chunk);
		object.setSlot(LEVEL_TWO_OFFSET, offset);
	}

	/**
	 * Read from the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_StackAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(FRAME_AT_, subscript);
	}

	/**
	 * Write to the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@Override @AvailMethod
	void o_StackAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject anObject)
	{
		object.setSlot(FRAME_AT_, subscript, anObject);
	}


	/**
	 * If immutable, copy the object as mutable, otherwise answer the original
	 * mutable continuation.  This is used by the {@linkplain L2Interpreter
	 * interpreter} to ensure it is always executing a mutable continuation and
	 * is therefore always able to directly modify it.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_EnsureMutable (
		final @NotNull AvailObject object)
	{
		return isMutable ? object : object.copyAsMutableContinuation();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LevelTwoChunk (
		final @NotNull AvailObject object)
	{
		return object.slot(LEVEL_TWO_CHUNK);
	}

	@Override @AvailMethod
	int o_LevelTwoOffset (
		final @NotNull AvailObject object)
	{
		return object.slot(LEVEL_TWO_OFFSET);
	}

	/**
	 * Answer the number of slots allocated for arguments, locals, and stack
	 * entries.
	 */
	@Override @AvailMethod
	int o_NumArgsAndLocalsAndStack (
		final @NotNull AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	/**
	 * The object was just scanned, and its pointers converted into valid
	 * ToSpace pointers.  Do any follow-up activities specific to the kind of
	 * object it is.
	 *
	 * <p>
	 * In particular, a Continuation object needs to bring its L2Chunk object
	 * into ToSpace and link it into the ring of saved chunks.  Chunks that are
	 * no longer accessed can be reclaimed, or at least their entries can be
	 * reclaimed, at flip time.
	 * </p>
	 */
	@Override @AvailMethod @Deprecated
	void o_PostFault (
		final @NotNull AvailObject object)
	{
		final AvailObject chunk = object.levelTwoChunk();
		if (chunk.isValid())
		{
			chunk.isSaved(true);
		}
		else
		{
			object.levelTwoChunkOffset(
				L2ChunkDescriptor.unoptimizedChunk(),
				L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
		}
	}

	/**
	 * Answer a fresh mutable copy of the given continuation object.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_CopyAsMutableContinuation (
		final @NotNull AvailObject object)
	{
		if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final AvailObject result = mutable().create(
			object.variableObjectSlotsCount());
		assert result.objectSlotsCount() == object.objectSlotsCount();
		result.setSlot(CALLER, object.slot(CALLER));
		result.setSlot(FUNCTION, object.slot(FUNCTION));
		result.pc(object.pc());
		result.stackp(object.stackp());
		result.levelTwoChunkOffset(
			object.levelTwoChunk(),
			object.levelTwoOffset());
		for (int i = object.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			result.argOrLocalOrStackAtPut(i, object.argOrLocalOrStackAt(i));
		}
		return result;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == LEVEL_TWO_OFFSET
			|| e == LEVEL_TWO_CHUNK;
	}

	/**
	 * A substitute for the {@linkplain AvailObject null object}, for use by
	 * {@link Primitive#prim59_ContinuationStackData}.
	 */
	private static AvailObject nullSubstitute;

	/**
	 * Answer a substitute for the {@linkplain AvailObject null object}. This is
	 * primarily for use by {@link Primitive#prim59_ContinuationStackData}.
	 *
	 * @return An immutable bottom-typed variable.
	 */
	public static @NotNull AvailObject nullSubstitute ()
	{
		return nullSubstitute;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		nullSubstitute = VariableDescriptor.forInnerType(
			BottomTypeDescriptor.bottom()).makeImmutable();
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		nullSubstitute = null;
	}

	/**
	 * Create a new continuation with the given data.  The continuation should
	 * represent the state upon entering the new context - i.e., set the pc to
	 * the first instruction, clear the stack, and set up all local variables.
	 *
	 * @param function The function being invoked.
	 * @param caller The calling continuation.
	 * @param startingChunk The level two chunk to invoke.
	 * @param startingOffset The offset into the chunk at which to resume.
	 * @param args The {@link List} of arguments
	 * @param locals The {@link List} of (non-argument) local variables.
	 * @return The new continuation.
	 */
	public static AvailObject create (
		final @NotNull AvailObject function,
		final @NotNull AvailObject caller,
		final @NotNull AvailObject startingChunk,
		final int startingOffset,
		final @NotNull List<AvailObject> args,
		final @NotNull List<AvailObject> locals)
	{
		final ContinuationDescriptor descriptor = mutable();
		final AvailObject code = function.code();
		final AvailObject cont = descriptor.create(
			code.numArgsAndLocalsAndStack());
		cont.setSlot(CALLER, caller);
		cont.setSlot(FUNCTION, function);
		cont.pc(1);
		cont.stackp(
			cont.objectSlotsCount() + 1 - descriptor.numberOfFixedObjectSlots);
		cont.levelTwoChunkOffset(startingChunk, startingOffset);
		for (int i = code.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			cont.argOrLocalOrStackAtPut(i, NullDescriptor.nullObject());
		}
		//  Set up arguments...
		final int nArgs = args.size();
		assert nArgs == code.numArgs();
		for (int i = 1; i <= nArgs; i++)
		{
			//  arguments area
			cont.argOrLocalOrStackAtPut(i, args.get(i - 1));
		}
		final int nLocals = locals.size();
		assert nLocals == code.numLocals();
		for (int i = 1; i <= nLocals; i++)
		{
			//  non-argument locals
			cont.argOrLocalOrStackAtPut(nArgs + i, locals.get(i - 1));
		}
		return cont;
	}

	/**
	 * Construct a new {@link ContinuationDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ContinuationDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ContinuationDescriptor}.
	 */
	private static final ContinuationDescriptor mutable =
		new ContinuationDescriptor(true);

	/**
	 * Answer the mutable {@link ContinuationDescriptor}.
	 *
	 * @return The mutable {@link ContinuationDescriptor}.
	 */
	public static ContinuationDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ContinuationDescriptor}.
	 */
	private static final ContinuationDescriptor immutable =
		new ContinuationDescriptor(false);

	/**
	 * Answer the immutable {@link ContinuationDescriptor}.
	 *
	 * @return The immutable {@link ContinuationDescriptor}.
	 */
	public static ContinuationDescriptor immutable ()
	{
		return immutable;
	}
}
