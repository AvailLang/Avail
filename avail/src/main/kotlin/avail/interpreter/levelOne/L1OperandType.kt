/*
 * L1OperandType.kt
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

package avail.interpreter.levelOne

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.CompiledCodeDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.representation.AvailObject


/**
 * An L1 instruction consists of an [L1Operation] and its operands, each
 * implicitly described by the operation's [L1OperandType]s.  These operand
 * types say how to interpret some integer that occurs as the encoding of an
 * actual operand of an instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum class L1OperandType
{
	/**
	 * The integer in the nybblecode stream is to be treated as itself, a simple
	 * integer.
	 */
	IMMEDIATE
	{
		override fun dispatch(dispatcher: L1OperandTypeDispatcher, index: Int) =
			dispatcher.doImmediate(index)
	},

	/**
	 * The integer in the nybblecode stream is to be treated as an index into
	 * the current [compiled&#32;code][CompiledCodeDescriptor] object's
	 * [literals][A_RawFunction.literalAt]. This allows instructions to refer to
	 * arbitrary [AvailObject]s.
	 */
	LITERAL
	{
		override fun dispatch(dispatcher: L1OperandTypeDispatcher, index: Int) =
			dispatcher.doLiteral(index)
	},

	/**
	 * The integer in the nybblecode stream is to be treated as an index into
	 * the arguments and local variables of the continuation.  The arguments
	 * come first, numbered from 1, then the local variables.
	 */
	LOCAL
	{
		override fun dispatch(dispatcher: L1OperandTypeDispatcher, index: Int) =
			dispatcher.doLocal(index)
	},

	/**
	 * The integer in the nybblecode stream is to be treated as an index into
	 * the current [function][FunctionDescriptor]'s captured outer variables.
	 */
	OUTER
	{
		override fun dispatch(dispatcher: L1OperandTypeDispatcher, index: Int) =
			dispatcher.doOuter(index)
	};

	/**
	 * Invoke an operation on the [L1OperandTypeDispatcher] which is specific to
	 * which [L1OperandType] the receiver is.  Subclasses of
	 * `L1OperandTypeDispatcher` will perform something suitable for that
	 * subclass, perhaps consuming and interpreting an operand from a nybblecode
	 * stream.
	 *
	 * @param dispatcher
	 *   The [L1OperandTypeDispatcher] on which to invoke a method specific to
	 *   this operand type.
	 */
	internal abstract fun dispatch(
		dispatcher: L1OperandTypeDispatcher,
		index: Int)
}
