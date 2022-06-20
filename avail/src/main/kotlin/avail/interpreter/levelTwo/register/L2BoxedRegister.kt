/*
 * L2BoxedRegister.kt
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
package avail.interpreter.levelTwo.register

import avail.descriptor.representation.AvailObject
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.*
import avail.optimizer.L2Generator
import avail.optimizer.reoptimizer.L2Regenerator

/**
 * `L2BoxedRegister` models the conceptual usage of a register that can store an
 * [AvailObject].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `L2BoxedRegister`.
 *
 * @param debugValue
 *   A value used to distinguish the new instance visually during debugging of
 *   L2 translations.
 */
class L2BoxedRegister constructor(debugValue: Int) : L2Register(debugValue)
{
	override val registerKind get() = BOXED_KIND

	override fun copyForTranslator(generator: L2Generator): L2BoxedRegister =
		L2BoxedRegister(generator.nextUnique())

	override fun copyAfterColoring(): L2BoxedRegister
	{
		val result = L2BoxedRegister(finalIndex())
		result.setFinalIndex(finalIndex())
		return result
	}

	override fun copyForRegenerator(regenerator: L2Regenerator) =
		L2BoxedRegister(regenerator.nextUnique())
}
