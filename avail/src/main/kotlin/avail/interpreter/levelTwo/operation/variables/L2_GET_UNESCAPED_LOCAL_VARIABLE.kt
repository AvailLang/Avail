/*
 * L2_GET_UNESCAPED_LOCAL_VARIABLE.kt
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

import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.unsupported
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2Optimizer
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import avail.utility.notNullAnd
import org.objectweb.asm.MethodVisitor

/**
 * Assign a value to a [variable][VariableDescriptor].  This is a kind of
 * placeholder that facilitates code motion of variable creation and writes
 * during the [postponement][L2Optimizer.postponeConditionallyUsedValues]
 * optimization.
 *
 * See "/doc/Optimization/Level_Two/Variable_elision.md" for
 * details.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_GET_UNESCAPED_LOCAL_VARIABLE(
	val frame: Frame,
	var variable: L2ReadBoxedOperand,
	var variableOut: L2WriteBoxedOperand,
	@On(SUCCESS) var extractedValue: L2WriteBoxedOperand,
	@On(SUCCESS) var ifReadSucceeded: L2PcOperand,
	@On(OFF_RAMP) var ifReadFailed: L2PcOperand
): L2ControlFlowInstruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" Virtual get ↓")
		append(variable.registerString())
		append(" → ")
		append(extractedValue.registerString())
		append("  (var out = ")
		append(variableOut.registerString())
		append(")")
	}

	/**
	 * See if the value we would get from the variable is already known via a
	 * still-postponed variable creation or variable set instruction.  If so, we
	 * rewrite the get as a move from the setting (or initializing) expression,
	 * followed by a jump to the success case.
	 */
	override fun regenerateForPostponement(regenerator: L2Regenerator)
	{
		val manifest = regenerator.currentManifest
		val semanticVariable = variable.semanticValue()
		if (manifest.hasSemanticValue(semanticVariable))
		{
			// The variable has already been explicitly created.  Keep it simple
			// and always explicitly read from the variable.  We don't have to
			// worry about reactors being present.
			super.regenerateForPostponement(regenerator)
			return
		}
		val postponed = manifest.postponedInstructions()[semanticVariable]!!
		val originValue = when (postponed)
		{
			is L2_VIRTUAL_SET_LOCAL_VARIABLE ->
				postponed.valueToWrite.semanticValue()
			is L2_SET_UNESCAPED_LOCAL_VARIABLE ->
				postponed.valueToWrite.semanticValue()
			is L2_CREATE_VARIABLE ->
				postponed.initialValueOrNil.semanticValue()
			is L2_GET_UNESCAPED_LOCAL_VARIABLE ->
				postponed.extractedValue.pickSemanticValue()
			else ->
			{
				super.regenerateForPostponement(regenerator)
				return
			}
		}
		val originType = manifest.restrictionFor(originValue)
		when
		{
			originType.containedByType(ANY.o) ->
			{
				// The variable is definitely assigned.
				regenerator.moveBoxedRegister(
					originValue, extractedValue.semanticValues())
				regenerator.moveBoxedRegister(
					variable.semanticValue(), variableOut.semanticValues())
				regenerator.addInstruction(
					regenerator.basicTransformInstruction(
						L2_JUMP(edgeTo(ifReadSucceeded.targetBlock()))))
				return
			}
			originType.constantOrNull.notNullAnd { isNil } ->
			{
				// The variable is definitely unassigned.
				regenerator.moveBoxedRegister(
					variable.semanticValue(), variableOut.semanticValues())
				regenerator.addInstruction(
					regenerator.basicTransformInstruction(
						L2_JUMP(edgeTo(ifReadFailed.targetBlock()))))
				return
			}
			// The variable might be assigned and might not.
			else -> super.regenerateForPostponement(regenerator)
		}
	}

	override fun sourceOfMoveToRegister(
		destinationRegister: L2Register<*>
	): L2Register<*>? = when (destinationRegister)
	{
		variableOut.register() -> variable.register()
		else -> null
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor
	) = unsupported
}
