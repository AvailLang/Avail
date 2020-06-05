/*
 * RegisterSet.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.optimizer

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.utility.PrefixSharingList
import java.util.*

/**
 * This class maintains register information during naive translation from Level
 * One compiled code (nybblecodes) to Level Two wordcodes, known as
 * [chunks][L2Chunk].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class RegisterSet
{
	/**
	 * The mapping from each register to its current state, if any.
	 */
	val registerStates = mutableMapOf<L2Register, RegisterState>()

	/**
	 * Output debug information about this RegisterSet to the specified
	 * [StringBuilder].
	 *
	 * @param builder
	 *   Where to describe this RegisterSet.
	 */
	fun debugOn(builder: StringBuilder)
	{
		val sortedRegs = registerStates.keys.toMutableList()
		sortedRegs.sortWith (Comparator { r1: L2Register, r2: L2Register ->
			r1.uniqueValue.toLong().compareTo(r2.uniqueValue.toLong())
		})
		for (reg in sortedRegs)
		{
			val state = stateForReading(reg)
			builder.append(String.format(
				"%n\t%s = %.100s : %s",
				reg,
				state.constant(),
				state.type()))
			val aliases = state.origins()
			if (aliases.isNotEmpty())
			{
				builder.append(",  ALIASES = ")
				builder.append(aliases)
			}
			val sources = state.sourceInstructions()
			if (sources.isNotEmpty())
			{
				builder.append(",  SOURCES = ")
				var first = true
				for (source in sources)
				{
					if (!first)
					{
						builder.append(", ")
					}
					builder.append("#")
					builder.append(source.offset())
					first = false
				}
			}
		}
	}

	/**
	 * Answer the [RegisterState] for the specified [L2Register], creating one
	 * and associating it with the register for subsequent lookups. Ensure the
	 * RegisterState is modifiable, copying it and writing it back if necessary.
	 *
	 * @param register
	 *   The `L2Register` to look up.
	 * @return
	 *   The mutable RegisterState that describes the state of the L2Register at
	 *   a particular point in the generated code.
	 */
	fun stateForModifying(register: L2Register): RegisterState
	{
		var state = registerStates[register]
		if (state === null)
		{
			state = RegisterState.blank()
		}
		if (state.isShared)
		{
			state = RegisterState(state)
			registerStates[register] = state
		}
		assert(!state.isShared)
		return state
	}

	/**
	 * Answer the [RegisterState] for the specified [L2Register], creating one
	 * and associating it with the register for subsequent lookups.
	 *
	 * @param register
	 *   The `L2Register` to look up.
	 * @return
	 *   The `RegisterState` that describes the state of the `L2Register` at a
	 *   particular point in the generated code.
	 */
	fun stateForReading(register: L2Register): RegisterState
	{
		var state = registerStates[register]
		if (state === null)
		{
			state = RegisterState.blank()
		}
		return state
	}

	/**
	 * Answer whether this register contains a constant at the current code
	 * generation point.
	 *
	 * @param register
	 *   The register.
	 * @return
	 *   Whether the register has most recently been assigned a constant.
	 */
	fun hasConstantAt(register: L2Register): Boolean =
		stateForReading(register).hasConstant()

	/**
	 * Answer whether all of the supplied registers are constant here.
	 *
	 * @param registerReads
	 *   The [List] of [L2ReadBoxedOperand]s to examine for being constant in
	 *   this register set.
	 * @return
	 *   `true` if all of the registers are constants here, otherwise `false`.
	 */
	fun allRegistersAreConstant(registerReads: List<L2ReadBoxedOperand>): Boolean
	{
		for (element in registerReads)
		{
			if (!hasConstantAt(element.register()))
			{
				return false
			}
		}
		return true
	}

	/**
	 * Associate this register with a constant at the current code generation
	 * point.
	 *
	 * @param register
	 *   The register.
	 * @param value
	 *   The constant [value][A_BasicObject] bound to the register.
	 */
	fun constantAtPut(register: L2Register, value: A_BasicObject)
	{
		val strongValue = value as AvailObject
		val state = stateForModifying(register)
		state.constant(strongValue)
		if (!strongValue.equalsNil())
		{
			val type =
				AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn(strongValue)
			assert(!type.isTop)
			assert(!type.isBottom)
			state.type(type)
		}
	}

	/**
	 * Associate this register with a constant at the current code generation
	 * point.
	 *
	 * @param register
	 *   The register.
	 * @param value
	 *   The constant [value][AvailObject] bound to the register.
	 * @param instruction
	 *   The instruction that puts the constant in the register.
	 */
	fun constantAtPut(
		register: L2Register,
		value: A_BasicObject,
		instruction: L2Instruction)
	{
		val strongValue = value as AvailObject
		val state = stateForModifying(register)
		state.constant(strongValue)
		if (!strongValue.equalsNil())
		{
			val type =
				AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn(strongValue)
			assert(!type.isTop)
			assert(!type.isBottom)
			state.type(type)
		}
		propagateWriteTo(register, instruction)
	}

	/**
	 * Retrieve the constant currently associated with this register.  Fail if
	 * the register is not bound to a constant at this point.
	 *
	 * @param register
	 *   The register.
	 * @return
	 *   The constant object.
	 */
	fun constantAt(register: L2Register): AvailObject =
		stateForReading(register).constant()!!

	/**
	 * Remove any current constant binding for the specified register.
	 *
	 * @param register
	 *   The register.
	 */
	fun removeConstantAt(register: L2Register)
	{
		stateForModifying(register).constant(null)
	}

	/**
	 * Answer whether this register has a type bound to it at the current code
	 * generation point.
	 *
	 * @param register
	 *   The register.
	 * @return
	 *   Whether the register has a known type at this point.
	 */
	fun hasTypeAt(register: L2Register): Boolean =
		stateForReading(register).type() !== null

	/**
	 * Answer the type bound to the register at this point in the code.
	 *
	 * @param register
	 *   The register.
	 * @return
	 *   The type bound to the register, or null if not bound.
	 */
	fun typeAt(register: L2Register): A_Type =
		stateForReading(register).type()!!

	/**
	 * Associate this register with a type at the current code generation point.
	 *
	 * @param register
	 *   The register.
	 * @param type
	 *   The type of object that will be in the register at this point.
	 */
	private fun typeAtPut(register: L2Register, type: A_Type)
	{
		assert(!type.isBottom)
		assert(!type.equalsInstanceTypeFor(NilDescriptor.nil))
		stateForModifying(register).type(type)
	}

	/**
	 * Associate this register with a type at the current code generation point.
	 * Record the fact that this type was set by an assignment by the given
	 * instruction.
	 *
	 * @param register
	 *   The register.
	 * @param type
	 *   The type of object that will be in the register at this point.
	 * @param instruction
	 *   The instruction that affected this register.
	 */
	fun typeAtPut(
		register: L2Register,
		type: A_Type,
		instruction: L2Instruction)
	{
		typeAtPut(register, type)
		if (type.instanceCount().equalsInt(1) && !type.isInstanceMeta)
		{
			// There is only one value that it could be.
			val onlyPossibleValue = type.instance()
			stateForModifying(register).constant(onlyPossibleValue)
		}
		propagateWriteTo(register, instruction)
	}

	/**
	 * Produce the set of all registers known to contain the same value as the
	 * given register.
	 *
	 * Follow all transitive [RegisterState.origins] and
	 * [RegisterState.invertedOrigins] to get the complete set.
	 *
	 * @param register
	 *   An [L2Register]
	 * @return
	 *   The set of all [L2Register]s known to contain the same value as the
	 *   given register.
	 */
	private fun allEquivalentRegisters(register: L2Register): Set<L2Register>
	{
		var equivalents = mutableSetOf<L2Register>()
		equivalents.add(register)
		while (true)
		{
			val newEquivalents = equivalents.toMutableSet()
			for (reg in equivalents)
			{
				val state = stateForReading(reg)
				newEquivalents.addAll(state.origins())
				newEquivalents.addAll(state.invertedOrigins())
			}
			if (equivalents.size == newEquivalents.size)
			{
				equivalents = newEquivalents
				break
			}
			equivalents = newEquivalents
		}
		return equivalents
	}

	/**
	 * No new instruction has written to the register, but the path taken from a
	 * type test has ascertained that the register contains a stronger type than
	 * had been determined.
	 *
	 * This is subtle, but we also update the type for each register which is
	 * known to currently have the same value.
	 *
	 * @param register
	 *    The register that needs its type strengthened.
	 * @param type
	 *   The type to strengthen it to.
	 */
	fun strengthenTestedTypeAtPut(register: L2Register, type: A_Type)
	{
		for (alias in allEquivalentRegisters(register))
		{
			typeAtPut(alias, type)
		}
	}

	/**
	 * No new instruction has written to the register, but the path taken from a
	 * value test has ascertained that the register contains a specific value.
	 *
	 * This is subtle, but we also update the known value for each register
	 * which has been shown to have the same value.
	 *
	 * @param register
	 *   The register that needs its type strengthened.
	 * @param value
	 *   The value in the register.
	 */
	fun strengthenTestedValueAtPut(register: L2Register, value: A_BasicObject)
	{
		for (alias in allEquivalentRegisters(register))
		{
			constantAtPut(alias, value)
		}
	}

	/**
	 * Unbind any type information from the register at this point in the code.
	 *
	 * @param register
	 *   The register from which to clear type information.
	 */
	fun removeTypeAt(register: L2Register)
	{
		stateForModifying(register).type(null)
	}

	/**
	 * The sourceRegister's value was just written to the destinationRegister.
	 * Propagate this information into the registerOrigins to allow the earliest
	 * remaining register with the same value to always be used during register
	 * source normalization.  This is essential for eliminating redundant moves.
	 *
	 * Eventually primitive constructor/deconstructor pairs (e.g., tuple
	 * creation and tuple subscripting) could be combined in a similar way to
	 * perform a simple object escape analysis.  For example, consider this
	 * sequence of level two instructions:
	 *
	 *  * r1 := ...
	 *  * r2 := ...
	 *  * r3 := makeTuple(r1, r2)
	 *  * r4 := tupleAt(r3, 1)
	 *
	 * It can be shown that r4 will always contain the value that was in r1.
	 * In fact, if r3 is no longer needed then the tuple doesn't even have to be
	 * constructed at all.  While this isn't expected to be useful by itself,
	 * inlining is expected to reveal a great deal of such combinations.
	 *
	 * @param sourceRegister
	 *   The [L2Register] which is the source of a move.
	 * @param destinationRegister
	 *   The [L2Register] which is the destination of a move.
	 * @param instruction
	 *   The [L2Instruction] which is moving the value.
	 */
	fun propagateMove(
		sourceRegister: L2Register,
		destinationRegister: L2Register,
		instruction: L2Instruction)
	{
		if (sourceRegister === destinationRegister)
		{
			return
		}
		propagateWriteTo(destinationRegister, instruction)
		val sourceState = stateForReading(sourceRegister)
		val destinationState = stateForModifying(destinationRegister)
		val sourceOrigins = sourceState.origins()
		val destinationOrigins =
			PrefixSharingList.append(sourceOrigins, sourceRegister)
		destinationState.origins(destinationOrigins)
		for (origin in destinationOrigins)
		{
			stateForModifying(origin).addInvertedOrigin(destinationRegister)
		}
	}

	/**
	 * Some sort of write to the destinationRegister has taken place.  Moves
	 * are handled differently.
	 *
	 * Update the the [registerStates] to reflect the fact that the
	 * destination register is no longer related to any of its earlier sources.
	 *
	 * @param destinationRegister
	 *   The [L2Register] being overwritten.
	 * @param instruction
	 *   The instruction doing the writing.
	 */
	fun propagateWriteTo(
		destinationRegister: L2Register,
		instruction: L2Instruction)
	{
		// Firstly, the destinationRegister's value is no longer derived
		// from any other register (until and unless the client says which).
		val destinationState = stateForModifying(destinationRegister)
		val origins = destinationState.origins()
		for (origin in origins)
		{
			stateForModifying(origin).removeInvertedOrigin(
				destinationRegister)
		}
		destinationState.origins(mutableListOf())

		// Secondly, any registers that were derived from the old value of
		// the destinationRegister are no longer equivalent to it.
		for (descendant in destinationState.invertedOrigins())
		{
			val state = stateForModifying(descendant)
			assert(state.origins().contains(destinationRegister))
			state.removeOrigin(destinationRegister)
		}
		destinationState.invertedOrigins(mutableListOf())

		// Finally, *this* is the instruction that produces a value for the
		// destination.
		destinationState.clearSources()
		destinationState.addSource(instruction)
	}

	/**
	 * Combine the information from the argument and the receiver, modifying the
	 * receiver to reflect the combination.  The register types should become
	 * the union of the types, representing the fact that the two RegisterSets
	 * are alternative paths that lead to an instruction having either one type
	 * or the other.  Similarly, the constant information should degrade to type
	 * information unless both sides say they should be the same constant.
	 *
	 * @param other
	 *   The RegisterSet with information to mix into the receiver.
	 * @return
	 *   Whether the receiver changed due to the new information.
	 */
	fun mergeFrom(other: RegisterSet): Boolean
	{
		var registerSetChanged = false
		for (entry in registerStates.entries)
		{
			val reg = entry.key
			var state = entry.value
			// We'll write this back only if it's modified below.
			state = RegisterState(state)
			val otherState = other.stateForReading(reg)
			// Merge in the type information, truncating type information about
			// registers which are not known in both sources.
			val type = state.type()
			val otherType = otherState.type()
			var entryChanged = false
			if (type !== null)
			{
				if (otherType !== null)
				{
					val union = otherType.typeUnion(type)
					if (!union.equals(type))
					{
						entryChanged = true
						state.type(union)
					}
				}
				else
				{
					entryChanged = true
					state.type(null)
					// No type, so no constant.
					state.constant(null)
				}
			}

			// Only keep constant information where it agrees.
			val constant = state.constant()
			val otherConstant = otherState.constant()
			if (constant !== null
				&& otherConstant !== constant)
			{
				// They disagree, so it's not really a constant here.
				entryChanged = true
				state.constant(null)
			}

			// Keep the intersection of the lists of origin registers.  In
			// theory the two lists might have overlapping elements in a
			// different order, but in that case any order will be good enough.
			val oldList = state.origins()
			val otherList = otherState.origins()
			val newList = mutableListOf<L2Register>()
			newList.addAll(oldList)
			val listChanged = newList.retainAll(otherList)
			if (listChanged)
			{
				entryChanged = true
				state.origins(newList)
				for (oldOrigin in oldList)
				{
					assert(oldOrigin !== reg) {
						"Register should not have been its own origin"
					}
					if (!newList.contains(oldOrigin))
					{
						stateForModifying(oldOrigin).removeInvertedOrigin(reg)
					}
				}
				for (newOrigin in newList)
				{
					assert(newOrigin !== reg) {
						"Register should not be its own origin"
					}
					if (!oldList.contains(newOrigin))
					{
						stateForModifying(newOrigin).addInvertedOrigin(reg)
					}
				}
			}
			if (entryChanged)
			{
				entry.setValue(state)
				registerSetChanged = true
			}
		}

		// The registerSourceInstructions for any definitely typed register
		// should be the union of the provided sources.  The idea is to keep
		// those instructions from being discarded, since their results *may*
		// be used here.  However, only keep information about registers that
		// are mentioned in both RegisterSets.
		for (entry in registerStates.entries)
		{
			val reg = entry.key
			var state = entry.value
			val sources = state.sourceInstructions()
			val otherSources =
				other.stateForReading(reg).sourceInstructions()
			for (otherSource in otherSources)
			{
				if (!sources.contains(otherSource))
				{
					registerSetChanged = true
					if (state.isShared)
					{
						state = RegisterState(state)
						entry.setValue(state)
					}
					state.addSource(otherSource)
				}
			}
		}
		return registerSetChanged
	}

	override fun toString(): String
	{
		val formatter = Formatter()
		formatter.format("RegisterSet(%n\tConstants:")
		val sorted: Map<L2Register?, RegisterState> = TreeMap(registerStates)
		for ((key, value) in sorted)
		{
			val constant = value.constant()
			if (constant !== null)
			{
				formatter.format(
					"%n\t\t%s = %s",
					key,
					constant.toString().replace("\n", "\n\t\t"))
			}
		}
		formatter.format("%n\tTypes:")
		for ((key, value) in sorted)
		{
			val type = value.type()
			if (type !== null)
			{
				formatter.format(
					"%n\t\t%s = %s",
					key,
					type.toString().replace("\n", "\n\t\t"))
			}
		}
		formatter.format("%n\tOrigins:")
		for ((key, value) in sorted)
		{
			val origins = value.origins()
			if (origins.isNotEmpty())
			{
				formatter.format(
					"%n\t\t%s = %s",
					key,
					origins.toString().replace("\n", "\n\t\t"))
			}
		}
		formatter.format("%n\tSources:")
		for ((key, value) in sorted)
		{
			val sourceInstructions = value.sourceInstructions()
			if (sourceInstructions.isNotEmpty())
			{
				formatter.format("%n\t\t%s = ", key)
				var first = true
				for (instruction in sourceInstructions)
				{
					if (!first)
					{
						formatter.format(", ")
					}
					formatter.format("#%d", instruction.offset())
					first = false
				}
			}
		}
		return formatter.toString()
	}
}
