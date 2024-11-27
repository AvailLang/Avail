/*
 * L2_GET_VARIABLE.kt
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

import avail.descriptor.representation.A_BasicObject.Companion.makeImmutableMethod
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.variables.A_Variable.Companion.getValueMethod
import avail.exceptions.VariableGetException
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Extract the value of a variable. If the variable is unassigned, then branch
 * to the specified [offset][Interpreter.setOffset].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_GET_VARIABLE(
	var variable: L2ReadBoxedOperand,
	@On(SUCCESS) var extractedValue: L2WriteBoxedOperand,
	@On(SUCCESS) var ifReadSucceeded: L2PcOperand,
	@On(OFF_RAMP) var ifReadFailed: L2PcOperand
) : L2ControlFlowInstruction()
{
	// Subtle. Reading from a variable can fail, so don't remove this.
	override val hasSideEffect get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(extractedValue.registerString())
		append(" ← ↓")
		append(variable.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes, ::variable, ::extractedValue)
	}

	/**
	 * See if the value we would get from the variable is already known via a
	 * still-postponed variable creation or variable set instruction.  If so, we
	 * rewrite the get as a move from the setting (or initializing) expression,
	 * followed by a jump to the success case.
	 */
	override fun regenerateForPostponement(regenerator: L2Regenerator)
	{
		val semanticVariable = variable.semanticValue()
		val manifest = regenerator.currentManifest
		if (manifest.hasSemanticValue(semanticVariable))
		{
			// The variable has already been made real, so use this get
			// instruction verbatim.
			super.regenerateForPostponement(regenerator)
			return
		}
		// The variable isn't present yet in the manifest, so its creation must
		// still be postponed.
		val variableSource =
			manifest.postponedInstructions()[semanticVariable]!!
		var valueSource: L2ReadBoxedOperand = when (variableSource)
		{
			is L2_VIRTUAL_SET_LOCAL_VARIABLE -> variableSource.valueToWrite
			is L2_CREATE_VARIABLE -> variableSource.initialValueOrNil
			else ->
			{
				super.regenerateForPostponement(regenerator)
				return
			}
		}
		if (valueSource.restriction().constantOrNull == nil)
		{
			// The variable is always uninitialized here.  Always fail the get.
			regenerator.addInstruction(
				L2_JUMP(regenerator.transformOperand(ifReadFailed)))
			return
		}
		if (valueSource.restriction().type.isSubtypeOf(Types.ANY.o))
		{
			// The variable is definitely assigned here.  Use that value.
			regenerator.moveBoxedRegister(
				valueSource.semanticValue(),
				extractedValue.semanticValues())
			// Allow the assignments (with before/after variable) and creation
			// instruction to stay postponed.  The chain will be collapsed into an
			// initializing creation at the first place the variable is actually
			// needed, if anywhere.
			//
			// Write a jump to the happy target of the get.
			regenerator.addInstruction(
				L2_JUMP(regenerator.transformOperand(ifReadSucceeded)))
			return
		}
		// The value may or may not be assigned.  Fall back to a dynamic check.
		super.regenerateForPostponement(regenerator)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: try {
		val tryStart = Label()
		val catchStart = Label()
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			Type.getInternalName(VariableGetException::class.java))
		method.visitLabel(tryStart)
		// ::    dest = variable.getValue().makeImmutable();
		translator.load(method, variable.register())
		getValueMethod.generateCall(method)
		makeImmutableMethod.generateCall(method)
		translator.store(method, extractedValue.register())
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableGetException to be pushed onto the stack. So always do the
		// jump.
		translator.jump(method, ifReadSucceeded)
		// :: } catch (VariableGetException e) {
		method.visitLabel(catchStart)
		method.visitInsn(Opcodes.POP)
		// ::    goto failure;
		translator.jumpOrFallThrough(method, ifReadFailed)
		// :: }
	}
}
