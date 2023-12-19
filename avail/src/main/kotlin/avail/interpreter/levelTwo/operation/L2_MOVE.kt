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
import avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.READ_FLOAT
import avail.interpreter.levelTwo.L2OperandType.READ_INT
import avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED
import avail.interpreter.levelTwo.L2OperandType.WRITE_FLOAT
import avail.interpreter.levelTwo.L2OperandType.WRITE_INT
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2Operand
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
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2FloatRegister
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.L2Register.RegisterKind
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.BOXED_KIND
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.FLOAT_KIND
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.INTEGER_KIND
import avail.optimizer.L2Generator
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast
import avail.utility.structures.EnumMap.Companion.enumMap
import org.objectweb.asm.MethodVisitor

/**
 * Move an [AvailObject] from the source to the destination.  The
 * [L2Generator] creates more moves than are strictly necessary, but
 * various mechanisms cooperate to remove redundant inter-register moves.
 *
 * The object being moved is not made immutable by this operation, as that
 * is the responsibility of the [L2_MAKE_IMMUTABLE] operation.
 *
 *
 * @param R
 *   The kind of [L2Register] to be moved.
 * @param RR
 *   The kind of [L2ReadOperand] to use for reading.
 * @param WR
 *   The kind of [L2WriteOperand] to use for writing.
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
abstract class L2_MOVE<
	R : L2Register,
	RR : L2ReadOperand<R>,
	WR : L2WriteOperand<R>,
	RV : L2ReadVectorOperand<R, RR>>
private constructor(
	val kind: RegisterKind,
	vararg theNamedOperandTypes: L2NamedOperandType
) : L2Operation("MOVE(" + kind.kindName + ")", *theNamedOperandTypes)
{
	/**
	 * Synthesize an [L2ReadOperand] of the appropriately strengthened type
	 * [RR].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] being read.
	 * @param manifest
	 *   The [L2ValueManifest] from which to extract the semantic value.
	 */
	abstract fun createRead(
		semanticValue: L2SemanticValue,
		manifest: L2ValueManifest
	): RR

	/**
	 * Synthesize an [L2WriteOperand] of the appropriately strengthened type
	 * [WR].
	 *
	 * @param generator
	 *   The [L2Generator] used for creating the write.
	 * @param semanticValues
	 *   The [L2SemanticValue]s to populate.
	 * @param restriction
	 *   The [TypeRestriction] that the stored values will satisfy.
	 * @param forceRegister
	 *   If specified and non-null, this is the register to be written.
	 *   Otherwise, a new one will be allocated.
	 * @return
	 *   A new [L2WriteOperand] of the appropriate type [WR].
	 */
	abstract fun createWrite(
		generator: L2Generator,
		semanticValues: Set<L2SemanticValue>,
		restriction: TypeRestriction,
		forceRegister: R? = null
	): WR

	/**
	 * Create an [L2ReadVectorOperand] suitable for the [RegisterKind].  This
	 * isn't needed by the move instruction, but it's handy for parameterizing
	 * other [L2Operation]s, like [L2_PHI_PSEUDO_OPERATION].
	 *
	 * @param elements
	 *   A [List] of [L2ReadOperand]s, strengthened to the [RR] type.
	 * @return
	 */
	abstract fun createVector(elements: List<RR>): RV

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest
	)
	{
		assert(this === instruction.operation)
		val source = instruction.operand<L2ReadOperand<R>>(0)
		val destination = instruction.operand<L2WriteOperand<R>>(1)

		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(manifest)
		destination.instructionWasAddedForMove(source.semanticValue(), manifest)
	}

	override fun getConstantCodeFrom(instruction: L2Instruction): A_RawFunction?
	{
		assert(instruction.operation === boxed)
		val source = sourceOf(instruction)
		val producer = source.definition().instruction
		return producer.operation.getConstantCodeFrom(producer)
	}

	override fun shouldEmit(instruction: L2Instruction): Boolean
	{
		assert(instruction.operation === this)
		val source = instruction.operand<L2ReadOperand<R>>(0)
		val destination = instruction.operand<L2WriteOperand<R>>(1)
		return source.finalIndex() != destination.finalIndex()
	}

	override val isMove: Boolean get() = true

	/**
	 * Given an [L2Instruction] using an operation of this class, extract the
	 * source [L2ReadOperand] that is moved by the instruction.
	 *
	 * @param instruction
	 *   The move instruction to examine.
	 * @return
	 *   The move's source [L2ReadOperand].
	 */
	fun sourceOf(
		instruction: L2Instruction
	): RR
	{
		assert(instruction.operation is L2_MOVE<*, *, *, *>)
		return instruction.operand(0)
	}

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
	): WR
	{
		assert(instruction.operation === this)
		return instruction.operand(1)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean)->Unit
	)
	{
		assert(this === instruction.operation)
		val source = instruction.operand<L2ReadOperand<R>>(0)
		val destination = instruction.operand<L2WriteOperand<R>>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		destination.appendWithWarningsTo(builder, 0, warningStyleChange)
		builder.append(" ← ")
		source.appendWithWarningsTo(builder, 0, warningStyleChange)
	}

	override fun toString(): String = name

	override fun extractTupleElement(
		tupleReg: L2ReadBoxedOperand,
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
		val source: L2ReadOperand<R> = transformedOperands[0].cast()
		val destination: L2WriteOperand<R> = transformedOperands[1].cast()

		val generator = regenerator.targetGenerator
		val restriction =
			generator.currentManifest.restrictionFor(source.semanticValue())
		val newDestination = createWrite(
			generator,
			destination.semanticValues(),
			restriction,
			destination.register())
		generator.addInstruction(this, source, newDestination)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction
	)
	{
		val source = instruction.operand<L2ReadOperand<R>>(0)
		val destination = instruction.operand<L2WriteOperand<R>>(1)

		// :: destination = source;
		translator.load(method, source.register())
		translator.store(method, destination.register())
	}

	companion object
	{
		/**
		 * A map from the [RegisterKind]s to the appropriate `L2_MOVE`
		 * operations.
		 */
		private val movesByKind = enumMap<RegisterKind, L2_MOVE<*, *, *, *>>()

		/**
		 * Initialize the move operation for boxed values.
		 */
		val boxed = object : L2_MOVE<
			L2BoxedRegister,
			L2ReadBoxedOperand,
			L2WriteBoxedOperand,
			L2ReadBoxedVectorOperand>(
			BOXED_KIND,
			READ_BOXED.named("source boxed"),
			WRITE_BOXED.named("destination boxed")
		)
		{
			override fun createRead(
				semanticValue: L2SemanticValue,
				manifest: L2ValueManifest
			) = manifest.readBoxed(semanticValue)

			override fun createWrite(
				generator: L2Generator,
				semanticValues: Set<L2SemanticValue>,
				restriction: TypeRestriction,
				forceRegister: L2BoxedRegister?
			) = L2WriteBoxedOperand(
				semanticValues,
				restriction,
				forceRegister ?: L2BoxedRegister(generator.nextUnique()))

			override fun createVector(elements: List<L2ReadBoxedOperand>) =
				L2ReadBoxedVectorOperand(elements)
		}

		/**
		 * Initialize the move operation for int values.
		 */
		val unboxedInt = object : L2_MOVE<
			L2IntRegister,
			L2ReadIntOperand,
			L2WriteIntOperand,
			L2ReadIntVectorOperand>(
			INTEGER_KIND,
			READ_INT.named("source int"),
			WRITE_INT.named("destination int")
		)
		{
			override fun createRead(
				semanticValue: L2SemanticValue,
				manifest: L2ValueManifest
			) = manifest.readInt(semanticValue)

			override fun createWrite(
				generator: L2Generator,
				semanticValues: Set<L2SemanticValue>,
				restriction: TypeRestriction,
				forceRegister: L2IntRegister?
			) = generator.intWrite(
				semanticValues.cast(), restriction, forceRegister)

			override fun createVector(elements: List<L2ReadIntOperand>) =
				L2ReadIntVectorOperand(elements)
		}

		/**
		 * Initialize the move operation for float values.
		 */
		val unboxedFloat = object : L2_MOVE<
			L2FloatRegister,
			L2ReadFloatOperand,
			L2WriteFloatOperand,
			L2ReadFloatVectorOperand>(
			FLOAT_KIND,
			READ_FLOAT.named("source float"),
			WRITE_FLOAT.named("destination float")
		)
		{
			override fun createRead(
				semanticValue: L2SemanticValue,
				manifest: L2ValueManifest
			) = manifest.readFloat(semanticValue)

			override fun createWrite(
				generator: L2Generator,
				semanticValues: Set<L2SemanticValue>,
				restriction: TypeRestriction,
				forceRegister: L2FloatRegister?
			) = generator.floatWrite(
				semanticValues.cast(), restriction, forceRegister)

			override fun createVector(elements: List<L2ReadFloatOperand>) =
				L2ReadFloatVectorOperand(elements)
		}

		/**
		 * Answer an `L2_MOVE` suitable for transferring data of the given
		 * [RegisterKind].
		 *
		 * @param R
		 *   The subtype of [L2Register] to use.
		 * @param RR
		 *   The subtype of [L2ReadOperand] to use.
		 * @param WR
		 *   The subtype of [L2WriteOperand] to use.
		 * @param RV
		 *   The subtype of [L2ReadVectorOperand] to use.
		 * @param registerKind
		 *   The [RegisterKind] to be transferred by the move.
		 * @return
		 *   The requested `L2_MOVE` operation.
		 */
		fun <
			R : L2Register,
			RR : L2ReadOperand<R>,
			WR : L2WriteOperand<R>,
			RV : L2ReadVectorOperand<R, RR>>
			moveByKind(registerKind: RegisterKind): L2_MOVE<R, RR, WR, RV> =
			movesByKind[registerKind]!!.cast()
	}

	init
	{
		assert(movesByKind[kind] === null)
		@Suppress("LeakingThis")
		movesByKind[kind] = this
	}
}
