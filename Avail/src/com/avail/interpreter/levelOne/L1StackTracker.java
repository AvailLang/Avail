/**
 * interpreter/levelOne/L1StackTracker.java
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

import static com.avail.descriptor.AvailObject.error;
import static java.lang.Math.max;
import com.avail.descriptor.AvailObject;

abstract class L1StackTracker implements L1OperationDispatcher
{
	int [] currentOperands = null;

	int currentDepth = 0;

	int currentDepth ()
	{
		return currentDepth;
	}

	int maxDepth = 0;

	int maxDepth ()
	{
		return maxDepth;
	}

	boolean reachable = true;

	boolean reachable ()
	{
		return reachable;
	}

	void track (final L1Instruction instruction)
	{
		assert reachable;
		assert currentDepth >= 0;
		assert maxDepth >= currentDepth;
		currentOperands = instruction.operands();
		instruction.operation().dispatch(this);
		currentOperands = null;
		assert currentDepth >= 0;
		maxDepth = max(maxDepth, currentDepth);
	}

	abstract AvailObject literalAt (int literalIndex);


	// Operation dispatching

	@Override
	public void L1_doCall ()
	{
		currentDepth += 1 - literalAt(currentOperands[0]).numArgs();
	}

	@Override
	public void L1_doPushLiteral ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushLocal ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doClose ()
	{
		currentDepth += 1 - currentOperands[0];
	}

	@Override
	public void L1_doSetLocal ()
	{
		currentDepth--;
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushOuter ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPop ()
	{
		currentDepth--;
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doSetOuter ()
	{
		currentDepth--;
	}

	@Override
	public void L1_doGetLocal ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doMakeTuple ()
	{
		currentDepth += 1 - currentOperands[0];
	}

	@Override
	public void L1_doGetOuter ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doExtension ()
	{
		error("The extension nybblecode should not be dispatched.");
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		currentDepth++;
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		currentDepth++;
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		currentDepth--;
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		currentDepth += 1 - literalAt(currentOperands[0]).numArgs() * 2;
	}

	@Override
	public void L1Ext_doGetType ()
	{
		currentDepth++;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		currentDepth++;
	}

	@Override
	public void L1Ext_doReserved ()
	{
		error("Reserved nybblecode");
	}

	@Override
	public void L1Implied_doReturn ()
	{
		assert currentDepth == 1;
		currentDepth = 0;
		reachable = false;
	}
}