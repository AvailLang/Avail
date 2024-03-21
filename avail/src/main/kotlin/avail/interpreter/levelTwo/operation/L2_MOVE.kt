/*
 * L2_MOVE.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_FLOAT
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_INT
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_FLOAT
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_INT
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadFloatVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadOperand
import avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2Generator
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.truncateTo
import avail.utility.cast
import avail.utility.notNullAnd
import org.objectweb.asm.MethodVisitor

/**
 * Move an [AvailObject] from the source to the destination.  The
 * [L2Generator] creates more moves than are strictly necessary, but
 * various mechanisms cooperate to remove redundant inter-register moves.
 *
 * The object being moved is not made immutable by this operation, as that
 * is the responsibility of the [L2_MAKE_IMMUTABLE] operation, injected at
 * necessary points during very late analysis.
 *
 * @param K
 *   The [RegisterKind] of [L2Register] to be moved.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property kind
 *   The kind of data moved by this operation.
 * @constructor
 * Construct an `L2_MOVE` operation.
 *
 * @param kind
 *   The [RegisterKind] serviced by this operation.
 * @param theNamedOperandTypes
 *   An array of [L2NamedOperandType]s that describe this particular
 *   L2Operation, allowing it to be specialized by register type.
 */
abstract class L2_MOVE<K: RegisterKind<K>>
private constructor(
	val kind: K,
	vararg theNamedOperandTypes: L2NamedOperandType
) : L2Operation(
	if (kind as RegisterKind<*> == BOXED_KIND) "MOVE"
	else "MOVE(" + kind.kindName + ")",
	*theNamedOperandTypes)
{
	/**
	 * Synthesize an [L2ReadOperand] of the appropriately strengthened [K] kind
	 * of register.
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] being read.
	 * @param manifest
	 *   The [L2ValueManifest] from which to extract the semantic value.
	 */
	abstract fun createRead(
		semanticValue: L2SemanticValue<K>,
		manifest: L2ValueManifest
	): L2ReadOperand<K>

	/**
	 * Synthesize an [L2WriteOperand] of the appropriately strengthened [K] kind
	 * of register.
	 *
	 * @param uniqueGenerator
	 *   A source of unique [Int]s.
	 * @param semanticValues
	 *   The [L2SemanticValue]s to populate.
	 * @param restriction
	 *   The [TypeRestriction] that the stored values will satisfy.
	 * @param forceRegister
	 *   If specified and non-null, this is the register to be written.
	 *   Otherwise, a new one will be allocated.
	 * @return
	 *   A new [L2WriteOperand] of the appropriate [K] kind of register.
	 */
	abstract fun createWrite(
		uniqueGenerator: ()->Int,
		semanticValues: Set<L2SemanticValue<K>>,
		restriction: TypeRestriction,
		forceRegister: L2Register<K>? = null
	): L2WriteOperand<K>

	/**
	 * Create an [L2ReadVectorOperand] suitable for the [RegisterKind].  This
	 * isn't needed by the move instruction, but it's handy for parameterizing
	 * other [L2Operation]s, like [L2_PHI_PSEUDO_OPERATION].
	 *
	 * @param elements
	 *   A [List] of [L2ReadOperand]s, strengthened to the [K] kind of register.
	 * @return
	 */
	abstract fun createVector(
		elements: List<L2ReadOperand<K>>
	): L2ReadVectorOperand<L2ReadOperand<K>>

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		val source = instruction.operand<L2ReadOperand<K>>(0)
		val destination = instruction.operand<L2WriteOperand<K>>(1)

		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(manifest)
		destination.instructionWasAddedForMove(source.semanticValue(), manifest)
	}

	override fun getConstantCodeFrom(instruction: L2Instruction): A_RawFunction?
	{
		assert(this === boxed)
		val source = sourceOfMove(instruction)
		return source.definition().instruction.constantCode
	}

	override fun shouldEmit(instruction: L2Instruction): Boolean
	{
		val source = instruction.operand<L2ReadOperand<K>>(0)
		val destination = instruction.operand<L2WriteOperand<K>>(1)
		return source.finalIndex() != destination.finalIndex()
	}

	/**
	 * Given an [L2Instruction] using an operation of this class, extract the
	 * source [L2ReadOperand] that is moved by the instruction.
	 *
	 * @param instruction
	 *   The move instruction to examine.
	 * @return
	 *   The move's source [L2ReadOperand].
	 */
	fun sourceOfMove(
		instruction: L2Instruction
	): L2ReadOperand<K> = instruction.operand(0)

	/**
	 * Given an [L2Instruction] using this operation, extract the source
	 * [L2ReadOperand] that is moved by the instruction.
	 *
	 * @param instruction
	 *   The move instruction to examine.
	 * @return
	 *   The move's source [L2WriteOperand].
	 */
	fun destinationOf(
		instruction: L2Instruction
	): L2WriteOperand<K> =
		instruction.operand(1)

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean)->Unit)
	{
		val source = instruction.operand<L2ReadOperand<K>>(0)
		val destination = instruction.operand<L2WriteOperand<K>>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		if (destination.restriction().constantOrNull.notNullAnd { isNil })
		{
			// Assume propagation of nil into a new semantic value will be
			// both successful and uninteresting.
			val tempDest = StringBuilder()
			destination.appendWithWarningsTo(tempDest, 0) { }
			builder.append(tempDest.toString().truncateTo(30))
			builder.append(" ← ")
			val tempSource = StringBuilder()
			source.appendWithWarningsTo(tempSource, 0) { }
			builder.append(tempSource.toString().truncateTo(20))
		}
		else
		{
			destination.appendWithWarningsTo(builder, 0, warningStyleChange)
			builder.append(" ← ")
			source.appendWithWarningsTo(builder, 0, warningStyleChange)
		}
	}

	override fun toString(): String = name

	override fun extractTupleElement(
		tupleReg: L2ReadOperand<BOXED_KIND>,
		index: Int,
		generator: L2Generator
	): L2ReadBoxedOperand
	{
		val instruction = tupleReg.definition().instruction
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		// val destination = instruction.operand<L2WriteOperand<R>>(1)

		return generator.extractTupleElement(source, index)
	}

	override fun emitTransformedInstruction(
		transformedOperands: Array<L2Operand>,
		regenerator: L2Regenerator)
	{
		// Try to strengthen the output restriction.
		val source: L2ReadOperand<K> = transformedOperands[0].cast()
		val destination: L2WriteOperand<K> = transformedOperands[1].cast()

		val manifest = regenerator.currentManifest
		val restriction = manifest.restrictionFor(source.semanticValue())
		val newDestination = createWrite(
			regenerator::nextUnique,
			destination.semanticValues(),
			restriction,
			destination.register())
		regenerator.addInstruction(this, source, newDestination)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction
	)
	{
		val source = instruction.operand<L2ReadOperand<K>>(0)
		val destination = instruction.operand<L2WriteOperand<K>>(1)

		// :: destination = source;
		translator.load(method, source.register())
		translator.store(method, destination.register())
	}

	companion object
	{
		/**
		 * A private mutable map from the [RegisterKind]s to the appropriate
		 * [L2_MOVE] operations.
		 */
		private val privateMovesByKind =
			mutableMapOf<RegisterKind<*>, L2_MOVE<*>>()

		/** The public immutable map from [RegisterKind] to [L2_MOVE]. */
		val movesByKind: Map<RegisterKind<*>, L2_MOVE<*>> get() =
			privateMovesByKind

		/**
		 * Initialize the move operation for boxed values.
		 */
		val boxed = object : L2_MOVE<BOXED_KIND>(
			BOXED_KIND,
			READ_BOXED.named("source boxed"),
			WRITE_BOXED.named("destination boxed")
		)
		{
			override fun createRead(
				semanticValue: L2SemanticValue<BOXED_KIND>,
				manifest: L2ValueManifest
			) = manifest.readBoxed(semanticValue as L2SemanticBoxedValue)

			override fun createWrite(
				uniqueGenerator: ()->Int,
				semanticValues: Set<L2SemanticValue<BOXED_KIND>>,
				restriction: TypeRestriction,
				forceRegister: L2Register<BOXED_KIND>?
			) = L2WriteBoxedOperand(
				semanticValues,
				restriction,
				forceRegister?.cast() ?: L2BoxedRegister(uniqueGenerator()))

			override fun createVector(
				elements: List<L2ReadOperand<BOXED_KIND>>
			) = L2ReadBoxedVectorOperand(elements.cast())
		}

		/**
		 * Initialize the move operation for int values.
		 */
		val unboxedInt = object : L2_MOVE<INTEGER_KIND>(
			INTEGER_KIND,
			READ_INT.named("source int"),
			WRITE_INT.named("destination int")
		)
		{
			override fun createRead(
				semanticValue: L2SemanticValue<INTEGER_KIND>,
				manifest: L2ValueManifest
			) = manifest.readInt(semanticValue as L2SemanticUnboxedInt)

			override fun createWrite(
				uniqueGenerator: ()->Int,
				semanticValues: Set<L2SemanticValue<INTEGER_KIND>>,
				restriction: TypeRestriction,
				forceRegister: L2Register<INTEGER_KIND>?
			): L2WriteIntOperand
			{
				assert(restriction.isUnboxedInt)
				return L2WriteIntOperand(
					semanticValues.cast(),
					restriction,
					forceRegister?.cast() ?:
						L2IntRegister(uniqueGenerator()))
			}

			override fun createVector(
				elements: List<L2ReadOperand<INTEGER_KIND>>
			) = L2ReadIntVectorOperand(elements.cast())
		}

		/**
		 * Initialize the move operation for float values.
		 */
		val unboxedFloat = object : L2_MOVE<FLOAT_KIND>(
			FLOAT_KIND,
			READ_FLOAT.named("source float"),
			WRITE_FLOAT.named("destination float")
		)
		{
			override fun createRead(
				semanticValue: L2SemanticValue<FLOAT_KIND>,
				manifest: L2ValueManifest
			) = manifest.readFloat(semanticValue as L2SemanticUnboxedFloat)

			override fun createWrite(
				uniqueGenerator: ()->Int,
				semanticValues: Set<L2SemanticValue<FLOAT_KIND>>,
				restriction: TypeRestriction,
				forceRegister: L2Register<FLOAT_KIND>?
			): L2WriteFloatOperand
			{
				assert(restriction.isUnboxedFloat)
				return L2WriteFloatOperand(
					semanticValues.cast(),
					restriction,
					forceRegister.cast() ?:
						L2FloatRegister(uniqueGenerator()))
			}

			override fun createVector(
				elements: List<L2ReadOperand<FLOAT_KIND>>
			) = L2ReadFloatVectorOperand(elements.cast())
		}
	}

	init
	{
		assert(privateMovesByKind[kind] === null)
		@Suppress("LeakingThis")
		privateMovesByKind[kind] = this
	}
}
