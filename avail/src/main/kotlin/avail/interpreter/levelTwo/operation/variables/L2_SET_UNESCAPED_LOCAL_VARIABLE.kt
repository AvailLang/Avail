/*
 * L2_SET_UNESCAPED_LOCAL_VARIABLE.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.levelTwo.operation.variables

import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.HiddenVariable.GLOBAL_STATE
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.MethodVisitor

/**
 * Assign a value to a [variable][VariableDescriptor] *without*
 * checking that it's of the correct type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@WritesHiddenVariable(GLOBAL_STATE::class)
class L2_SET_UNESCAPED_LOCAL_VARIABLE(
	var variable: L2ReadBoxedOperand,
	var valueToWrite: L2ReadBoxedOperand,
	var variableOut: L2WriteBoxedOperand
): L2Instruction()
{
	init
	{
		assert(variable.restriction().containedByType(mostGeneralVariableType))
	}

	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(variable.registerString())
		append(" ← ")
		append(valueToWrite.registerString())
		append("   (var out = ")
		append(variableOut.registerString())
		append(")")
	}

	override fun sourceOfMoveToRegister(
		destinationRegister: L2Register<*>): L2Register<*>?
	{
		assert(destinationRegister == variableOut.register())
		return variable.register()
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// It's not allowed to fail.
		// :: variable.setUnescapedLocalValueNoCheck(valueToWrite);
		translator.load(method, variable.register())
		translator.load(method, valueToWrite.register())
		A_Variable.setUnescapedLocalValueNoCheckMethod.generateCall(method)
		if (variable.finalIndex() != variableOut.finalIndex())
		{
			translator.load(method, variable.register())
			translator.store(method, variableOut.register())
		}
	}

	companion object
	{
		@ReferencedInGeneratedCode
		@JvmStatic
		fun failedWrite(e: Exception): Nothing
		{
			println("Failed to write local variable: $e")
			unsupported
		}

		/**
		 * The static [CheckedMethod] that invokes [failedWrite].
		 */
		val failedWriteMethod = CheckedMethod.staticMethod(
			L2_SET_UNESCAPED_LOCAL_VARIABLE::class.java,
			::failedWrite.name,
			Nothing::class.java,
			Exception::class.java)
	}
}
