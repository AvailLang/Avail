/*
 * L2ReadOperand.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operand

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.A_Type
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast

/**
 * `L2ReadOperand` abstracts the capabilities of actual register read operands.
 *
 * @param K
 *   The [RegisterKind] that is being read.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property semanticValue
 *   The [L2SemanticValue] that is being read when an [L2Instruction] uses this
 *   [L2Operand].
 * @property restriction
 *   A type restriction, certified by the VM, that this particular read of this
 *   register is guaranteed to satisfy.
 * @property register
 *   The actual [L2Register].  This is only set during late optimization of the
 *   control flow graph.
 * @constructor
 * Construct a new `L2ReadOperand` for the specified [L2SemanticValue] and
 * [TypeRestriction], using information from the given [L2ValueManifest].
 *
 * @param semanticValue
 *   The [L2SemanticValue] that is being read when an [L2Instruction] uses this
 *   [L2Operand].
 * @param restriction
 *   The [TypeRestriction] that bounds the value being read.
 * @param register
 *   The [L2Register] being read by this operand.
 */
abstract class L2ReadOperand<K: RegisterKind<K>>
protected constructor(
	private var semanticValue: L2SemanticValue<K>,
	private var restriction: TypeRestriction,
	private var register: L2Register<K>) : L2Operand()
{
	/**
	 * Answer the [RegisterKind] of register that is read by this
	 * `L2ReadOperand`.
	 *
	 * @return
	 *   The [RegisterKind].
	 */
	abstract val kind: K

	/**
	 * Answer the [L2SemanticValue] being read.
	 *
	 * @return
	 *   The [L2SemanticValue].
	 */
	open fun semanticValue(): L2SemanticValue<K> = semanticValue

	/**
	 * Answer this read's [L2Register].
	 *
	 * @return
	 *   The register.
	 */
	fun register(): L2Register<K> = register

	/**
	 * Answer a String that describes this operand for debugging.
	 *
	 * @return
	 *   A [String].
	 */
	fun registerString(): String = "$register[$semanticValue]"

	/**
	 * Answer the [L2Register]'s [finalIndex][L2Register.finalIndex].
	 *
	 * @return
	 *   The index of the register, computed during register coloring.
	 */
	fun finalIndex(): Int = register().finalIndex()

	/**
	 * Answer the type restriction for this register read.
	 *
	 * @return
	 *   A [TypeRestriction].
	 */
	fun restriction(): TypeRestriction = restriction

	/**
	 * Answer this read's type restriction's basic type.
	 *
	 * @return
	 *   An [A_Type].
	 */
	fun type(): A_Type = restriction.type

	/**
	 * Answer this read's type restriction's constant value (i.e., the exact
	 * value that this read is guaranteed to produce), or `null` if such a
	 * constraint is not available.
	 *
	 * @return
	 *   The exact [A_BasicObject] that's known to be in this register, or else
	 *   `null`.
	 */
	fun constantOrNull(): AvailObject? = restriction.constantOrNull

	/**
	 * Answer the [L2WriteOperand] that provided the value that this operand is
	 * reading.  The control flow graph must be in SSA form.
	 *
	 * @return
	 *   The defining [L2WriteOperand].
	 */
	fun definition(): L2WriteOperand<K> = register.definition().cast()

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		val manifestRestriction = manifest.restrictionFor(semanticValue)
		if (manifestRestriction != restriction)
		{
			val intersection = restriction.intersection(manifestRestriction)
			restriction = intersection
			manifest.setRestriction(semanticValue, intersection)
		}
		register().addUse(this)
	}

	/**
	 * Create an `L2ReadOperand` like this one, but with a different
	 * [register].
	 *
	 * @param newRegister
	 *   The [L2Register] to use in the copy.
	 * @return
	 *   A duplicate of the receiver, but with a different [L2Register].
	 */
	abstract fun copyForRegister(newRegister: L2Register<K>): L2ReadOperand<K>

	override fun instructionWasInserted(
		newInstruction: L2Instruction)
	{
		super.instructionWasInserted(newInstruction)
		register().addUse(this)
	}

	override fun instructionWasRemoved()
	{
		super.instructionWasRemoved()
		register().removeUse(this)
	}

	override fun replaceRegisters(
		registerRemap: Map<L2Register<*>, L2Register<*>>,
		theInstruction: L2Instruction)
	{
		val replacement: L2Register<K>? = registerRemap[register].cast()
		if (replacement === null || replacement === register)
		{
			return
		}
		register().removeUse(this)
		replacement.addUse(this)
		register = replacement
	}

	override fun transformEachRead(
		transformer: (L2ReadOperand<*>) -> L2ReadOperand<*>
	) : L2ReadOperand<K> = transformer(this).cast()

	override fun addReadsTo(readOperands: MutableList<L2ReadOperand<*>>)
	{
		readOperands.add(this)
	}

	override fun addSourceRegistersTo(
		sourceRegisters: MutableList<L2Register<*>>)
	{
		sourceRegisters.add(register)
	}

	override fun appendTo(builder: StringBuilder)
	{
		builder.append('@').append(registerString())
		if (restriction.constantOrNull === null)
		{
			// Don't redundantly print restriction information for constants.
			builder.append(restriction.suffixString())
		}
	}

	/**
	 * Answer the [L2Instruction] which generates the value that will populate
	 * this register. Skip over move instructions. The containing graph must be
	 * in SSA form.
	 *
	 * @return
	 *   The requested `L2Instruction`.
	 */
	fun definitionSkippingMoves(): L2Instruction
	{
		var other = definition().instruction
		while (true)
		{
			val op = other.operation
			other = when
			{
				op is L2_MOVE<*> ->
				{
					op.sourceOf(other).definition().instruction
				}
				else -> return other
			}
		}
	}

	/**
	 * Find the set of [L2SemanticValue]s and [TypeRestriction] leading to this
	 * read operand.  The control flow graph is not necessarily in SSA form, so
	 * the underlying register may have multiple definitions to choose from,
	 * some of which are not in this read's history.
	 *
	 * If there is a write of the register in the same block as the read,
	 * extract the information from that.
	 *
	 * Otherwise each incoming edge must carry this information in its
	 * manifest.  Note that there's no phi function to merge differing registers
	 * into this one, otherwise the phi itself would have been considered the
	 * nearest write.  We still have to take the union of the restrictions, and
	 * the intersection of the synonyms' sets of [L2SemanticValue]s.
	 *
	 * @return
	 *   A [Pair] consisting of a [Set] of synonymous [L2SemanticValue]s, and
	 *   the [TypeRestriction] guaranteed at this read.
	 */
	fun findSourceInformation(): Pair<Set<L2SemanticValue<K>>, TypeRestriction>
	{
		// Either the write must happen inside the block we're moving from, or
		// it must have come in along the edges, and is therefore in each
		// incoming edge's manifest.
		val thisBlock = instruction.basicBlock()
		for (def in register.definitions())
		{
			if (def.instruction.basicBlock() == thisBlock)
			{
				// Ignore ghost instructions that haven't been fully removed
				// yet, during placeholder substitution.
				if (thisBlock.instructions().contains(def.instruction))
				{
					return def.semanticValues() to def.restriction()
				}
			}
		}

		// Integrate the information from the block's incoming manifests.
		val incoming = thisBlock.predecessorEdges().iterator()
		assert(incoming.hasNext())
		val firstManifest = incoming.next().manifest()
		val semanticValues = mutableSetOf<L2SemanticValue<K>>()
		var typeRestriction: TypeRestriction? = null
		for (syn in firstManifest.synonymsForRegister(register))
		{
			semanticValues.addAll(syn.semanticValues())
			val nextRestriction =
				firstManifest.restrictionFor(syn.pickSemanticValue())
			typeRestriction = when (typeRestriction)
			{
				null -> nextRestriction
				else -> typeRestriction.union(nextRestriction)
			}
		}
		incoming.forEachRemaining {
			val nextManifest = it.manifest()
			val newSemanticValues = mutableSetOf<L2SemanticValue<K>>()
			for (syn in nextManifest.synonymsForRegister(register))
			{
				newSemanticValues.addAll(syn.semanticValues())
				typeRestriction = typeRestriction!!.union(
					nextManifest.restrictionFor(syn.pickSemanticValue()))
			}
			// Intersect with the newSemanticValues.
			semanticValues.retainAll(newSemanticValues)
		}
		return semanticValues to typeRestriction!!
	}

	/**
	 * Answer the [L2WriteBoxedOperand] which produces the value that will
	 * populate this register. Skip over move instructions. Also skip over
	 * boxing and unboxing operations that don't alter the value. The containing
	 * graph must be in SSA form.
	 *
	 * @return
	 *   The requested `L2Instruction`.
	 */
	fun originalBoxedWriteSkippingMoves(): L2WriteBoxedOperand
	{
		var def: L2WriteOperand<*> = definition()
		var earliestBoxed: L2WriteBoxedOperand? = null
		while (true)
		{
			if (def is L2WriteBoxedOperand)
			{
				earliestBoxed = def
			}
			val instruction = def.instruction
			if (instruction.operation.isMove)
			{
				val operation = instruction.operation
					.cast<L2Operation?, L2_MOVE<K>>()
				def = operation.sourceOf(instruction).definition()
				continue
			}
			//TODO: Trace back through L2_[BOX|UNBOX]_[INT|FLOAT], etc.
			return earliestBoxed!!
		}
	}

	/**
	 * Create a new register of a suitable type for this read.
	 *
	 * @return
	 *   A new [L2Register] of the appropriate [RegisterKind] (i.e., [K]).
	 */
	abstract fun createNewRegister(): L2Register<K>

	override fun postOptimizationCleanup()
	{
		// Leave the restriction in place.  It shouldn't be all that big.
		// Same for the semanticValue.
		restriction.makeShared()
	}
}
