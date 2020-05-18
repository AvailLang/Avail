/*
 * L2_RETURN.java
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

import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.function.Consumer

/**
 * Return from the current [L2Chunk] with the given return value.  The
 * value to return will be stored in [Interpreter.setLatestResult], so the
 * caller will need to look there.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_RETURN : L2ControlFlowOperation(
	L2OperandType.READ_BOXED.`is`("return value"))
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		// A return instruction doesn't mention where it might end up.
		assert(registerSets.size == 0)
	}

	// Never remove this.
	override fun hasSideEffect(): Boolean = true

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: Consumer<Boolean>)
	{
		assert(this == instruction.operation())
		val value = instruction.operand<L2ReadBoxedOperand>(0)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(value.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val value = instruction.operand<L2ReadBoxedOperand>(0)

		// :: interpreter.setLatestResult(value);
		translator.loadInterpreter(method)
		method.visitInsn(Opcodes.DUP)
		translator.load(method, value.register())
		Interpreter.setLatestResultMethod.generateCall(method)
		// :: interpreter.returnNow = true;
		method.visitInsn(Opcodes.DUP)
		translator.intConstant(method, 1)
		Interpreter.returnNowField.generateWrite(method)
		// interpreter.returningFunction = interpreter.function;
		method.visitInsn(Opcodes.DUP)
		Interpreter.interpreterFunctionField.generateRead(method)
		Interpreter.interpreterReturningFunctionField.generateWrite(method)
		// :: return null;
		method.visitInsn(Opcodes.ACONST_NULL)
		method.visitInsn(Opcodes.ARETURN)
	}
}