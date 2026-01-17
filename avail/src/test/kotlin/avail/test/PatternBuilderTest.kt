/*
 * PatternBuilderTest.kt
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
package avail.test

import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.interpreter.primitive.general.P_Hash
import avail.interpreter.primitive.integers.P_BitShiftRight
import avail.interpreter.primitive.integers.P_BitwiseAnd
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticValue.Companion.constant
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation
import avail.optimizer.values.PatternBuilder
import avail.optimizer.values.PatternBuilder.Companion.pattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Basic functionality test of [PatternBuilder]'s implementations.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class PatternBuilderTest
{
	@Test
	fun example()
	{
		val value = primitiveInvocation(
			P_BitwiseAnd,
			listOf(
				primitiveInvocation(
					P_BitShiftRight,
					listOf(
						primitiveInvocation(
							P_Hash,
							listOf(constant(trueObject))),
						constant(fromInt(20)))),
				constant(fromInt(31))))
		val pattern = pattern {
			P_BitwiseAnd(
				P_BitShiftRight(
					P_Hash(
						capture(0)
					),
					capture(1)
				),
				capture(2)
			)
		}
		val manifest = L2ValueManifest(BySemanticValue)
		manifest
		val matches = mutableListOf<List<L2SemanticBoxedValue>>()
		pattern.matchForEach(value) { matches.add(it.toList()) }
		val (a, b, c) = matches[0]
		assertEquals(a, constant(trueObject))
		assertEquals(b, constant(fromInt(20)))
		assertEquals(c, constant(fromInt(31)))
	}
}
