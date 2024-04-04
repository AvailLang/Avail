/*
 * L2_MOVE_CONSTANT.kt
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

import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Descriptor.Companion.brief
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.types.A_Type
import avail.interpreter.levelTwo.L2NamedOperandType
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.InstructionLayout
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2FloatImmediateOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2Operand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.L2WriteOperand
import avail.interpreter.levelTwo.operation.L2_MOVE.L2_MOVE_BOXED
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.FLOAT_KIND
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2Generator
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticConstant
import avail.optimizer.values.L2SemanticUnboxedFloat
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.Strings.increaseIndentation
import org.objectweb.asm.MethodVisitor

/**
 * Move a constant [AvailObject] into a register.  There are subclasses for the
 * different [RegisterKind]s.
 *
 * @param C
 *   The [L2Operand] that provides the constant value.
 * @param K
 *   The [RegisterKind] that can hold the constant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property pushConstant
 *   A function to invoke to push the constant value.
 *
 * @constructor
 * Construct an `L2_MOVE_CONSTANT` operation.
 *
 * @param pushConstant
 *   A function to invoke to generate JVM code to push the constant value.
 * @param theNamedOperandTypes
 *   An array of [L2NamedOperandType]s that describe this particular
 *   instruction, allowing it to be specialized by [RegisterKind].
 */
abstract class L2_MOVE_CONSTANT<C: L2Operand, K: RegisterKind<K>>
private constructor(
): L2Instruction()
{
	/** Subclasses should answer the appropriate [RegisterKind]. */
	abstract val kind: K

	/**
	 * The source of this move.  This is a member function instead of a field,
	 * to simplify the reflection logic in [InstructionLayout].
	 */
	abstract fun constant(): C

	/**
	 * The destination of this move.  This is a member function instead of a
	 * field, to simplify the reflection logic in [InstructionLayout].
	 */
	abstract fun destination(): L2WriteOperand<K>

	/** Extract an [L2SemanticValue] with a kind that matches [K]. */
	abstract fun getConstantSemanticValue(): L2SemanticValue<K>

	/**
	 * Emit JVM code that causes the constant value of the appropriate type to
	 * be pushed.
	 *
	 * @param translator
	 *   The [JVMTranslator] on which to emit the code.
	 * @param method
	 *   The [MethodVisitor] that indicates which method is being written.
	 */
	abstract fun pushConstant(
		translator: JVMTranslator,
		method: MethodVisitor)

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// Ensure the new write ends up in the same synonym as the source.
		constant().instructionWasAdded(manifest)
		val semanticValue = destination().pickSemanticValue()
		if (manifest.hasSemanticValue(semanticValue))
		{
			// The constant semantic value exists, but for another register
			// kind.
			destination().instructionWasAddedForMove(semanticValue, manifest)
		}
		else
		{
			// The constant semantic value has not been encountered for any
			// register kinds yet.
			destination().instructionWasAdded(manifest)
		}
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		// If the constant is already present in the manifest, we *must*
		// do a move from the existing synonym, otherwise it will get
		// confused later, when it sees a definition with an overlapping
		// synonym.
		val manifest = regenerator.currentManifest
		val semanticConstant = getConstantSemanticValue()
		if (manifest.hasSemanticValue(semanticConstant)
			&& manifest.getDefinitions(semanticConstant).isNotEmpty())
		{
			val newValues = destination().semanticValues()
				.filterNot(manifest::hasSemanticValue)
			if (newValues.isNotEmpty())
			{
				regenerator.moveRegister(kind, semanticConstant, newValues)
			}
			return
		}
		super.emitTransformedInstruction(regenerator)
	}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		destination().appendWithWarningsTo(builder, 0, warningStyleChange)
		builder.append(" ← ")
		builder.brief {
			this.append(increaseIndentation(constant().toString(), 2))
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: destination = constant;
		pushConstant(translator, method)
		translator.store(method, destination().register())
	}

	class L2_MOVE_CONSTANT_BOXED
	constructor(
		var source: L2ConstantOperand,
		var destination: L2WriteBoxedOperand
	): L2_MOVE_CONSTANT<L2ConstantOperand, BOXED_KIND>()
	{
		override val kind: BOXED_KIND get() = BOXED_KIND

		override fun constant(): L2ConstantOperand = source

		override fun destination(): L2WriteBoxedOperand = destination

		override fun getConstantSemanticValue(): L2SemanticValue<BOXED_KIND> =
			L2SemanticConstant(source.constant)

		override fun pushConstant(
			translator: JVMTranslator,
			method: MethodVisitor
		) = translator.literal(method, constant().constant)

		override fun extractFunctionOuter(
			functionRegister: L2ReadBoxedOperand,
			outerIndex: Int,
			outerType: A_Type,
			generator: L2Generator): L2ReadBoxedOperand
		{
			// The exact function is known statically.
			val constantFunction: A_Function = constant().constant
			return generator.boxedConstant(
				constantFunction.outerVarAt(outerIndex))
		}

		override fun extractTupleElement(
			tupleReg: L2ReadBoxedOperand,
			index: Int,
			write: L2WriteBoxedOperand,
			generator: L2Generator)
		{
			// Extract the element from the constant right now.
			val tupleElement = constant().constant.tupleAt(index)
			generator.addInstruction(
				L2_MOVE_BOXED(
					generator.boxedConstant(tupleElement),
					write))
		}

		/** The constant must be a function at this point. */
		override val constantCode: A_RawFunction get() = source.constant.code()
	}

	class L2_MOVE_CONSTANT_INT
	constructor(
		var source: L2IntImmediateOperand,
		var destination: L2WriteIntOperand
	): L2_MOVE_CONSTANT<L2IntImmediateOperand, INTEGER_KIND>()
	{
		override val kind: INTEGER_KIND get() = INTEGER_KIND

		override fun constant(): L2IntImmediateOperand = source

		override fun destination(): L2WriteIntOperand = destination

		override fun getConstantSemanticValue() =
			L2SemanticUnboxedInt(L2SemanticConstant(fromInt(source.value)))

		override fun pushConstant(
			translator: JVMTranslator,
			method: MethodVisitor
		) = translator.intConstant(method, constant().value)
	}

	class L2_MOVE_CONSTANT_FLOAT
	constructor(
		var source: L2FloatImmediateOperand,
		var destination: L2WriteFloatOperand
	): L2_MOVE_CONSTANT<L2FloatImmediateOperand, FLOAT_KIND>()
	{
		override val kind: FLOAT_KIND get() = FLOAT_KIND

		override fun constant(): L2FloatImmediateOperand = source

		override fun destination(): L2WriteFloatOperand = destination

		override fun getConstantSemanticValue(): L2SemanticUnboxedFloat =
			L2SemanticUnboxedFloat(L2SemanticConstant(fromDouble(source.value)))

		override fun pushConstant(
			translator: JVMTranslator,
			method: MethodVisitor
		) = translator.doubleConstant(method, constant().value)
	}
}
