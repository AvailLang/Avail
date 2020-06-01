/*
 * L2_MOVE.java
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.types.A_Type
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.interpreter.levelTwo.register.L2BoxedRegister
import com.avail.interpreter.levelTwo.register.L2FloatRegister
import com.avail.interpreter.levelTwo.register.L2IntRegister
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.Casts
import org.objectweb.asm.MethodVisitor
import java.util.*
import kotlin.collections.set

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
abstract class L2_MOVE<R : L2Register, RR : L2ReadOperand<R>, WR : L2WriteOperand<R>>
private constructor(
		val kind: RegisterKind,
		vararg theNamedOperandTypes: L2NamedOperandType)
	: L2Operation("MOVE(" + kind.kindName + ")", *theNamedOperandTypes)
{
	/**
	 * Synthesize an [L2WriteOperand] of the appropriately strengthened type
	 * [WR].
	 *
	 * @param generator
	 *   The [L2Generator] used for creating the write.
	 * @param semanticValue
	 *   The [L2SemanticValue] to populate.
	 * @param restriction
	 *   The [TypeRestriction] that the stored values will satisfy.
	 * @return
	 *   A new [L2WriteOperand] of the appropriate type [WR].
	 */
	abstract fun createWrite(
		generator: L2Generator,
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction): WR

	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val source: L2ReadOperand<R> = instruction.operand(0)
		val destination: L2WriteOperand<R> = instruction.operand(1)
		assert(source.register() !== destination.register())
		registerSet.removeConstantAt(destination.register())
		if (registerSet.hasTypeAt(source.register()))
		{
			registerSet.typeAtPut(
				destination.register(),
				registerSet.typeAt(source.register()),
				instruction)
		}
		else
		{
			registerSet.removeTypeAt(destination.register())
		}
		if (registerSet.hasConstantAt(source.register()))
		{
			registerSet.constantAtPut(
				destination.register(),
				registerSet.constantAt(source.register()),
				instruction)
		}
		registerSet.propagateMove(
			source.register(), destination.register(), instruction)
	}

	override fun instructionWasAdded(
		instruction: L2Instruction,
		manifest: L2ValueManifest)
	{
		assert(this === instruction.operation())
		val source: L2ReadOperand<R> = instruction.operand(0)
		val destination: L2WriteOperand<R> = instruction.operand(1)

		// Ensure the new write ends up in the same synonym as the source.
		source.instructionWasAdded(manifest)
		destination.instructionWasAddedForMove(
			source.semanticValue(), manifest)
	}

	override fun extractFunctionOuter(
		instruction: L2Instruction,
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator): L2ReadBoxedOperand
	{
		assert(this === instruction.operation() && this === boxed)
		val sourceRead =
			instruction.operand<L2ReadBoxedOperand>(0)
		//		final L2WriteBoxedOperand destinationWrite = instruction.operand(1);

		// Trace it back toward the actual function creation.
		val earlierInstruction =
			sourceRead.definitionSkippingMoves(true)
		return earlierInstruction.operation().extractFunctionOuter(
			earlierInstruction,
			sourceRead,
			outerIndex,
			outerType,
			generator)
	}

	override fun getConstantCodeFrom(instruction: L2Instruction): A_RawFunction?
	{
		assert(instruction.operation() === boxed)
		val source = sourceOf(instruction)
		val producer = source.definition().instruction()
		return producer.operation().getConstantCodeFrom(producer)
	}

	override fun shouldEmit(instruction: L2Instruction): Boolean
	{
		assert(instruction.operation() === this)
		val sourceReg: L2ReadOperand<R> = instruction.operand(0)
		val destinationReg: L2WriteOperand<R> = instruction.operand(1)
		return sourceReg.finalIndex() != destinationReg.finalIndex()
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
		instruction: L2Instruction): RR
	{
		assert(instruction.operation() is L2_MOVE<*, *, *>)
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
		instruction: L2Instruction): WR
	{
		assert(instruction.operation() === this)
		return instruction.operand(1)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this === instruction.operation())
		val source: L2ReadOperand<R> = instruction.operand(0)
		val destination: L2WriteOperand<R> = instruction.operand(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		destination.appendWithWarningsTo(builder, 0, warningStyleChange)
		builder.append(" ← ")
		source.appendWithWarningsTo(builder, 0, warningStyleChange)
	}

	override fun toString(): String = name()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val source: L2ReadOperand<R> = instruction.operand(0)
		val destination: L2WriteOperand<R> = instruction.operand(1)

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
		private val movesByKind =
			EnumMap<RegisterKind, L2_MOVE<*, *, *>>(RegisterKind::class.java)

		/**
		 * Initialize the move operation for boxed values.
		 */
		@kotlin.jvm.JvmField
		val boxed: L2_MOVE<L2BoxedRegister, L2ReadBoxedOperand, L2WriteBoxedOperand> =
			object : L2_MOVE<L2BoxedRegister, L2ReadBoxedOperand, L2WriteBoxedOperand>(
				RegisterKind.BOXED,
				L2OperandType.READ_BOXED.named("source boxed"),
				L2OperandType.WRITE_BOXED.named("destination boxed"))
		{
			override fun createWrite(
				generator: L2Generator,
				semanticValue: L2SemanticValue,
				restriction: TypeRestriction): L2WriteBoxedOperand
			{
				return generator.boxedWrite(semanticValue, restriction)
			}
		}

		/**
		 * Initialize the move operation for int values.
		 */
		val unboxedInt: L2_MOVE<L2IntRegister, L2ReadIntOperand, L2WriteIntOperand> =
			object : L2_MOVE<L2IntRegister, L2ReadIntOperand, L2WriteIntOperand>(
				RegisterKind.INTEGER,
				L2OperandType.READ_INT.named("source int"),
				L2OperandType.WRITE_INT.named("destination int"))
		{
			override fun createWrite(
				generator: L2Generator,
				semanticValue: L2SemanticValue,
				restriction: TypeRestriction): L2WriteIntOperand =
					generator.intWrite(semanticValue, restriction)
		}

		/**
		 * Initialize the move operation for float values.
		 */
		val unboxedFloat: L2_MOVE<L2FloatRegister, L2ReadFloatOperand, L2WriteFloatOperand> =
			object : L2_MOVE<L2FloatRegister, L2ReadFloatOperand, L2WriteFloatOperand>(
				RegisterKind.FLOAT,
				L2OperandType.READ_FLOAT.named("source float"),
				L2OperandType.WRITE_FLOAT.named("destination float"))
		{
			override fun createWrite(
				generator: L2Generator,
				semanticValue: L2SemanticValue,
				restriction: TypeRestriction): L2WriteFloatOperand =
					generator.floatWrite(semanticValue, restriction)
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
		 * @param registerKind
		 *   The [RegisterKind] to be transferred by the move.
		 * @return
		 *   The requested `L2_MOVE` operation.
		 */
		fun <R : L2Register, RR : L2ReadOperand<R>, WR : L2WriteOperand<R>> moveByKind(
			registerKind: RegisterKind): L2_MOVE<R, RR, WR> =
				Casts.cast(movesByKind[registerKind]!!)
	}

	init
	{
		assert(movesByKind[kind] == null)
		movesByKind[kind] = this
	}
}
