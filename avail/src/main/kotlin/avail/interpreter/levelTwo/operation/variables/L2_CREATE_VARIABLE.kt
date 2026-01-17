/*
 * L2_CREATE_VARIABLE.kt
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

import avail.descriptor.functions.A_RegisterDump
import avail.descriptor.types.VariableTypeDescriptor
import avail.descriptor.variables.VariableDescriptor
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2_CREATE_CONTINUATION
import avail.interpreter.levelTwo.operation.L2_SAVE_ALL_AND_PC_TO_INT
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import avail.utility.isNullOr
import org.objectweb.asm.MethodVisitor

/**
 * Create a new [variable&#32;object][VariableDescriptor] of the specified
 * [variable&#32;type][VariableTypeDescriptor].
 *
 * Note that this instruction does not have a side-effect, although it must not
 * run twice (for the same conceptual variable), since the identity of the new
 * variable must be preserved.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_CREATE_VARIABLE
constructor(
	var localIndex: L2IntImmediateOperand,
	var outerType: L2ConstantOperand,
	var variable: L2WriteBoxedOperand,
	var initialValueOrNil: L2ReadBoxedOperand,
	var constantVariableIfElided: L2ConstantOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(variable.registerString())
		append(" ← new(local#")
		append(localIndex.value)
		append(") ")
		append(outerType.constant)
		if (initialValueOrNil.constantOrNull.isNullOr { notNil })
		{
			append(" := ")
			append(initialValueOrNil.registerString())
		}
	}

	/**
	 * We *do* allow an [L2_CREATE_VARIABLE] to go both ways at an
	 * [L2_SAVE_ALL_AND_PC_TO_INT].  It goes along the
	 * [reference][L2_SAVE_ALL_AND_PC_TO_INT.reference] edge to allow variable
	 * creation to be postponed until after the reification completes and the
	 * continuation is returned into.  It also goes along the
	 * [ifFallThrough][L2_SAVE_ALL_AND_PC_TO_INT.ifFallThrough] edge, where it
	 * gets transformed by the eventual [L2_CREATE_CONTINUATION] in the
	 * reification part that captures the initialization value in case the
	 * continuation becomes shared or immutable, allowing that local variable to
	 * be initialized correctly on creation (and switch to L1 execution).  Note
	 * that other state information of the [L2_SAVE_ALL_AND_PC_TO_INT] has to be
	 * updated to capture this information, since that instruction is what
	 * creates the [A_RegisterDump] subsequently used by the
	 * [L2_CREATE_CONTINUATION].
	 */
	override fun L2Regenerator.regenerateForPostponement()
	{
		// Always try to postpone the local variable creation instruction.
		currentManifest.recordPostponedInstruction(this@L2_CREATE_VARIABLE)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: newVar = newVariableWithOuterType(outerType  [,null] );
		translator.loadLiteralObject(method, outerType.constant)
		translator.load(method, initialValueOrNil)
		VariableDescriptor.newVariableWithOuterTypeMethod.generateCall(method)
		translator.store(method, variable.register())
	}
}
