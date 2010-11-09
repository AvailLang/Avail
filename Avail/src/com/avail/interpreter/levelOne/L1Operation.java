/**
 * interpreter/levelOne/L1Operation.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this *   list of conditions and the following disclaimer.
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

import com.avail.interpreter.levelOne.L1OperationDispatcher;

public enum L1Operation
{
	L1_doCall(0, L1OperandType.LITERAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doCall();
		}
	},


	L1_doVerifyType(1, L1OperandType.LITERAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doVerifyType();
		}
	},


	L1_doReturn(2)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doReturn();
		}
	},


	L1_doPushLiteral(3, L1OperandType.LITERAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLiteral();
		}
	},


	L1_doPushLastLocal(4, L1OperandType.LOCAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLastLocal();
		}
	},


	L1_doPushLocal(5, L1OperandType.LOCAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLocal();
		}
	},


	L1_doPushLastOuter(6, L1OperandType.OUTER)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushLastOuter();
		}
	},


	L1_doClose(7, L1OperandType.IMMEDIATE, L1OperandType.LITERAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doClose();
		}
	},


	L1_doSetLocal(8, L1OperandType.LOCAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doSetLocal();
		}
	},


	L1_doGetLocalClearing(9, L1OperandType.LOCAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetLocalClearing();
		}
	},


	L1_doPushOuter(10, L1OperandType.OUTER)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPushOuter();
		}
	},


	L1_doPop(11)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doPop();
		}
	},


	L1_doGetOuterClearing(12, L1OperandType.OUTER)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetOuterClearing();
		}
	},


	L1_doSetOuter(13, L1OperandType.OUTER)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doSetOuter();
		}
	},


	L1_doGetLocal(14, L1OperandType.LOCAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doGetLocal();
		}
	},


	L1_doExtension(15, L1OperandType.EXTENSION)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1_doExtension();
		}
	},


	L1Ext_doGetOuter(16, L1OperandType.OUTER)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetOuter();
		}
	},


	L1Ext_doMakeList(17, L1OperandType.IMMEDIATE)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doMakeList();
		}
	},


	L1Ext_doPushLabel(18)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doPushLabel();
		}
	},


	L1Ext_doGetLiteral(19, L1OperandType.LITERAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetLiteral();
		}
	},


	L1Ext_doSetLiteral(20, L1OperandType.LITERAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doSetLiteral();
		}
	},


	L1Ext_doSuperCall(21, L1OperandType.LITERAL)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doSuperCall();
		}
	},


	L1Ext_doGetType(22, L1OperandType.IMMEDIATE)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doGetType();
		}
	},


	L1Ext_doReserved(23)
	{
		public void dispatch(L1OperationDispatcher operationDispatcher)
		{
			operationDispatcher.L1Ext_doReserved();
		}
	};

	private final L1OperandType [] operandTypes;

	public L1OperandType [] operandTypes ()
	{
		return operandTypes;
	};

	// Constructors
	L1Operation (
			int ordinalCheck,
			L1OperandType ... operandTypes)
	{
		assert ordinalCheck == ordinal();
		this.operandTypes = operandTypes;
	};

	public abstract void dispatch (L1OperationDispatcher operationDispatcher);
};
