/*
 * MessageBundleDescriptor.kt
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
package com.avail.descriptor.bundles

import com.avail.compiler.splitter.MessageSplitter
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots.DEFINITION_PARSING_PLANS
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots.GRAMMATICAL_RESTRICTIONS
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots.MACROS_TUPLE
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots.MESSAGE
import com.avail.descriptor.bundles.MessageBundleDescriptor.ObjectSlots.METHOD
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_Map.Companion.hasKey
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_Method.Companion.definitionsTuple
import com.avail.descriptor.methods.A_Method.Companion.methodAddBundle
import com.avail.descriptor.methods.A_Method.Companion.numArgs
import com.avail.descriptor.methods.A_Method.Companion.sealedArgumentsTypesTuple
import com.avail.descriptor.methods.A_Sendable
import com.avail.descriptor.methods.A_Sendable.Companion.bodySignature
import com.avail.descriptor.methods.MacroDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.module.A_Module.Companion.addBundle
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_DefinitionParsingPlan.Companion.definition
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor.Companion.newParsingPlan
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.combine2
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.Descriptor
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import com.avail.descriptor.sets.A_Set.Companion.setWithoutElementCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.toList
import com.avail.descriptor.tuples.TupleDescriptor.Companion.tupleWithout
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE
import com.avail.descriptor.types.TypeTag
import com.avail.dispatch.LookupTree
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.SignatureException
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED_FLAG
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.Collections.nCopies
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import kotlin.concurrent.withLock

/**
 * A message bundle is how a message name is bound to a [method][A_Method].
 * Besides the message name, which is an [A_Atom], the bundle also contains
 * information useful for parsing its invocations.  This information includes
 * parsing instructions which, when aggregated with other bundles, forms a
 * [message&#32;bundle&#32;tree][MessageBundleTreeDescriptor].  This allows
 * parsing of multiple similar methods *in aggregate*, avoiding the cost of
 * repeatedly parsing the same constructs (tokens and subexpressions) for
 * different purposes.
 *
 * Additionally, the message bundle's
 * [grammatical&#32;restrictions][ObjectSlots.GRAMMATICAL_RESTRICTIONS] are held
 * here, rather than with the [method][MethodDescriptor], since these rules are
 * intended to work with the actual [tokens][A_Token] that occur (i.e., how
 * sends are *written*), not their underlying semantics (what the methods *do*).
 *
 * @constructor
 * Construct a new `MessageBundleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param
 *   The [MessageSplitter] that describes how to parse invocations of this
 *   message bundle.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class MessageBundleDescriptor private constructor(
	mutability: Mutability,
	private val messageSplitter: MessageSplitter
) : Descriptor(mutability, TypeTag.BUNDLE_TAG, ObjectSlots::class.java, null)
{
	/**
	 * A [LookupTree] used to determine the most specific
	 * [macro&#32;definition][MacroDescriptor] that satisfies the
	 * supplied argument types.  A `null` indicates the tree has not yet been
	 * constructed.
	 */
	@Volatile
	private var macroTestingTree: LookupTree<A_Definition, A_Tuple>? = null

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
		 * A [set][A_Set] of
		 * [grammatical&#32;restrictions][A_GrammaticalRestriction] that apply
		 * to this message bundle.
		 */
		GRAMMATICAL_RESTRICTIONS,

		/**
		 * The [tuple][A_Tuple] of [macro][MacroDescriptor] definitions that are
		 * defined for this message bundle.
		 */
		MACROS_TUPLE,

		/**
		 * The [A_Map] from [A_Definition] to [A_DefinitionParsingPlan].  The
		 * keys should always agree with the [A_Method]'s collection of
		 * definitions and macro definitions.
		 */
		DEFINITION_PARSING_PLANS
	}

	/**
	 * Extract the current [macroTestingTree], creating one atomically, if
	 * necessary.
	 *
	 * @param self
	 *   The [A_Method] for which to answer the [macroTestingTree].
	 * @return
	 *   The [LookupTree] for looking up macro definitions.
	 */
	private fun macroTestingTree(
		self: AvailObject
	): LookupTree<A_Definition, A_Tuple>
	{
		var tree = macroTestingTree
		if (tree === null) {
			val method = self.slot(METHOD)
			val numArgs = method.numArgs
			val newTree = MethodDescriptor.runtimeDispatcher.createRoot(
				toList(self.slot(MACROS_TUPLE)),
				nCopies(
					numArgs,
					restrictionForType(
						PARSE_PHRASE.mostGeneralType, BOXED_FLAG)),
				Unit)
			do
			{
				// Try to replace null with the new tree.  If the replacement
				// fails, it means someone else already succeeded, so use that
				// winner's tree.
				macroTestingTreeUpdater.compareAndSet(this, null, newTree)
				tree = macroTestingTree
			}
			while (tree === null)
		}
		return tree
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === METHOD
		|| e === GRAMMATICAL_RESTRICTIONS
		|| e === MACROS_TUPLE
		|| e === DEFINITION_PARSING_PLANS

	override fun o_AddGrammaticalRestriction(
		self: AvailObject,
		grammaticalRestriction: A_GrammaticalRestriction
	) = self.synchronizeIf(isShared) {
			addGrammaticalRestriction(self, grammaticalRestriction)
		}

	override fun o_AddDefinitionParsingPlan(
		self: AvailObject,
		plan: A_DefinitionParsingPlan
	) = self.synchronizeIf(isShared) { addDefinitionParsingPlan(self, plan) }

	/**
	 * Method/bundle manipulation takes place while all fibers are L1-precise
	 * and suspended.  Use a global lock at the outermost calls to side-step
	 * deadlocks.  Because no fiber is running, we don't have to protect
	 * subsystems like the L2Generator from these changes.
	 *
	 * Also create a definition parsing plan for this bundle.  HOWEVER, note
	 * that we don't update the current module's message bundle tree here, and
	 * leave that to the caller to deal with.  Other modules' parsing should be
	 * unaffected by this change.
	 */
	@Throws(SignatureException::class)
	override fun o_BundleAddMacro(
		self: AvailObject,
		macro: A_Macro,
		ignoreSeals: Boolean
	) = L2Chunk.invalidationLock.withLock {
		if (!ignoreSeals)
		{
			val paramTypes = macro.bodySignature().argsTupleType
			val seals: A_Tuple = self.bundleMethod.sealedArgumentsTypesTuple
			seals.forEach { seal: A_Tuple ->
				val sealType = tupleTypeForSizesTypesDefaultType(
					singleInt(seal.tupleSize), seal, bottom)
				if (paramTypes.isSubtypeOf(sealType))
				{
					throw SignatureException(AvailErrorCode.E_METHOD_IS_SEALED)
				}
			}
		}
		// Install the macro.
		self.updateSlotShared(MACROS_TUPLE) { appendCanDestroy(macro, true) }
		// It's only a macro change, so don't invalidate dependent L2Chunks.
		synchronized(self) {
			macroTestingTree = null
			addDefinitionParsingPlan(self, newParsingPlan(self, macro))
		}
	}

	override fun o_BundleMethod(self: AvailObject) =
		self.mutableSlot(METHOD)

	override fun o_DefinitionParsingPlans(self: AvailObject): A_Map =
		self.slot(DEFINITION_PARSING_PLANS)

	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> {
		val fields = super.o_DescribeForDebugger(self).toMutableList()
		fields.add(
			AvailObjectFieldHelper(
				self,
				DebuggerObjectSlots("messageSplitter"),
				-1,
				arrayOf(messageSplitter),
				forcedName = "messageSplitter"))
		fields.add(
			AvailObjectFieldHelper(
				self,
				DebuggerObjectSlots("macroTestingTree"),
				-1,
				arrayOf(macroTestingTree),
				forcedName = "macroTestingTree"))
		return fields.toTypedArray()
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.traversed().sameAddressAs(self)

	override fun o_GrammaticalRestrictions(self: AvailObject): A_Set =
		self.mutableSlot(GRAMMATICAL_RESTRICTIONS)

	override fun o_HasGrammaticalRestrictions(self: AvailObject) =
		self.mutableSlot(GRAMMATICAL_RESTRICTIONS).setSize > 0

	override fun o_Hash(self: AvailObject) =
		combine2(self.message.hash(), 0x0312CAB9)

	override fun o_Kind(self: AvailObject) = MESSAGE_BUNDLE.o

	override fun o_LookupMacroByPhraseTuple(
		self: AvailObject,
		argumentPhraseTuple: A_Tuple
	): A_Tuple
	{
		val methodDescriptor =
			self.bundleMethod.traversed().descriptor() as MethodDescriptor
		return MethodDescriptor.runtimeDispatcher.lookupByValues(
			macroTestingTree(self),
			argumentPhraseTuple,
			Unit,
			methodDescriptor.dynamicLookupStats())
	}

	override fun o_MacrosTuple(self: AvailObject): A_Tuple
	{
		assert(isShared)
		return synchronized(self) { self.slot(MACROS_TUPLE) }
	}

	override fun o_Message(self: AvailObject): A_Atom = self.slot(MESSAGE)

	override fun o_MessagePart(self: AvailObject, index: Int): A_String =
		// One-based index.
		messageSplitter.messageParts[index - 1]

	override fun o_MessageParts(self: AvailObject): A_Tuple =
		tupleFromArray(*messageSplitter.messageParts)

	override fun o_MessageSplitter(self: AvailObject): MessageSplitter =
		messageSplitter

	override fun o_NumArgs(self: AvailObject): Int =
		messageSplitter.numberOfArguments

	override fun o_RemoveMacro(
		self: AvailObject,
		macro: A_Macro
	) = self.synchronizeIf(isShared) {
		removeMacro(self, macro)
	}

	override fun o_RemovePlanForSendable(
		self: AvailObject,
		sendable: A_Sendable
	) = self.synchronizeIf(isShared) {
		removePlanForSendable(self, sendable)
	}

	override fun o_RemoveGrammaticalRestriction(
		self: AvailObject,
		obsoleteRestriction: A_GrammaticalRestriction
	) = self.synchronizeIf(isShared) {
		removeGrammaticalRestriction(self, obsoleteRestriction)
	}

	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.MESSAGE_BUNDLE

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("message bundle") }
			at("method") { self.slot(MESSAGE).atomName.writeTo(writer) }
			at("macro definitions") { self.slot(MACROS_TUPLE).writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("message bundle") }
			at("method") { self.slot(MESSAGE).atomName.writeTo(writer) }
		}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		// The existing definitions are also printed in parentheses to help
		// distinguish polymorphism from occurrences of non-polymorphic
		// homonyms.
		builder.append("bundle \"")
		builder.append(self.message.atomName.asNativeString())
		builder.append("\"")
		when (val numMacros = self.macrosTuple.tupleSize)
		{
			0 -> { }
			1 -> builder.append(" (1 macro)")
			else -> builder.append(" ($numMacros macros)")
		}
	}


	@Deprecated("Not supported", ReplaceWith("newBundle()"))
	override fun mutable() = unsupported

	@Deprecated("Not supported", ReplaceWith("newBundle()"))
	override fun immutable() = unsupported

	@Deprecated("Not supported", ReplaceWith("newBundle()"))
	override fun shared() = unsupported

	companion object {
		/** Atomic access to [macroTestingTree]. */
		private val macroTestingTreeUpdater =
			AtomicReferenceFieldUpdater.newUpdater(
				MessageBundleDescriptor::class.java,
				LookupTree::class.java,
				"macroTestingTree")

		/**
		 * Add an [A_DefinitionParsingPlan] to this bundle.  This is performed
		 * to make the bundle agree with the method definitions and macro
		 * definitions.
		 *
		 * @param self
		 *   The affected message bundle.
		 * @param plan
		 *   A definition parsing plan.
		 */
		private fun addDefinitionParsingPlan(
			self: AvailObject,
			plan: A_DefinitionParsingPlan)
		{
			var plans: A_Map = self.slot(DEFINITION_PARSING_PLANS)
			plans = plans.mapAtPuttingCanDestroy(plan.definition, plan, true)
			self.setSlot(DEFINITION_PARSING_PLANS, plans.makeShared())
		}

		/**
		 * Remove an [A_Macro] from this bundle.  This is performed to make the
		 * bundle agree with the method's definitions and macro definitions.
		 *
		 * @param self
		 *   The affected message bundle.
		 * @param macro
		 *   The [A_Macro] to be removed.
		 */
		private fun removeMacro(
			self: AvailObject,
			macro: A_Macro)
		{
			self.updateSlotShared(MACROS_TUPLE) { tupleWithout(this, macro) }
			removePlanForSendable(self, macro)
		}

		/**
		 * Remove a [A_DefinitionParsingPlan] from this bundle, specifically the
		 * one associated with the given [A_Sendable] (which is either an
		 * [A_Definition] or [A_Macro]).  This is performed to make the bundle
		 * agree with the method's definitions and macros.
		 *
		 * @param self
		 *   The affected message bundle.
		 * @param sendable
		 *   A method definition or macro whose plan should be removed.
		 */
		private fun removePlanForSendable(
			self: AvailObject,
			sendable: A_Sendable
		) = self.updateSlotShared(DEFINITION_PARSING_PLANS) {
			assert(hasKey(sendable))
			mapWithoutKeyCanDestroy(sendable, true)
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
		) = self.updateSlotShared(GRAMMATICAL_RESTRICTIONS) {
			setWithElementCanDestroy(grammaticalRestriction, true)
		}

		/**
		 * Remove a grammatical restriction from this bundle.
		 *
		 * @param self
		 *   A message bundle.
		 * @param obsoleteRestriction
		 *   The [grammatical&#32;restriction][A_GrammaticalRestriction] to
		 *   remove.
		 */
		private fun removeGrammaticalRestriction(
			self: AvailObject,
			obsoleteRestriction: A_GrammaticalRestriction
		) = self.updateSlotShared(GRAMMATICAL_RESTRICTIONS) {
			setWithoutElementCanDestroy(obsoleteRestriction, true)
		}

		/**
		 * Create a new [message&#32;bundle][A_Bundle] for the given message.
		 * Add the bundle to the method's collection of
		 * [owning&#32;bundles][MethodDescriptor.owningBundles].  Update the
		 * atom's (methodName's) bundle field to point to the new bundle. Also
		 * update the current loading module, if any, to be responsible for
		 * destruction of the bundle upon unloading.
		 *
		 * @param methodName
		 *   The message name, an [atom][AtomDescriptor].
		 * @param method
		 *   The method that this bundle represents.
		 * @param splitter
		 *   A MessageSplitter for this message name.
		 * @return
		 *   A new [message&#32;bundle][A_Bundle].
		 */
		fun newBundle(
			methodName: A_Atom,
			method: A_Method,
			splitter: MessageSplitter
		): A_Bundle
		{
			assert(methodName.isAtom)
			assert(splitter.numberOfArguments == method.numArgs)
			assert(splitter.messageName.equals(methodName.atomName))
			val currentModule = Interpreter.currentOrNull()
				?.availLoaderOrNull()
				?.module
			return initialMutableDescriptor.create {
				setSlot(METHOD, method)
				setSlot(MESSAGE, methodName.makeShared())
				setSlot(MACROS_TUPLE, emptyTuple())
				setSlot(GRAMMATICAL_RESTRICTIONS, emptySet)
				setSlot(DEFINITION_PARSING_PLANS, emptyMap)
				setDescriptor(
					MessageBundleDescriptor(Mutability.SHARED, splitter))
				method.methodAddBundle(this)
				currentModule?.addBundle(this)
				// Note that there are no macro implementations in this bundle
				// at this time, since this bundle is new.
				var plans = emptyMap
				for (definition in method.definitionsTuple)
				{
					val plan = newParsingPlan(this, definition)
					plans = plans.mapAtPuttingCanDestroy(definition, plan, true)
				}
				setSlot(DEFINITION_PARSING_PLANS, plans.makeShared())
			}
		}

		/**
		 * The mutable [MessageBundleDescriptor].  It has a dummy
		 * [MessageSplitter] to ensure the field is always non-null.
		 */
		private val initialMutableDescriptor = MessageBundleDescriptor(
			Mutability.MUTABLE,
			MessageSplitter(stringFrom("dummy")))
	}
}
