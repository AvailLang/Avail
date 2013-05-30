/**
 * RegisterSet.java
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
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.utility.Transformer2;

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
	 * The {@link L2Translator} using this RegisterSet to translate level one
	 * code to level two.
	 */
	private final L2Translator translator;

	/**
	 * The mapping from each register to its current type, if known.
	 */
	final Map<L2Register, A_Type> registerTypes =
		new HashMap<>(10);

	/**
	 * The mapping from each register to its current value, if known.
	 */
	final Map<L2Register, AvailObject> registerConstants =
		new HashMap<>(10);

	/**
	 * The mapping from each register to a list of other registers that have the
	 * same value (if any).  These occur in the order in which the registers
	 * acquired the value.
	 *
	 * <p>
	 * The inverse map is kept in {@link #invertedOrigins}, to more efficiently
	 * disconnect this information.
	 * </p>
	 */
	final Map<L2Register, List<L2Register>> registerOrigins = new HashMap<>();

	/**
	 * The inverse of {@link #registerOrigins}.  For each key, the value is the
	 * collection of registers that this value has been copied into (and not yet
	 * been overwritten).
	 */
	final Map<L2Register, Set<L2Register>> invertedOrigins = new HashMap<>();

	/**
	 * A mapping from {@link L2Register}s to the {@link Set}s of {@link
	 * L2Instruction}s that may have provided the current value in that
	 * register.  There may be more than one such instruction due to multiple
	 * paths converging by jumping to labels.
	 */
	final Map<L2Register, Set<L2Instruction>> registerSourceInstructions =
		new HashMap<>();

	/**
	 * Output debug information about this RegisterSet to the specified
	 * {@link StringBuilder}.
	 *
	 * @param builder Where to describe this RegisterSet.
	 */
	void debugOn (
		final StringBuilder builder)
	{
		final Map<L2Register, A_Type> typeMap = registerTypes;
		final List<L2Register> sortedRegs = new ArrayList<>(typeMap.keySet());
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
			builder.append(String.format("%n\t%s : %s", reg, typeMap.get(reg)));
			final List<L2Register> aliases = registerOrigins.get(reg);
			final Set<L2Instruction> sources =
				registerSourceInstructions.get(reg);
			if (aliases != null && !aliases.isEmpty())
			{
				builder.append(",  ALIASES = ");
				builder.append(aliases);
			}
			assert sources != null;
			assert !sources.isEmpty();
			builder.append(",  SOURCES =");
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

	/**
	 * Answer the base {@linkplain CompiledCodeDescriptor compiled code} for
	 * which this chunk is being constructed.  Answer null when generating the
	 * default chunk.
	 *
	 * @return The root compiled code being translated.
	 */
	public @Nullable A_RawFunction codeOrNull ()
	{
		return translator.codeOrNull();
	}

	/**
	 * Answer the base {@linkplain CompiledCodeDescriptor compiled code} for
	 * which this chunk is being constructed.  Fail if it's null, which happens
	 * when the default chunk is being generated.
	 *
	 * @return The root compiled code being translated.
	 */
	public A_RawFunction codeOrFail ()
	{
		return translator.codeOrFail();
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
		return registerConstants.containsKey(register);
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
		registerConstants.put(register, strongValue);
		registerTypes.put(
			register,
			AbstractEnumerationTypeDescriptor.withInstance(strongValue));
		propagateWriteTo(register, instruction);
	}

	/**
	 * Retrieve the constant currently associated with this register, or null
	 * if the register is not bound to a constant at this point.
	 *
	 * @param register The register.
	 * @return The constant object or null.
	 */
	public AvailObject constantAt (
		final L2Register register)
	{
		return registerConstants.get(register);
	}

	/**
	 * Remove any current constant binding for the specified register.
	 *
	 * @param register The register.
	 */
	public void removeConstantAt (
		final L2Register register)
	{
		registerConstants.remove(register);
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
		return registerTypes.containsKey(register);
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
		return registerTypes.get(register);
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
		registerTypes.put(register, type);
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
		registerTypes.put(register, type);
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
		registerTypes.remove(register);
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
		final List<L2Register> sourceOrigins = registerOrigins.get(
			sourceRegister);
		final List<L2Register> destinationOrigins =
			sourceOrigins == null
				? new ArrayList<L2Register>(1)
				: new ArrayList<L2Register>(sourceOrigins);
		destinationOrigins.add(sourceRegister);
		registerOrigins.put(destinationRegister, destinationOrigins);
		for (final L2Register origin : destinationOrigins)
		{
			Set<L2Register> set = invertedOrigins.get(origin);
			if (set == null)
			{
				set = new HashSet<L2Register>();
				invertedOrigins.put(origin, set);
			}
			set.add(destinationRegister);
		}
	}

	/**
	 * Some sort of write to the destinationRegister has taken place.  Moves
	 * are handled differently.
	 *
	 * <p>
	 * Update the {@link #registerOrigins} and {@link #invertedOrigins} maps to
	 * reflect the fact that the destination register is no longer related to
	 * any of its earlier sources.
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
		final List<L2Register> origins =
			registerOrigins.get(destinationRegister);
		if (origins != null && !origins.isEmpty())
		{
			for (final L2Register origin : origins)
			{
				invertedOrigins.get(origin).remove(destinationRegister);
			}
			origins.clear();
		}

		// Secondly, any registers that were derived from the old value of
		// the destinationRegister are no longer equivalent to it.
		final Set<L2Register> descendants =
			invertedOrigins.get(destinationRegister);
		if (descendants != null && !descendants.isEmpty())
		{
			for (final L2Register descendant : descendants)
			{
				final List<L2Register> list = registerOrigins.get(descendant);
				assert list.contains(destinationRegister);
				list.remove(destinationRegister);
			}
			descendants.clear();
		}

		// Finally, *this* is the instruction that produces a value for the
		// destination.
		registerSourceInstructions.put(
			destinationRegister,
			Collections.singleton(instruction));
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
			final List<L2Register> origins = registerOrigins.get(givenRegister);
			final AvailObject value = registerConstants.get(givenRegister);
			if (value != null && value.equalsNil())
			{
				// Optimization -- always use the dedicated null register.
				return fixed(FixedRegister.NULL);
			}
			if (origins == null || origins.isEmpty())
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
	 * Answer the specified fixed register.
	 *
	 * @param registerEnum The {@link FixedRegister} identifying the register.
	 * @return The {@link L2ObjectRegister} named by the registerEnum.
	 */
	public L2ObjectRegister fixed (
		final FixedRegister registerEnum)
	{
		return translator.fixed(registerEnum);
	}

	/**
	 * Answer the register holding the specified continuation slot.  The slots
	 * are the arguments, then the locals, then the stack entries.  The first
	 * argument occurs just after the {@link FixedRegister}s.
	 *
	 * @param slotNumber
	 *            The index into the continuation's slots.
	 * @return
	 *            A register representing that continuation slot.
	 */
	public L2ObjectRegister continuationSlot (
		final int slotNumber)
	{
		return translator.continuationSlot(slotNumber);
	}

	/**
	 * Answer the register holding the specified argument/local number (the
	 * 1st argument is the 3rd architectural register).
	 *
	 * @param argumentNumber
	 *            The argument number for which the "architectural" register is
	 *            being requested.  If this is greater than the number of
	 *            arguments, then answer the register representing the local
	 *            variable at that position minus the number of registers.
	 * @return A register that represents the specified argument or local.
	 */
	L2ObjectRegister argumentOrLocal (
		final int argumentNumber)
	{
		return translator.argumentOrLocal(argumentNumber);
	}

	/**
	 * Clear all type/constant/origin information for all registers.
	 *
	 * @param instruction The instruction responsible for clearing this state.
	 */
	public void clearEverythingFor (final L2Instruction instruction)
	{
		registerTypes.clear();
		registerConstants.clear();
		registerOrigins.clear();
		invertedOrigins.clear();
		registerSourceInstructions.clear();
		constantAtPut(
			fixed(FixedRegister.NULL),
			NilDescriptor.nil(),
			instruction);
		typeAtPut(
			fixed(FixedRegister.CALLER),
			ContinuationTypeDescriptor.mostGeneralType());
		propagateWriteTo(fixed(FixedRegister.CALLER), instruction);
	}

	/**
	 * Construct a new {@link RegisterSet}.
	 *
	 * @param translator The {@link L2Translator} using these registers.
	 */
	RegisterSet (final L2Translator translator)
	{
		this.translator = translator;
	}

	/**
	 * Copy a {@link RegisterSet}.
	 *
	 * @param original The original RegisterSet to copy.
	 */
	RegisterSet (final RegisterSet original)
	{
		this.translator = original.translator;
		this.registerTypes.putAll(original.registerTypes);
		this.registerConstants.putAll(original.registerConstants);
		this.registerOrigins.putAll(original.registerOrigins);
		this.invertedOrigins.putAll(original.invertedOrigins);
		this.registerSourceInstructions.putAll(
			original.registerSourceInstructions);
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
	boolean add (final RegisterSet other)
	{
		assert this.translator == other.translator;
		boolean changed = false;
		// Merge in the type information, truncating type information about
		// registers which are not known in both sources.
		final Iterator<Map.Entry<L2Register, A_Type>> typesIterator =
			registerTypes.entrySet().iterator();
		while (typesIterator.hasNext())
		{
			final Map.Entry<L2Register, A_Type> entry = typesIterator.next();
			final L2Register reg = entry.getKey();
			final A_Type otherType = other.registerTypes.get(reg);
			if (otherType != null)
			{
				final A_Type existingType = entry.getValue();
				final A_Type union = otherType.typeUnion(existingType);
				if (!union.equals(existingType))
				{
					changed = true;
					entry.setValue(union);
				}
			}
			else
			{
				changed = true;
				typesIterator.remove();
				// No type, so no constant.
				registerConstants.remove(reg);
			}
		}

		// Only keep constant information where it agrees.
		final Iterator<Map.Entry<L2Register, AvailObject>> constantsIterator =
			registerConstants.entrySet().iterator();
		while (constantsIterator.hasNext())
		{
			final Map.Entry<L2Register, AvailObject> entry =
				constantsIterator.next();
			final L2Register reg = entry.getKey();
			if (!other.registerConstants.containsKey(reg)
				|| !other.registerConstants.get(reg).equals(entry.getValue()))
			{
				// They disagree, so it's not really a constant here.
				changed = true;
				constantsIterator.remove();
			}
		}

		// For each register keep the intersection of its lists of origin
		// registers.  In theory the two lists might have overlapping elements
		// in a different order, but in that case any order will be good enough.
		final Iterator<Map.Entry<L2Register, List<L2Register>>>
			originsIterator = registerOrigins.entrySet().iterator();
		while (originsIterator.hasNext())
		{
			final Map.Entry<L2Register, List<L2Register>> entry =
				originsIterator.next();
			final L2Register reg = entry.getKey();
			final List<L2Register> list = entry.getValue();
			final List<L2Register> otherList = other.registerOrigins.get(reg);
			if (otherList != null)
			{
				final List<L2Register> intersection = new ArrayList<>(list);
				intersection.retainAll(otherList);
				if (!intersection.equals(list))
				{
					changed = true;
					entry.setValue(intersection);
				}
			}
			else
			{
				changed = true;
				originsIterator.remove();
			}
		}

		// Rebuild the invertedOrigins from scratch.
		invertedOrigins.clear();
		for (final Map.Entry<L2Register, List<L2Register>> entry
			: registerOrigins.entrySet())
		{
			final L2Register target = entry.getKey();
			for (final L2Register origin : entry.getValue())
			{
				Set<L2Register> targetSet = invertedOrigins.get(origin);
				if (targetSet == null)
				{
					targetSet = new HashSet<>();
					invertedOrigins.put(origin, targetSet);
				}
				targetSet.add(target);
			}
		}

		// The registerSourceInstructions for any definitely typed register
		// should be the union of the provided sources.  The idea is to keep
		// those instructions from being discarded, since their results *may*
		// be used here.  However, only keep information about registers that
		// are mentioned in both RegisterSets.
		final Iterator<Map.Entry<L2Register, Set<L2Instruction>>>
			sourcesIterator = registerSourceInstructions.entrySet().iterator();
		while (sourcesIterator.hasNext())
		{
			final Map.Entry<L2Register, Set<L2Instruction>> entry =
				sourcesIterator.next();
			final L2Register reg = entry.getKey();
			final Set<L2Instruction> otherSources =
				other.registerSourceInstructions.get(reg);
			if (otherSources == null)
			{
				sourcesIterator.remove();
			}
			else
			{
				final Set<L2Instruction> sources = entry.getValue();
				final Set<L2Instruction> union = new HashSet<>(sources);
				union.addAll(otherSources);
				if (!union.equals(sources))
				{
					changed = true;
					entry.setValue(union);
				}
			}
		}
		return changed;
	}
}
