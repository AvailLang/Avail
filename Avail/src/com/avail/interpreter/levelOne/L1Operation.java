/**
 * interpreter/levelOne/L1Operation.java
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

import java.io.ByteArrayOutputStream;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.descriptor.ContainerDescriptor;
import com.avail.descriptor.ImplementationSetDescriptor;
import com.avail.descriptor.MethodSignatureDescriptor;
import com.avail.interpreter.levelOne.L1OperationDispatcher;

/**
 * An {@link L1Operation} is encoded within a {@link
 * CompiledCodeDescriptor.ObjectSlots#NYBBLES nybblecode stream} as an opcode
 * followed by operands.  Opcodes less than 16 are encoded as a single nybble,
 * and the others are represented as the {@link #L1_doExtension extension}
 * nybble followed by the opcode minus 16.  The {@link #L1Implied_Return
 * return} instruction does not actually occur, and is implied immediately after
 * the end of a stream of nybblecodes.
 * <p>
 * The operands are encoded in such a way that very small values occupy a single
 * nybble, but values up to {@link Integer#MAX_VALUE} are supported efficiently.
 * 
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public enum L1Operation
{
	/**
	 * Invoke a method.
	 * <p>
	 * The operand is an index into the current code's {@link
	 * CompiledCodeDescriptor.ObjectSlots#LITERAL_AT_ literals}, which
	 * specifies an {@link ImplementationSetDescriptor implementation set} that
	 * contains a collection of {@link MethodSignatureDescriptor methods} that
	 * may be invoked.  The arguments are expected to already have been pushed.
	 * They are popped from the stack and void is pushed.  When the invoked
	 * method eventually returns, this void entry is replaced with the method's
	 * return value.
	 */
	L1_doCall(0, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doCall();
		}
	},


	/**
	 * Verify that the value on top of the stack has a type that agrees with the
	 * literal {@link TypeDescriptor type} specified in the operand.
	 * <p>
	 * This instruction is usually generated after a {@link #L1_doCall} to
	 * ensure the call's return value agrees with what the method declared.
	 * Fail (i.e., exit the VM) if it does not agree.  Better responses exist,
	 * such as raising a primitive exception.  Something like this will be
	 * implemented in the future.
	 */
	L1_doVerifyType(1, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doVerifyType();
		}
	},


	/**
	 * Push the literal whose index is specified by the operand. 
	 */
	L1_doPushLiteral(2, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLiteral();
		}
	},


	/**
	 * Push a local variable -- not its value, but the variable itself.  This
	 * should be the last use of the variable, so erase it from the continuation
	 * at the same time.
	 * <p>
	 * Clearing the variable keeps the variable's reference count from changing,
	 * so it may stay {@link Descriptor#mutableDescriptor() mutable} if it was
	 * before.
	 * <p>
	 * If an argument or constant is specified then push the value, since there
	 * is no actual {@link ContainerDescriptor variable} to operate on.  Clear
	 * the slot of the continuation reserved for the argument or constant.
	 */
	L1_doPushLastLocal(3, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLastLocal();
		}
	},


	/**
	 * Push a local variable -- not its value, but the variable itself.  If an
	 * argument or constant is specified then push the value, since there is no
	 * actual {@link ContainerDescriptor variable} to operate on.
	 */
	L1_doPushLocal(4, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLocal();
		}
	},


	/**
	 * Push an outer variable, i.e. a variable lexically captured by the current
	 * closure.  This should be the last use of the variable, so clear it from
	 * the closure if the closure is still mutable. 
	 */
	L1_doPushLastOuter(5, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLastOuter();
		}
	},


	L1_doClose(6, L1OperandType.IMMEDIATE, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doClose();
		}
	},


	L1_doSetLocal(7, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doSetLocal();
		}
	},


	L1_doGetLocalClearing(8, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetLocalClearing();
		}
	},


	L1_doPushOuter(9, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushOuter();
		}
	},


	L1_doPop(10)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPop();
		}
	},


	L1_doGetOuterClearing(11, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetOuterClearing();
		}
	},


	L1_doSetOuter(12, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doSetOuter();
		}
	},


	L1_doGetLocal(13, L1OperandType.LOCAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetLocal();
		}
	},


	L1_doMakeList(14, L1OperandType.IMMEDIATE)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doMakeList();
		}
	},


	L1_doExtension(15, L1OperandType.EXTENSION)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doExtension();
		}
	},


	L1Ext_doGetOuter(16, L1OperandType.OUTER)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetOuter();
		}
	},


	L1Ext_doPushLabel(17)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doPushLabel();
		}
	},


	L1Ext_doGetLiteral(18, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetLiteral();
		}
	},


	L1Ext_doSetLiteral(19, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doSetLiteral();
		}
	},


	L1Ext_doSuperCall(20, L1OperandType.LITERAL)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doSuperCall();
		}
	},


	L1Ext_doGetType(21, L1OperandType.IMMEDIATE)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetType();
		}
	},


	L1Ext_doReserved(22)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doReserved();
		}
	},
	

	L1Implied_Return(23)
	{
		@Override
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Implied_doReturn();
		}
		
		@Override
		public void writeTo (ByteArrayOutputStream stream)
		{
			assert false
				: "The implied return instruction should not be output";
		}

	};


	/**
	 * This operation's collection of {@link L1OperandType operand types}. 
	 */
	private final L1OperandType [] operandTypes;

	
	/**
	 * Return this operation's collection of {@link L1OperandType operand
	 * types}.
	 * 
	 * @return The kinds of operands this operation expects.
	 */
	public L1OperandType [] operandTypes ()
	{
		return operandTypes;
	};


	/**
	 * Construct a new {@link L1Operation}.  The expected {@link Enum#ordinal()
	 * ordinal} is passed as a cross-check so that each operation's definition
	 * shows the ordinal.  The rest of the arguments are the {@link
	 * L1OperandType operand types} that this operation expects.
	 *
	 * @param ordinalCheck This operation's ordinal.
	 * @param operandTypes This operation's list of {@link L1OperandType operand
	 *                     types}.
	 */
	L1Operation (
			int ordinalCheck,
			L1OperandType ... operandTypes)
	{
		assert ordinalCheck == ordinal();
		this.operandTypes = operandTypes;
	};

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
	public void writeTo (ByteArrayOutputStream stream)
	{
		int nybble = ordinal();
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
};
