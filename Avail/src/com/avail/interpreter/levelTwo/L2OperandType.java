/**
 * interpreter/levelTwo/L2OperandType.java
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

package com.avail.interpreter.levelTwo;

import com.avail.interpreter.levelTwo.L2OperandTypeDispatcher;


public enum L2OperandType
{
	CONSTANT(false, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doConstant();
		};
	},
	IMMEDIATE(false, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doImmediate();
		};
	},
	PC(false, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doPC();
		};
	},
	PRIMITIVE(false, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doPrimitive();
		};
	},
	SELECTOR(false, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doSelector();
		};
	},
	READ_POINTER(true, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadPointer();
		};
	},
	WRITE_POINTER(false, true)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doWritePointer();
		};
	},
	READWRITE_POINTER(true, true)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadWritePointer();
		};
	},
	READ_INT(true, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadInt();
		};
	},
	WRITE_INT(false, true)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doWriteInt();
		};
	},
	READWRITE_INT(true, true)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadWriteInt();
		};
	},
	READ_VECTOR(true, false)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadVector();
		};
	},
	WRITE_VECTOR(false, true)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doWriteVector();
		};
	},
	READWRITE_VECTOR(true, true)
	{
		@Override
		void dispatch(L2OperandTypeDispatcher dispatcher)
		{
			dispatcher.doReadWriteVector();
		};
	};

	abstract void dispatch(L2OperandTypeDispatcher dispatcher);

	public final boolean isSource;
	public final boolean isDestination;

	L2OperandType (boolean isSource, boolean isDestination)
	{
		this.isSource = isSource;
		this.isDestination = isDestination;
	}

};
