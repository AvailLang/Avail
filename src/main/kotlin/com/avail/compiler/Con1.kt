/*
 * Con1.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.compiler

import com.avail.compiler.AvailCompiler.PartialSubexpressionList

/**
 * This is a subtype of function, but it also tracks the
 * [PartialSubexpressionList] that describes the nesting of parse expressions
 * that led to this particular continuation.  A function is also supplied to the
 * constructor.
 *
 * @property superexpressions
 *   A [PartialSubexpressionList] containing all enclosing incomplete
 *   expressions currently being parsed along this history, or `null` if it is
 *   root `PartialSubexpressionList`.
 * @property innerContinuation
 *   A function to invoke.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Con1`.
 *
 * @param superexpressions
 *   The enclosing partially-parsed expressions.
 * @param innerContinuation
 *   A function that will be invoked.
 */
internal class Con1 constructor(
	val superexpressions: PartialSubexpressionList?,
	private val innerContinuation: (CompilerSolution)->Unit)
: (CompilerSolution)->Unit
{
	override fun invoke(solution: CompilerSolution)
	{
		innerContinuation(solution)
	}
}
