/*
 * L2_JUMP_IF_UNBOX_FLOAT.kt
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

package avail.interpreter.levelTwo.operation.numbers

import avail.descriptor.numbers.A_Number
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DOUBLE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE
import avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.On
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2WriteFloatOperand
import avail.interpreter.levelTwo.operation.L2ConditionalJump
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Jump to `"if unboxed"` if a `double` was unboxed from an [AvailObject],
 * otherwise jump to `"if not unboxed"`.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_JUMP_IF_UNBOX_FLOAT(
	var source: L2ReadBoxedOperand,
	@On(SUCCESS) var destination: L2WriteFloatOperand,
	@On(FAILURE) var ifNotUnboxed: L2PcOperand,
	@On(SUCCESS) var ifUnboxed: L2PcOperand
): L2ConditionalJump()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(destination.registerString())
		append(" ←? ")
		append(source.registerString())
		renderOperandsExcludingFields(
			desiredOperandTypes, ::source, ::destination)
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		destination.restrict { source.restriction().forUnboxedFloat() }
		super.instructionWasAdded(manifest)
		ifUnboxed.manifest().intersectType(source, DOUBLE.o)
		ifNotUnboxed.manifest().subtractType(source, DOUBLE.o)
	}

	override val readsThatMightDestroy get() = emptyList<L2ReadBoxedOperand>()

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		// :: if (!source.isDouble()) goto ifNotUnboxed;
		translator.load(method, source.register())
		A_Number.isDoubleMethod.generateCall(method)
		translator.jumpIf(method, Opcodes.IFEQ, ifNotUnboxed)
		// :: else {
		// ::    destination = source.extractDouble();
		// ::    goto ifUnboxed;
		// :: }
		translator.load(method, source.register())
		A_Number.extractDoubleMethod.generateCall(method)
		translator.store(method, destination.register())
		translator.jumpOrFallThrough(method, ifUnboxed)
	}
}
