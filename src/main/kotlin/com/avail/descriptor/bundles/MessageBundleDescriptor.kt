/*
 * MessageBundleDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.bundles

import com.avail.annotations.AvailMethod
import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.Descriptor
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots.*
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.definition
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor.Companion.newParsingPlan
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import com.avail.descriptor.representation.*
import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.TypeDescriptor.Types.MESSAGE_BUNDLE
import com.avail.descriptor.types.TypeTag
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*

/**
 * A message bundle is how a message name is bound to a [method][A_Method].
 * Besides the message name, which is an [A_Atom], the bundle also contains
 * information useful for parsing its invocations.  This information includes
 * parsing instructions which, when aggregated with other bundles, forms a
 * [message&#32; bundle&#32; tree][MessageBundleTreeDescriptor].  This allows
 * parsing of multiple similar methods *in aggregate*, avoiding the cost of
 * repeatedly parsing the same constructs (tokens and subexpressions) for
 * different purposes.
 *
 * Additionally, the message bundle's [grammatical&#32;
 * restrictions][ObjectSlots.GRAMMATICAL_RESTRICTIONS] are held here, rather
 * than with the [method][MethodDescriptor], since these rules are intended to
 * work with the actual [tokens][A_Token] that occur (i.e., how sends are
 * *written*), not their underlying semantics (what the methods *do*).
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class MessageBundleDescriptor private constructor(
	mutability: Mutability
) : Descriptor(mutability, TypeTag.BUNDLE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The [method][MethodDescriptor] for which this is a message bundle.
		 * That is, if a use of this bundle is parsed, the resulting code will
		 * ultimately invoke this method.  A method may have multiple such
		 * bundles due to renaming of imports.
		 */
		METHOD,

		/**
		 * An [atom][AtomDescriptor] which is the "true name" of this bundle.
		 * Due to import renaming, a [method][MethodDescriptor] might have
		 * multiple such names, one per bundle.
		 */
		MESSAGE,

		/**
		 * The [MessageSplitter] that describes how to parse invocations of this
		 * message bundle.
		 */
		MESSAGE_SPLITTER_POJO,

		/**
		 * A [set][A_Set] of [grammatical&#32;
		 * restrictions][A_GrammaticalRestriction] that apply to this message
		 * bundle.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * The [A_Map] from [A_Definition] to [A_DefinitionParsingPlan].  The
		 * keys should always agree with the [A_Method]'s collection of
		 * definitions and macro definitions.
		 */
		DEFINITION_PARSING_PLANS
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === METHOD
		|| e === GRAMMATICAL_RESTRICTIONS
		|| e === DEFINITION_PARSING_PLANS

	@AvailMethod
	override fun o_AddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction
	) = self.synchronizeIf(isShared) {
			addGrammaticalRestriction(self, grammaticalRestriction)
		}

	@AvailMethod
	override fun o_AddDefinitionParsingPlan(
		self: AvailObject,
		plan: A_DefinitionParsingPlan
	) = self.synchronizeIf(isShared) { addDefinitionParsingPlan(self, plan) }

	@AvailMethod
	override fun o_BundleMethod(self: AvailObject) =
		self.mutableSlot(METHOD)

	override fun o_DefinitionParsingPlans(self: AvailObject) =
		self.slot(DEFINITION_PARSING_PLANS)

	@AvailMethod
	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.traversed().sameAddressAs(self)

	@AvailMethod
	override fun o_GrammaticalRestrictions(self: AvailObject) =
		self.mutableSlot(GRAMMATICAL_RESTRICTIONS)

	@AvailMethod
	override fun o_HasGrammaticalRestrictions(self: AvailObject) =
		self.mutableSlot(GRAMMATICAL_RESTRICTIONS).setSize() > 0

	@AvailMethod
	override fun o_Hash(self: AvailObject) =
		self.message().hash() xor 0x0312CAB9

	@AvailMethod
	override fun o_Kind(self: AvailObject) = MESSAGE_BUNDLE.o()

	@AvailMethod
	override fun o_Message(self: AvailObject) = self.slot(MESSAGE)

	@AvailMethod
	override fun o_MessageParts(self: AvailObject): A_Tuple {
		val splitterPojo = self.slot(MESSAGE_SPLITTER_POJO)
		val messageSplitter = splitterPojo.javaObjectNotNull<MessageSplitter>()
		return messageSplitter.messagePartsTuple
	}

	@AvailMethod
	override fun o_MessageSplitter(self: AvailObject): MessageSplitter {
		val splitterPojo = self.slot(MESSAGE_SPLITTER_POJO)
		return splitterPojo.javaObjectNotNull()
	}

	@AvailMethod
	override fun o_RemovePlanForDefinition(
		self: AvailObject,
		definition: A_Definition
	) = self.synchronizeIf(isShared) {
		removePlanForDefinition(self, definition)
	}

	@AvailMethod
	override fun o_RemoveGrammaticalRestriction(
		self: AvailObject,
		obsoleteRestriction: A_GrammaticalRestriction
	) = self.synchronizeIf(isShared) {
		removeGrammaticalRestriction(self, obsoleteRestriction)
	}

	@AvailMethod
	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.MESSAGE_BUNDLE

	override fun o_WriteTo(
		self: AvailObject, writer: JSONWriter
	) {
		writer.startObject()
		writer.write("kind")
		writer.write("message bundle")
		writer.write("method")
		self.slot(MESSAGE).atomName().writeTo(writer)
		writer.endObject()
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		aStream: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		// The existing definitions are also printed in parentheses to help
		// distinguish polymorphism from occurrences of non-polymorphic
		// homonyms.
		aStream.append("bundle \"")
		aStream.append(self.message().atomName().asNativeString())
		aStream.append("\"")
	}

	override fun mutable() = mutable

	// There is no immutable variant.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Add a [definition parsing plan][DefinitionParsingPlanDescriptor] to
		 * this bundle.  This is performed to make the bundle agree with the
		 * method's definitions and macro definitions.
		 *
		 * @param self
		 *   The affected message bundle.
		 * @param plan
		 *   A definition parsing plan.
		 */
		private fun addDefinitionParsingPlan(
			self: AvailObject,
			plan: A_DefinitionParsingPlan
		) {
			var plans: A_Map = self.slot(DEFINITION_PARSING_PLANS)
			plans = plans.mapAtPuttingCanDestroy(plan.definition(), plan, true)
			self.setSlot(DEFINITION_PARSING_PLANS, plans.makeShared())
		}

		/**
		 * Remove a [A_DefinitionParsingPlan] from this bundle, specifically
		 * the one associated with the give [A_Definition].  This is performed
		 * to make the bundle agree with the method's definitions and macro
		 * definitions.
		 *
		 * @param self
		 *   The affected message bundle.
		 * @param definition
		 *   A definition whose plan should be removed.
		 */
		private fun removePlanForDefinition(
			self: AvailObject,
			definition: A_Definition
		) {
			var plans: A_Map = self.mutableSlot(DEFINITION_PARSING_PLANS)
			assert(plans.hasKey(definition))
			plans = plans.mapWithoutKeyCanDestroy(definition, true)
			self.setMutableSlot(DEFINITION_PARSING_PLANS, plans.makeShared())
		}

		/**
		 * Add a grammatical restriction to this bundle.
		 *
		 * @param self
		 *   The affected message bundle.
		 * @param grammaticalRestriction
		 *   A [grammatical&#32;restriction][A_GrammaticalRestriction].
		 */
		private fun addGrammaticalRestriction(
			self: AvailObject,
			grammaticalRestriction: A_GrammaticalRestriction
		) {
			var restrictions: A_Set = self.slot(GRAMMATICAL_RESTRICTIONS)
			restrictions = restrictions.setWithElementCanDestroy(
				grammaticalRestriction, true)
			self.setSlot(GRAMMATICAL_RESTRICTIONS, restrictions.makeShared())
		}

		/**
		 * Remove a grammatical restriction from this bundle.
		 *
		 * @param self
		 *   A message bundle.
		 * @param obsoleteRestriction
		 *   The [grammatical&#32; restriction][A_GrammaticalRestriction] to
		 *   remove.
		 */
		private fun removeGrammaticalRestriction(
			self: AvailObject,
			obsoleteRestriction: A_GrammaticalRestriction
		) {
			var restrictions: A_Set = self.mutableSlot(GRAMMATICAL_RESTRICTIONS)
			restrictions = restrictions.setWithoutElementCanDestroy(
				obsoleteRestriction, true)
			self.setMutableSlot(
				GRAMMATICAL_RESTRICTIONS, restrictions.makeShared())
		}

		/**
		 * Create a new [message&#32; bundle][A_Bundle] for the given message.
		 * Add the bundle to the method's collection of [owning&32;
		 * bundles][MethodDescriptor.ObjectSlots.OWNING_BUNDLES].
		 *
		 * @param methodName
		 *   The message name, an [atom][AtomDescriptor].
		 * @param method
		 *   The method that this bundle represents.
		 * @param splitter
		 *   A MessageSplitter for this message name.
		 * @return
		 *   A new [message&#32; bundle][A_Bundle].
		 */
		fun newBundle(
			methodName: A_Atom,
			method: A_Method,
			splitter: MessageSplitter): A_Bundle {
			assert(methodName.isAtom)
			assert(splitter.numberOfArguments == method.numArgs())
			assert(splitter.messageName.equals(methodName.atomName()))
			val splitterPojo = identityPojo(splitter)
			val result = mutable.create()
			result.setSlot(METHOD, method)
			result.setSlot(MESSAGE, methodName)
			result.setSlot(MESSAGE_SPLITTER_POJO, splitterPojo)
			result.setSlot(GRAMMATICAL_RESTRICTIONS, emptySet())
			var plans = emptyMap()
			for (definition in method.definitionsTuple()) {
				val plan = newParsingPlan(result, definition)
				plans = plans.mapAtPuttingCanDestroy(definition, plan, true)
			}
			for (definition in method.macroDefinitionsTuple()) {
				val plan = newParsingPlan(result, definition)
				plans = plans.mapAtPuttingCanDestroy(definition, plan, true)
			}
			result.setSlot(DEFINITION_PARSING_PLANS, plans)
			result.makeShared()
			method.methodAddBundle(result)
			return result
		}

		/** The mutable [MessageBundleDescriptor].  */
		private val mutable = MessageBundleDescriptor(Mutability.MUTABLE)

		/** The shared [MessageBundleDescriptor].  */
		private val shared = MessageBundleDescriptor(Mutability.SHARED)
	}
}