/*
 * L1StackTracker.kt
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

package com.avail.interpreter.levelOne

import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.methods.A_Method.Companion.numArgs
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.error
import kotlin.math.max

/**
 * An `L1StackTracker` verifies the integrity of a sequence of
 * [operations][L1Operation] and [operands][L1OperandType], and calculates how
 * big a stack will be necessary.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
internal abstract class L1StackTracker : L1OperationDispatcher
{
	/** The operands of the [L1Operation] being traced. */
	private var currentOperands: IntArray? = null

	/**
	 * The number of items that will have been pushed on the stack at this point
	 * in the code.
	 */
	private var currentDepth = 0

	/** The maximum stack depth needed so far. */
	internal var maxDepth = 0
		private set

	/**
	 * Record the effect of an L1 instruction.
	 *
	 * @param operation
	 *   The [L1Operation].
	 * @param operands
	 *   The operation's [Int] operands.
	 */
	fun track(operation: L1Operation, vararg operands: Int)
	{
		assert(currentDepth >= 0)
		assert(maxDepth >= currentDepth)
		currentOperands = operands
		operation.dispatch(this)
		currentOperands = null
		assert(currentDepth >= 0)
		maxDepth = max(maxDepth, currentDepth)
	}

	/**
	 * Answer the literal at the specified index.
	 *
	 * @param literalIndex
	 *   The literal's index.
	 * @return
	 *   The literal [AvailObject].
	 */
	internal abstract fun literalAt(literalIndex: Int): AvailObject

	override fun L1_doCall()
	{
		val bundle = literalAt(currentOperands!![0])
		currentDepth += 1 - bundle.bundleMethod.numArgs
	}

	override fun L1_doPushLiteral()
	{
		currentDepth++
	}

	override fun L1_doPushLastLocal()
	{
		currentDepth++
	}

	override fun L1_doPushLocal()
	{
		currentDepth++
	}

	override fun L1_doPushLastOuter()
	{
		currentDepth++
	}

	override fun L1_doClose()
	{
		currentDepth += 1 - currentOperands!![0]
	}

	override fun L1_doSetLocal()
	{
		currentDepth--
	}

	override fun L1_doGetLocalClearing()
	{
		currentDepth++
	}

	override fun L1_doPushOuter()
	{
		currentDepth++
	}

	override fun L1_doPop()
	{
		currentDepth--
	}

	override fun L1_doGetOuterClearing()
	{
		currentDepth++
	}

	override fun L1_doSetOuter()
	{
		currentDepth--
	}

	override fun L1_doGetLocal()
	{
		currentDepth++
	}

	override fun L1_doMakeTuple()
	{
		currentDepth += 1 - currentOperands!![0]
	}

	override fun L1_doGetOuter()
	{
		currentDepth++
	}

	override fun L1_doExtension()
	{
		error("The extension nybblecode should not be dispatched.")
	}

	override fun L1Ext_doPushLabel()
	{
		currentDepth++
	}

	override fun L1Ext_doGetLiteral()
	{
		currentDepth++
	}

	override fun L1Ext_doSetLiteral()
	{
		currentDepth--
	}

	override fun L1Ext_doDuplicate()
	{
		currentDepth++
	}

	override fun L1Ext_doSetSlot()
	{
		currentDepth--
	}

	override fun L1Ext_doPermute()
	{
		// No change.
	}

	override fun L1Ext_doSuperCall()
	{
		val bundle = literalAt(currentOperands!![0])
		currentDepth += 1 - bundle.bundleMethod.numArgs
	}
}
