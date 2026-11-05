/*
 * BitOperationFolderTest.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.test.optimizer

import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Add
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.And
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Div
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Max
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Min
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Mod
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Mul
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Or
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.SelectBit
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Shl
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Shr
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Sub
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Ushr
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Xor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Check each [BitOperation]'s `long` folder, applied to sign-extended ints.
 * [Add], [Sub], and [Mul] produce the exact result, and the other operations
 * produce the sign-extended result of the 32-bit operation.  The folder must
 * agree with the JVM code that the operation generates.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class BitOperationFolderTest
{
	/** Boundary values, plus a deterministic random sample. */
	private val samples: List<Int> = listOf(
		Int.MIN_VALUE, Int.MIN_VALUE + 1, -65536, -2, -1, 0, 1, 2, 31, 32,
		65536, Int.MAX_VALUE - 1, Int.MAX_VALUE
	) + Random(1).let { r -> List(40) { r.nextInt() } }

	/**
	 * Check one operation over pairs of inputs.
	 *
	 * @param operation
	 *   The [BitOperation] whose folder is checked.
	 * @param bs
	 *   The second operands to try.
	 * @param precondition
	 *   Which pairs the operation is defined for.
	 * @param expected
	 *   The 32-bit operation.
	 */
	private fun check(
		operation: BitOperation,
		bs: List<Int> = samples,
		precondition: (Int, Int) -> Boolean = { _, _ -> true },
		expected: (Int, Int) -> Long)
	{
		for (a in samples)
		{
			for (b in bs)
			{
				if (!precondition(a, b)) continue
				assertEquals(
					expected(a, b),
					operation.folder(a.toLong(), b.toLong()),
					"$operation($a, $b)")
			}
		}
	}

	@Test
	fun testFolders()
	{
		val shifts = (0 .. 40).toList()
		val positive: (Int, Int) -> Boolean = { a, b -> a >= 0 && b > 0 }
		// The exact operations.  Products of ints always fit in a long.
		check(Add) { a, b -> a.toLong() + b }
		check(Sub) { a, b -> a.toLong() - b }
		check(Mul) { a, b -> a.toLong() * b }
		// The 32-bit operations.
		check(And) { a, b -> (a and b).toLong() }
		check(Or) { a, b -> (a or b).toLong() }
		check(Xor) { a, b -> (a xor b).toLong() }
		check(Div, precondition = positive) { a, b -> (a / b).toLong() }
		check(Mod, precondition = positive) { a, b -> (a % b).toLong() }
		check(Ushr, shifts) { a, b -> (a ushr b).toLong() }
		check(Shr, shifts) { a, b -> (a shr b).toLong() }
		check(Shl, shifts) { a, b -> (a shl b).toLong() }
		check(Max) { a, b -> max(a, b).toLong() }
		check(Min) { a, b -> min(a, b).toLong() }
		check(SelectBit, (0 .. 100).toList()) { a, b ->
			((a ushr min(b, 31)) and 1).toLong()
		}
	}
}
