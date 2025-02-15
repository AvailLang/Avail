/*
 * L2WriteOperand.kt
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

import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForConstant
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2Synonym
import avail.optimizer.L2Synonym.Companion.appendSemanticValues
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast

/**
 * `L2WriteOperand` abstracts the capabilities of actual register write
 * operands.
 *
 * @param K
 * The subclass of [RegisterKind] that this operates on.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property restriction
 *   The [TypeRestriction] that indicates what values may be written to the
 *   destination register.
 * @property register
 *   The actual [L2Register]. This is only set during late optimization of the
 *   control flow graph.
 *
 * @constructor
 * Construct a new `L2WriteOperand` for the specified [L2SemanticValue].
 *
 * @param semanticValues
 *   The [Set] of [L2SemanticValue] that this operand is effectively producing.
 * @param restriction
 *   The [TypeRestriction] that indicates what values are allowed to be written
 *   into the register.
 * @param registerOrNull
 *   The [L2Register] to write.  This can be null until the instruction is
 *   actually written to an [L2BasicBlock].
 */
abstract class L2WriteOperand<K : RegisterKind<K>>
constructor(
	private var semanticValues: Set<L2SemanticValue<K>>,
	private var restriction: TypeRestriction,
	protected var registerOrNull: L2Register<K>? = null
) : L2Operand()
{
	/**
	 * Answer the [RegisterKind] of register that is written by this
	 * `L2WriteOperand`.
	 *
	 * @return
	 *   The [RegisterKind].
	 */
	abstract val kind: K

	/**
	 * Answer this write's immutable set of [L2SemanticValue]s.
	 *
	 * @return
	 *   The semantic value being written.
	 */
	open fun semanticValues(): Set<L2SemanticValue<K>> = semanticValues

	/**
	 * Answer this write's sole [L2SemanticValue], failing if there isn't
	 * exactly one.
	 *
	 * @return
	 *   The write operand's [L2SemanticValue].
	 */
	open fun onlySemanticValue(): L2SemanticValue<K>
	{
		assert(semanticValues.size == 1)
		return semanticValues.single()
	}

	/**
	 * Choose an arbitrary one of the [L2SemanticValue]s that this operand
	 * writes.
	 *
	 * @return
	 *   The write operand's [L2SemanticValue].
	 */
	open fun pickSemanticValue(): L2SemanticValue<K> = semanticValues.first()

	/**
	 * Answer this write's [TypeRestriction].
	 *
	 * @return
	 *   The [TypeRestriction] that constrains what's being written.
	 */
	fun restriction(): TypeRestriction = restriction

	/**
	 * Alter this write's [restriction].
	 *
	 * @param transformer
	 *   An extension lambda to produce a new [TypeRestriction] from the
	 *   existing one.
	 */
	fun restrict(transformer: TypeRestriction.()->TypeRestriction)
	{
		// Try to preserve the restriction's identity if unchanged.
		val newRestriction = restriction.transformer().intersection(restriction)
		if (newRestriction != restriction)
		{
			restriction = newRestriction
		}
	}

	/**
	 * Answer the [L2Register]'s [finalIndex][L2Register.finalIndex].
	 *
	 * @return
	 *   The index of the register, computed during register coloring.
	 */
	fun finalIndex(): Int = register().finalIndex

	/**
	 * Answer the register that is to be written.
	 *
	 * @return
	 *   An [L2Register].
	 */
	open fun register(): L2Register<K> = registerOrNull!!

	/**
	 * Answer the register that is to be written, if that is already set.
	 *
	 * @return
	 *   An [L2Register] or `null`.
	 */
	fun registerIfKnown() = registerOrNull

	/**
	 * Answer a String that describes this operand for debugging.
	 *
	 * @return
	 *   A [String].
	 */
	fun registerString(): String = buildString {
		when (val reg = registerOrNull)
		{
			null -> append(kind.prefix)
			else -> append(reg.toString())
		}
		append("[")
		appendSemanticValues(semanticValues, false)
		append("]")
	}

	override fun adjustCloneForInstruction(
		theInstruction: L2Instruction,
		generator: L2GeneratorInterface)
	{
		super.adjustCloneForInstruction(theInstruction, generator)
		if (generator.mode == BySemanticValue)
			registerOrNull = kind.createRegister(generator.nextUnique())
	}

	/**
	 * Force this write to be to a particular [L2Register].  This operand must
	 * be part of an instruction that has not yet been emitted, so don't update
	 * anything except the register field.
	 */
	fun forceRegister(register: L2Register<K>)
	{
		registerOrNull = register
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		register().addDefinition(this)
		manifest.recordDefinition(this)
		manifest.removePostponedInstructionFor(this)
	}

	/**
	 * This operand is a write of a move-like operation.  Make the semantic
	 * value a synonym of the given [L2ReadOperand]'s semantic value.
	 *
	 * @param source
	 *   The [L2ReadOperand] that provides the value.
	 * @param manifest
	 *   The [L2ValueManifest] in which to capture the synonymy of the source
	 *   and destination.
	 */
	fun instructionWasAddedForMove(
		source: L2ReadOperand<K>,
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		register().addDefinition(this)
		manifest.recordDefinitionForMove(this, source.semanticValue())
	}

	/**
	 * An [L2_MOVE_CONSTANT] has been generated, so update the provided current
	 * [L2ValueManifest] to reflect that change.
	 *
	 * @param semanticConstant
	 *   The semanticc constant that was moved.
	 * @param manifest
	 *   The [L2ValueManifest] to update with the fact of this move.
	 */
	fun instructionWasAddedForMoveConstant(
		semanticConstant: L2SemanticValue<K>,
		restriction: TypeRestriction,
		manifest: L2ValueManifest)
	{
		assert(semanticConstant.isConstant)
		super.instructionWasAdded(manifest)
		register().addDefinition(this)
		val (old, new) = (semanticValues() + semanticConstant)
			.partition(manifest::hasSemanticValue)
		if (new.isNotEmpty())
		{
			manifest.introduceSynonym(L2Synonym(new), restriction)
			if (old.isNotEmpty())
			{
				manifest.mergeExistingSemanticValues(old.first(), new.first())
			}
		}
		manifest.recordDefinitionForMove(this, semanticConstant)
	}

	override fun instructionWasInserted(newInstruction: L2Instruction)
	{
		super.instructionWasInserted(newInstruction)
		register().addDefinition(this)
	}

	override fun instructionWasRemoved()
	{
		super.instructionWasRemoved()
		register().removeDefinition(this)
	}

	override fun transformEachWrite(
		transformer: (L2WriteOperand<*>)->L2WriteOperand<*>
	): L2WriteOperand<K> = transformer(this).cast()

	/**
	 * Add the given [L2SemanticValue] to this write operand's set of semantic
	 * values.  DO NOT update any other structures to reflect this change, as
	 * this is the caller's responsibility.
	 *
	 * @param newSemanticValue
	 *   The new [L2SemanticValue] to add to the write operand's set of semantic
	 *   values.
	 */
	fun retroactivelyIncludeSemanticValue(newSemanticValue: L2SemanticValue<K>)
	{
		semanticValues += newSemanticValue
	}

	override fun addWritesTo(writeOperands: MutableList<L2WriteOperand<*>>)
	{
		writeOperands.add(this)
	}

	override fun addDestinationRegistersTo(
		destinationRegisters: MutableList<L2Register<*>>)
	{
		registerOrNull?.let { destinationRegisters += it }
	}

	/**
	 * Ensure the given constant is written to each of the [L2SemanticValue]s
	 * of the given [L2WriteOperand].
	 */
	fun moveConstantForWrite(
		constant: AvailObject,
		generator: L2GeneratorInterface
	): Unit = generator.run {
		val read = kind.readConstant(generator, constant)
		// Populate the rest of the semantic values.
		val (old, new) = semanticValues().partition(
			currentManifest::hasSemanticValue)
		if (new.isNotEmpty())
		{
			+kind.move(
				read,
				kind.createWrite(
					new.toSet(),
					restrictionForConstant(
						constant, kind.restrictionFlag)))
		}
		// Ensure already-populated semantic values end up in the same synonym
		// as the semantic constant.
		for (oldValue in old)
		{
			currentManifest.mergeExistingSemanticValues(
				read.semanticValue(), oldValue)
		}
	}

	override fun appendTo(builder: StringBuilder)
	{
		builder.append("→").append(registerString())
	}

	override fun simpleAppendOperand(
		commands: MutableList<String>,
		sources: MutableList<String>,
		targets: MutableList<String>)
	{
		targets.add(register().toString())
	}

	/** Only pay attention to the semantic values. */
	override fun equivalentTo(other: L2Operand) =
		other is L2WriteOperand<*>
			&& semanticValues == other.semanticValues

	override val equivalentHash: Int get() =
		semanticValues.sumOf(L2SemanticValue<K>::hashCode)

	override fun mergeFromOperands(operands: List<L2Operand>)
	{
		assert(operands.all(::equivalentTo))
		@Suppress("UNCHECKED_CAST")
		operands as List<L2WriteOperand<K>>
		semanticValues = operands
			.map { it.semanticValues }
			.reduce(Set<L2SemanticValue<K>>::intersect)
		restriction = operands
			.map { it.restriction }
			.reduce(TypeRestriction::union)
	}

	override fun postOptimizationCleanup()
	{
		// Leave the restriction in place.  It shouldn't be all that big.
		semanticValues = emptySet()
		restriction.makeShared()
	}
}
