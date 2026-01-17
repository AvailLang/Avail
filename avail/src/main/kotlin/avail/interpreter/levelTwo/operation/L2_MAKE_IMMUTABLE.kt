/*
 * L2_MAKE_IMMUTABLE.kt
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

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.types.A_Type
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.JVMTranslator
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
class L2_MAKE_IMMUTABLE(
	var input: L2ReadBoxedOperand,
	var output: L2WriteBoxedOperand
): L2Instruction()
{
	/**
	 * The make-immutable instruction should only be inserted near the end of
	 * optimization.
 	 */
	override fun L2GeneratorInterface.extractFunctionOuter(
		functionRegister: L2ReadBoxedOperand,
		outerIndex: Int,
		outerType: A_Type
	): L2ReadBoxedOperand = unsupported

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(output.registerString())
		append(" ← ")
		append(input.registerString())
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// The make-immutable instruction should only be inserted after all
		// optimization passes that use graph regeneration.
		throw AssertionError("Should not reach this")
	}


	override val readsThatMightDestroy: List<L2ReadBoxedOperand>
		get()
		{
			// The make-immutable instructions are added *after* all uses of
			// this method.
			throw AssertionError("Should not reach this")
		}


	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: output = input.makeImmutable();
		translator.load(method, input)
		A_BasicObject.makeImmutableMethod.generateCall(method)
		translator.store(method, output.register())
	}
}
