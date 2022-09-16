/*
 * A_Bundle.kt
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
package avail.descriptor.bundles

import avail.compiler.AvailCompiler
import avail.compiler.splitter.MessageSplitter
import avail.descriptor.atoms.A_Atom
import avail.descriptor.maps.A_Map
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_GrammaticalRestriction
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Sendable
import avail.descriptor.methods.GrammaticalRestrictionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.parsing.A_DefinitionParsingPlan
import avail.descriptor.parsing.DefinitionParsingPlanDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.exceptions.SignatureException

/**
 * `A_Bundle` is an interface that specifies the operations specific to
 * [message&#32;bundles][MessageBundleDescriptor] that an [AvailObject] must
 * implement.  It's a sub-interface of [A_BasicObject], the interface that
 * defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Bundle : A_BasicObject {
	companion object {
		/**
		 * Add a
		 * [definition&#32;parsing&#32;plan][DefinitionParsingPlanDescriptor] to
		 * this bundle, to bring it into agreement with the method's definitions
		 * and macro definitions.
		 *
		 * @param plan
		 *   The definition parsing plan to add.
		 */
		fun A_Bundle.addDefinitionParsingPlan(plan: A_DefinitionParsingPlan) =
			dispatch { o_AddDefinitionParsingPlan(it, plan) }

		/**
		 * Remove information about this [definition][A_Definition] or
		 * [macro][A_Macro] from this bundle.
		 *
		 * @param sendable
		 *   The method definition or macro whose plan should be removed.
		 */
		fun A_Bundle.removePlanForSendable(sendable: A_Sendable) =
			dispatch { o_RemovePlanForSendable(it, sendable) }

		/**
		 * Add a [grammatical][GrammaticalRestrictionDescriptor] to the
		 * receiver.
		 *
		 * @param grammaticalRestriction
		 *   The grammatical restriction to be added.
		 */
		fun A_Bundle.addGrammaticalRestriction(
			grammaticalRestriction: A_GrammaticalRestriction
		) = dispatch { o_AddGrammaticalRestriction(it, grammaticalRestriction) }

		/**
		 * Add the [A_Macro] definition to this bundle, respecting seals if
		 * requested.  Throw a [SignatureException] (without making changes), if
		 * the macro body's signature is incompatible with the bundle's name.
		 * See [MessageSplitter] and its related classes for information about
		 * how the message name is related to argument structure.
		 */
		@Throws(SignatureException::class)
		fun A_Bundle.bundleAddMacro(macro: A_Macro, ignoreSeals: Boolean) =
			dispatch { o_BundleAddMacro(it, macro, ignoreSeals) }

		/**
		 * Answer the [method][MethodDescriptor] that this bundle names.
		 * Multiple bundles may refer to the same method to support renaming of
		 * imported names.
		 *
		 * @return
		 *   This bundle's [A_Method].
		 */
		val A_Bundle.bundleMethod get() = dispatch { o_BundleMethod(it) }

		/**
		 * Answer this bundle's [A_Map] from [A_Definition] to
		 * [A_DefinitionParsingPlan].
		 *
		 * The [AvailCompiler] works by attempting to execute all
		 * [parsing&#32;plans][A_DefinitionParsingPlan] in aggregate, using a
		 * lazily expanded [message&#32bundle&#32;tree][A_BundleTree] to avoid
		 * duplication of effort.
		 *
		 * @return
		 *   The map of definition parsing plans.
		 */
		val A_Bundle.definitionParsingPlans
			get() = dispatch { o_DefinitionParsingPlans(it) }

		/**
		 * Answer the set of
		 * [grammatical&#32;restrictions][GrammaticalRestrictionDescriptor] that
		 * have been attached to this bundle.
		 *
		 * @return
		 *   This bundle's grammatical restrictions.
		 */
		val A_Bundle.grammaticalRestrictions
			get() = dispatch { o_GrammaticalRestrictions(it) }

		/**
		 * Answer whether this bundle has any
		 * [grammatical&#32;restrictions][GrammaticalRestrictionDescriptor].
		 *
		 * @return
		 *   Whether this bundle has grammatical restrictions.
		 */
		val A_Bundle.hasGrammaticalRestrictions
			get() = dispatch { o_HasGrammaticalRestrictions(it) }

		/**
		 * Look up the [A_Macro] definition to invoke, given an [A_Tuple] of
		 * argument phrases.  Use the [A_Bundle]'s macro testing tree to find
		 * the macro definition to invoke.  Answer the [A_Tuple] of applicable
		 * macro definitions.
		 *
		 * Note that this testing tree approach is only applicable if all of the
		 * macro definitions are visible (defined in the current module or an
		 * ancestor.  That should be the *vast* majority of the use of macros,
		 * but when it isn't, other lookup approaches are necessary.
		 *
		 * @param argumentPhraseTuple
		 *   The argument phrases destined to be transformed by the macro.
		 * @return
		 *   The selected macro definitions.
		 */
		fun A_Bundle.lookupMacroByPhraseTuple(
			argumentPhraseTuple: A_Tuple
		): A_Tuple = dispatch {
			o_LookupMacroByPhraseTuple(it, argumentPhraseTuple)
		}

		/**
		 * Answer a [tuple][A_Tuple] that comprises all [macros][A_Macro]
		 * defined for this bundle.
		 *
		 * @return
		 *   The current macros of this bundle.
		 */
		val A_Bundle.macrosTuple: A_Tuple get() = dispatch { o_MacrosTuple(it) }

		/**
		 * Answer the name of this bundle.  It must be parsable as a method name
		 * according to the rules of the [MessageSplitter].
		 *
		 * @return
		 *   An [A_Atom] naming this bundle.
		 */
		val A_Bundle.message: A_Atom get() = dispatch { o_Message(it) }

		/**
		 * Answer a [message&#32;part][messageParts] produced by the
		 * [MessageSplitter] when applied to this bundle's name.  The part is a
		 * substring of the bundle name.  The index is one-based.
		 *
		 * @return
		 *   A string extracted from part of the bundle's message.
		 * @see [message]
		 */
		fun A_Bundle.messagePart(index: Int) : A_String =
			dispatch { o_MessagePart(it, index) }

		/**
		 * Answer the message parts produced by the [MessageSplitter] when
		 * applied to this bundle's name.  It's basically an [A_Tuple] of
		 * [A_String]s in the order the tokens appear in the bundle's name.
		 *
		 * @return
		 *   A tuple of strings extracted from the bundle's message.
		 * @see [message]
		 */
		val A_Bundle.messageParts: A_Tuple
			get() = dispatch { o_MessageParts(it) }

		/**
		 * Answer the [MessageSplitter] holding parse planning information for
		 * invocations of this message bundle.
		 *
		 * @return
		 *   The bundle's [MessageSplitter].
		 */
		val A_Bundle.messageSplitter get() = dispatch { o_MessageSplitter(it) }

		/**
		 * Answer the arity of this [A_Bundle], which must be the same for all
		 * bundles of its [A_Method].
		 *
		 * @return
		 *   The arity of this bundle.
		 */
		val A_Bundle.numArgs: Int get() = dispatch { o_NumArgs(it) }

		/**
		 * Remove a [grammatical][GrammaticalRestrictionDescriptor] from the
		 * receiver.
		 *
		 * @param grammaticalRestriction
		 *   The grammatical restriction to remove.
		 */
		fun A_Bundle.removeGrammaticalRestriction(
			grammaticalRestriction: A_GrammaticalRestriction
		) = dispatch {
			o_RemoveGrammaticalRestriction(it, grammaticalRestriction)
		}

		/**
		 * Remove a [macro][A_Macro] from the receiver.
		 *
		 * @param macro
		 *   The [A_Macro] to remove from this bundle.
		 */
		fun A_Bundle.removeMacro(macro: A_Macro) =
			dispatch { o_RemoveMacro(it, macro) }
	}
}
