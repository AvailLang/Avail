/**
 * L2OperandType.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.operand.L2CommentOperand;
import com.avail.interpreter.levelTwo.register.*;


/**
 * An {@code L2OperandType} specifies the nature of a level two operand.  It
 * doesn't fully specify how the operand is used, but it does say whether the
 * associated register is being read or written or both, and how to interpret
 * the raw {@code int} that encodes such an operand.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public enum L2OperandType
{
	/**
	 * The operand represents the actual object at the specified index of the
	 * chunk's list of literals.
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
	 * The operand represents the very integer used to encode it.
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
	 * The operand represents an offset into the chunk's wordcodes, presumably
	 * for the purpose of branching there at some time and under some condition.
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
	 * The operand represents a virtual machine {@link Primitive} to be
	 * executed.  It has the same number as the corresponding primitive's {@link
	 * Enum#ordinal() ordinal()}.
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
	 * Like a {@link #CONSTANT}, the operand represents the actual object at the
	 * specified index of the chunk's list of literals, but more specifically
	 * that object is a {@linkplain MethodDescriptor method} holding a hierarchy
	 * of multi-methods.  Presumably a dispatch will take place through this
	 * method, or at least a dependency is established with respect to which
	 * multi-methods are present.
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
	 * The operand represents an {@linkplain L2ObjectRegister object register},
	 * capable of holding any Avail object.  The specified index is passed to
	 * {@link Interpreter#pointerAt(int)} to extract the current value.
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
	 * The operand represents an {@linkplain L2ObjectRegister object register},
	 * capable of holding any Avail object.  The specified index is passed to
	 * {@link Interpreter#pointerAtPut(int, AvailObject)} to set the current
	 * value.  This operand must <em>only</em> be used for blindly writing a new
	 * value to the register -- the previous value of the register may not be
	 * read on behalf of this operand.  Writing to the register is compulsory.
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
	 * The operand represents an {@linkplain L2ObjectRegister object register},
	 * capable of holding any Avail object.  The specified index is passed to
	 * {@link Interpreter#pointerAt(int)} to read the current value, and
	 * {@link Interpreter#pointerAtPut(int, AvailObject)} to set a new value.
	 * A read before a write is not compulsory, but it is permitted.  Since a
	 * read may precede the write, there's no point in making the write
	 * compulsory, since it could just be writing the value that it read, and
	 * that's essentially the same as not writing at all.
	 */
	READWRITE_POINTER(true, true)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadWritePointer();
		}
	},

	/**
	 * The operand represents an {@linkplain L2IntegerRegister integer
	 * register}, capable of holding any {@code int}.  The specified index is
	 * passed to {@link Interpreter#integerAt(int)} to extract the current
	 * value.
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
	 * The operand represents an {@linkplain L2IntegerRegister integer
	 * register}, capable of holding any {@code int}.  The specified index is
	 * passed (as the first argument) to {@link
	 * Interpreter#integerAtPut(int, int)} to set the current value.  This
	 * operand must <em>only</em> be used for blindly writing a new value to the
	 * register -- the previous value of the register may not be read on behalf
	 * of this operand.  Writing to the register is compulsory.
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
	 * The operand represents an {@linkplain L2IntegerRegister integer
	 * register}, capable of holding any {@code int}.  The specified index is
	 * passed to {@link Interpreter#integerAt(int)} to read the current value,
	 * and (as the first argument) to {@link
	 * Interpreter#integerAtPut(int, int)} to set a new value.  A read before
	 * a write is not compulsory, but it is permitted.  Since a read may precede
	 * the write, there's no point in making the write compulsory, since it
	 * could just be writing the value that it read, and that's essentially the
	 * same as not writing at all.
	 */
	READWRITE_INT(true, true)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadWriteInt();
		}
	},

	/**
	 * The operand represents a {@linkplain L2RegisterVector vector of object
	 * registers}, each of which should be treated as being read as though it
	 * were a {@link #READ_POINTER}.  The specified index identifies a tuple of
	 * integers in the {@linkplain L2ChunkDescriptor chunk}'s {@linkplain
	 * com.avail.descriptor.L2ChunkDescriptor.ObjectSlots#VECTORS vectors}.
	 * Each integer in that tuple is the index of an {@link L2ObjectRegister}.
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
	 * The operand represents a {@linkplain L2RegisterVector vector of object
	 * registers}, each of which should be treated as being written as though it
	 * were a {@link #WRITE_POINTER}.  The specified index identifies a tuple of
	 * integers in the {@linkplain L2ChunkDescriptor chunk}'s {@linkplain
	 * com.avail.descriptor.L2ChunkDescriptor.ObjectSlots#VECTORS vectors}.
	 * Each integer in that tuple is the index of an {@link L2ObjectRegister}.
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
	 * The operand represents a {@linkplain L2RegisterVector vector of object
	 * registers}, each of which should be treated as being read and/or written
	 * as though it were a {@link #READWRITE_POINTER}.  The specified index
	 * identifies a tuple of integers in the {@linkplain L2ChunkDescriptor
	 * chunk}'s {@linkplain
	 * com.avail.descriptor.L2ChunkDescriptor.ObjectSlots#VECTORS}.  Each
	 * integer in that tuple is the index of an {@link L2ObjectRegister}.
	 */
	READWRITE_VECTOR(true, true)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadWriteVector();
		}
	},

	/**
	 * The operand represents a {@linkplain L2CommentOperand}, which holds a
	 * descriptive {@link String} during optimization, but has no effect on the
	 * actual wordcode stream.
	 */
	COMMENT(false, false)
	{
		@Override
		void dispatch(final L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doImplicitlyInitializeVector();
		}
	};


	/**
	 * Invoke an entry point of the passed {@linkplain L2OperandTypeDispatcher
	 * operand type dispatcher} that's specific to which {@link L2OperandType}
	 * the receiver is.
	 *
	 * @param dispatcher
	 *            The {@link L2OperandTypeDispatcher} to visit with the
	 *            receiver.
	 */
	abstract void dispatch(L2OperandTypeDispatcher dispatcher);

	/**
	 * Whether the receiver is to be treated as a source of information.
	 */
	public final boolean isSource;

	/**
	 * Whether the receiver is to be treated as a destination for information.
	 */
	public final boolean isDestination;

	/**
	 * Construct a new {@link L2OperandType}.  Remember, this is an enum, so
	 * the only constructor calls are in the enum member definitions.
	 *
	 * @param isSource
	 *            Whether I represent a (potential) read from a register.
	 * @param isDestination
	 *            Whether I represent a write to a register.  If I am also to be
	 *            considered a read, then it is treated as a <em>potential</em>
	 *            write.
	 */
	private L2OperandType (final boolean isSource, final boolean isDestination)
	{
		this.isSource = isSource;
		this.isDestination = isDestination;
	}

	/**
	 * Create a {@link L2NamedOperandType} from the receiver and a {@link
	 * String} naming its role for some {@link L2Operation}.
	 *
	 * @param name The name of this operand.
	 * @return A named operand type.
	 */
	public L2NamedOperandType is (final String name)
	{
		return new L2NamedOperandType(this, name);
	}
}
