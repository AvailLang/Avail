/*
 * L2_MAKE_IMMUTABLE.kt
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

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.types.A_Type
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.utility.Casts
import org.objectweb.asm.MethodVisitor

/**
 * Force the specified object to be immutable.  Maintenance of conservative
 * sticky-bit reference counts is mostly separated out into this operation to
 * allow code transformations to obviate the need for it in certain non-obvious
 * circumstances.
 *
 * To keep this instruction from being neither removed due to not having
 * side-effect, nor kept from being re-ordered due to having side-effect, the
 * instruction has an input and an output, the latter of which should be the
 * only way to use the value after this instruction.  Accidentally using the
 * input value again would be incorrect, since that use could be re-ordered to a
 * point before this instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_MAKE_IMMUTABLE : L2Operation(
	L2OperandType.READ_BOXED.named("input"),
	L2OperandType.WRITE_BOXED.named("output"))
{
	override fun extractFunctionOuter(
		instruction: L2Instruction,
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator): L2ReadBoxedOperand
	{
		assert(this == instruction.operation())
		val read = instruction.operand<L2ReadBoxedOperand>(0)
		//		final L2WriteBoxedOperand write = instruction.operand(1);

		// Trace it back toward the actual function creation. We don't care if
		// the function is still mutable, since the generated JVM code will make
		// the outer variable immutable.
		val earlierInstruction = read.definitionSkippingMoves(true)
		return earlierInstruction.operation().extractFunctionOuter(
			earlierInstruction,
			functionRegister,
			outerIndex,
			outerType,
			generator)
	}

	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		val read = instruction.operand<L2ReadBoxedOperand>(0)
		val write = instruction.operand<L2WriteBoxedOperand>(1)
		read.instructionWasAdded(manifest)
		write.instructionWasAddedForMakeImmutable(
			read.semanticValue(), manifest)
	}

	override fun updateManifest(
		instruction: L2Instruction,
		manifest: L2ValueManifest,
		optionalPurpose: L2NamedOperandType.Purpose?)
	{
		assert(this == instruction.operation())
		assert(optionalPurpose == null)
		val read = sourceOfImmutable(instruction)
		val write = destinationOfImmutable(instruction)

		// Only deal with the boxed form.  The unboxed values are dealt with by
		// subsequent move instructions.
		val synonym = manifest.semanticValueToSynonym(read.semanticValue())

		// Make inaccessible all places holding the mutable boxed value.
		manifest.forget(synonym)

		// Add back the new definition, which is restricted to be immutable.
		manifest.recordDefinition(write)
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val read = instruction.operand<L2ReadBoxedOperand>(0)
		val write = instruction.operand<L2WriteBoxedOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(write.registerString())
		builder.append(" ← ")
		builder.append(read.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val read = instruction.operand<L2ReadBoxedOperand>(0)
		val write = instruction.operand<L2WriteBoxedOperand>(1)

		// :: output = input.makeImmutable();
		translator.load(method, read.register())
		A_BasicObject.makeImmutableMethod.generateCall(method)
		translator.store(method, write.register())
	}

	/**
	 * Given an [L2Instruction] using this operation, extract the source
	 * [L2ReadBoxedOperand] that is made immutable by the instruction.
	 *
	 * @param instruction
	 *   The make-immutable instruction to examine.
	 * @return
	 *   The instruction's source [L2ReadBoxedOperand].
	 */
	@kotlin.jvm.JvmStatic
	fun sourceOfImmutable(
		instruction: L2Instruction): L2ReadBoxedOperand
	{
		assert(instruction.operation() is L2_MAKE_IMMUTABLE)
		{
			"$instruction is an  ${instruction.operation()}"
		}
		return Casts.cast<Any, L2ReadBoxedOperand>(instruction.operand(0))
	}

	/**
	 * Given an [L2Instruction] using this operation, extract the destination
	 * [L2WriteBoxedOperand] that receives the immutable value produced by the
	 * instruction.
	 *
	 * @param instruction
	 *   The make-immutable instruction to examine.
	 * @return
	 *   The instruction's destination [L2WriteBoxedOperand].
	 */
	fun destinationOfImmutable(
		instruction: L2Instruction): L2WriteBoxedOperand
	{
		assert(instruction.operation() is L2_MAKE_IMMUTABLE)
		return Casts.cast<Any, L2WriteBoxedOperand>(instruction.operand(1))
	}
}