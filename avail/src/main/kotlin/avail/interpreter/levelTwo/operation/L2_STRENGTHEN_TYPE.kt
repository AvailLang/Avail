/*
 * L2_STRENGTHEN_TYPE.kt
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

import avail.descriptor.types.A_Type
import avail.descriptor.types.TypeTag
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.READ_BOXED
import avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED
import avail.interpreter.levelTwo.L2Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.L2Generator
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor

/**
 * Grant the specified input object a stronger type.  This instruction must
 * survive a [L2Regenerator], so it's treated as having a side-effect, even
 * though it generates no JVM instructions.  In general, an [L2Operation] has
 * the responsibility to propagate type information when the containing
 * [L2Instruction] is emitted, but sometimes that isn't convenient, such as
 * when some property p(f(x)) is shown to hold, implying x itself should be
 * consequently constrained to have some other property p'(x).
 *
 * Testing a [TypeTag] into some range is one such situation, as it may be
 * awkward to associate the object from which the tag was extracted with a
 * stronger type.  This is aggravated if the type tag ranges are augmented with
 * don't-care values to minimize spurious tests, as the actual tags that could
 * occur (i.e., excluding the don't-cares) aren't apparent at a mere
 * [L2_JUMP_IF_COMPARE_INT] instruction.
 *
 * To keep this instruction from being neither removed due to not having
 * side-effect, nor kept from being re-ordered relative to other instructions
 * that might also have no side-effect (but nonetheless require the stronger
 * type during regeneration), the instruction has both an input and an output,
 * the latter of which should be the only way to use the value after this
 * instruction.  Accidentally using the input value again would be incorrect,
 * since that use could be re-ordered to a point before this instruction.
 *
 * The restricted type information is part of the [L2WriteBoxedOperand].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object L2_STRENGTHEN_TYPE : L2Operation(
	READ_BOXED.named("input"),
	WRITE_BOXED.named("output"))
{
	// Don't allow this instruction to be folded out or reordered, even though
	// it does nothing to its input.
	override val hasSideEffect: Boolean
		get() = true

	override fun extractFunctionOuter(
		instruction: L2Instruction,
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type,
		generator: L2Generator): L2ReadBoxedOperand
	{
		assert(this == instruction.operation)
		val read = instruction.operand<L2ReadBoxedOperand>(0)
		// val write: L2WriteBoxedOperand = instruction.operand(1)
		// val type: L2ConstantOperand = instruction.operand(2)

		// Trace it back toward the actual function creation. We don't care if
		// the function is still mutable, since the generated JVM code will make
		// the outer variable immutable.
		val earlierInstruction = read.definitionSkippingMoves(true)
		return earlierInstruction.operation.extractFunctionOuter(
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
		val oldSynonym = manifest.semanticValueToSynonym(read.semanticValue())
		manifest.forgetBoxedRegistersFor(oldSynonym)
		write.instructionWasAddedForMove(read.semanticValue(), manifest)
		val newSynonym =
			manifest.semanticValueToSynonym(write.pickSemanticValue())
		manifest.updateConstraint(newSynonym) {
			restriction = restriction.intersection(write.restriction())
		}
	}

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation)
		val read = instruction.operand<L2ReadBoxedOperand>(0)
		val write = instruction.operand<L2WriteBoxedOperand>(1)

		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(write.registerString())
		builder.append(" ← ")
		builder.append(read.registerString())
		builder.append(" strengthened to ${write.restriction()}")
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val source = instruction.operand<L2ReadBoxedOperand>(0)
		val destination = instruction.operand<L2WriteBoxedOperand>(1)

		// Unlike plain moves, keep this instruction until the end. Technically,
		// we really only need to keep it around in the graph we'll use for
		// inlining, not the final JVM code.
		if (source.finalIndex() != destination.finalIndex())
		{
			// :: destination = source;
			translator.load(method, source.register())
			translator.store(method, destination.register())
		}
	}
}
