/**
 * RegisterState.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.optimizer;

import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * This class maintains information about one {@linkplain L2Register} on behalf
 * of a {@link RegisterSet} held by an {@linkplain L2Instruction} inside an
 * {@linkplain L2Chunk} constructed by the {@link L2Translator}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class RegisterState
{
	/**
	 * The exact value in this register.  If the exact value is not known, this
	 * field is {@code null}.
	 */
	public @Nullable AvailObject constant;

	/** The type of value in this register. */
	public @Nullable A_Type type;

	/**
	 * The list of other registers that are known to have the same value (if
	 * any).  These occur in the order in which the registers acquired the
	 * common value.
	 *
	 * <p>
	 * The inverse map is kept in {@link #invertedOrigins}, to more
	 * efficiently disconnect this information.
	 * </p>
	 */
	public final List<L2Register> origins = new ArrayList<>();

	/**
	 * The inverse of {@link #origins}.  For each key, the value is the
	 * collection of registers that this value has been copied into (and not yet
	 * been overwritten).
	 */
	public final Set<L2Register> invertedOrigins = new HashSet<>();

	/**
	 * The {@link Set} of {@link L2Instruction}s that may have provided the
	 * current value in that register.  There may be more than one such
	 * instruction due to multiple paths converging by jumping to labels.
	 */
	public final Set<L2Instruction> sourceInstructions = new HashSet<>();

	/**
	 * Answer whether this register contains a constant at the current code
	 * generation point.
	 *
	 * @return Whether this register has most recently been assigned a constant.
	 */
	public boolean hasConstant ()
	{
		return constant != null;
	}

//	/**
//	 * The sourceRegister's value was just written to the destinationRegister.
//	 * Propagate this information into the registerOrigins to allow the earliest
//	 * remaining register with the same value to always be used during register
//	 * source normalization.  This is essential for eliminating redundant moves.
//	 *
//	 * <p>
//	 * Eventually primitive constructor/deconstructor pairs (e.g., tuple
//	 * creation and tuple subscripting) could be combined in a similar way to
//	 * perform a simple object escape analysis.  For example, consider this
//	 * sequence of level two instructions:
//	 * <ul>
//	 * <li>r1 := ...</li>
//	 * <li>r2 := ...</li>
//	 * <li>r3 := makeTuple(r1, r2)</li>
//	 * <li>r4 := tupleAt(r3, 1)</li>
//	 * </ul>
//	 * It can be shown that r4 will always contain the value that was in r1.
//	 * In fact, if r3 is no longer needed then the tuple doesn't even have to be
//	 * constructed at all.  While this isn't expected to be useful by itself,
//	 * inlining is expected to reveal a great deal of such combinations.
//	 * </p>
//	 *
//	 * @param sourceRegister
//	 *            The {@link L2Register} which is the source of a move.
//	 * @param destinationRegister
//	 *            The {@link L2Register} which is the destination of a move.
//	 * @param instruction
//	 *            The {@link L2Instruction} which is moving the value.
//	 */
//	public void propagateMove (
//		final L2Register sourceRegister,
//		final L2Register destinationRegister,
//		final L2Instruction instruction)
//	{
//		if (sourceRegister == destinationRegister)
//		{
//			return;
//		}
//		propagateWriteTo(destinationRegister, instruction);
//		final List<L2Register> sourceOrigins = origins.get(
//			sourceRegister);
//		final List<L2Register> destinationOrigins =
//			sourceOrigins == null
//				? new ArrayList<L2Register>(1)
//				: new ArrayList<L2Register>(sourceOrigins);
//		destinationOrigins.add(sourceRegister);
//		origins.put(destinationRegister, destinationOrigins);
//		for (final L2Register origin : destinationOrigins)
//		{
//			Set<L2Register> set = invertedOrigins.get(origin);
//			if (set == null)
//			{
//				set = new HashSet<L2Register>();
//				invertedOrigins.put(origin, set);
//			}
//			set.add(destinationRegister);
//		}
//	}
//
//	/**
//	 * Some sort of write to the destinationRegister has taken place.  Moves
//	 * are handled differently.
//	 *
//	 * <p>
//	 * Update the {@link #origins} and {@link #invertedOrigins} maps to
//	 * reflect the fact that the destination register is no longer related to
//	 * any of its earlier sources.
//	 * </p>
//	 *
//	 * @param destinationRegister The {@link L2Register} being overwritten.
//	 * @param instruction The instruction doing the writing.
//	 */
//	public void propagateWriteTo (
//		final L2Register destinationRegister,
//		final L2Instruction instruction)
//	{
//		// Firstly, the destinationRegister's value is no longer derived
//		// from any other register (until and unless the client says which).
//		final List<L2Register> origins =
//			origins.get(destinationRegister);
//		if (origins != null && !origins.isEmpty())
//		{
//			for (final L2Register origin : origins)
//			{
//				invertedOrigins.get(origin).remove(destinationRegister);
//			}
//			origins.clear();
//		}
//
//		// Secondly, any registers that were derived from the old value of
//		// the destinationRegister are no longer equivalent to it.
//		final Set<L2Register> descendants =
//			invertedOrigins.get(destinationRegister);
//		if (descendants != null && !descendants.isEmpty())
//		{
//			for (final L2Register descendant : descendants)
//			{
//				final List<L2Register> list = origins.get(descendant);
//				assert list.contains(destinationRegister);
//				list.remove(destinationRegister);
//			}
//			descendants.clear();
//		}
//
//		// Finally, *this* is the instruction that produces a value for the
//		// destination.
//		sourceInstructions.put(
//			destinationRegister,
//			Collections.singleton(instruction));
//	}
//
//	/**
//	 * Answer a register which contains the same value as the givenRegister.
//	 * Use the register which has held this value for the longest time, as
//	 * this should eliminate the most redundant moves.
//	 *
//	 * @param givenRegister
//	 *            An L2Register to normalize.
//	 * @param givenOperandType
//	 *            The type of {@link L2Operand} in which this register occurs.
//	 * @return An {@code L2Register} to use instead of the givenRegister.
//	 */
//	public L2Register normalize (
//		final L2Register givenRegister,
//		final L2OperandType givenOperandType)
//	{
//		if (givenOperandType.isSource && !givenOperandType.isDestination)
//		{
//			final List<L2Register> origins = origins.get(givenRegister);
//			final AvailObject value = registerConstants.get(givenRegister);
//			if (value != null && value.equalsNil())
//			{
//				// Optimization -- always use the dedicated null register.
//				return fixed(FixedRegister.NULL);
//			}
//			if (origins == null || origins.isEmpty())
//			{
//				// The origin of the register's value is indeterminate here.
//				return givenRegister;
//			}
//			// Use the register that has been holding this value the longest.
//			return origins.get(0);
//		}
//		return givenRegister;
//	}
//
//	/**
//	 * A {@linkplain Transformer2 transformer} which converts from a {@linkplain
//	 * L2Register register} to another (or the same) register.  At the point
//	 * when the transformation happens, a source register is replaced by the
//	 * earliest known register to contain the same value, thereby attempting to
//	 * eliminate newer registers introduced by moves and decomposable primitive
//	 * pairs (e.g., <a,b>[1]).
//	 */
//	final Transformer2<L2Register, L2OperandType, L2Register> normalizer =
//		new Transformer2<L2Register, L2OperandType, L2Register>()
//		{
//			@Override
//			public L2Register value (
//				final @Nullable L2Register register,
//				final @Nullable L2OperandType operandType)
//			{
//				assert register != null;
//				assert operandType != null;
//				return normalize(register, operandType);
//			}
//		};
//
//	/**
//	 * Answer the specified fixed register.
//	 *
//	 * @param registerEnum The {@link FixedRegister} identifying the register.
//	 * @return The {@link L2ObjectRegister} named by the registerEnum.
//	 */
//	public L2ObjectRegister fixed (
//		final FixedRegister registerEnum)
//	{
//		return translator.fixed(registerEnum);
//	}
//
//	/**
//	 * Answer the register holding the specified continuation slot.  The slots
//	 * are the arguments, then the locals, then the stack entries.  The first
//	 * argument occurs just after the {@link FixedRegister}s.
//	 *
//	 * @param slotNumber
//	 *            The index into the continuation's slots.
//	 * @return
//	 *            A register representing that continuation slot.
//	 */
//	public L2ObjectRegister continuationSlot (
//		final int slotNumber)
//	{
//		return translator.continuationSlot(slotNumber);
//	}
//
//	/**
//	 * Answer the register holding the specified argument/local number (the
//	 * 1st argument is the 3rd architectural register).
//	 *
//	 * @param argumentNumber
//	 *            The argument number for which the "architectural" register is
//	 *            being requested.  If this is greater than the number of
//	 *            arguments, then answer the register representing the local
//	 *            variable at that position minus the number of registers.
//	 * @return A register that represents the specified argument or local.
//	 */
//	L2ObjectRegister argumentOrLocal (
//		final int argumentNumber)
//	{
//		return translator.argumentOrLocal(argumentNumber);
//	}
//
//	/**
//	 * Clear all type/constant/origin information for all registers.
//	 *
//	 * @param instruction The instruction responsible for clearing this state.
//	 */
//	public void clearEverythingFor (final L2Instruction instruction)
//	{
//		registerTypes.clear();
//		registerConstants.clear();
//		origins.clear();
//		invertedOrigins.clear();
//		sourceInstructions.clear();
//		constantAtPut(
//			fixed(FixedRegister.NULL),
//			NilDescriptor.nil(),
//			instruction);
//		typeAtPut(
//			fixed(FixedRegister.CALLER),
//			ContinuationTypeDescriptor.mostGeneralType());
//		propagateWriteTo(fixed(FixedRegister.CALLER), instruction);
//	}

	/**
	 * Construct a new {@link RegisterState}.
	 */
	RegisterState ()
	{
		// Dealt with by individual field declarations.
	}

	/**
	 * Copy a {@link RegisterState}.
	 *
	 * @param original The original RegisterSet to copy.
	 */
	RegisterState (final RegisterState original)
	{
		this.constant = original.constant;
		this.type = original.type;
		this.origins.addAll(original.origins);
		this.invertedOrigins.addAll(original.invertedOrigins);
		this.sourceInstructions.addAll(original.sourceInstructions);
	}
}
