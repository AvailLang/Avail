/*
 * L2_TUPLE_SIZE.kt
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

import com.avail.descriptor.tuples.A_Tuple
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * Answer the [size][A_Tuple.tupleSize] of the specified [tuple][A_Tuple].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_TUPLE_SIZE : L2Operation(
	L2OperandType.READ_BOXED.named("tuple"),
	L2OperandType.WRITE_INT.named("size of tuple"))
{
	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val tuple = instruction.operand<L2ReadBoxedOperand>(0)
		val size = instruction.operand<L2WriteIntOperand>(1)
		renderPreamble(instruction, builder)
		builder.append(' ')
		builder.append(size.registerString())
		builder.append(" ← ")
		builder.append(tuple.registerString())
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val tuple = instruction.operand<L2ReadBoxedOperand>(0)
		val size = instruction.operand<L2WriteIntOperand>(1)

		// :: size = tuple.tupleSize();
		translator.load(method, tuple.register())
		A_Tuple.tupleSizeMethod.generateCall(method)
		translator.store(method, size.register())
	}
}