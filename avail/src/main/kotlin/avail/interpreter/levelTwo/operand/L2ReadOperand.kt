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
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import avail.utility.mapToSet
import avail.utility.notNullAnd

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
 * @param registerOrNull
 *   The optional [L2Register] being read by this operand.  This may be null
 *   until the containing [L2Instruction] is written to an [L2BasicBlock].
 */
abstract class L2ReadOperand<K : RegisterKind<K>>
protected constructor(
	private var semanticValue: L2SemanticValue<K>,
	private var restriction: TypeRestriction,
	private var registerOrNull: L2Register<K>? = null
) : L2Operand()
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
	 * Answer this read's [L2Register].
	 *
	 * @return
	 *   The register.
	 */
	open fun register(): L2Register<K> = registerOrNull!!

	/**
	 * Set the [L2Register] that this instruction should read.
	 */
	fun setRegister(newRegister: L2Register<K>)
	{
		registerOrNull = newRegister
	}

	/**
	 * Answer the [L2SemanticValue] being read.
	 *
	 * @return
	 *   The [L2SemanticValue].
	 */
	open fun semanticValue(): L2SemanticValue<K> = semanticValue

	/**
	 * Answer whether this [L2ReadOperand] supplies a constant directly, rather
	 * than consuming it from a prior point of definition (write).
	 */
	val isConstantRead get() = registerOrNull.notNullAnd { isConstant }

	/**
	 * Answer a String that describes this operand for debugging.
	 *
	 * @return
	 *   A [String].
	 */
	fun registerString(): String
	{
		val regString = registerOrNull?.let { it.toString() } ?: kind.prefix
		return if (isConstantRead)
		{
			// The register has been replaced by a fresh one with no definition,
			// which later passes recognize as being a constant read at the
			// point of usage.
			"const[${register().constant}]"
		}
		else
		{
			"$regString[$semanticValue]"
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
	 * Answer the type restriction for this register read.
	 *
	 * @return
	 *   A [TypeRestriction].
	 */
	fun restriction(): TypeRestriction = restriction

	/**
	 * Alter this read's [restriction].
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
	val constantOrNull: AvailObject? get() = restriction.constantOrNull

	/**
	 * Answer the [L2WriteOperand] that provided the value that this operand is
	 * reading.  The control flow graph must be in SSA form.
	 *
	 * @return
	 *   The defining [L2WriteOperand].
	 */
	fun definition(): L2WriteOperand<K> = register().definition().cast()

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		if (!isConstantRead)
		{
			// After phi move insertion and constant substitution, there may be
			// reads of unavailable semantic values, even though the register is
			// definitely available.
			if (manifest.hasSemanticValue(semanticValue))
			{
				restrict { manifest.restrictionFor(this@L2ReadOperand) }
				// Phi instructions pass in the manifest from the appropriate
				// incoming edge, not the current manifest.  Don't write back to
				// it in this case.
				if (instruction !is L2_PHI<*>)
				{
					manifest.setRestriction(semanticValue(), restriction)
				}
			}
		}
		if (manifest.caresAboutSemanticValues && instruction !is L2_PHI<*>)
		{
			setRegister(manifest.getDefinition(semanticValue))
		}
		register().addUse(this)
	}

	override fun instructionWasInserted(
		newInstruction: L2Instruction
	)
	{
		super.instructionWasInserted(newInstruction)
		register().addUse(this)
	}

	override fun instructionWasRemoved()
	{
		super.instructionWasRemoved()
		register().removeUse(this)
	}

	override fun transformEachRead(
		transformer: (L2ReadOperand<*>)->L2ReadOperand<*>
	): L2ReadOperand<K> = transformer(this).cast()

	override fun addReadsTo(readOperands: MutableList<L2ReadOperand<*>>)
	{
		readOperands.add(this)
	}

	override fun addSourceRegistersTo(
		sourceRegisters: MutableList<L2Register<*>>)
	{
		registerOrNull?.let(sourceRegisters::add)
	}

	override fun adjustCloneForInstruction(
		theInstruction: L2Instruction,
		generator: L2GeneratorInterface)
	{
		super.adjustCloneForInstruction(theInstruction, generator)
		if (generator.mode == BySemanticValue)
		{
			if (theInstruction is L2_PHI<*>)
			{
				// Phi instructions are constructed with the incoming registers
				// already set up.  Because otherwise the generator wouldn't be
				// able to.
				assert(registerOrNull != null)
			}
		}
		else
		{
			assert(registerOrNull != null)
		}
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

	override fun simpleAppendOperand(
		commands: MutableList<String>,
		sources: MutableList<String>,
		targets: MutableList<String>)
	{
		if (isConstantRead)
			simpleAppendConstant(constantOrNull!!, commands, sources)
		else
			sources.add(register().toString())
	}

	/**
	 * Answer the [L2Instruction] which generates the value that will populate
	 * this register. Skip over move instructions. The containing graph must be
	 * in SSA form.
	 *
	 * @param manifest
	 *   The manifest in which to follow a postponed instruction chain, if
	 *   necessary.
	 * @return
	 *   The requested [L2Instruction], which could still be postponed or
	 *   already emitted.
	 */
	fun definitionSkippingMoves(manifest: L2ValueManifest?): L2Instruction
	{
		val sourceInstruction: L2Instruction =
			registerOrNull?.definition()?.instruction
				?: (manifest!!.getDefinitionOrNull(semanticValue)?.definition()
					?.instruction)
				?: (manifest!!.postponedInstructionFor(semanticValue)!!)
		return when (sourceInstruction)
		{
			// Recurse.  Iteration wouldn't be worth it here.
			is L2_MOVE<*> ->
				sourceInstruction.source.definitionSkippingMoves(manifest)
			else -> sourceInstruction
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
		for (def in register().definitions())
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
		for (syn in firstManifest.synonymsForRegister(register()))
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
			for (syn in nextManifest.synonymsForRegister(register()))
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
	 * Create a new *consstant* pseudo-register, using the restriction to
	 * determine the constant value.
	 */
	abstract fun createConstantRegister(): L2Register<K>

	/**
	 * Create a new *consstant* pseudo-register, using the restriction to
	 * determine the constant value.
	 */
	abstract fun createSemanticConstant(): L2SemanticValue<K>

	/**
	 * If this [L2ReadOperand] produces a constant value, replace its register
	 * with a fresh one that has no definition, to break dependency chains from
	 * its defining writes, allowing fewer registers to be live at the same
	 * time, and return true.  Otherwise return false.
	 *
	 * @return
	 *   Whether the register was replaced because it's a constant read.
	 */
	fun replaceIfConstantRead(): Boolean
	{
		if (constantOrNull === null) return false

		instruction.sourceRegisters.remove(register())
		register().removeUse(this)
		registerOrNull = createConstantRegister()
		semanticValue = createSemanticConstant()
		register().addUse(this)
		// Simply rebuild the sourceRegisters.
		instruction.sourceRegisters.clear()
		instruction.readOperands.forEach { read ->
			instruction.sourceRegisters.add(read.register())
		}
		return true
	}

	/** Only pay attention to the semantic value. */
	override fun equivalentTo(other: L2Operand) =
		other is L2ReadOperand<*>
			&& semanticValue == other.semanticValue

	override val equivalentHash: Int get() = semanticValue.hashCode()

	override fun mergeFromOperands(operands: List<L2Operand>)
	{
		assert(operands.all(::equivalentTo))
		@Suppress("UNCHECKED_CAST")
		operands as List<L2ReadOperand<*>>
		val values = operands.mapToSet { it.semanticValue }
		assert(values.size == 1)
		restriction = operands
			.map { it.restriction }
			.reduce(TypeRestriction::union)
	}

	override fun postOptimizationCleanup()
	{
		// Leave the restriction in place.  It shouldn't be all that big.
		// Same for the semanticValue.
		restriction.makeShared()
	}
}
