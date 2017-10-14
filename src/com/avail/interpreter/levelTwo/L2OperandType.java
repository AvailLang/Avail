/**
 * L2OperandType.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.DefinitionDescriptor;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;

import java.util.List;


/**
 * An {@code L2OperandType} specifies the nature of a level two operand.  It
 * doesn't fully specify how the operand is used, but it does say whether the
 * associated register is being read or written or both.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public enum L2OperandType
{
	/**
	 * An {@link L2ConstantOperand} holds a specific AvailObject.
	 */
	CONSTANT(false, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doConstant();
		}
	},

	/**
	 * An {@link L2ImmediateOperand} holds an {@code int} value.
	 */
	IMMEDIATE(false, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doImmediate();
		}
	},

	/**
	 * An {@link L2PcOperand} holds an offset into the chunk's instructions,
	 * presumably for the purpose of branching there at some time and under some
	 * condition.
	 */
	PC(false, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doPC();
		}
	},

	/**
	 * An {@link L2PrimitiveOperand} holds a {@link Primitive} to be invoked.
	 */
	PRIMITIVE(false, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doPrimitive();
		}
	},

	/**
	 * Like a {@link #CONSTANT}, the {@link L2SelectorOperand} holds the actual
	 * AvailObject, but it is known to be an {@link A_Bundle}.  The {@link
	 * L2Chunk} depends on this bundle, invalidating itself if its {@link
	 * DefinitionDescriptor definitions} change.
	 */
	SELECTOR(false, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doSelector();
		}
	},

	/**
	 * The {@link L2ReadPointerOperand} holds the {@link L2ObjectRegister} that
	 * will be read.
	 */
	READ_POINTER(true, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadPointer();
		}
	},

	/**
	 * The {@link L2WritePointerOperand} holds the {@link L2ObjectRegister} that
	 * will be written (but not read).
	 */
	WRITE_POINTER(false, true)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doWritePointer();
		}
	},

	/**
	 * The {@link L2ReadIntOperand} holds the {@link L2IntegerRegister} that
	 * will be read.
	 */
	READ_INT(true, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadInt();
		}
	},

	/**
	 * The {@link L2WriteIntOperand} holds the {@link L2IntegerRegister} that
	 * will be written (but not read).
	 */
	WRITE_INT(false, true)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doWriteInt();
		}
	},

	/**
	 * The {@link L2ReadVectorOperand} holds a {@link List} of {@link
	 * L2ReadPointerOperand}s which will be read.
	 */
	READ_VECTOR(true, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadVector();
		}
	},

	/**
	 * The {@link L2WriteVectorOperand} holds a {@link List} of {@link
	 * L2WritePointerOperand}s which will be written (but not read).
	 */
	WRITE_VECTOR(false, true)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doWriteVector();
		}
	},

	/**
	 * The {@link L2CommentOperand} holds descriptive text that does not affect
	 * analysis or execution of level two code.  It is for diagnostic purposes
	 * only.
	 */
	COMMENT(false, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doComment();
		}
	};

	/**
	 * Invoke an entry point of the passed {@linkplain L2OperandTypeDispatcher
	 * operand type dispatcher} that's specific to which {@code L2OperandType}
	 * the receiver is.
	 *
	 * @param dispatcher
	 *            The {@link L2OperandTypeDispatcher} to visit with the
	 *            receiver.
	 */
	abstract void dispatch (L2OperandTypeDispatcher dispatcher);

	/**
	 * Whether the receiver is to be treated as a source of information.
	 */
	public final boolean isSource;

	/**
	 * Whether the receiver is to be treated as a destination for information.
	 */
	public final boolean isDestination;

	/**
	 * Construct a new {@code L2OperandType}.  Remember, this is an enum, so
	 * the only constructor calls are in the enum member definitions.
	 *
	 * @param isSource
	 *            Whether I represent a (potential) read from a register.
	 * @param isDestination
	 *            Whether I represent a write to a register.  If I am also to be
	 *            considered a read, then it is treated as a <em>potential</em>
	 *            write.
	 */
	L2OperandType (final boolean isSource, final boolean isDestination)
	{
		this.isSource = isSource;
		this.isDestination = isDestination;
	}

	/**
	 * Create a {@link L2NamedOperandType} from the receiver and a {@link
	 * String} naming its role within some {@link L2Operation}.
	 *
	 * @param roleName The name of this operand.
	 * @return A named operand type.
	 */
	public L2NamedOperandType is (final String roleName)
	{
		return new L2NamedOperandType(this, roleName);
	}
}
