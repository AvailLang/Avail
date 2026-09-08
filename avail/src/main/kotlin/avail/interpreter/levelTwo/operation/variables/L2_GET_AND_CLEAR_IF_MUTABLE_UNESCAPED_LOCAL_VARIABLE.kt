/*
 * L2_GET_AND_CLEAR_IF_MUTABLE_UNESCAPED_LOCAL_VARIABLE.kt
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
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2ControlFlowInstruction
import avail.interpreter.levelTwo.operation.L2_MOVE_BOXED
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2Optimizer
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.Frame
import avail.utility.notNullAnd

/**
 * Extract the value of a [variable] into [extractedValue].  If the variable is
 * mutable, clear it, without forcing the extracted value to be immutable.  If
 * the variable is immutable or shared, do not clear the variable; note that the
 * extracted value will already be immutable.  If the variable had a (non-nil)
 * value, jump to [ifReadSucceeded].  If the variable was unassigned, branch to
 * [ifReadFailed] instead.  Also transfer [variable] to [variableOut]
 * unconditionally.
 *
 * This instruction is a marker that transforms arriving postponed writes and
 * creations during the [L2Optimizer.postponeConditionallyUsedValues]
 * optimization.
 *
 * See "/doc/Optimization/Level_Two/Variable_elision.md" for
 * details.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_GET_AND_CLEAR_IF_MUTABLE_UNESCAPED_LOCAL_VARIABLE(
	var frame: L2ArbitraryConstantOperand<Frame>,
	var variable: L2ReadBoxedOperand,
	var variableOut: L2WriteBoxedOperand,
	@On(SUCCESS) var extractedValue: L2WriteBoxedOperand,
	@On(SUCCESS) var ifReadSucceeded: L2PcOperand,
	@On(OFF_RAMP) var ifReadFailed: L2PcOperand
): L2ControlFlowInstruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" Virtual get clearing if mutable ↓")
		append(variable.registerString())
		append(" → ")
		append(extractedValue.registerString())
		append("  (var out = ")
		append(variableOut.registerString())
		append(")")
	}

	override val hasSideEffect get() = true

	/**
	 * See if the value we would get from the variable is already known via a
	 * still-postponed variable creation or variable set instruction.  If so, we
	 * rewrite the get as a move from the setting (or initializing) expression,
	 * followed by a jump to the success case.
	 */
	override fun L2Regenerator.regenerateForPostponement()
	{
		val semanticVariable = variable.semanticValue()
		if (currentManifest.hasSemanticValue(semanticVariable))
		{
			// The variable has already been explicitly created.  Keep it simple
			// and always explicitly read from the variable.  We don't have to
			// worry about reactors being present.  Since this instruction does
			// a branch, we can't postpone it, so force it to generate.
			basicRegenerateForPostponement()
			return
		}
		val postponed = currentManifest
			.postponedInstructionFor(semanticVariable, BOXED_KIND)!!
		val originOfValue: L2ReadBoxedOperand = when (postponed)
		{
			is L2_SET_UNESCAPED_LOCAL_VARIABLE -> postponed.valueToWrite
			is L2_CREATE_VARIABLE -> postponed.initialValueOrNil
			else ->
			{
				basicRegenerateForPostponement()
				return
			}
		}
		// The move from variable to variableOut is unconditional.
		val originRestriction = currentManifest.restrictionFor(originOfValue)
		when
		{
			originRestriction.containedByType(ANY()) ->
			{
				// The variable is definitely assigned.
				currentManifest.recordPostponedInstruction(
					variableOut.pickSemanticValue(),
					L2_MOVE_BOXED(variable, variableOut))
				// Make sure to strengthen the restriction on the destination of
				// the move, since the restriction on the origin valueOfValue
				// can be far more precise than the variable type.
				currentManifest.recordPostponedInstruction(
					extractedValue.pickSemanticValue(),
					L2_MOVE_BOXED(
						originOfValue,
						L2WriteBoxedOperand(
							extractedValue.semanticValues(),
							extractedValue.restriction().intersection(
								originOfValue.restriction()))))
				jumpTo(ifReadSucceeded.targetBlock())
				return
			}
			originRestriction.constantOrNull.notNullAnd { isNil } ->
			{
				// The variable is definitely unassigned.
				currentManifest.recordPostponedInstruction(
					variableOut.pickSemanticValue(),
					L2_MOVE_BOXED(variable, variableOut))
				jumpTo(ifReadFailed.targetBlock())
				return
			}
			// The variable might be assigned and might not.
			else -> basicRegenerateForPostponement()
		}
	}

	override fun sourceOfMoveToRegister(
		destinationRegister: L2Register<*>
	): L2Register<*>? = when (destinationRegister)
	{
		variableOut.register() -> variable.register()
		else -> null
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: variableOut = variable;
		if (variableOut.finalIndex() != variable.finalIndex())
		{
			load(variable)
			store(variableOut.register())
		}
		GetClearMode.ClearIfMutable.run {
			translateJvmVariableRead(
				variable,
				extractedValue,
				ifReadSucceeded = ifReadSucceeded,
				ifReadFailed = ifReadFailed)
		}
	}
}
