/**
 * AvailInstruction.java
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

package com.avail.compiler.instruction;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.instruction.AvailVariableAccessNote;
import java.io.ByteArrayOutputStream;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

/**
 * {@code AvailInstruction} implements an abstract instruction set that doesn't
 * have to agree precisely with the actual implemented Level One nybblecode
 * instruction set.  The mapping is approximately one-to-one, however, other
 * than providing the ability to defer certain analyses, such as last-use of
 * variables, until after selection of AvailInstructions.  This allows the
 * analysis to simply mark the already abstractly-emitted instructions with
 * information that affects the precise nybblecodes that will ultimately be
 * emitted.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AvailInstruction
{

	/**
	 * Write a nybble-coded int in a variable-sized format to the {@linkplain
	 * ByteArrayOutputStream stream}.  Small values take only one nybble,
	 * and we can represent any int up to {@link Integer#MAX_VALUE}.
	 *
	 * @param anInteger The integer to write.
	 * @param aStream The stream on which to write the integer.
	 */
	public void writeIntegerOn (
			final int anInteger,
			final ByteArrayOutputStream aStream)
	{
		if (anInteger < 0)
		{
			error("Only positive integers, please");
			return;
		}
		if (anInteger < 10)
		{
			aStream.write(anInteger);
			return;
		}
		if (anInteger < 58)
		{
			aStream.write((anInteger + 150) >>> 4);
			aStream.write((anInteger + 150) & 15);
			return;
		}
		if (anInteger < 0x13A)
		{
			aStream.write(13);
			aStream.write((anInteger - 58) >>> 4);
			aStream.write((anInteger - 58) & 15);
			return;
		}
		if (anInteger < 0x10000)
		{
			aStream.write(14);
			aStream.write(anInteger >>> 12);
			aStream.write((anInteger >>> 8) & 15);
			aStream.write((anInteger >>> 4) & 15);
			aStream.write(anInteger & 15);
			return;
		}
		if (anInteger < 0x100000000L)
		{
			aStream.write(15);
			aStream.write(anInteger >>> 28);
			aStream.write((anInteger >>> 24) & 15);
			aStream.write((anInteger >>> 20) & 15);
			aStream.write((anInteger >>> 16) & 15);
			aStream.write((anInteger >>> 12) & 15);
			aStream.write((anInteger >>> 8) & 15);
			aStream.write((anInteger >>> 4) & 15);
			aStream.write(anInteger & 15);
			return;
		}
		error("Integer is out of range");
		return;
	}

	/**
	 * Write nybbles representing this instruction to the {@linkplain
	 * ByteArrayOutputStream stream}.
	 *
	 * @param aStream Where to write the nybbles.
	 */
	public abstract void writeNybblesOn (
		final ByteArrayOutputStream aStream);


	/**
	 * The instructions of a block are being iterated over.  Coordinate
	 * optimizations between instructions using localData and outerData, two
	 * {@linkplain List lists} manipulated by overrides of this method.  Treat
	 * each instruction as though it is the last one in the block, and save
	 * enough information in the lists to be able to undo consequences of this
	 * assumption when a later instruction shows it to be unwarranted.
	 *
	 * <p>
	 * The data lists are keyed by local or outer index.  Each entry is an
	 * {@link AvailVariableAccessNote}, which keeps track of the immediately
	 * previous use of the variable.
	 * </p>
	 *
	 * @param localData A list of {@linkplain AvailVariableAccessNote}s, one for
	 *                  each local variable.
	 * @param outerData A list of {@linkplain AvailVariableAccessNote}s, one for
	 *                  each outer variable.
	 * @param codeGenerator The code generator.
	 */
	public void fixFlagsUsingLocalDataOuterDataCodeGenerator (
			final List<AvailVariableAccessNote> localData,
			final List<AvailVariableAccessNote> outerData,
			final AvailCodeGenerator codeGenerator)
	{
		// Do nothing here in the general case.
	}


	/**
	 * Answer whether this instruction is a use of a local variable.
	 *
	 * @return False for this class, possibly true in a subclass.
	 */
	public boolean isLocalUse ()
	{
		return false;
	}

	/**
	 * Answer whether this instruction is a use of an outer variable.
	 *
	 * @return False for this class, possibly true in a subclass.
	 */
	public boolean isOuterUse ()
	{
		return false;
	}

	/**
	 * Answer whether this instruction is a use of a label variable.
	 *
	 * @return False for this class, possibly true in a subclass.
	 */
	public boolean isPushLabel ()
	{
		return false;
	}
}
