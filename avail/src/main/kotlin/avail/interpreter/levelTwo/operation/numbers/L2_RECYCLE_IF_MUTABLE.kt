/*
 * L2_RECYCLE_IF_MUTABLE.kt
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

import avail.descriptor.numbers.IntegerDescriptor.RecyclePool
import avail.descriptor.representation.AvailObject
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.jvm.CheckedField
import avail.optimizer.jvm.CheckedField.Companion.instanceField
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.Opcodes.ACONST_NULL

/**
 * Offer the boxed [Int] in [source] to the current [Interpreter]'s
 * [RecyclePool], if it's mutable.  This is only inserted at a last use of a
 * register that holds a boxed integer, with no other live reference to the
 * object.
 *
 * After the offer, the JVM local for [source] is nulled.  Nothing may read the
 * register again, so any such read fails fast instead of observing a recycled
 * object.
 */
class L2_RECYCLE_IF_MUTABLE(
	var source: L2ReadBoxedOperand
): L2Instruction()
{
	override val hasSideEffect: Boolean get() = true

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(source.registerString())
	}

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// The recycle instruction should only be inserted after all
		// optimization passes that use graph regeneration.
		throw AssertionError("Should not reach this")
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: interpreter.integerPool.recycleIfMutable(source);
		// :: source = null;
		loadInterpreter()
		load(integerPoolField)
		load(source)
		generateCall(recycleIfMutableMethod)
		method.visitInsn(ACONST_NULL)
		store(source.register())
	}

	companion object
	{
		/** The [CheckedField] for [Interpreter.integerPool]. */
		val integerPoolField: CheckedField =
			instanceField(Interpreter::integerPool)

		/** The [CheckedMethod] for [RecyclePool.recycleIfMutable]. */
		val recycleIfMutableMethod: CheckedMethod = instanceMethod(
			RecyclePool::recycleIfMutable,
			AvailObject::class.java)
	}
}
