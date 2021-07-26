/*
 * DefinitionParsingPlanDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.descriptor.parsing

import com.avail.annotations.HideFieldInDebugger
import com.avail.compiler.AvailCompilerFragmentCache
import com.avail.compiler.ParsingConversionRule.Companion.ruleNumber
import com.avail.compiler.ParsingOperation.CONVERT
import com.avail.compiler.ParsingOperation.Companion.decode
import com.avail.compiler.ParsingOperation.Companion.operand
import com.avail.compiler.ParsingOperation.PARSE_PART
import com.avail.compiler.ParsingOperation.PARSE_PART_CASE_INSENSITIVELY
import com.avail.compiler.ParsingOperation.PERMUTE_LIST
import com.avail.compiler.ParsingOperation.PUSH_LITERAL
import com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT
import com.avail.compiler.splitter.MessageSplitter
import com.avail.compiler.splitter.MessageSplitter.Companion.constantForIndex
import com.avail.compiler.splitter.MessageSplitter.Companion.permutationAtIndex
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.bundles.A_Bundle.Companion.messagePart
import com.avail.descriptor.bundles.A_Bundle.Companion.messageSplitter
import com.avail.descriptor.bundles.A_BundleTree
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Sendable
import com.avail.descriptor.methods.A_Sendable.Companion.parsingSignature
import com.avail.descriptor.methods.MacroDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.bundle
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.definition
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.parsingInstructions
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor.ObjectSlots.BUNDLE
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor.ObjectSlots.DEFINITION
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor.ObjectSlots.PARSING_INSTRUCTIONS
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.combine3
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TypeDescriptor.Types.DEFINITION_PARSING_PLAN
import com.avail.descriptor.types.TypeTag
import com.avail.utility.StackPrinter
import java.util.IdentityHashMap

/**
 * A definition parsing plan describes the sequence of parsing operations that
 * must be performed to parse an invocation of a [definition][A_Definition],
 * possibly a [macro&#32;definition][MacroDescriptor].
 *
 * The sequences of instructions in multiple definition parse plans may have
 * common prefixes with each other, and it's along this commonality that
 * [bundle&#32;trees][A_BundleTree] are organized, avoiding the
 * need to parse the same content multiple times as much as possible.
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
class DefinitionParsingPlanDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability, TypeTag.PARSING_PLAN_TAG, ObjectSlots::class.java, null
) {
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [message&#32;bundle][A_Bundle] that this will parse invocations
		 * of.
		 */
		BUNDLE,

		/**
		 * The [definition][A_Definition] that this will parse invocations of.
		 * Note that the exact argument type information is included in the
		 * parsing operations, but this doesn't statically determine which
		 * actual definition will be invoked.
		 */
		DEFINITION,

		/**
		 * A tuple of integers that describe how to parse an invocation of this
		 * method. The integers encode parsing instructions, many of which can
		 * be executed *en masse* against a piece of Avail source code for
		 * multiple potential methods. This is facilitated by the incremental
		 * construction of a
		 * [message&#32;bundle&#32;tree][MessageBundleTreeDescriptor]. The
		 * instructions are produced during analysis of the method name by the
		 * [MessageSplitter], which has a description of the complete
		 * instruction set.
		 */
		@HideFieldInDebugger
		PARSING_INSTRUCTIONS
	}

	/**
	 * Show the types of local variables and outer variables.
	 */
	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> {
		// Weaken the plan's type to make sure we're not sending something it
		// won't understand.
		val fields = mutableListOf(*super.o_DescribeForDebugger(self))
		try
		{
			val instructionsTuple = self.parsingInstructions
			val descriptionsList = (1..instructionsTuple.tupleSize).map { i ->
				val encodedInstruction = instructionsTuple.tupleIntAt(i)
				val operation = decode(encodedInstruction)
				val operand = operand(encodedInstruction)
				buildString {
					append("$i. ${operation.name}")
					if (operand > 0) {
						append(" ($operand)")
						append(when (operation) {
							PARSE_PART,
							PARSE_PART_CASE_INSENSITIVELY -> {
								val part = self.bundle.messagePart(operand)
									.asNativeString()
								" Part = '$part'"
							}
							PUSH_LITERAL ->
								" Constant = ${constantForIndex(operand)}"
							PERMUTE_LIST ->
								" Permutation = ${permutationAtIndex(operand)}"
							TYPE_CHECK_ARGUMENT ->
								" Type = ${constantForIndex(operand)}"
							CONVERT -> " Conversion = ${ruleNumber(operand)}"
							else -> ""
						})
					}
				}
			}
			fields.add(
				AvailObjectFieldHelper(
					self,
					DebuggerObjectSlots("Symbolic instructions"),
					-1,
					descriptionsList.toTypedArray()))
		}
		catch (e: Exception)
		{
			val stackStrings = StackPrinter.trace(e).split("\\n").toTypedArray()
			stackStrings.mapIndexedTo(fields) { lineNumber, line ->
				AvailObjectFieldHelper(
					self,
					DebuggerObjectSlots("ERROR while producing instructions"),
					lineNumber + 1,
					line)
			}
		}
		return fields.toTypedArray()
	}

	override fun o_Bundle(self: AvailObject): A_Bundle = self.slot(BUNDLE)

	override fun o_Definition(self: AvailObject): A_Definition =
		self.slot(DEFINITION)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean {
		if (!another.kind().equals(DEFINITION_PARSING_PLAN.o)) {
			return false
		}
		val strongAnother = another as A_DefinitionParsingPlan
		return (self.slot(DEFINITION) === strongAnother.definition
			&& self.slot(BUNDLE) === strongAnother.bundle)
	}

	override fun o_Hash(self: AvailObject) = combine3(
		self.slot(DEFINITION).hash(),
		self.slot(BUNDLE).hash(),
		-0x6d5d9ebe)

	override fun o_Kind(self: AvailObject): A_Type =
		DEFINITION_PARSING_PLAN.o

	override fun o_ParsingInstructions(self: AvailObject): A_Tuple =
		self.slot(PARSING_INSTRUCTIONS)

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	): Unit = with(builder) {
		// The existing definitions are also printed in parentheses to help
		// distinguish polymorphism from occurrences of non-polymorphic
		// homonyms.
		append("plan for ")
		append(self.bundle.message)
		append(" at ")
		append(self.definition.parsingSignature())
	}

	override fun mutable() = mutable

	// There is no immutable variant.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Create a new [A_DefinitionParsingPlan] for the given parameters.  Do
		 * not install it.
		 *
		 * @param bundle
		 *   The bundle for this plan.
		 * @param definition
		 *   The definition for this plan.
		 * @return
		 *   A new [A_DefinitionParsingPlan].
		 */
		fun newParsingPlan(
			bundle: A_Bundle,
			definition: A_Sendable
		): A_DefinitionParsingPlan = mutable.create {
			setSlot(BUNDLE, bundle)
			setSlot(DEFINITION, definition)
			setSlot(
				PARSING_INSTRUCTIONS,
				bundle.messageSplitter.instructionsTupleFor(
					definition.parsingSignature()))
		}

		/** The mutable [DefinitionParsingPlanDescriptor].  */
		private val mutable =
			DefinitionParsingPlanDescriptor(Mutability.MUTABLE)

		/** The shared [DefinitionParsingPlanDescriptor].  */
		private val shared = DefinitionParsingPlanDescriptor(Mutability.SHARED)
	}
}
