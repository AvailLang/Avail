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

import avail.descriptor.representation.A_Variable
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.variables.VariableDescriptor
import avail.interpreter.levelTwo.HiddenVariable.GLOBAL_STATE
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.WritesHiddenVariable
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2Synonym
import avail.optimizer.manifest.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator

/**
 * Assign a value to a [variable][VariableDescriptor] *without* checking that
 * it's of the correct type, or handling exceptions thrown by write reactors.
 * There must not be any write reactors attached at this point, and the variable
 * must not have become shared.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@WritesHiddenVariable(GLOBAL_STATE::class)
class L2_SET_UNESCAPED_LOCAL_VARIABLE
constructor(
	var variable: L2ReadBoxedOperand,
	var valueToWrite: L2ReadBoxedOperand,
	var variableOut: L2WriteBoxedOperand
): L2Instruction()
{
	init
	{
		assert(this.variable.restriction()
			.containedByType(mostGeneralVariableType))
		assert(!this.variable.isConstantRead) {
			"L2_SET_UNESCAPED_LOCAL_VARIABLE is an elided local here."
		}
	}

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
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

	/**
	 * Rewrite this postponed instruction in the manifest, replacing it and
	 * sometimes others in the process.  For example, if an [L2_CREATE_VARIABLE]
	 * produces a variable that's used in an [L2_SET_UNESCAPED_LOCAL_VARIABLE],
	 * both can be replaced by an [L2_CREATE_VARIABLE] that has the more
	 * up-to-date value as its initialization value.
	 *
	 * HOWEVER, we must not do that if the initialization value is computed
	 * recursively from the variable itself, since that would introduce a cyclic
	 * dependency order, which is impossible to satisfy.
	 *
	 * @receiver
	 *   The [L2ValueManifest] containing this postponed instruction.
	 * @param
	 *   The [L2Synonym] under which the instruction is to be postponed.
	 * @return
	 *   `true` if a replacement was made, otherwise `false`.
	 */
	override fun L2ValueManifest.rewritePostponed(
		synonym: L2Synonym
	): Boolean
	{
		// Check if the value to be written depends on this variable.
		// If so, we must not merge the assignment with the creation, or
		// it would introduce an unsatisfiable dependency order.
		val variableOrigin = postponedInstructionFor(
			variable.semanticValue(),
			BOXED_KIND)
		if (variableOrigin != null
			&& checkDependency(
				valueToWrite.semanticValue(),
				variableOrigin,
				mutableSetOf()))
		{
			// The variable must already exist to know what to assign.
			// Don't merge the assignment into the creation.
			return false
		}
		when (variableOrigin)
		{
			is L2_CREATE_VARIABLE ->
			{
				// A create/setter pair can be collapsed to be a create with the
				// setter's value as its initial value.
				removePostponedInstructionFor(
					synonym.pickSemanticValue(),
					BOXED_KIND)
				removePostponedInstructionFor(
					variable.semanticValue(),
					BOXED_KIND)
				assert(variable.restriction() == variableOut.restriction())
				recordPostponedInstruction(
					synonym.pickSemanticValue(),
					L2_CREATE_VARIABLE(
						localIndex = variableOrigin.localIndex,
						outerType = variableOrigin.outerType,
						variable = L2WriteBoxedOperand(
							variableOut.semanticValues(),
							variableOut.restriction()),
						initialValueOrNil = valueToWrite,
						constantVariableIfElided =
							variableOrigin.constantVariableIfElided))
				return true
			}
			is L2_SET_UNESCAPED_LOCAL_VARIABLE ->
			{
				// We have a chain of setters.  The first one takes a variable
				// and value as input and produces an output variable, which is
				// consumed by the current instruction, also a setter that
				// writes some value and produces the output variable.  Due to
				// the way unescaped local instructions are emitted, there will
				// be no other uses of that intermediate variable.  Remove both
				// setters and write a new instruction that takes the first
				// instruction's input variable, sets the second instruction's
				// value, and produces the second instruction's output variable.
				// This effectively removes an unobserved write.
				removePostponedInstructionFor(
					synonym.pickSemanticValue(),
					BOXED_KIND)
				removePostponedInstructionFor(
					variable.semanticValue(),
					BOXED_KIND)
				assert(!variableOrigin.variable.isConstantRead) {
					"This would read from an elided local variable"
				}
				recordPostponedInstruction(
					synonym.pickSemanticValue(),
					L2_SET_UNESCAPED_LOCAL_VARIABLE(
						variable = variableOrigin.variable,
						valueToWrite = valueToWrite,
						variableOut = L2WriteBoxedOperand(
							variableOrigin.variableOut.semanticValues() +
								variableOut.semanticValues(),
							variableOut.restriction())))
				return true
			}
		}
		return false
	}

	override fun JVMTranslator.translateToJVM()
	{
		// It's not allowed to fail.
		if (variable.finalIndex() != variableOut.finalIndex())
		{
			load(variable)
			store(variableOut.register())
		}
		// :: variable.setUnescapedLocalValueNoCheck(valueToWrite);
		load(variable)
		load(valueToWrite)
		generateCall(A_Variable.setValueNoCheckMethod)
	}
}
