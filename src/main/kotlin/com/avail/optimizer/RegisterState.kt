/*
 * RegisterState.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.optimizer

import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.types.A_Type
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.utility.PrefixSharingList
import java.util.*

/**
 * This class maintains information about one [L2Register] on behalf of a
 * [RegisterSet] held by an [L2Instruction] inside an [L2Chunk] constructed by
 * the [L2Generator].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class RegisterState
{
	/**
	 * The exact value in this register.  If the exact value is not known, this
	 * field is `null`.
	 */
	private var constant: AvailObject? = null

	/** The type of value in this register.  */
	private var type: A_Type? = null

	/**
	 * The list of other registers that are known to have the same value (if
	 * any).  These occur in the order in which the registers acquired the
	 * common value.
	 *
	 * The inverse map is kept in [invertedOrigins], to more efficiently
	 * disconnect this information.
	 */
	private var origins = mutableListOf<L2Register>()

	/**
	 * The inverse of [origins].  For each key, the value is the collection of
	 * registers that this value has been copied into (and not yet been
	 * overwritten).
	 */
	private var invertedOrigins = mutableListOf<L2Register>()

	/**
	 * The [Set] of [L2Instruction]s that may have provided the current value in
	 * that register.  There may be more than one such instruction due to
	 * multiple paths converging by jumping to labels.
	 */
	private var sourceInstructions = listOf<L2Instruction>()

	/**
	 * Indicates whether this RegisterState may have been shared among multiple
	 * [RegisterSet]s.  If so, the RegisterSet must not modify this object, but
	 * must make a copy first.
	 */
	var isShared: Boolean
		private set

	/**
	 * Answer whether this register contains a constant at the current code
	 * generation point.
	 *
	 * @return
	 *   Whether this register has most recently been assigned a constant.
	 */
	fun hasConstant(): Boolean = constant !== null

	/**
	 * Return the constant [AvailObject] that this register has in it at this
	 * point, or `null` if no such exact value is known.
	 *
	 * @return
	 *   The constant value or null.
	 */
	fun constant(): AvailObject? = constant

	/**
	 * Set the constant [AvailObject] that this register has in it at this
	 * point. Accept `null` to indicate no such value is known.
	 *
	 * @param newConstant
	 *   The constant value or null.
	 */
	fun constant(newConstant: AvailObject?)
	{
		assert(!isShared)
		constant = newConstant
	}

	/**
	 * Return the [type][A_Type] that constrains this register at this point, or
	 * `null` if no type constraint is known.
	 *
	 * @return
	 *   The constraining type or null.
	 */
	fun type(): A_Type? = type

	/**
	 * Set the [type][A_Type] that constrains this register at this Accepts
	 * `null` if no type constraint is known.
	 *
	 * @param newType
	 *   The constraining type or null.
	 */
	fun type(newType: A_Type?)
	{
		assert(!isShared)
		type = newType
	}

	/**
	 * Return the immutable [List] of [L2Instruction]s that may have provided
	 * the current value in that register.
	 *
	 * @return
	 *   An immutable list of L2Instructions.
	 */
	fun sourceInstructions(): List<L2Instruction> = sourceInstructions

	/**
	 * Empty my list of source [L2Instruction]s which may have provided
	 * values for this register at this point.
	 */
	fun clearSources()
	{
		assert(!isShared)
		sourceInstructions = emptyList()
	}

	/**
	 * Add an L2Instruction to my list of instructions that are potential
	 * sources of the value in the register at this point.
	 *
	 * @param newSource
	 *   An instruction that may provide a value for this register.
	 */
	fun addSource(newSource: L2Instruction)
	{
		assert(!isShared)
		if (!sourceInstructions.contains(newSource))
		{
			sourceInstructions =
				PrefixSharingList.append(sourceInstructions, newSource)
		}
	}

	/**
	 * Answer the immutable [List] of [L2Register]s that provided
	 * values for the represented register.
	 *
	 * @return
	 *   The source registers.
	 */
	fun origins(): List<L2Register>
	{
//		return Collections.unmodifiableList(origins);
		return origins
	}

	/**
	 * Replace the immutable [List] of [L2Register]s that provided values for
	 * the represented register.
	 *
	 * @param originRegisters
	 *   The source registers.
	 */
	fun origins(originRegisters: MutableList<L2Register>)
	{
		assert(!isShared)
		assert(originRegisters.size <= 1
			   || HashSet(originRegisters).size == originRegisters.size)
		origins = originRegisters
	}

	/**
	 * Update my [origins] to exclude the specified [L2Register]. The receiver
	 * must be mutable.
	 *
	 * @param origin
	 *   The [L2Register] that the current register is no longer fed from.
	 */
	fun removeOrigin(origin: L2Register)
	{
		assert(!isShared)
		if (origins.isNotEmpty())
		{
			if (PrefixSharingList.last(origins) === origin)
			{
				origins = PrefixSharingList.withoutLast(origins)
			}
			else
			{
				origins = ArrayList(origins)
				origins.remove(origin)
			}
		}
	}

	/**
	 * Answer the [L2Register]s that this one feeds (via a move).
	 *
	 * @return
	 *   An immutable list of registers.
	 */
	fun invertedOrigins(): List<L2Register> = invertedOrigins

	/**
	 * Replace the immutable [List] of [L2Register]s that were supplied values
	 * from the represented register.
	 *
	 * @param invertedOriginRegisters
	 *   The destination registers.
	 */
	fun invertedOrigins(invertedOriginRegisters: MutableList<L2Register>)
	{
		assert(!isShared)
		assert(invertedOriginRegisters.size <= 1
			   || HashSet(invertedOriginRegisters).size
			   == invertedOriginRegisters.size)
		invertedOrigins = invertedOriginRegisters
	}

	/**
	 * Update my [invertedOrigins] to include the specified [L2Register].  The
	 * receiver must be mutable.
	 *
	 * @param invertedOrigin
	 *   The [L2Register] that the current register feeds.
	 */
	fun addInvertedOrigin(invertedOrigin: L2Register)
	{
		assert(!isShared)
		invertedOrigins =
			PrefixSharingList.append(invertedOrigins, invertedOrigin)
	}

	/**
	 * Update my [invertedOrigins] to exclude the specified [L2Register].  The
	 * receiver must be mutable.
	 *
	 * @param invertedOrigin
	 *   The [L2Register] that the current register no longer is considered to
	 *   feed.
	 */
	fun removeInvertedOrigin(invertedOrigin: L2Register)
	{
		assert(!isShared)
		if (invertedOrigins.isNotEmpty())
		{
			if (PrefixSharingList.last(invertedOrigins) === invertedOrigin)
			{
				invertedOrigins = PrefixSharingList.withoutLast(invertedOrigins)
			}
			else
			{
				invertedOrigins = ArrayList(invertedOrigins)
				invertedOrigins.remove(invertedOrigin)
			}
		}
	}

	/**
	 * Mark this [RegisterState] as shared, preventing subsequent modifications.
	 * However, a mutable copy can be produced with the
	 * [copy constructor][RegisterState].
	 */
	fun share()
	{
		isShared = true
	}

	/**
	 * Construct a new [RegisterState].
	 */
	private constructor()
	{
		// Initialized by individual field declarations.
		isShared = true
	}

	/**
	 * Copy a `RegisterState`.
	 *
	 * @param original
	 *   The original RegisterSet to copy.
	 */
	internal constructor(original: RegisterState)
	{
		isShared = false
		constant = original.constant
		type = original.type
		origins = original.origins
		invertedOrigins = original.invertedOrigins
		sourceInstructions = original.sourceInstructions
	}

	companion object
	{
		/** The immutable initial register state in which nothing is known.  */
		private val blank = RegisterState()

		/**
		 * Answer the immutable initial register state in which nothing is known.
		 * @return The blank RegisterState.
		 */
		fun blank(): RegisterState = blank
	}
}