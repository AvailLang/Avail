/*
 * L2_TUPLE_SUBRANGE_NO_FAIL.kt
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

package avail.interpreter.levelTwo.operation.tuples

import avail.descriptor.tuples.TupleDescriptor
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.optimizer.jvm.JVMTranslator

/**
 * Extract a tuple in specified range of subscripts from a
 * [tuple][TupleDescriptor].  The indices must be known to be in range.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_TUPLE_SUBRANGE_NO_FAIL(
	var tuple: L2ReadBoxedOperand,
	var lowSubscript: L2ReadIntOperand,
	var highSubscript: L2ReadIntOperand,
	var destination: L2WriteBoxedOperand
): L2Instruction()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(destination.registerString())
		append(" ← ")
		append(tuple.registerString())
		append("(no fail)[")
		append(lowSubscript)
		append("..")
		append(highSubscript)
		append(']')
	}

	/**
	 * In theory, reading from a tuple doesn't destroy the tuple, and doesn't
	 * have to mark the resulting values as immutable either.  However, any
	 * access to the tuple later could see a mutated form of this element, so
	 * until we can couple the mutability constraints between the tuple and its
	 * elements, we must make the extracted subtuple immutable.
	 */
	override fun propagateMutability(
		firstUses: MutableMap<L2BoxedRegister, Pair<Int, L2ReadBoxedOperand>>,
		mutables: MutableSet<L2BoxedRegister>)
	{
		// If the tuple has already become immutable, there's no need to mark
		// the extracted element as immutable.
		if (tuple.register() in mutables)
		{
			// Tuple is still potentially mutable, so the extracted elements are
			// potentially mutable.  Force the subtuple to be immutable,
			// regardless of whether it gets used multiple times or not.
			mutables -= destination.register()
			val instructionIndex = basicBlock().instructions().indexOf(this)
			// Blame the next instruction, so that we generate the makeImmutable
			// right after this tupleAt.
			firstUses[destination.register()] =
				(instructionIndex + 1) to L2ReadBoxedOperand(
					destination.pickSemanticValue(),
					destination.restriction(),
					destination.register())
		}
		else
		{
			// The tuple is immutable at this point (perhaps because at least
			// two uses have been encountered).  Therefore, all its elements are
			// already immutable.
			return
		}
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: destination =
		//       tuple.staticTupleCopyFromTo(lowSubscript, highSubscript)
		load(tuple)
		load(lowSubscript)
		load(highSubscript)
		generateCall(TupleDescriptor.tupleCopyFromToMethod)
		store(destination.register())
	}
}
