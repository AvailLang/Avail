/*
 * L2_JUMP_IF_KIND_OF_OBJECT.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
import avail.descriptor.types.A_Type.Companion.instance
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.reoptimizer.L2Regenerator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to the target if the value is an instance of the type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_JUMP_IF_KIND_OF_OBJECT(
	var value: L2ReadBoxedOperand,
	var type: L2ReadBoxedOperand,
	@On(SUCCESS) var ifKind: L2PcOperand,
	@On(FAILURE) var ifNotKind: L2PcOperand
): L2ConditionalJump()
{
	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		// Restrict the value to the type along the ifKind branch, but because
		// the provided type can be more specific at runtime, we can't restrict
		// the ifNotKind branch.
		ifKind.manifest().intersectType(value, type.type().instance)
	}

	override fun emitTransformedInstruction(
		regenerator: L2Regenerator)
	{
		type.restriction().constantOrNull?.let { constantType ->
			regenerator.jumpIfKindOfConstant(
				value,
				constantType,
				ifKind.targetBlock(),
				ifNotKind.targetBlock())
			return
		}
		ifKind.manifest().intersectType(value, type.type().instance)
		super.emitTransformedInstruction(regenerator)
	}

	override fun appendToWithWarnings(
		builder: StringBuilder,
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble(builder)
		builder.append(' ')
		builder.append(value.registerString())
		builder.append(" ∈ ")
		builder.append(type.registerString())
		renderOperandsExcludingFields(
			builder, desiredOperandTypes, ::value, ::type)
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (value.isInstanceOf(type)) goto isKind;
		// :: else goto isNotKind;
		translator.load(method, value.register())
		translator.load(method, type.register())
		A_BasicObject.isInstanceOfMethod.generateCall(method)
		emitBranch(translator, method, this, Opcodes.IFNE, ifKind, ifNotKind)
	}
}
