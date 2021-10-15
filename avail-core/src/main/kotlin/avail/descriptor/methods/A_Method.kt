/*
 * A_Method.kt
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
package avail.descriptor.methods

import avail.descriptor.bundles.A_Bundle
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.module.A_Module
import avail.descriptor.parsing.A_DefinitionParsingPlan
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor
import avail.descriptor.sets.A_Set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.TupleTypeDescriptor
import avail.dispatch.LookupTree
import avail.exceptions.AvailErrorCode
import avail.exceptions.MethodDefinitionException
import avail.exceptions.SignatureException
import avail.interpreter.execution.AvailLoader
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.operand.TypeRestriction

/**
 * `A_Method` is an interface that specifies behavior specific to Avail
 * [methods][MethodDescriptor] that an [AvailObject] must implement.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface A_Method : A_ChunkDependable {
	companion object
	{
		/**
		 * Answer a [tuple][A_Tuple] that comprises all
		 * [definitions][A_Definition] of this [A_Method].
		 *
		 * @return
		 *   The current definitions of this method.
		 */
		val A_Method.definitionsTuple: A_Tuple
			get() = dispatch { o_DefinitionsTuple(it) }

		/**
		 * Answer the [set][A_Set] of
		 * [semantic&#32;restrictions][A_SemanticRestriction] which restrict the
		 * applicability and return type of this [A_Method] at relevant call
		 * sites.
		 *
		 * @return
		 *   The set of semantic restrictions on this method.
		 */
		val A_Method.semanticRestrictions: A_Set
			get() = dispatch { o_SemanticRestrictions(it) }

		/**
		 * Answer a [tuple][A_Tuple] that comprises all
		 * [tuple&#32;types][TupleTypeDescriptor] below which new
		 * [definitions][A_Definition] may no longer be added.
		 *
		 * @return
		 *   The current seals of this method.
		 */
		val A_Method.sealedArgumentsTypesTuple: A_Tuple
			get() = dispatch { o_SealedArgumentsTypesTuple(it) }

		/**
		 * Add the [definition][A_Definition] to this [A_Method]. Causes
		 * dependent [chunks][L2Chunk] to be invalidated. Answer the
		 * [A_DefinitionParsingPlan]s that were created for the new definition.
		 *
		 * @param definition
		 *   The definition to be added.
		 * @throws SignatureException
		 *   If the definition could not be added.
		 */
		@Throws(SignatureException::class)
		fun A_Method.methodAddDefinition(definition: A_Definition) =
			dispatch { o_MethodAddDefinition(it, definition) }

		/**
		 * Answer all [definitions][A_Definition] of this [A_Method] that could
		 * match the given [List] of argument types.
		 *
		 * @param argTypes
		 *   The [types][A_Type] of the formal parameters, ordered by position.
		 * @return
		 *   A [list][List] of definitions.
		 */
		fun A_Method.filterByTypes(argTypes: List<A_Type>): List<A_Definition> =
			dispatch { o_FilterByTypes(it, argTypes) }

		/**
		 * Answer all [definitions][A_Definition] of this [A_Method] that could
		 * match arguments conforming to the given [types][A_Type], i.e., the
		 * definitions that could be invoked at runtime for a call site with the
		 * given static types.
		 *
		 * @param argRestrictions
		 *   The static types of the proposed arguments, ordered by position.
		 * @return
		 *   A [list][List] of definitions.
		 */
		fun A_Method.definitionsAtOrBelow(
			argRestrictions: List<TypeRestriction>
		): List<A_Definition> =
			dispatch { o_DefinitionsAtOrBelow(it, argRestrictions) }

		/**
		 * Is the given [definition][A_Definition] present in this [A_Method]?
		 *
		 * @param definition
		 *   A definition.
		 * @return
		 *   `true` if the definition is present in this method, `false`
		 *   otherwise.
		 */
		fun A_Method.includesDefinition(definition: A_Definition): Boolean =
			dispatch { o_IncludesDefinition(it, definition) }

		/**
		 * Answer the [definition][A_Definition] of this [A_Method] that should
		 * be invoked for the given [tuple][A_Tuple] of
		 * [argument&#32;types][A_Type]. Use the testing tree to select a
		 * definition.
		 *
		 * @param argumentTypeTuple
		 *   The [tuple][A_Tuple] of argument types, ordered by position.
		 * @return
		 *   The selected definition.
		 * @throws MethodDefinitionException
		 *   In the event the lookup doesn't produce exactly one definition.
		 *   Possible error codes are:
		 *   * [AvailErrorCode.E_NO_METHOD_DEFINITION], and
		 *   * [AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION].
		 */
		@Throws(MethodDefinitionException::class)
		fun A_Method.lookupByTypesFromTuple(
			argumentTypeTuple: A_Tuple
		): A_Definition =
			dispatch { o_LookupByTypesFromTuple(it, argumentTypeTuple) }

		/**
		 * Answer the [definition][A_Definition] of this [A_Method] that should
		 * be invoked for the given values. Use the testing tree to select a
		 * definition. If lookup fails, then write an appropriate
		 * [error&#32;code][AvailErrorCode] into `errorCode` and answer
		 * [nil][NilDescriptor.nil].
		 *
		 * @param argumentList
		 *   The [List] of arguments, ordered by position.
		 * @return
		 *   The selected definition if it's unique.
		 * @throws MethodDefinitionException
		 *   In the event the lookup doesn't produce exactly one definition.
		 *   Possible error codes are:
		 *   * [AvailErrorCode.E_NO_METHOD_DEFINITION] and
		 *   * [AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION].
		 */
		@Throws(MethodDefinitionException::class)
		fun A_Method.lookupByValuesFromList(
			argumentList: List<A_BasicObject>
		): A_Definition =
			dispatch { o_LookupByValuesFromList(it, argumentList) }

		/**
		 * Remove the specified [definition][A_Definition] from this [A_Method].
		 * Behaves idempotently.
		 *
		 * @param definition
		 *   A non-bootstrap definition.
		 */
		fun A_Method.removeDefinition(definition: A_Definition) =
			dispatch { o_RemoveDefinition(it, definition) }

		/**
		 * Answer the arity of this [A_Method].
		 *
		 * @return
		 *   The arity of this method.
		 */
		val A_Method.numArgs: Int get() = dispatch { o_NumArgs(it) }

		/**
		 * Add a [semantic&#32;restriction][A_SemanticRestriction] to this
		 * [A_Method]. Behaves idempotently.
		 *
		 * @param restriction
		 *   The semantic restriction to add.
		 */
		fun A_Method.addSemanticRestriction(
			restriction: A_SemanticRestriction
		) = dispatch { o_AddSemanticRestriction(it, restriction) }

		/**
		 * Remove an extant [semantic][A_SemanticRestriction] from this method.
		 * Behaves idempotently.
		 *
		 * @param restriction
		 *   The semantic restriction to remove.
		 */
		fun A_Method.removeSemanticRestriction(
			restriction: A_SemanticRestriction
		) = dispatch { o_RemoveSemanticRestriction(it, restriction) }

		/**
		 * Add a seal to this [A_Method]. Behaves idempotently.
		 *
		 * @param typeTuple
		 *   A [tuple][A_Tuple] of formal parameter [types][A_Type] that
		 *   specifies the location of the seal.
		 */
		fun A_Method.addSealedArgumentsType(typeTuple: A_Tuple) =
			dispatch { o_AddSealedArgumentsType(it, typeTuple) }

		/**
		 * Remove a seal from this [A_Method]. Behaves idempotently.
		 *
		 * @param typeTuple
		 *   A [tuple][A_Tuple] of formal parameter [types][A_Type] that
		 *   specifies the location of the seal.
		 */
		fun A_Method.removeSealedArgumentsType(typeTuple: A_Tuple) =
			dispatch { o_RemoveSealedArgumentsType(it, typeTuple) }

		/**
		 * Is this `A_Method` empty? A method is empty if it comprises no
		 * [definitions][A_Definition], no
		 * [semantic&#32;restrictions][A_SemanticRestriction], and no seals.
		 *
		 * @return
		 *   `true` if this method is empty, `false` otherwise.
		 */
		val A_Method.isMethodEmpty: Boolean
			get() = dispatch { o_IsMethodEmpty(it) }

		/**
		 * Answer the [tuple][A_Tuple] of [message][A_Bundle] that name this
		 * [A_Method].
		 *
		 * @return
		 *   A tuple of message bundles.
		 */
		val A_Method.bundles: A_Set
			get() = dispatch { o_Bundles(it) }

		/**
		 * Specify that the given [message&#32;bundle][A_Bundle] names this
		 * [A_Method].
		 *
		 * @param bundle
		 *   A message bundle.
		 */
		fun A_Method.methodAddBundle(bundle: A_Bundle) =
			dispatch { o_MethodAddBundle(it, bundle) }

		/**
		 * As part of unloading a module, remove the bundle from this
		 * [A_Method].
		 *
		 * @param bundle
		 *   The message bundle to remove from this method.
		 */
		fun A_Method.methodRemoveBundle(bundle: A_Bundle) =
			dispatch { o_MethodRemoveBundle(it, bundle) }

		/**
		 * Answer the tuple of [macro][MacroDescriptor] for this method. Their
		 * order is irrelevant, but fixed for use by the macro testing tree.
		 *
		 * @return
		 *   A tuple of [macro&#32;definitions][A_Definition].
		 */
		val A_Method.macrosTuple: A_Tuple
			get() = dispatch { o_MacrosTuple(it) }

		/**
		 * The membership of this [method][MethodDescriptor] has changed.
		 * Invalidate anything that depended on the previous membership,
		 * including any [LookupTree]s or dependent [L2Chunk]s.
		 */
		fun A_Method.membershipChanged() = dispatch { o_MembershipChanged(it) }

		/**
		 * Of this method's bundles, choose one that is visible in the current
		 * module.  The current module is determined by the current
		 * [fiber][A_Fiber]'s [AvailLoader].  If none are visible, choose one at
		 * random.
		 *
		 * @param currentModule
		 *   The current module being loaded.
		 * @return
		 *   One of this method's [bundles][A_Bundle].
		 */
		fun A_Method.chooseBundle(currentModule: A_Module): A_Bundle =
			dispatch { o_ChooseBundle(it, currentModule) }

		/**
		 * This method's lexer, or [NilDescriptor.nil].
		 */
		var A_Method.lexer: A_Lexer
			get() = dispatch { o_Lexer(it) }
			set(value) = dispatch { o_SetLexer(it, value) }

		/**
		 * Answer this method's [LookupTree], suitable for dispatching method
		 * invocations (i.e., finding out which [A_Definition] to actually
		 * invoke.
		 *
		 * Note that when fibers are running, this testing tree may be in flux,
		 * due to lazy expansion of parts of the tree.  In general this is not
		 * usually a problem, as volatile reads are used during dispatching, and
		 * a lock is held during actual expansion.
		 *
		 * @return
		 *   The method's type dispatch tree.
		 */
		val A_Method.testingTree: LookupTree<A_Definition, A_Tuple>
			get() = dispatch { o_TestingTree(it) }
	}
}
