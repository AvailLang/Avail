/*
 * L2IntRegister.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
*
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.interpreter.levelTwo.register

import com.avail.optimizer.L2Generator
import com.avail.optimizer.reoptimizer.L2Inliner

/**
 * `L2IntRegister` models the conceptual usage of a register that can store a
 * machine integer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `L2IntRegister`.
 *
 * @param debugValue
 *   A value used to distinguish the new instance visually during debugging of
 *   L2 translations.
 */
class L2IntRegister constructor(debugValue: Int) : L2Register(debugValue)
{
	override fun registerKind(): RegisterKind = RegisterKind.INTEGER

	override fun copyForTranslator(generator: L2Generator): L2IntRegister =
		L2IntRegister(generator.nextUnique())

	override fun copyAfterColoring(): L2IntRegister
	{
		val result = L2IntRegister(finalIndex())
		result.setFinalIndex(finalIndex())
		return result
	}

	override fun copyForInliner(inliner: L2Inliner): L2IntRegister =
		L2IntRegister(inliner.nextUnique())
}
