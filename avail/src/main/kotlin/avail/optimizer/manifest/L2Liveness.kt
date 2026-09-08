/*
 * L2LivenessInformation.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.optimizer.manifest

import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2Synonym.Companion.appendSemanticValues
import avail.optimizer.values.L2SemanticValue

/**
 * An [L2Liveness] tracks liveness information at one edge of the
 * [L2ControlFlowGraph].
 */
class L2Liveness()
{
	/**
	 * Given a collection of [L2Liveness] instances indicating the liveness on
	 * edges leading out of a common basic block, combine that information to
	 * represent what's known just before the divergence.
	 *
	 * The "always" information is an intersection of each input's "always"
	 * information.  The "sometimes" information is the union of each input's
	 * "sometimes" information *and* each input's "always" inputs, minus the
	 * output's "always" information.
	 *
	 * @param successorLivenesses
	 *   The [L2Liveness] entries present along outbound edges from the same
	 *   basic block.
	 */
	constructor(successorLivenesses: Iterable<L2Liveness>): this()
	{
		successorLivenesses
			.map(L2Liveness::alwaysLiveInRegisters)
			.reduceOrNull(Set<L2Register<*>>::intersect)
			?.toCollection(alwaysLiveInRegisters)
		successorLivenesses.forEach {
			sometimesLiveInRegisters.addAll(it.sometimesLiveInRegisters)
			sometimesLiveInRegisters.addAll(it.alwaysLiveInRegisters)
		}
		sometimesLiveInRegisters.removeAll(alwaysLiveInRegisters)

		successorLivenesses
			.map(L2Liveness::alwaysLiveInSemanticValues)
			.reduceOrNull(Set<L2SemanticValue>::intersect)
			?.toCollection(alwaysLiveInSemanticValues)
		successorLivenesses.forEach {
			sometimesLiveInSemanticValues.addAll(
				it.sometimesLiveInSemanticValues)
			sometimesLiveInSemanticValues.addAll(it.alwaysLiveInSemanticValues)
		}
		sometimesLiveInSemanticValues.removeAll(alwaysLiveInSemanticValues)
	}

	/**
	 * The [Set] of every [L2Register] that is written in all pasts, and is
	 * consumed along all future paths after the start of this block.
	 *
	 * This should be disjoint from [sometimesLiveInRegisters].
	 */
	val alwaysLiveInRegisters: Set<L2Register<*>>
		field = mutableSetOf()

	/**
	 * The [Set] of every [L2Register] that is written in all pasts, and is
	 * consumed along *some but not all* future paths.
	 *
	 * This should be disjoint from [alwaysLiveInRegisters].
	 */
	val sometimesLiveInRegisters: Set<L2Register<*>>
		field = mutableSetOf()

	/**
	 * An immutable set containing the always-in-and sometimes-in registers.
	 */
	val registers: Set<L2Register<*>>
		get() = alwaysLiveInRegisters + sometimesLiveInRegisters

	/**
	 * The [Set] of every [L2SemanticValue] that is written in all pasts, and is
	 * consumed along all future paths after the start of this block.
	 *
	 * This should be disjoint from [sometimesLiveInSemanticValues].
	 */
	val alwaysLiveInSemanticValues: Set<L2SemanticValue>
		field = mutableSetOf()

	/**
	 * The [Set] of every [L2SemanticValue] that is written in all pasts, and is
	 * consumed along *some but not all* future paths.
	 *
	 * This should be disjoint from [alwaysLiveInSemanticValues].
	 */
	val sometimesLiveInSemanticValues: Set<L2SemanticValue>
		field = mutableSetOf()

	/**
	 * An immutable set containing the always-in-and sometimes-in semontic
	 * values.
	 */
	val semanticvalues: Set<L2SemanticValue>
		get() = alwaysLiveInSemanticValues + sometimesLiveInSemanticValues

	/**
	 * Answer `true` if this contains no live registers or semantic values,
	 * otherwise `false`.
	 *
	 * @return
	 *   Whether the receiver indicates this position in the code has no
	 *   requirements of prior instructions.
	 */
	fun isEmpty(): Boolean =
		sometimesLiveInRegisters.isEmpty()
			&& alwaysLiveInRegisters.isEmpty()
			&& sometimesLiveInSemanticValues.isEmpty()
			&& alwaysLiveInSemanticValues.isEmpty()

	/**
	 * Produce a very brief summary of my content.
	 */
	fun shortSummary(): String
	{
		val regs =
			(alwaysLiveInRegisters + sometimesLiveInRegisters).joinToString("&")
		val values = buildString {
			appendSemanticValues(
				alwaysLiveInSemanticValues + sometimesLiveInSemanticValues,
				canWrap = false)
		}
		return when
		{
			regs.isEmpty() && values.isEmpty() -> "(nothing live)"
			regs.isEmpty() -> values
			values.isEmpty() -> regs
			else -> "$regs + $values"
		}
	}

	/**
	 * Add the register as always-live, ensuring it's also removed from the
	 * sometimes-live.
	 */
	fun add(register: L2Register<*>)
	{
		alwaysLiveInRegisters.add(register)
		sometimesLiveInRegisters.remove(register)
	}

	/**
	 * Add the semantic value as always-live, ensuring it's also removed from
	 * the sometimes-live.
	 */
	fun add(value: L2SemanticValue)
	{
		alwaysLiveInSemanticValues.add(value)
		sometimesLiveInSemanticValues.remove(value)
	}

	/**
	 * Remove the register from always-live or sometimes-live if present.
	 */
	fun remove(register: L2Register<*>)
	{
		alwaysLiveInRegisters.remove(register)
		sometimesLiveInRegisters.remove(register)
	}

	/**
	 * Remove the semantic values from always-live or sometimes-live if present.
	 */
	fun removeAll(values: Iterable<L2SemanticValue>)
	{
		alwaysLiveInSemanticValues.removeAll(values)
		sometimesLiveInSemanticValues.removeAll(values)
	}
}
