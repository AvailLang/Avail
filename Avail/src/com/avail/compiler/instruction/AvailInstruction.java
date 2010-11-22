/**
 * compiler/instruction/AvailInstruction.java
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
import java.io.ByteArrayOutputStream;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public class AvailInstruction
{


	// initialize-release

	public void initialize ()
	{
		//  Do nothing


	}



	// nybblecodes

	public int sizeInNybbles ()
	{
		//  Answer how many nybbles writeNybblesOn: will take.

		ByteArrayOutputStream out = new ByteArrayOutputStream(8);
		writeNybblesOn(out);
		return out.size();
	}

	public void writeIntegerOn (
			final int anInteger,
			final ByteArrayOutputStream aStream)
	{
		//  Write a nybble-coded integer (in variable-sized format) to the stream (a WriteStream on a ByteArray).

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
			aStream.write((((anInteger - 10) >>> 4) + 10));
			aStream.write(((anInteger - 10) & 15));
			return;
		}
		if (anInteger < 0x13A)
		{
			aStream.write(13);
			aStream.write(((anInteger - 58) >>> 4));
			aStream.write(((anInteger - 58) & 15));
			return;
		}
		if (anInteger < 0x10000)
		{
			aStream.write(14);
			aStream.write(anInteger >>> 12);
			aStream.write(((anInteger >>> 8) & 15));
			aStream.write(((anInteger >>> 4) & 15));
			aStream.write(anInteger & 15);
			return;
		}
		if (anInteger < 0x100000000L)
		{
			aStream.write(15);
			aStream.write(anInteger >>> 28);
			aStream.write(((anInteger >>> 24) & 15));
			aStream.write(((anInteger >>> 20) & 15));
			aStream.write(((anInteger >>> 16) & 15));
			aStream.write(((anInteger >>> 12) & 15));
			aStream.write(((anInteger >>> 8) & 15));
			aStream.write(((anInteger >>> 4) & 15));
			aStream.write(anInteger & 15);
			return;
		}
		error("Integer is out of range");
		return;
	}

	public void writeNybblesOn (
			final ByteArrayOutputStream aStream)
	{
		//  Write nybbles to the stream (a WriteStream on a ByteArray).

		error("Subclass responsibility: writeNybblesOn: in Avail.AvailInstruction");
		return;
	}



	// optimization

	public void fixFlagsUsingLocalDataOuterDataCodeGenerator (
			final List<AvailVariableAccessNote> localData,
			final List<AvailVariableAccessNote> outerData,
			final AvailCodeGenerator codeGenerator)
	{
		//  The instructions of a block are being iterated over.  Coordinate optimizations
		//  between instructions using localData and outerData, two Arrays manipulated by
		//  overrides of this method.  Treat each instruction as though it is the last one in the
		//  block, and save enough information in the Arrays to be able to undo consequences
		//  of this assumption when a later instruction shows it to be unwarranted.
		//
		//  The data Arrays are keyed by local or outer index.  Each entry is either nil or a
		//  Dictionary.  The Dictionary has the (optional) keys #previousGet and #previousPush,
		//  and the values are the previously encountered instructions.


	}



	// testing

	public boolean isLocalUse ()
	{
		return false;
	}

	public boolean isOuterUse ()
	{
		return false;
	}

	public boolean isPushLabel ()
	{
		return false;
	}



	// Nybble encodings

	public static byte callNybble ()
	{
		return 0;
	}

	public static byte closeNybble ()
	{
		return 7;
	}

	public static byte extensionNybble ()
	{
		return 15;
	}

	public static byte getLocalClearingNybble ()
	{
		return 9;
	}

	public static byte getLocalNybble ()
	{
		return 14;
	}

	public static byte getOuterClearingNybble ()
	{
		return 12;
	}

	public static byte popNybble ()
	{
		return 11;
	}

	public static byte pushLastLocalNybble ()
	{
		return 4;
	}

	public static byte pushLastOuterNybble ()
	{
		return 6;
	}

	public static byte pushLiteralNybble ()
	{
		return 3;
	}

	public static byte pushLocalNybble ()
	{
		return 5;
	}

	public static byte pushOuterNybble ()
	{
		return 10;
	}

	public static byte setLocalNybble ()
	{
		return 8;
	}

	public static byte setOuterNybble ()
	{
		return 13;
	}

	public static byte verifyTypeNybble ()
	{
		return 1;
	}

	public static byte getLiteralExtendedNybble ()
	{
		return 3;
	}

	public static byte getOuterExtendedNybble ()
	{
		return 0;
	}

	public static byte getTypeExtendedNybble ()
	{
		return 6;
	}

	public static byte makeListExtendedNybble ()
	{
		return 1;
	}

	public static byte pushLabelExtendedNybble ()
	{
		return 2;
	}

	public static byte setLiteralExtendedNybble ()
	{
		return 4;
	}

	public static byte superCallExtendedNybble ()
	{
		return 5;
	}



}
