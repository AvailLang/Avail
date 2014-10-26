/**
 * RegisterSet.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.utility.evaluation.*;

/**
 * This class maintains register information during naive translation from level
 * one compiled code (nybblecodes) to level two wordcodes, known as {@linkplain
 * L2Chunk chunks}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class RegisterSet
{
	/**
	 * The fixed architectural {@linkplain L2ObjectRegister registers}, keyed by
	 * {@link FixedRegister}.
	 */
	final EnumMap<FixedRegister, L2ObjectRegister> fixedRegisters;

	/**
	 * The mapping from each register to its current state, if any.
	 */
	final Map<L2Register, RegisterState> registerStates =
		new HashMap<>(10);

	/**
	 * Output debug information about this RegisterSet to the specified
	 * {@link StringBuilder}.
	 *
	 * @param builder Where to describe this RegisterSet.
	 */
	void debugOn (
		final StringBuilder builder)
	{
		final List<L2Register> sortedRegs =
			new ArrayList<>(registerStates.keySet());
		Collections.sort(sortedRegs, new Comparator<L2Register>()
		{
			@Override
			public int compare(
				final @Nullable L2Register r1,
				final @Nullable L2Register r2)
			{
				assert r1 != null;
				assert r2 != null;
				return (int)(r1.uniqueValue - r2.uniqueValue);
			}
		});
		for (final L2Register reg : sortedRegs)
		{
			final RegisterState state = stateFor(reg);
			builder.append(String.format(
				"%n\t%s = %.100s : %s",
				reg,
				state.constant,
				state.type));
			final List<L2Register> aliases = state.origins;
			if (!aliases.isEmpty())
			{
				builder.append(",  ALIASES = ");
				builder.append(aliases);
			}
			final Set<L2Instruction> sources = state.sourceInstructions;
			if (!sources.isEmpty())
			{
				builder.append(",  SOURCES = ");
				boolean first = true;
				for (final L2Instruction source : sources)
				{
					if (!first)
					{
						builder.append(", ");
					}
					builder.append("#");
					builder.append(source.offset());
					first = false;
				}
			}
		}

	}

	/**
	 * Lookup the {@link L2ObjectRegister} that represents the specified {@link
	 * FixedRegister}.
	 *
	 * @param fixedRegister The FixedRegister to look up.
	 * @return The corresponding L2ObjectRegister.
	 */
	L2ObjectRegister fixed (
		final FixedRegister fixedRegister)
	{
		return fixedRegisters.get(fixedRegister);
	}

	/**
	 * Answer the {@link RegisterState} for the specified {@link L2Register},
	 * creating one and associating it with the register for subsequent lookups.
	 *
	 * @param register The L2Register to look up.
	 * @return The RegisterState that describes the state of the L2Register at
	 *         a particular point in the generated code.
	 */
	public RegisterState stateFor (
		final L2Register register)
	{
		RegisterState state = registerStates.get(register);
		if (state == null)
		{
			state = new RegisterState();
			registerStates.put(register, state);
		}
		return state;
	}

	/**
	 * Answer whether this register contains a constant at the current code
	 * generation point.
	 *
	 * @param register The register.
	 * @return Whether the register has most recently been assigned a constant.
	 */
	public boolean hasConstantAt (
		final L2Register register)
	{
		return stateFor(register).hasConstant();
	}

	/**
	 * Associate this register with a constant at the current code generation
	 * point.
	 *
	 * @param register
	 *            The register.
	 * @param value
	 *            The constant {@link AvailObject value} bound to the register.
	 * @param instruction
	 *            The instruction that puts the constant in the register.
	 */
	public void constantAtPut (
		final L2Register register,
		final A_BasicObject value,
		final L2Instruction instruction)
	{
		final AvailObject strongValue = (AvailObject) value;
		final RegisterState state = stateFor(register);
		state.constant = strongValue;
		if (!strongValue.equalsNil())
		{
			final A_Type type =
				AbstractEnumerationTypeDescriptor.withInstance(strongValue);
			assert !type.isTop();
			assert !type.isBottom();
			state.type = type;
		}
		propagateWriteTo(register, instruction);
	}

	/**
	 * Retrieve the constant currently associated with this register.  Fail if
	 * the register is not bound to a constant at this point.
	 *
	 * @param register The register.
	 * @return The constant object.
	 */
	public AvailObject constantAt (
		final L2Register register)
	{
		final AvailObject value = stateFor(register).constant;
		assert value != null;
		return value;
	}

	/**
	 * Remove any current constant binding for the specified register.
	 *
	 * @param register The register.
	 */
	public void removeConstantAt (
		final L2Register register)
	{
		stateFor(register).constant = null;
	}

	/**
	 * Answer whether this register has a type bound to it at the current code
	 * generation point.
	 *
	 * @param register The register.
	 * @return Whether the register has a known type at this point.
	 */
	public boolean hasTypeAt (
		final L2Register register)
	{
		return stateFor(register).type != null;
	}

	/**
	 * Answer the type bound to the register at this point in the code.
	 *
	 * @param register The register.
	 * @return The type bound to the register, or null if not bound.
	 */
	public A_Type typeAt (
		final L2Register register)
	{
		final A_Type type = stateFor(register).type;
		assert type != null;
		return type;
	}

	/**
	 * Associate this register with a type at the current code generation point.
	 *
	 * @param register
	 *            The register.
	 * @param type
	 *            The type of object that will be in the register at this point.
	 */
	private void typeAtPut (
		final L2Register register,
		final A_Type type)
	{
		assert !type.isBottom();
		assert !type.equals(
			AbstractEnumerationTypeDescriptor.withInstance(
				NilDescriptor.nil()));
		stateFor(register).type = type;
	}

	/**
	 * Associate this register with a type at the current code generation point.
	 * Record the fact that this type was set by an assignment by the given
	 * instruction.
	 *
	 * @param register
	 *            The register.
	 * @param type
	 *            The type of object that will be in the register at this point.
	 * @param instruction
	 *            The instruction that affected this register.
	 */
	public void typeAtPut (
		final L2Register register,
		final A_Type type,
		final L2Instruction instruction)
	{
		typeAtPut(register, type);
		if (type.instanceCount().equals(IntegerDescriptor.one())
			&& !type.isInstanceMeta())
		{
			// There is only one value that it could be.
			final AvailObject onlyPossibleValue = type.instance();
			stateFor(register).constant = onlyPossibleValue;
		}
		propagateWriteTo(register, instruction);
	}

	/**
	 * Unbind any type information from the register at this point in the code.
	 *
	 * @param register The register from which to clear type information.
	 */
	public void removeTypeAt (
		final L2Register register)
	{
		stateFor(register).type = null;
	}


	/**
	 * The sourceRegister's value was just written to the destinationRegister.
	 * Propagate this information into the registerOrigins to allow the earliest
	 * remaining register with the same value to always be used during register
	 * source normalization.  This is essential for eliminating redundant moves.
	 *
	 * <p>
	 * Eventually primitive constructor/deconstructor pairs (e.g., tuple
	 * creation and tuple subscripting) could be combined in a similar way to
	 * perform a simple object escape analysis.  For example, consider this
	 * sequence of level two instructions:
	 * <ul>
	 * <li>r1 := ...</li>
	 * <li>r2 := ...</li>
	 * <li>r3 := makeTuple(r1, r2)</li>
	 * <li>r4 := tupleAt(r3, 1)</li>
	 * </ul>
	 * It can be shown that r4 will always contain the value that was in r1.
	 * In fact, if r3 is no longer needed then the tuple doesn't even have to be
	 * constructed at all.  While this isn't expected to be useful by itself,
	 * inlining is expected to reveal a great deal of such combinations.
	 * </p>
	 *
	 * @param sourceRegister
	 *            The {@link L2Register} which is the source of a move.
	 * @param destinationRegister
	 *            The {@link L2Register} which is the destination of a move.
	 * @param instruction
	 *            The {@link L2Instruction} which is moving the value.
	 */
	public void propagateMove (
		final L2Register sourceRegister,
		final L2Register destinationRegister,
		final L2Instruction instruction)
	{
		if (sourceRegister == destinationRegister)
		{
			return;
		}
		propagateWriteTo(destinationRegister, instruction);
		final RegisterState sourceState = stateFor(sourceRegister);
		final RegisterState destinationState = stateFor(destinationRegister);
		final List<L2Register> sourceOrigins = sourceState.origins;
		final List<L2Register> destinationOrigins =
			new ArrayList<L2Register>(sourceOrigins);
		destinationOrigins.add(sourceRegister);
		destinationState.origins.clear();
		destinationState.origins.addAll(destinationOrigins);
		for (final L2Register origin : destinationOrigins)
		{
			stateFor(origin).invertedOrigins.add(destinationRegister);
		}
	}

	/**
	 * Some sort of write to the destinationRegister has taken place.  Moves
	 * are handled differently.
	 *
	 * <p>
	 * Update the the {@link #registerStates} to reflect the fact that the
	 * destination register is no longer related to any of its earlier sources.
	 * </p>
	 *
	 * @param destinationRegister The {@link L2Register} being overwritten.
	 * @param instruction The instruction doing the writing.
	 */
	public void propagateWriteTo (
		final L2Register destinationRegister,
		final L2Instruction instruction)
	{
		// Firstly, the destinationRegister's value is no longer derived
		// from any other register (until and unless the client says which).
		final RegisterState destinationState = stateFor(destinationRegister);
		final List<L2Register> origins = destinationState.origins;
		if (!origins.isEmpty())
		{
			for (final L2Register origin : origins)
			{
				stateFor(origin).invertedOrigins.remove(destinationRegister);
			}
			origins.clear();
		}

		// Secondly, any registers that were derived from the old value of
		// the destinationRegister are no longer equivalent to it.
		final Set<L2Register> descendants = destinationState.invertedOrigins;
		if (!descendants.isEmpty())
		{
			for (final L2Register descendant : descendants)
			{
				final List<L2Register> list = stateFor(descendant).origins;
				assert list.contains(destinationRegister);
				list.remove(destinationRegister);
			}
			descendants.clear();
		}

		// Finally, *this* is the instruction that produces a value for the
		// destination.
		destinationState.sourceInstructions.clear();
		destinationState.sourceInstructions.add(instruction);
	}

	/**
	 * Answer a register which contains the same value as the givenRegister.
	 * Use the register which has held this value for the longest time, as
	 * this should eliminate the most redundant moves.
	 *
	 * @param givenRegister
	 *            An L2Register to normalize.
	 * @param givenOperandType
	 *            The type of {@link L2Operand} in which this register occurs.
	 * @return An {@code L2Register} to use instead of the givenRegister.
	 */
	public L2Register normalize (
		final L2Register givenRegister,
		final L2OperandType givenOperandType)
	{
		if (givenOperandType.isSource && !givenOperandType.isDestination)
		{
			final RegisterState givenState = stateFor(givenRegister);
			final List<L2Register> origins = givenState.origins;
			final AvailObject value = givenState.constant;
			if (value != null && value.equalsNil())
			{
				// Optimization -- always use the dedicated null register.
				fixed(FixedRegister.NULL);
			}
			if (origins.isEmpty())
			{
				// The origin of the register's value is indeterminate here.
				return givenRegister;
			}
			// Use the register that has been holding this value the longest.
			return origins.get(0);
		}
		return givenRegister;
	}

	/**
	 * A {@linkplain Transformer2 transformer} which converts from a {@linkplain
	 * L2Register register} to another (or the same) register.  At the point
	 * when the transformation happens, a source register is replaced by the
	 * earliest known register to contain the same value, thereby attempting to
	 * eliminate newer registers introduced by moves and decomposable primitive
	 * pairs (e.g., <a,b>[1]).
	 */
	final Transformer2<L2Register, L2OperandType, L2Register> normalizer =
		new Transformer2<L2Register, L2OperandType, L2Register>()
		{
			@Override
			public L2Register value (
				final @Nullable L2Register register,
				final @Nullable L2OperandType operandType)
			{
				assert register != null;
				assert operandType != null;
				return normalize(register, operandType);
			}
		};

	/**
	 * Clear all type/constant/origin information for all registers.
	 *
	 * @param instruction The instruction responsible for clearing this state.
	 */
	public void clearEverythingFor (
		final L2Instruction instruction)
	{
		registerStates.clear();
		constantAtPut(
			fixed(FixedRegister.NULL),
			NilDescriptor.nil(),
			instruction);
		typeAtPut(
			fixed(FixedRegister.CALLER),
			ContinuationTypeDescriptor.mostGeneralType());
		propagateWriteTo(
			fixed(FixedRegister.CALLER),
			instruction);
	}

	/**
	 * Construct a new {@link RegisterSet}.
	 *
	 * @param fixedRegisters
	 *            The map from {@link FixedRegister}s to {@link
	 *            L2ObjectRegister}s.
	 */
	RegisterSet (
		final EnumMap<FixedRegister, L2ObjectRegister> fixedRegisters)
	{
		this.fixedRegisters = fixedRegisters;
	}

	/**
	 * Copy a {@link RegisterSet}.
	 *
	 * @param original The original RegisterSet to copy.
	 */
	RegisterSet (
		final RegisterSet original)
	{
		this.fixedRegisters = original.fixedRegisters;
		registerStates.clear();
		for (final Map.Entry<L2Register, RegisterState> entry
			: original.registerStates.entrySet())
		{
			registerStates.put(
				entry.getKey(),
				new RegisterState(entry.getValue()));
		}
	}

	/**
	 * Combine the information from the argument and the receiver, modifying the
	 * receiver to reflect the combination.  The register types should become
	 * the union of the types, representing the fact that the two RegisterSets
	 * are alternative paths that lead to an instruction having either one type
	 * or the other.  Similarly, the constant information should degrade to type
	 * information unless both sides say they should be the same constant.
	 *
	 * @param other The RegisterSet with information to mix into the receiver.
	 * @return Whether the receiver changed due to the new information.
	 */
	boolean mergeFrom (final RegisterSet other)
	{
		boolean changed = false;
		for (final Map.Entry<L2Register, RegisterState> entry
			: registerStates.entrySet())
		{
			final L2Register reg = entry.getKey();
			final RegisterState state = entry.getValue();
			final RegisterState otherState = other.stateFor(reg);
			// Merge in the type information, truncating type information about
			// registers which are not known in both sources.
			final A_Type type = state.type;
			final A_Type otherType = otherState.type;
			if (type != null)
			{
				if (otherType != null)
				{
					final A_Type union = otherType.typeUnion(type);
					if (!union.equals(type))
					{
						changed = true;
						state.type = union;
					}
				}
				else
				{
					changed = true;
					state.type = null;
					// No type, so no constant.
					state.constant = null;
				}
			}

			// Only keep constant information where it agrees.
			final AvailObject constant = state.constant;
			final AvailObject otherConstant = otherState.constant;
			if (constant != null)
			{
				if (otherConstant == null || !otherConstant.equals(constant))
				{
					// They disagree, so it's not really a constant here.
					changed = true;
					state.constant = null;
				}
			}

			// Keep the intersection of the lists of origin registers.  In
			// theory the two lists might have overlapping elements in a
			// different order, but in that case any order will be good enough.
			final List<L2Register> list = state.origins;
			final List<L2Register> otherList = otherState.origins;
			changed |= list.retainAll(otherList);
		}

		// Rebuild the invertedOrigins from scratch.
		for (final Map.Entry<L2Register, RegisterState> entry
			: registerStates.entrySet())
		{
			entry.getValue().invertedOrigins.clear();
		}
		for (final Map.Entry<L2Register, RegisterState> entry
			: registerStates.entrySet())
		{
			final L2Register target = entry.getKey();
			for (final L2Register origin : entry.getValue().origins)
			{
				stateFor(origin).invertedOrigins.add(target);
			}
		}

		// The registerSourceInstructions for any definitely typed register
		// should be the union of the provided sources.  The idea is to keep
		// those instructions from being discarded, since their results *may*
		// be used here.  However, only keep information about registers that
		// are mentioned in both RegisterSets.
		final Iterator<Map.Entry<L2Register, RegisterState>> sourcesIterator =
			registerStates.entrySet().iterator();
		while (sourcesIterator.hasNext())
		{
			final Map.Entry<L2Register, RegisterState> entry =
				sourcesIterator.next();
			final L2Register reg = entry.getKey();
			final Set<L2Instruction> otherSources =
				other.stateFor(reg).sourceInstructions;
			final Set<L2Instruction> sources =
				entry.getValue().sourceInstructions;
			changed |= sources.addAll(otherSources);
		}
		return changed;
	}

	@Override
	public String toString ()
	{
		@SuppressWarnings("resource")
		final Formatter formatter = new Formatter();
		formatter.format("RegisterSet(%n\tConstants:");
		final Map<L2Register, RegisterState> sorted =
			new TreeMap<>(registerStates);
		for (final Map.Entry<L2Register, RegisterState> entry
			: sorted.entrySet())
		{
			final AvailObject constant = entry.getValue().constant;
			if (constant != null)
			{
				formatter.format("%n\t\t%s = %s", entry.getKey(), constant);
			}
		}
		formatter.format("%n\tTypes:");
		for (final Map.Entry<L2Register, RegisterState> entry
			: sorted.entrySet())
		{
			final A_Type type = entry.getValue().type;
			if (type != null)
			{
				formatter.format("%n\t\t%s = %s", entry.getKey(), type);
			}
		}
		formatter.format("%n\tOrigins:");
		for (final Map.Entry<L2Register, RegisterState> entry
			: sorted.entrySet())
		{
			final List<L2Register> origins = entry.getValue().origins;
			if (!origins.isEmpty())
			{
				formatter.format("%n\t\t%s = %s", entry.getKey(), origins);
			}
		}
		formatter.format("%n\tSources:");
		for (final Map.Entry<L2Register, RegisterState> entry
			: sorted.entrySet())
		{
			final Set<L2Instruction> sourceInstructions =
				entry.getValue().sourceInstructions;
			if (!sourceInstructions.isEmpty())
			{
				formatter.format(
					"%n\t\t%s = ",
					entry.getKey());
				boolean first = true;
				for (final L2Instruction instruction : sourceInstructions)
				{
					if (!first)
					{
						formatter.format(", ");
					}
					formatter.format("#%d", instruction.offset());
					first = false;
				}
			}
		}
		return formatter.toString();
	}
}
