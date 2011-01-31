/**
 * compiler/instruction/AvailPushOuterVariable.java
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

package com.avail.compiler.instruction;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.instruction.AvailVariableAccessNote;
import com.avail.descriptor.ClosureDescriptor;
import com.avail.interpreter.levelOne.L1Operation;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Push an outer (lexically captured) variable.  Not its value, but the
 * variable itself.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailPushOuterVariable extends AvailPushVariable
{

	/**
	 * Construct a new {@link AvailPushOuterVariable}.
	 *
	 * @param variableIndex
	 *        The index of the variable in a {@link ClosureDescriptor closure's}
	 *        captured outer variable.
	 */
	public AvailPushOuterVariable (int variableIndex)
	{
		super(variableIndex);
	}


	@Override
	public void writeNybblesOn (
			final ByteArrayOutputStream aStream)
	{
		if (isLastAccess)
		{
			L1Operation.L1_doPushLastOuter.writeTo(aStream);
		}
		else
		{
			L1Operation.L1_doPushOuter.writeTo(aStream);
		}
		writeIntegerOn(index, aStream);
	}


	/**
	 * The instructions of a block are being iterated over.  Coordinate
	 * optimizations between instructions using localData and outerData, two
	 * {@link List lists} manipulated by overrides of this method.  Treat each
	 * instruction as though it is the last one in the block, and save enough
	 * information in the lists to be able to undo consequences of this
	 * assumption when a later instruction shows it to be unwarranted.
	 * <p>
	 * The data lists are keyed by local or outer index.  Each entry is either
	 * null or a {@link AvailVariableAccessNote}, which keeps track of the
	 * previous time a get or push happened.
	 * <p>
	 * I push an outer variable, which is an outer reference to either a local
	 * or an argument.
	 */
	@Override
	public void fixFlagsUsingLocalDataOuterDataCodeGenerator (
			final List<AvailVariableAccessNote> localData,
			final List<AvailVariableAccessNote> outerData,
			final AvailCodeGenerator codeGenerator)
	{
		AvailVariableAccessNote note = outerData.get(index - 1);
		if (note == null)
		{
			note = new AvailVariableAccessNote();
			outerData.set(index - 1, note);
		}
		// If there was a push before this one, set its isLastAccess to false,
		// as the receiver is clearly a later use.
		final AvailPushVariable previousPush = note.previousPush();
		if (previousPush != null)
		{
			previousPush.isLastAccess(false);
		}
		isLastAccess(true);
		// If there was a get before this push, make sure its canClear flag is
		// false (the variable escapes).
		final AvailGetVariable previousGet = note.previousGet();
		if (previousGet != null)
		{
			previousGet.canClear(false);
		}
		note.previousPush(this);
	}


	@Override
	public boolean isOuterUse ()
	{
		return true;
	}
}
