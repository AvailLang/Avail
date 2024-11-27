/*
 * L1_FALL_BACK_TO_L1.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.fallBackToL1Method
import avail.interpreter.levelTwo.HiddenVariable.CURRENT_FUNCTION
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.ReadsHiddenVariable
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Fall back to the L1 [unoptimizedChunk], continuing with the data provided for
 * reconstituting this frame for resumption.
 *
 * Since the lifetime of that dummy continuation is limited, and can't be
 * observed externally, we do something kind of sneaky.  The continuation that
 * we create has as its caller [Interpreter.theReifiedContinuation], even if
 * there are unreified frames on the JVM call stack that would reify themselves
 * if asked. Those frames will remain on the JVM call stack, ready to return or
 * reify when the [unoptimizedChunk] says to, so all should continue to work as
 * expected.
 *
 * Note that it implicitly uses the [Interpreter.function] and
 * [Interpreter.theReifiedContinuation].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@ReadsHiddenVariable(CURRENT_FUNCTION::class)
@WritesHiddenVariable(CURRENT_FUNCTION::class)
class L2_FALL_BACK_TO_L1(
	val pc: Int,
	val stackp: Int,
	var frameValues: L2ReadBoxedVectorOperand
) : L2ControlFlowInstruction()
{
	override val isCold: Boolean get() = true

	// Never remove this.
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" pc: $pc, stackp: $stackp,")
		append("\n\tframe data: ")
		frameValues.elements.joinTo(this, limit = 5) { it.registerString() }
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		translator.loadInterpreter(method)
		// :: interpreter
		translator.intConstant(method, pc)
		// :: interpreter, pc
		translator.intConstant(method, stackp)
		// :: interpreter, pc, stackp
		translator.objectArrayFromRegisters(
			method,
			frameValues.elements.map(L2ReadBoxedOperand::register),
			A_BasicObject::class.java)
		// :: interpreter, pc, stackp, frameValues
		fallBackToL1Method.generateCall(method)
		// :: stack reifier (= null)
		method.visitInsn(Opcodes.ARETURN)
	}
}
