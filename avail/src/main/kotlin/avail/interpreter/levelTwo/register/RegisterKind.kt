/*
 * RegisterKind.kt
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
package avail.interpreter.levelTwo.register

import avail.descriptor.numbers.A_Number.Companion.extractDouble
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_FLOAT_FLAG
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_MOVE_BOXED
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_MOVE_FLOAT
import avail.interpreter.levelTwo.operation.L2_MOVE_INT
import avail.interpreter.levelTwo.operation.L2_PHI
import avail.interpreter.levelTwo.operation.L2_PHI_BOXED
import avail.interpreter.levelTwo.operation.L2_PHI_FLOAT
import avail.interpreter.levelTwo.operation.L2_PHI_INT
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Synonym
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedFloat
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticDummy
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.utility.cast
import avail.utility.mapToSet
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * One of the kinds of registers that Level Two supports.
 *
 * @property kindName
 *   The descriptive name of this register kind.
 * @property prefix
 *   The prefix to use for registers of this kind.
 * @property jvmTypeString
 *    The JVM [Type] string.
 * @property loadInstruction
 *   The JVM instruction that loads a register of this kind.
 * @property storeInstruction
 *   The JVM instruction for storing.
 * @property restrictionFlag
 *   The [RestrictionFlagEncoding] used to indicate a [TypeRestriction] has
 *   an available register of this kind.
 * @property Self
 *   The receiver's statically determinable type.
 *
 * @constructor
 * Create an instance of the enum.
 *
 * @param ordinal
 *   A unique [Int] for each instance.
 * @param kindName
 *   A descriptive name for this kind of register.
 * @param prefix
 *   The prefix to use when naming registers of this kind.
 * @param jvmTypeString
 *   The canonical [String] used to identify this [Type] of register to the
 *   JVM.
 * @param loadInstruction
 *   The JVM instruction for loading.
 * @param storeInstruction
 *   The JVM instruction for storing.
 * @param restrictionFlag
 *   The corresponding [RestrictionFlagEncoding].
 */
sealed class RegisterKind<Self : RegisterKind<Self>>
constructor (
	val ordinal: Int,
	val kindName: String,
	val prefix: String,
	val jvmTypeString: String,
	val loadInstruction: Int,
	val storeInstruction: Int,
	val restrictionFlag: RestrictionFlagEncoding)
{
	/**
	 * Answer a suitable [L2ReadOperand] for extracting the indicated
	 * [L2SemanticValue] of this kind.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] to consume via an [L2ReadOperand].
	 * @param restriction
	 *   The [TypeRestriction] relevant to this read.
	 * @param register
	 *   The earliest known defining [RegisterKind] of the [L2SemanticValue].
	 * @return
	 *   The new [L2ReadOperand].
	 */
	abstract fun readOperand(
		semanticValue: L2SemanticValue<Self>,
		restriction: TypeRestriction,
		register: L2Register<Self>
	): L2ReadOperand<Self>

	/**
	 * Synthesize an [L2ReadOperand] of the appropriately strengthened [Self]
	 * kind of register.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] being read.2
	 * @param manifest
	 *   The [L2ValueManifest] from which to extract the semantic value.
	 */
	abstract fun createRead(
		semanticValue: L2SemanticValue<Self>,
		manifest: L2ValueManifest
	): L2ReadOperand<Self>

	/**
	 * Synthesize an [L2WriteOperand] of the appropriately strengthened [Self]
	 * kind of register.
	 *
	 * @param semanticValues
	 *   The [L2SemanticValue]s to populate.
	 * @param restriction
	 *   The [TypeRestriction] that the stored values will satisfy.
	 * @return
	 *   A new [L2WriteOperand] of the appropriate [Self] kind of register.
	 */
	abstract fun createWrite(
		semanticValues: Set<L2SemanticValue<Self>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<Self>? = null
	): L2WriteOperand<Self>

	/**
	 * Create a register of this kind with the given unique id.
	 *
	 * @param id
	 *   The unique id number for the new [L2Register].
	 */
	abstract fun createRegister(id: Int): L2Register<Self>

	/**
	 * Create an [L2ReadVectorOperand] suitable for the [RegisterKind].
	 *
	 * @param elements
	 *   A [List] of [L2ReadOperand]s, strengthened to the [Self] kind of
	 *   register.
	 * @return
	 *   An [L2ReadVectorOperand] for register kind [Self].
	 */
	abstract fun createVector(
		elements: List<L2ReadOperand<Self>>
	): L2ReadVectorOperand<L2ReadOperand<Self>>

	/**
	 * Synthesize an [L2_MOVE] instruction for this [RegisterKind].
	 *
	 * @param source
	 *   An [L2ReadOperand] supplying the value.
	 * @param destination
	 *   An [L2WriteOperand] accepting the value.
	 */
	abstract fun move(
		source: L2ReadOperand<Self>,
		destination: L2WriteOperand<Self>
	): L2_MOVE<Self>

	/**
	 * Synthesize an [L2_MOVE] instruction for this [RegisterKind], but delaying
	 * the type check to runtime.
	 *
	 * @param source
	 *   An [L2SemanticValue] supplying the value.
	 * @param destinations
	 *   The [L2SemanticValue]s to write.
	 * @param manifest
	 *   The current [L2ValueManifest].
	 * @param restriction
	 *   The [TypeRestriction] indicating what type of value is being moved.
	 */
	fun dynamicMove(
		source: L2SemanticValue<*>,
		destinations: Set<L2SemanticValue<*>>,
		manifest: L2ValueManifest,
		restriction: TypeRestriction
	): L2_MOVE<Self>
	{
		assert(source.kind == this)
		assert(destinations.all { it.kind == this })
		return move(
			createRead(source.cast(), manifest),
			createWrite(destinations.cast(), restriction))
	}

	/**
	 * Synthesize a suitable [L2_MOVE_CONSTANT] if the value is not already in a
	 * register of this kind, and answer an [L2ReadOperand] that extracts it.
	 *
	 * @param generator
	 *   The [L2GeneratorInterface] on which to write instructions.
	 * @param boxedValue
	 *   An [AvailObject] supplying the boxed version of the value to move.
	 * @return
	 *   An [L2ReadOperand] that retrieves the constant value.
	 */
	abstract fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadOperand<Self>

	abstract fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<Self>>,
		destination: L2WriteOperand<Self>
	): L2_PHI<Self>

	/**
	 * Generate an [L2_PHI] and any additional moves to ensure the given set of
	 * related [L2SemanticValue]s are populated with values from the given
	 * sources.
	 *
	 * If there is already an [L2Register] common to all incoming edges'
	 * manifests (and [forcePhiCreation] is false), we can avoid the [L2_PHI]
	 * and instead use that register, moving the value into any semantic values
	 * in [relatedSemanticValues] that aren't already covered in all incoming
	 * edges.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to write instructions.
	 * @param relatedSemanticValues
	 *   The [List] of [L2SemanticValue]s that should constitute a synonym in
	 *   the current manifest, due to them being mutually connected to a synonym
	 *   in each predecessor manifest.  The synonyms may differ in the
	 *   predecessor manifests, but within each manifest there must be a synonym
	 *   for that manifest that contains all of these semantic values.
	 * @param forcePhiCreation
	 *   Whether to force creation of a phi instruction, even if all incoming
	 *   sources of the value are the same.
	 * @param typeRestriction
	 *   The [TypeRestriction] to bound the synonym.
	 * @param sourceManifests
	 *   A [List] of [L2ValueManifest]s, one for each incoming edge.
	 */
	fun L2GeneratorInterface.generatePhi(
		relatedSemanticValues: List<L2SemanticValue<Self>>,
		forcePhiCreation: Boolean,
		typeRestriction: TypeRestriction,
		sourceManifests: List<L2ValueManifest>
	): Unit
	{
		// Keep registers that are common to all incoming manifests.  Register
		// coloring will shorten chains of moves, and besides, they'll all be
		// under the same synonym anyhow.
		val relatedSemanticValuesSet = relatedSemanticValues.toSet()
		val pickSemanticValue = relatedSemanticValues[0]
		val registers = sourceManifests
			.map { m -> m.getDefinitions(pickSemanticValue).toSet() }
			.reduce(Set<L2Register<Self>>::intersect)
		if (registers.isNotEmpty() && !forcePhiCreation)
		{
			// There's at least one register common to all inputs, and the phi
			// creation isn't forced.  There should be at least one semantic
			// value in common along each input.  First update the manifest to
			// make the register(s) and the semantic values visible.
			currentManifest.introduceSynonym(
				L2Synonym(relatedSemanticValues), typeRestriction)
			currentManifest.updateDefinitions(pickSemanticValue) {
				plus(registers)
			}
			return
		}
		val (inSynonym, notInSynonym) = relatedSemanticValuesSet
			.partition(currentManifest::hasSemanticValue)
		assert(inSynonym.isEmpty()) //TODO Is this a valid invariant?
		val existingSynonyms = inSynonym
			.mapToSet(transform = currentManifest::semanticValueToSynonym)
		if (registers.isNotEmpty() && !forcePhiCreation)
		{
			// There's at least one register common to all inputs, and the phi
			// isn't forced.  First, merge the relevant synonyms, since they
			// represent the same value.
			if (existingSynonyms.size > 1)
			{
				val pick = existingSynonyms.first().pickSemanticValue()
				existingSynonyms.forEach {
					currentManifest.mergeExistingSemanticValues(
						pick, it.pickSemanticValue())
				}
			}
			if (notInSynonym.isNotEmpty())
			{
				// Move into any semantic values not already covered.
				moveRegister(pickSemanticValue, notInSynonym)
			}
			else if (registers.isEmpty())
			{
				// There are no applicable registers that were in common along
				// all incoming edges, so introduce an artificial move (to a
				// dummy semantic value) to ensure there is a visible definition
				// point (i.e., a visible register).
				moveRegister(pickSemanticValue, setOf(createSemanticDummy(this)))
			}
			// Remove any postponed instructions that the common register was
			// able to supply already.
			currentManifest.removePostponedInstructionFor(pickSemanticValue)

			//TODO Remove
			//		typeRestriction.constantOrNull?.let { constant ->
			//			// The value is constrained down to a constant, so make sure the
			//			// semantic constant with that value (and kind) is in the new
			//			// synonym. Either it's already present, it neeeds to be added to
			//			// the new synonym, or an existing synonym containing it has to be
			//			// merged with the new synonym.
			//			val semanticConstant = createSemanticConstant(constant)
			//			when
			//			{
			//				// Already in the new synonym.
			//				semanticConstant in relatedSemanticValuesSet -> { }
			//				// Already in the manifest for another synonym.  Merge them.
			//				currentManifest.hasSemanticValue(semanticConstant) ->
			//					currentManifest.mergeExistingSemanticValues(
			//						pickSemanticValue, semanticConstant)
			//				// Not yet in the manifest.  Augment the new synonym.
			//				else -> currentManifest.extendSynonym(
			//					currentManifest.semanticValueToSynonym(pickSemanticValue),
			//					semanticConstant)
			//			}
			//		}
			currentManifest.check()
			return
		}
		// Create a phi instruction, because we don't have the value in a
		// common register in all inputs – or because the phi is forced.
		val sources = sourceManifests.map { man ->
			readOperand(
				pickSemanticValue,
				man.restrictionFor(pickSemanticValue),
				man.getDefinition(pickSemanticValue))
		}
		+createPhi(
			createVector(sources),
			createWrite(relatedSemanticValuesSet, typeRestriction))
	}

	/**
	 * Create an [L2SemanticConstant] or a wrapped version if unboxed.
	 */
	abstract fun createSemanticConstant(
		value: AvailObject
	): L2SemanticValue<Self>

	/**
	 * Create an [L2SemanticDummy] or a wrapped version if unboxed.
	 *
	 * @param generator
	 *   The [L2GeneratorInterface] that's used to generate unique ids.
	 */
	abstract fun createSemanticDummy(
		generator: L2GeneratorInterface
	): L2SemanticValue<Self>

	companion object
	{
		/** Don't modify this array. */
		val all: Array<RegisterKind<*>> = arrayOf(
			BOXED_KIND,
			INTEGER_KIND,
			FLOAT_KIND)
	}
}


/**
 * The kind of register that holds an [AvailObject].
 */
object BOXED_KIND : RegisterKind<BOXED_KIND>(
	ordinal = 0,
	kindName = "boxed",
	prefix = "r",
	jvmTypeString = Type.getDescriptor(AvailObject::class.java),
	loadInstruction = Opcodes.ALOAD,
	storeInstruction = Opcodes.ASTORE,
	restrictionFlag = BOXED_FLAG)
{
	override fun readOperand(
		semanticValue: L2SemanticValue<BOXED_KIND>,
		restriction: TypeRestriction,
		register: L2Register<BOXED_KIND>
	) = L2ReadBoxedOperand(semanticValue, restriction).apply {
		setRegister(register)
	}

	override fun createRead(
		semanticValue: L2SemanticValue<BOXED_KIND>,
		manifest: L2ValueManifest
	): L2ReadBoxedOperand
	{
		semanticValue as L2SemanticBoxedValue
		val restriction = manifest.restrictionFor(semanticValue)
		assert(restriction.isBoxed)
		return L2ReadBoxedOperand(semanticValue, restriction)
	}

	override fun createWrite(
		semanticValues: Set<L2SemanticValue<BOXED_KIND>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<BOXED_KIND>?
	) = L2WriteBoxedOperand(semanticValues, restriction, forceRegister)

	override fun createRegister(id: Int) = L2BoxedRegister(id)

	override fun createVector(
		elements: List<L2ReadOperand<BOXED_KIND>>
	) = L2ReadBoxedVectorOperand(elements.cast())

	override fun move(
		source: L2ReadOperand<BOXED_KIND>,
		destination: L2WriteOperand<BOXED_KIND>
	) = L2_MOVE_BOXED(source.cast(), destination.cast())

	override fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadBoxedOperand = generator.boxedConstant(boxedValue)

	override fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<BOXED_KIND>>,
		destination: L2WriteOperand<BOXED_KIND>
	) = L2_PHI_BOXED(sources.cast(), destination.cast())

	override fun createSemanticConstant(
		value: AvailObject
	): L2SemanticBoxedValue = constant(value)

	override fun createSemanticDummy(
		generator: L2GeneratorInterface
	) = L2SemanticDummy(generator.nextUnique())
}

/**
 * The kind of register that holds an [Int].
 */
object INTEGER_KIND : RegisterKind<INTEGER_KIND>(
	ordinal = 1,
	kindName = "int",
	prefix = "i",
	jvmTypeString = Type.INT_TYPE.descriptor,
	loadInstruction = Opcodes.ILOAD,
	storeInstruction = Opcodes.ISTORE,
	restrictionFlag = UNBOXED_INT_FLAG)
{
	override fun readOperand(
		semanticValue: L2SemanticValue<INTEGER_KIND>,
		restriction: TypeRestriction,
		register: L2Register<INTEGER_KIND>
	) = L2ReadIntOperand(semanticValue, restriction).apply {
		setRegister(register)
	}

	override fun createRead(
		semanticValue: L2SemanticValue<INTEGER_KIND>,
		manifest: L2ValueManifest
	): L2ReadIntOperand
	{
		semanticValue as L2SemanticUnboxedInt
		val restriction = manifest.restrictionFor(semanticValue)
		assert(restriction.isUnboxedInt)
		return L2ReadIntOperand(semanticValue, restriction)
	}

	override fun createWrite(
		semanticValues: Set<L2SemanticValue<INTEGER_KIND>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<INTEGER_KIND>?
	): L2WriteIntOperand
	{
		assert(restriction.isUnboxedInt)
		return L2WriteIntOperand(semanticValues, restriction, forceRegister)
	}

	override fun createRegister(id: Int) = L2IntRegister(id)

	override fun createVector(
		elements: List<L2ReadOperand<INTEGER_KIND>>
	) = L2ReadIntVectorOperand(elements.cast())

	override fun move(
		source: L2ReadOperand<INTEGER_KIND>,
		destination: L2WriteOperand<INTEGER_KIND>
	) = L2_MOVE_INT(source.cast(), destination.cast())

	override fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadIntOperand = generator.unboxedIntConstant(boxedValue.extractInt)

	override fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<INTEGER_KIND>>,
		destination: L2WriteOperand<INTEGER_KIND>
	) = L2_PHI_INT(sources.cast(), destination.cast())

	override fun createSemanticConstant(
		value: AvailObject
	): L2SemanticUnboxedInt = constant(value).unboxedInt

	override fun createSemanticDummy(
		generator: L2GeneratorInterface
	) = L2SemanticDummy(generator.nextUnique()).unboxedInt
}

/**
 * The kind of register that holds a `double`.
 */
object FLOAT_KIND : RegisterKind<FLOAT_KIND>(
	ordinal = 2,
	kindName = "float",
	prefix = "f",
	jvmTypeString = Type.DOUBLE_TYPE.descriptor,
	loadInstruction = Opcodes.DLOAD,
	storeInstruction = Opcodes.DSTORE,
	restrictionFlag = UNBOXED_FLOAT_FLAG)
{
	override fun readOperand(
		semanticValue: L2SemanticValue<FLOAT_KIND>,
		restriction: TypeRestriction,
		register: L2Register<FLOAT_KIND>
	) = L2ReadFloatOperand(semanticValue, restriction).apply {
		setRegister(register)
	}

	override fun createRead(
		semanticValue: L2SemanticValue<FLOAT_KIND>,
		manifest: L2ValueManifest
	): L2ReadFloatOperand
	{
		semanticValue as L2SemanticUnboxedFloat
		val restriction = manifest.restrictionFor(semanticValue)
		assert(restriction.isUnboxedFloat)
		return L2ReadFloatOperand(semanticValue, restriction)
	}

	override fun createWrite(
		semanticValues: Set<L2SemanticValue<FLOAT_KIND>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<FLOAT_KIND>?
	): L2WriteFloatOperand
	{
		assert(restriction.isUnboxedFloat)
		return L2WriteFloatOperand(semanticValues, restriction, forceRegister)
	}

	override fun createRegister(id: Int) = L2FloatRegister(id)

	override fun createVector(
		elements: List<L2ReadOperand<FLOAT_KIND>>
	) = L2ReadFloatVectorOperand(elements.cast())

	override fun move(
		source: L2ReadOperand<FLOAT_KIND>,
		destination: L2WriteOperand<FLOAT_KIND>
	) = L2_MOVE_FLOAT(source.cast(), destination.cast())

	override fun readConstant(
		generator: L2GeneratorInterface,
		boxedValue: AvailObject
	): L2ReadFloatOperand =
		generator.unboxedFloatConstant(boxedValue.extractDouble)

	override fun createPhi(
		sources: L2ReadVectorOperand<L2ReadOperand<FLOAT_KIND>>,
		destination: L2WriteOperand<FLOAT_KIND>
	) = L2_PHI_FLOAT(sources.cast(), destination.cast())

	override fun createSemanticConstant(
		value: AvailObject
	): L2SemanticUnboxedFloat = constant(value).unboxedFloat

	override fun createSemanticDummy(
		generator: L2GeneratorInterface
	) = L2SemanticDummy(generator.nextUnique()).unboxedFloat
}
