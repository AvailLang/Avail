/**
 * interpreter/levelOne/L1OperandType.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelOne;

import com.avail.descriptor.*;



/**
 * An {@link L1Instruction} consists of an {@link L1Operation} and its
 * operands, each implicitly described by the operation's {@link
 * L1OperandType}s  These operand types say how to interpret some integer that
 * occurs as the encoding of an actual operand of an instruction.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public enum L1OperandType
{
	/**
	 * The integer in the nybblecode stream is to be treated as itself, a simple
	 * integer.
	 */
	IMMEDIATE()
	{
		@Override
		void dispatch(final L1OperandTypeDispatcher dispatcher)
		{
			dispatcher.doImmediate();
		}
	},
	/**
	 * The integer in the nybblecode stream is to be treated as an index into
	 * the current {@linkplain CompiledCodeDescriptor compiled code} object's
	 * {@linkplain AvailObject#literalAt(int) literals}.
	 * This allows instructions to refer to arbitrary {@linkplain AvailObject}s.
	 */
	LITERAL()
	{
		@Override
		void dispatch(final L1OperandTypeDispatcher dispatcher)
		{
			dispatcher.doLiteral();
		}
	},
	/**
	 * The integer in the nybblecode stream is to be treated as an index into
	 * the arguments and local variables of the continuation.  The arguments
	 * come first, numbered from 1, then the local variables.
	 */
	LOCAL()
	{
		@Override
		void dispatch(final L1OperandTypeDispatcher dispatcher)
		{
			dispatcher.doLocal();
		}
	},
	/**
	 * The integer in the nybblecode stream is to be treated as an index into
	 * the current {@linkplain FunctionDescriptor function}'s captured outer
	 * variables.
	 */
	OUTER()
	{
		@Override
		void dispatch(final L1OperandTypeDispatcher dispatcher)
		{
			dispatcher.doOuter();
		}
	},
	/**
	 * The next nybble from the nybblecode stream is to be treated as an
	 * extended nybblecode.  For some purposes it can be treated as though the
	 * value 16 has been added to it, bringing it into the range 16..31.
	 */
	EXTENSION()
	{
		@Override
		void dispatch(final L1OperandTypeDispatcher dispatcher)
		{
			dispatcher.doExtension();
		}
	};

	/**
	 * Invoke an operation on the {@link L1OperandTypeDispatcher} which is
	 * specific to which {@link L1OperandType} the receiver is.  Subclasses of
	 * {@code L1OperandTypeDispatcher} will perform something suitable for that
	 * subclass, perhaps consuming and interpreting an operand from a nybblecode
	 * stream.
	 *
	 * @param dispatcher
	 *            The {@link L1OperandTypeDispatcher} on which to invoke a
	 *            method specific to this operand type.
	 */
	abstract void dispatch(L1OperandTypeDispatcher dispatcher);

}
