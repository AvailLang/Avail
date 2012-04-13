/**
 * L1Operation.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import java.io.ByteArrayOutputStream;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;

/**
 * An {@link L1Operation} is encoded within a {@linkplain AvailObject#nybbles()
 * nybblecode stream} as an opcode followed by operands.  Opcodes less than 16
 * are encoded as a single nybble, and the others are represented as the
 * {@linkplain #L1_doExtension extension} nybble followed by the opcode minus
 * 16.  The {@linkplain #L1Implied_Return return} instruction does not actually
 * occur, and is implied immediately after the end of a stream of nybblecodes.
 *
 * <p>The operands are encoded in such a way that very small values occupy a
 * single nybble, but values up to {@link Integer#MAX_VALUE} are supported
 * efficiently.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public enum L1Operation
{
	/**
	 * Invoke a method.
	 *
	 * <p>The first operand is an index into the current code's {@link
	 * AvailObject#literalAt(int) literals}, which specifies a {@linkplain
	 * MethodDescriptor method} that contains a collection of {@linkplain
	 * MethodImplementationDescriptor method implementations} that might be
	 * invoked.  The arguments are expected to already have been pushed. They
	 * are popped from the stack and the literal specified by the second operand
	 * is pushed.  This is the expected type of the send.  When the invoked
	 * method eventually returns, the proposed return value is checked
	 * against the pushed type, and if it agrees then this stack entry is
	 * replaced by the returned value.  If it disagrees, some sort of runtime
	 * exception should take place instead.</p>
	 */
	L1_doCall(0, L1OperandType.LITERAL, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doCall();
		}
	},

	/**
	 * Push the literal whose index is specified by the operand.
	 */
	L1_doPushLiteral(1, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLiteral();
		}
	},

	/**
	 * Push a local variable -- not its value, but the variable itself.  This
	 * should be the last use of the variable, so erase it from the continuation
	 * at the same time.
	 *
	 * <p>Clearing the variable keeps the variable's reference count from
	 * changing, so it may stay {@link AbstractDescriptor#isMutable() mutable}
	 * if it was before.</p>
	 *
	 * <p>If an argument is specified then push the value, since there is no
	 * actual {@linkplain VariableDescriptor variable} to operate on.  Clear
	 * the slot of the continuation reserved for the argument.  Constants are
	 * treated like ordinary local variables, except that they can not be
	 * assigned after their definition, nor can a reference to the constant be
	 * taken.</p>
	 */
	L1_doPushLastLocal(2, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLastLocal();
		}
	},

	/**
	 * Push a local variable -- not its value, but the variable itself.  If an
	 * argument or constant is specified then push the value, since there is no
	 * actual {@linkplain VariableDescriptor variable} to operate on.
	 */
	L1_doPushLocal(3, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLocal();
		}
	},

	/**
	 * Push an outer variable, i.e. a variable lexically captured by the current
	 * function.  This should be the last use of the variable, so clear it from
	 * the function if the function is still mutable.
	 */
	L1_doPushLastOuter(4, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLastOuter();
		}
	},

	/**
	 * Create a function from the specified number of pushed outer variables and
	 * the specified literal {@linkplain CompiledCodeDescriptor compiled code
	 * object}.
	 */
	L1_doClose(5, L1OperandType.IMMEDIATE, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doClose();
		}
	},

	/**
	 * Pop the stack and write the value into the specified local variable or
	 * constant (the latter should only happen once).
	 */
	L1_doSetLocal(6, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doSetLocal();
		}
	},

	/**
	 * Extract the value from the specified local variable or constant.  If the
	 * variable is mutable, null it out in the continuation.  Raised a suitable
	 * runtime exception if the variable does not have a value.
	 */
	L1_doGetLocalClearing(7, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetLocalClearing();
		}
	},

	/**
	 * Push the specified outer variable of the {@linkplain FunctionDescriptor
	 * function}.
	 */
	L1_doPushOuter(8, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushOuter();
		}
	},

	/**
	 * Discard the top element of the stack.
	 */
	L1_doPop(9)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPop();
		}
	},

	/**
	 * Push the current value of the specified outer variable.  The outer
	 * variable is part of the {@linkplain FunctionDescriptor function} being
	 * executed.  Clear the slot holding this outer variable if the function is
	 * mutable.
	 */
	L1_doGetOuterClearing(10, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetOuterClearing();
		}
	},

	/**
	 * Pop the stack and write it to the specified outer variable of the
	 * {@linkplain FunctionDescriptor function}.
	 */
	L1_doSetOuter(11, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doSetOuter();
		}
	},

	/**
	 * Push the value of the specified local variable or constant.  Make it
	 * immutable, since it may still be needed by subsequent instructions.
	 */
	L1_doGetLocal(12, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetLocal();
		}
	},

	/**
	 * Pop the specified number of elements from the stack and assemble them
	 * into a tuple.  Push the tuple.
	 */
	L1_doMakeTuple(13, L1OperandType.IMMEDIATE)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doMakeTuple();
		}
	},

	/**
	 * Push the current value of the specified outer variable of the {@linkplain
	 * FunctionDescriptor function}.
	 */
	L1_doGetOuter(14, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetOuter();
		}
	},

	/**
	 * Process an extension nybblecode, which involves consuming the next
	 * nybble and dispatching it as though 16 were added to it.
	 */
	L1_doExtension(15, L1OperandType.EXTENSION)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doExtension();
		}
	},

	/**
	 * Push a continuation just like the current one, such that if it is ever
	 * resumed it will have the same effect as restarting the current one.
	 */
	L1Ext_doPushLabel(16)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doPushLabel();
		}
	},

	/**
	 * Get the value of a {@linkplain VariableDescriptor variable} literal.
	 * This is used only to read from module variables.
	 */
	L1Ext_doGetLiteral(17, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetLiteral();
		}
	},

	/**
	 * Pop the stack and write the value into a {@linkplain VariableDescriptor
	 * variable} literal.  This is used to write to module variables.
	 */
	L1Ext_doSetLiteral(18, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doSetLiteral();
		}
	},

	/**
	 * Expect arguments to have pushed on the stack, followed by the argument
	 * types with which to perform a method lookup.  The literal index of the
	 * {@linkplain MethodDescriptor method} is the first
	 * operand, and the second is the literal index of the type that this call
	 * site is supposed to produce.  After popping the arguments and argument
	 * types, push the expected type.  The callee will check its return result
	 * against this pushed type, leading to a runtime error if they disagree.
	 */
	L1Ext_doSuperCall(19, L1OperandType.LITERAL, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doSuperCall();
		}
	},

	/**
	 * Compute the type of the object at the specified depth on the stack, and
	 * push it.  Used only for arguments to a {@linkplain #L1Ext_doSuperCall
	 * super call}.
	 */
	L1Ext_doGetType(20, L1OperandType.IMMEDIATE)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetType();
		}
	},

	/**
	 * Duplicate the top stack element (i.e., push another occurrence of the top
	 * of stack}.  Make the object immutable since it now has an additional
	 * reference.
	 */
	L1Ext_doDuplicate(21)
	{
		@Override
		public void dispatch (final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doDuplicate();
		}
	},

	/**
	 * An unsupported instruction was encountered.
	 */
	L1Ext_doReserved(22)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doReserved();
		}
	},

	/**
	 * The nybblecode stream has been exhausted, and all that's left is to
	 * perform an implicit return to the caller.
	 */
	L1Implied_Return(23)
	{
		@Override
		public void dispatch(final L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Implied_doReturn();
		}

		@Override
		public void writeTo (final ByteArrayOutputStream stream)
		{
			assert false
			: "The implied return instruction should not be output";
		}
	};

	/**
	 * This operation's collection of {@linkplain L1OperandType operand types}.
	 */
	private final @NotNull L1OperandType [] operandTypes;

	/**
	 * Return this operation's collection of {@linkplain L1OperandType operand
	 * types}.
	 *
	 * @return The kinds of operands this operation expects.
	 */
	public L1OperandType [] operandTypes ()
	{
		return operandTypes;
	}

	/**
	 * Construct a new {@link L1Operation}.  The expected {@link Enum#ordinal()
	 * ordinal} is passed as a cross-check so that each operation's definition
	 * shows the ordinal.  The rest of the arguments are the {@linkplain
	 * L1OperandType operand types} that this operation expects.
	 *
	 * @param ordinalCheck
	 *        This operation's ordinal.
	 * @param operandTypes
	 *        This operation's list of {@linkplain L1OperandType operand types}.
	 */
	L1Operation (
		final int ordinalCheck,
		final @NotNull L1OperandType ... operandTypes)
	{
		assert ordinalCheck == ordinal();
		this.operandTypes = operandTypes;
	}

	/**
	 * Dispatch this operation through an {@link L1OperationDispatcher}.
	 *
	 * @param operationDispatcher The {@link L1OperationDispatcher} that will
	 *                            accept this operation.
	 */
	public abstract void dispatch (L1OperationDispatcher operationDispatcher);

	/**
	 * Write this operation to a {@link ByteArrayOutputStream}.  Do not output
	 * operands.
	 *
	 * @param stream The {@link ByteArrayOutputStream} on which to write the
	 *               nybble(s) representing this operation.
	 */
	public void writeTo (final @NotNull ByteArrayOutputStream stream)
	{
		final int nybble = ordinal();
		if (nybble < 16)
		{
			stream.write(nybble);
		}
		else
		{
			assert nybble < 32;
			stream.write(L1_doExtension.ordinal());
			stream.write(nybble - 16);
		}
	}
}
