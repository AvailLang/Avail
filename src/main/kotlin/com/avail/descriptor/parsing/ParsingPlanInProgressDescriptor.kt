/*
 * ParsingPlanInProgressDescriptor.kt
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
package com.avail.descriptor.parsing

import com.avail.annotations.AvailMethod
import com.avail.compiler.AvailCompilerFragmentCache
import com.avail.compiler.ParsingOperation
import com.avail.compiler.ParsingOperation.Companion.decode
import com.avail.descriptor.Descriptor
import com.avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.MacroDefinitionDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.bundle
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.definition
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.parsingInstructions
import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.nameHighlightingPc
import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPc
import com.avail.descriptor.parsing.A_ParsingPlanInProgress.Companion.parsingPlan
import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor.IntegerSlots.Companion.PARSING_PC
import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor.ObjectSlots.PARSING_PLAN
import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import java.util.*

/**
 * A definition parsing plan describes the sequence of parsing operations that
 * must be performed to parse an invocation of a [definition][A_Definition],
 * possibly a [macro&#32;definition][MacroDefinitionDescriptor].
 *
 * The sequences of instructions in multiple definition parse plans may have
 * common prefixes with each other, and it's along this commonality that
 * [message&#32;bundle&#32;trees][MessageBundleTreeDescriptor] are organized,
 * avoiding the need to parse the same content multiple times as much as
 * possible.
 *
 * This is taken even further by a cache of subexpressions found at each
 * parse point.  See [AvailCompilerFragmentCache] for more details.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ParsingPlanInProgressDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability,
	TypeTag.PARSING_PLAN_IN_PROGRESS_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java
) {
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * [BitField]s for the hash and the parsing pc.  See below.
		 */
		PARSING_PC_AND_MORE;

		companion object {
			/** The subscript into my parsing plan's parsing instructions. */
			@JvmField
			val PARSING_PC = BitField(PARSING_PC_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [A_DefinitionParsingPlan] that this will parse invocations of.
		 */
		PARSING_PLAN
	}

	override fun o_ParsingPc(self: AvailObject): Int =
		self.slot(PARSING_PC)

	override fun o_ParsingPlan(self: AvailObject): A_DefinitionParsingPlan =
		self.slot(PARSING_PLAN)

	@AvailMethod
	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean {
		if (!another.kind().equals(Types.PARSING_PLAN_IN_PROGRESS.o())) {
			return false
		}
		val strongAnother = another as A_ParsingPlanInProgress
		return (self.slot(PARSING_PLAN).equals(strongAnother.parsingPlan())
			&& self.slot(PARSING_PC) == strongAnother.parsingPc())
	}

	@AvailMethod
	override fun o_Hash(self: AvailObject): Int =
		((self.slot(PARSING_PC) xor -0x6d5d9ebe) * multiplier
			- self.slot(PARSING_PLAN).hash())

	@AvailMethod
	override fun o_Kind(self: AvailObject): A_Type =
		Types.PARSING_PLAN_IN_PROGRESS.o()

	override fun o_IsBackwardJump(self: AvailObject): Boolean {
		val plan: A_DefinitionParsingPlan = self.slot(PARSING_PLAN)
		val instructions = plan.parsingInstructions()
		val pc = self.slot(PARSING_PC)
		if (pc > instructions.tupleSize()) {
			return false
		}
		val instruction = instructions.tupleIntAt(pc)
		return decode(instruction) === ParsingOperation.JUMP_BACKWARD
	}

	/**
	 * Answer a [String] consisting of the name of the message with a visual
	 * indication inserted at the keyword or argument position related to the
	 * given program counter.
	 *
	 * @param self
	 *   The [A_ParsingPlanInProgress] to describe.
	 * @return
	 *   The annotated method name, a Java [String].
	 */
	@AvailMethod
	override fun o_NameHighlightingPc(self: AvailObject): String {
		val plan: A_DefinitionParsingPlan = self.slot(PARSING_PLAN)
		val pc = self.slot(PARSING_PC)
		return when {
			pc <= 1 -> "(any method invocation)"
			else -> plan.bundle().messageSplitter().highlightedNameFor(
				plan.definition().parsingSignature(), pc)
		}
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		aStream: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		aStream.append("plan @")
		aStream.append(self.parsingPc())
		aStream.append(" of ")
		aStream.append(self.nameHighlightingPc())
	}

	override fun mutable(): ParsingPlanInProgressDescriptor = mutable

	// There is no immutable variant.
	override fun immutable(): ParsingPlanInProgressDescriptor = shared

	override fun shared(): ParsingPlanInProgressDescriptor = shared

	companion object {
		/**
		 * Create a new [A_ParsingPlanInProgress] for the given parameters.
		 *
		 * @param plan
		 *   The bundle for this plan.
		 * @param pc
		 *   The program counter within the plan.
		 * @return
		 *   A new parsing-plan-in-progress.
		 */
		@JvmStatic
		fun newPlanInProgress(
			plan: A_DefinitionParsingPlan,
			pc: Int
		): A_ParsingPlanInProgress = mutable.create().apply {
			setSlot(PARSING_PLAN, plan)
			setSlot(PARSING_PC, pc)
		}.makeShared()

		/** The mutable [ParsingPlanInProgressDescriptor].  */
		private val mutable =
			ParsingPlanInProgressDescriptor(Mutability.MUTABLE)

		/** The shared [ParsingPlanInProgressDescriptor].  */
		private val shared = ParsingPlanInProgressDescriptor(Mutability.SHARED)
	}
}