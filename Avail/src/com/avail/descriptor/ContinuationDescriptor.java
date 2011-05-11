/**
 * descriptor/ContinuationDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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
 * Primitive#prim57_ExitContinuationWithResult_con_result exited}, which causes
 * the current process's continuation to be replaced by the specified
 * continuation's caller.  A return value is supplied to this caller.  A
 * continuation can also be {@linkplain
 * Primitive#prim56_RestartContinuationWithArguments_con_arguments restarted},
 * either with a specified tuple of arguments or {@linkplain
 * Primitive#prim58_RestartContinuation_con with the original arguments}.
 * </p>
 *
 * <p>
 * TODO: Support labels in arbitrary locations perhaps.  This would be the most
 * general continuation type, above all the rest.  Its only supported operation
 * would be a new Resume primitive that took no other arguments.
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
	public enum IntegerSlots
	{
		/**
		 * The index into the current continuation's {@linkplain
		 * ObjectSlots#CLOSURE closure's} compiled code's
		 * tuple of nybblecodes at which execution will next occur.
		 */
		PC,

		/**
		 * An index into this continuation's {@linkplain ObjectSlots#FRAME_AT_
		 * frame slots}.  It grows from the top + 1 (empty stack), and at its
		 * deepest it just abuts the last local variable.
		 */
		STACK_POINTER,

		/**
		 * An integer consisting of two unsigned shorts.  The high short is the
		 * index of the {@linkplain L2ChunkDescriptor Level Two chunk} which can
		 * be resumed directly to effect continued execution.  The low short is
		 * the Level Two program counter at which to resume.
		 */
		@BitFields(describedBy=HiLevelTwoChunkLowOffset.class)
		HI_LEVEL_TWO_CHUNK_LOW_OFFSET
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The continuation that invoked this one, or the {@link VoidDescriptor
		 * void object} for the outermost continuation.  When a continuation is
		 * not directly created by a {@linkplain L1Operation#L1Ext_doPushLabel
		 * push-label instruction}, it will have a type pushed on it.  This type
		 * is checked against any value that the callee attempts to return to
		 * it.  This supports link-time type strengthening at call sites.
		 */
		CALLER,

		/**
		 * The {@linkplain ClosureDescriptor closure} being executed via this
		 * continuation.
		 */
		CLOSURE,

		/**
		 * The slots allocated for locals, arguments, and stack entries.  The
		 * arguments are first, then the locals, and finally the stack entries
		 * (growing downwards from the top).  At its deepest, the stack slots
		 * will abut the last local.
		 */
		FRAME_AT_
	}

	/**
	 * The bit fields that make up the {@link
	 * IntegerSlots#HI_LEVEL_TWO_CHUNK_LOW_OFFSET} integer field.
	 */
	public static class HiLevelTwoChunkLowOffset
	{
		/**
		 * The {@linkplain L2ChunkDescriptor.IntegerSlots#INDEX index} of the
		 * {@linkplain L2ChunkDescriptor level two chunk} to execute when this
		 * continuation is resumed.
		 */
		@BitField(shift=16, bits=16)
		static final BitField LEVEL_TWO_CHUNK =
			bitField(HiLevelTwoChunkLowOffset.class, "LEVEL_TWO_CHUNK");

		/**
		 * The position in the level two chunk's {@linkplain
		 * L2ChunkDescriptor.ObjectSlots#WORDCODES wordcodes} at which to resume
		 * execution when the continuation is resumed.
		 */
		@BitField(shift=0, bits=16)
		static final BitField OFFSET =
			bitField(HiLevelTwoChunkLowOffset.class, "OFFSET");
	}


	@Override
	public void o_Caller (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CALLER, value);
	}

	@Override
	public void o_Closure (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CLOSURE, value);
	}

	@Override
	public @NotNull AvailObject o_ArgOrLocalOrStackAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.FRAME_AT_, subscript);
	}

	@Override
	public void o_ArgOrLocalOrStackAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		object.objectSlotAtPut(
			ObjectSlots.FRAME_AT_,
			subscript,
			value);
	}

	@Override
	public void o_Pc (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.PC, value);
	}

	@Override
	public void o_Stackp (
		final @NotNull AvailObject object,
		final int value)
	{
		assert value > 0; //TODO: Remove safety check
		object.integerSlotPut(IntegerSlots.STACK_POINTER, value);
	}

	@Override
	public @NotNull AvailObject o_Caller (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CALLER);
	}

	@Override
	public @NotNull AvailObject o_Closure (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CLOSURE);
	}

	@Override
	public int o_Pc (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PC);
	}

	@Override
	public int o_Stackp (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.STACK_POINTER);
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsContinuation(object);
	}

	@Override
	public boolean o_EqualsContinuation (
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
		if (!object.closure().equals(aContinuation.closure()))
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

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		return ContinuationTypeDescriptor.forClosureType(
			object.closure().type());
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		int h = 0x593599A;
		h ^= object.caller().hash();
		h = h + object.closure().hash() + object.pc() * object.stackp();
		for (int i = object.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			h = h * 23 + 0x221C9 ^ object.argOrLocalOrStackAt(i).hash();
		}
		return h;
	}

	@Override
	public boolean o_IsHashAvailable (
		final @NotNull AvailObject object)
	{
		if (!object.closure().isHashAvailable())
		{
			return false;
		}
		if (!object.caller().isHashAvailable())
		{
			return false;
		}
		for (int i = 1, _end1 = object.numArgsAndLocalsAndStack(); i <= _end1; i++)
		{
			if (!object.argOrLocalOrStackAt(i).isHashAvailable())
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}

	/**
	 * Set both my chunk index and the offset into it.
	 */
	@Override
	public void o_LevelTwoChunkIndexOffset (
		final @NotNull AvailObject object,
		final int index,
		final int offset)
	{
		object.bitSlotPut(
			IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET,
			HiLevelTwoChunkLowOffset.LEVEL_TWO_CHUNK,
			index);
		object.bitSlotPut(
			IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET,
			HiLevelTwoChunkLowOffset.OFFSET,
			offset);
	}

	/**
	 * Read from the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@Override
	public @NotNull AvailObject o_StackAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.objectSlotAt(
			ObjectSlots.FRAME_AT_,
			subscript);
	}

	/**
	 * Write to the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@Override
	public void o_StackAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject anObject)
	{
		object.objectSlotAtPut(
			ObjectSlots.FRAME_AT_,
			subscript,
			anObject);
	}


	/**
	 * If immutable, copy the object as mutable, otherwise answer the original
	 * mutable continuation.  This is used by the {@linkplain L2Interpreter
	 * interpreter} to ensure it is always executing a mutable continuation and
	 * is therefore always able to directly modify it.
	 */
	@Override
	public @NotNull AvailObject o_EnsureMutable (
		final @NotNull AvailObject object)
	{
		return isMutable ? object : object.copyAsMutableContinuation();
	}

	@Override
	public int o_LevelTwoChunkIndex (
		final @NotNull AvailObject object)
	{
		return object.bitSlot(
			IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET,
			HiLevelTwoChunkLowOffset.LEVEL_TWO_CHUNK);
	}

	@Override
	public int o_LevelTwoOffset (
		final @NotNull AvailObject object)
	{
		return object.bitSlot(
			IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET,
			HiLevelTwoChunkLowOffset.OFFSET);
	}

	/**
	 * Answer the number of slots allocated for arguments, locals, and stack
	 * entries.
	 */
	@Override
	public short o_NumArgsAndLocalsAndStack (
		final @NotNull AvailObject object)
	{
		return (short)(object.objectSlotsCount() - numberOfFixedObjectSlots);
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
	@Override
	public void o_PostFault (
		final @NotNull AvailObject object)
	{
		final AvailObject chunk = L2ChunkDescriptor.chunkFromId(
			object.levelTwoChunkIndex());
		if (chunk.isValid())
		{
			chunk.isSaved(true);
		}
		else
		{
			object.levelTwoChunkIndexOffset(
				L2ChunkDescriptor.indexOfUnoptimizedChunk(),
				L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
		}
	}

	/**
	 * Answer a fresh mutable copy of the given continuation object.
	 */
	@Override
	public @NotNull AvailObject o_CopyAsMutableContinuation (
		final @NotNull AvailObject object)
	{
		if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final AvailObject result = mutable().create(
			object.objectSlotsCount() - numberOfFixedObjectSlots);
		assert result.objectSlotsCount() == object.objectSlotsCount();
		result.caller(object.caller());
		result.closure(object.closure());
		result.pc(object.pc());
		result.stackp(object.stackp());
		result.levelTwoChunkIndexOffset(
			object.levelTwoChunkIndex(),
			object.levelTwoOffset());
		for (int i = object.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			result.argOrLocalOrStackAtPut(i, object.argOrLocalOrStackAt(i));
		}
		return result;
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == IntegerSlots.HI_LEVEL_TWO_CHUNK_LOW_OFFSET;
	}

	/**
	 * Create a new continuation with the given data.  The continuation should
	 * represent the state upon entering the new context - i.e., set the pc to
	 * the first instruction (skipping the primitive indicator if necessary),
	 * clear the stack, and set up all local variables.
	 *
	 * @param closure The closure being invoked.
	 * @param caller The calling continuation.
	 * @param startingChunkIndex The index of the level two chunk to invoke.
	 * @param args The List of arguments
	 * @return The new continuation.
	 */
	public static AvailObject create (
		final @NotNull AvailObject closure,
		final @NotNull AvailObject caller,
		final int startingChunkIndex,
		final @NotNull List<AvailObject> args)
	{
		final AvailObject code = closure.code();
		final List<AvailObject> locals = new ArrayList<AvailObject>(
			code.numLocals());
		final int nArgs = args.size();
		assert nArgs == code.numArgs();
		final int nLocals = code.numLocals();
		for (int i = 1; i <= nLocals; i++)
		{
			locals.add(
				ContainerDescriptor.forOuterType(
					code.localTypeAt(i)));
		}
		return create(
			closure,
			caller,
			startingChunkIndex,
			args,
			locals);
	}

	/**
	 * Create a new continuation with the given data.  The continuation should
	 * represent the state upon entering the new context - i.e., set the pc to
	 * the first instruction (skipping the primitive indicator if necessary),
	 * clear the stack, and set up all local variables.
	 *
	 * @param closure The closure being invoked.
	 * @param caller The calling continuation.
	 * @param startingChunkIndex The index of the level two chunk to invoke.
	 * @param args The {@link List} of arguments
	 * @param locals The {@link List} of (non-argument) local variables.
	 * @return The new continuation.
	 */
	public static AvailObject create (
		final @NotNull AvailObject closure,
		final @NotNull AvailObject caller,
		final int startingChunkIndex,
		final @NotNull List<AvailObject> args,
		final @NotNull List<AvailObject> locals)
	{
		final ContinuationDescriptor descriptor = mutable();
		final AvailObject code = closure.code();
		final AvailObject cont = descriptor.create(
			code.numArgsAndLocalsAndStack());
		cont.caller(caller);
		cont.closure(closure);
		cont.pc(1);
		cont.stackp(
			cont.objectSlotsCount() + 1 - descriptor.numberOfFixedObjectSlots);
		cont.levelTwoChunkIndexOffset(
			startingChunkIndex,
			1);
		for (int i = code.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			cont.argOrLocalOrStackAtPut(i, VoidDescriptor.voidObject());
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
			cont.argOrLocalOrStackAtPut(
				nArgs + i,
				locals.get(i));
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
	private final static ContinuationDescriptor mutable =
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
	private final static ContinuationDescriptor immutable =
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
