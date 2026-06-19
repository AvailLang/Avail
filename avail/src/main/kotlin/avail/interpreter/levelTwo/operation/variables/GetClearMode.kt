/*
 * GetClearMode.kt
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

import avail.descriptor.representation.A_Variable.Companion.getValueClearingMethod
import avail.descriptor.representation.A_Variable.Companion.getValueClearingMethodIfMutableMethod
import avail.descriptor.representation.A_Variable.Companion.getValueMakingImmutableMethod
import avail.exceptions.VariableGetException
import avail.exceptions.VariableSetException
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * The policy for determining the circumstance under which a variable should be
 * cleared.
 *
 * @property createInstruction
 *   A function to produce an [L2Instruction], given the variable read, the
 *   extracted value to write, the success edge, and the failure edge.
 * @property getterMethod
 *   A [CheckedMethod] that expects the variable as an argument and leaves the
 *   value of the variable on the stack, raising a suitable exception at runtime
 *   if it's not able to do so.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum class GetClearMode(
	val createInstruction: (
		variable: L2ReadBoxedOperand,
		extractedValue: L2WriteBoxedOperand,
		success: L2PcOperand,
		failure: L2PcOperand
	) -> L2Instruction,
	val getterMethod: CheckedMethod)
{
	/** Do not clear the variable being read. */
	NeverClear(
		::L2_GET_VARIABLE,
		getValueMakingImmutableMethod),

	/** Always clear the variable being read. */
	AlwaysClear(
		::L2_GET_VARIABLE_CLEARING,
		getValueClearingMethod),

	/** Only clear the variable being read if the variable is still mutable. */
	ClearIfMutable(
		::L2_GET_VARIABLE_CLEARING_IF_MUTABLE,
		getValueClearingMethodIfMutableMethod)

	;

	/**
	 * Emit JVM code to perform a read from a variable.  Clear the variable
	 * and/or make the value immutable if the receiver determines it should.  If
	 * the read is successful, the result is written to [extractedValue] and
	 * program flow continues at [ifReadSucceeded].  Otherwise, [extractedValue]
	 * is unaffected and flow continues at [ifReadFailed].
	 *
	 * @receiver
	 *   The [JVMTranslator] handling code generation.
	 * @param variable
	 *   The [L2BoxedRegister] containing the variable to read.
	 * @param extractedValue
	 *   Where to write the value of the variable if successfully extracted.
	 * @param ifReadSucceeded
	 *   Where to jump if the read was successful.
	 * @param ifReadFailed
	 *   Where to jump if the read failed in any way.
	 */
	fun JVMTranslator.translateJvmVariableRead(
		variable: L2ReadBoxedOperand,
		extractedValue: L2WriteBoxedOperand,
		ifReadSucceeded: L2PcOperand,
		ifReadFailed: L2PcOperand)
	{
		// :: try {
		val tryStart = Label()
		val catchStart = Label()
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			Type.getInternalName(VariableGetException::class.java))
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			Type.getInternalName(VariableSetException::class.java))
		method.visitLabel(tryStart)
		// ::    extractedValueReg = variable.getValue[Clearing]();
		load(variable)
		generateCall(getterMethod)
		store(extractedValue.register())
		// ::       goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableGetException to be pushed onto the stack. So always do the
		// jump.
		jump(ifReadSucceeded)
		// :: } catch (VariableGetException|VariableSetException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto failure;
		jumpOrFallThrough(ifReadFailed)
		// :: }
	}
}
