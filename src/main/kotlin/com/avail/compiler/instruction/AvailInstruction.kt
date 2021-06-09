/*
 * AvailInstruction.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.compiler.instruction

import com.avail.compiler.AvailCodeGenerator
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.io.NybbleOutputStream

/**
 * `AvailInstruction` implements an abstract instruction set that doesn't have
 * to agree precisely with the actual implemented Level One nybblecode
 * instruction set.  The mapping is approximately one-to-one, however, other
 * than providing the ability to defer certain analyses, such as last-use of
 * variables, until after selection of AvailInstructions.  This allows the
 * analysis to simply mark the already abstractly-emitted instructions with
 * information that affects the precise nybblecodes that will ultimately be
 * emitted.
 *
 * @property relevantTokens
 *   The tuple of tokens that contributed to producing this instruction.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct an instruction.  Capture the tokens that contributed to it.
 *
 * @param relevantTokens
 *   The [A_Tuple] of [A_Token]s that are associated with this instruction.
 */
abstract class AvailInstruction constructor(var relevantTokens: A_Tuple)
{
	/** `true` iff this instruction is a use of an outer variable. */
	open val isOuterUse: Boolean
		get() = false

	/**
	 * Write nybbles representing this instruction to the [NybbleOutputStream].
	 *
	 * @param aStream
	 *   Where to write the nybbles.
	 */
	abstract fun writeNybblesOn(aStream: NybbleOutputStream)

	/**
	 * The instructions of a block are being iterated over.  Coordinate
	 * optimizations between instructions using localData and outerData, two
	 * [lists][List] manipulated by overrides of this method.  Treat each
	 * instruction as though it is the last one in the block, and save enough
	 * information in the lists to be able to undo consequences of this
	 * assumption when a later instruction shows it to be unwarranted.
	 *
	 * The data lists are keyed by local or outer index.  Each entry is an
	 * [AvailVariableAccessNote], which keeps track of the immediately previous
	 * use of the variable.
	 *
	 * @param localData
	 *   A list of [AvailVariableAccessNote]s, one for each local variable.
	 * @param outerData
	 *   A list of [AvailVariableAccessNote]s, one for each outer variable.
	 * @param codeGenerator
	 *   The code generator.
	 */
	open fun fixUsageFlags(
		localData: MutableList<AvailVariableAccessNote?>,
		outerData: MutableList<AvailVariableAccessNote?>,
		codeGenerator: AvailCodeGenerator)
	{
		// Do nothing here in the general case.
	}

	/**
	 * Answer which line number to say that this instruction occurs on.  Use the
	 * [relevantTokens] as an approximation, but subclasses might be able to be
	 * more precise.  Answer -1 if this instruction doesn't seem to have a
	 * location in the source associated with it.
	 *
	 * @return
	 *   The line number for this instruction, or `-1`.
	 */
	val lineNumber: Int
		get()
		{
			return when {
				relevantTokens.tupleSize() == 0 -> -1
				else -> relevantTokens.tupleAt(1).lineNumber()
			}
		}

	companion object
	{
		/**
		 * Write a nybble-coded [Int] in a variable-sized format to the
		 * [NybbleOutputStream].  Small values take only one nybble, and we can
		 * represent any int up to [Integer.MAX_VALUE].
		 *
		 * @param anInteger
		 *   The integer to write.
		 * @param aStream
		 *   The stream on which to write the integer.
		 */
		@JvmStatic
		fun writeIntegerOn(anInteger: Int, aStream: NybbleOutputStream)
		{
			assert(anInteger >= 0) { "only positive integers" }
			when
			{
				anInteger < 10 ->
				{
					aStream.write(anInteger)
				}
				anInteger < 0x3A ->
				{
					aStream.write((anInteger - 10 + 0xA0).ushr(4))
					aStream.write(anInteger - 10 + 0xA0 and 15)
				}
				anInteger < 0x13A ->
				{
					aStream.write(13)
					aStream.write((anInteger - 0x3A).ushr(4))
					aStream.write(anInteger - 0x3A and 15)
				}
				anInteger < 0x10000 ->
				{
					aStream.write(14)
					aStream.write(anInteger.ushr(12))
					aStream.write(anInteger.ushr(8) and 15)
					aStream.write(anInteger.ushr(4) and 15)
					aStream.write(anInteger and 15)
				}
				else ->
				{
					// Treat it as an unsigned int.  i<0 case was already handled.
					aStream.write(15)
					aStream.write(anInteger.ushr(28))
					aStream.write(anInteger.ushr(24) and 15)
					aStream.write(anInteger.ushr(20) and 15)
					aStream.write(anInteger.ushr(16) and 15)
					aStream.write(anInteger.ushr(12) and 15)
					aStream.write(anInteger.ushr(8) and 15)
					aStream.write(anInteger.ushr(4) and 15)
					aStream.write(anInteger and 15)
				}
			}
		}
	}
}
