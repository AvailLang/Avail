/*
 * A_Method.kt
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
package com.avail.descriptor.methods

import com.avail.descriptor.fiber.A_Fiber
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.parsing.A_DefinitionParsingPlan
import com.avail.descriptor.parsing.A_Lexer
import com.avail.descriptor.parsing.LexerDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.TupleTypeDescriptor
import com.avail.dispatch.LookupTree
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.MethodDefinitionException
import com.avail.exceptions.SignatureException
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.operand.TypeRestriction

/**
 * `A_Method` is an interface that specifies behavior specific to Avail
 * [methods][MethodDescriptor] that an [AvailObject] must implement.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface A_Method : A_ChunkDependable {
	/**
	 * Answer a [tuple][A_Tuple] that comprises all [definitions][A_Definition]
	 * of this [A_Method].
	 *
	 * @return
	 *   The current definitions of this method.
	 */
	fun definitionsTuple(): A_Tuple

	/**
	 * Answer the [set][A_Set] of
	 * [semantic&#32;restrictions][A_SemanticRestriction] which restrict the
	 * applicability and return type of this [A_Method] at relevant call sites.
	 *
	 * @return
	 *   The set of semantic restrictions on this method.
	 */
	fun semanticRestrictions(): A_Set

	/**
	 * Answer a [tuple][A_Tuple] that comprises all
	 * [tuple&#32;types][TupleTypeDescriptor] below which new
	 * [definitions][A_Definition] may no longer be added.
	 *
	 * @return
	 *   The current seals of this method.
	 */
	fun sealedArgumentsTypesTuple(): A_Tuple

	/**
	 * Add the [definition][A_Definition] to this [A_Method]. Causes dependent
	 * [chunks][L2Chunk] to be invalidated. Answer the
	 * [A_DefinitionParsingPlan]s that were created for the new definition.
	 *
	 * @param definition
	 *   The definition to be added.
	 * @throws SignatureException
	 *   If the definition could not be added.
	 */
	@Throws(SignatureException::class)
	fun methodAddDefinition(definition: A_Definition)

	/**
	 * Answer all [definitions][A_Definition] of this [A_Method] that could
	 * match the given [List] of argument types.
	 *
	 * @param argTypes
	 *   The [types][A_Type] of the formal parameters, ordered by position.
	 * @return
	 *   A [list][List] of definitions.
	 */
	fun filterByTypes(argTypes: List<A_Type>): List<A_Definition>

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
	fun definitionsAtOrBelow(
		argRestrictions: List<TypeRestriction>
	): List<A_Definition>

	/**
	 * Is the given [definition][A_Definition] present in this [A_Method]?
	 *
	 * @param imp
	 *   A definition.
	 * @return
	 *   `true` if the definition is present in this method, `false` otherwise.
	 */
	fun includesDefinition(imp: A_Definition): Boolean

	/**
	 * Answer the [definition][A_Definition] of this [A_Method] that should be
	 * invoked for the given [tuple][A_Tuple] of [argument&#32;types][A_Type].
	 * Use the testing tree to select a definition.
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
	fun lookupByTypesFromTuple(argumentTypeTuple: A_Tuple): A_Definition

	/**
	 * Answer the [definition][A_Definition] of this [A_Method] that should be
	 * invoked for the given values. Use the testing tree to select a
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
	fun lookupByValuesFromList(argumentList: List<A_BasicObject>): A_Definition

	/**
	 * Look up the macro [A_Definition] to invoke, given an [A_Tuple] of
	 * argument phrases.  Use the [A_Method]'s macro testing tree to find the
	 * macro definition to invoke.  Answer the [A_Tuple] of applicable macro
	 * definitions.
	 *
	 * Note that this testing tree approach is only applicable if all of the
	 * macro definitions are visible (defined in the current module or an
	 * ancestor.  That should be the *vast* majority of the use of macros, but
	 * when it isn't, other lookup approaches are necessary.
	 *
	 * @param argumentPhraseTuple
	 *   The argument phrases destined to be transformed by the macro.
	 * @return
	 *   The selected macro definitions.
	 */
	fun lookupMacroByPhraseTuple(argumentPhraseTuple: A_Tuple): A_Tuple

	/**
	 * Remove the specified [definition][A_Definition] from this [A_Method].
	 * Behaves idempotently.
	 *
	 * @param definition
	 *   A non-bootstrap definition.
	 */
	fun removeDefinition(definition: A_Definition)

	/**
	 * Answer the arity of this [A_Method].
	 *
	 * @return
	 *   The arity of this method.
	 */
	fun numArgs(): Int

	/**
	 * Add a [semantic&#32;restriction][A_SemanticRestriction] to this
	 * [A_Method]. Behaves idempotently.
	 *
	 * @param restriction
	 *   The semantic restriction to add.
	 */
	fun addSemanticRestriction(restriction: A_SemanticRestriction)

	/**
	 * Remove an extant [semantic][A_SemanticRestriction] from this method.
	 * Behaves idempotently.
	 *
	 * @param restriction
	 *   The semantic restriction to remove.
	 */
	fun removeSemanticRestriction(restriction: A_SemanticRestriction)

	/**
	 * Add a seal to this [A_Method]. Behaves idempotently.
	 *
	 * @param typeTuple
	 *   A [tuple][A_Tuple] of formal parameter [types][A_Type] that specifies
	 *   the location of the seal.
	 */
	fun addSealedArgumentsType(typeTuple: A_Tuple)

	/**
	 * Remove a seal from this [A_Method]. Behaves idempotently.
	 *
	 * @param typeTuple
	 *   A [tuple][A_Tuple] of formal parameter [types][A_Type] that specifies
	 *   the location of the seal.
	 */
	fun removeSealedArgumentsType(typeTuple: A_Tuple)

	/**
	 * Is this `A_Method` empty? A method is empty if it comprises no
	 * [definitions][A_Definition], no
	 * [semantic&#32;restrictions][A_SemanticRestriction], and no seals.
	 *
	 * @return
	 *   `true` if this method is empty, `false` otherwise.
	 */
	fun isMethodEmpty(): Boolean

	/**
	 * Answer the [tuple][A_Tuple] of [message][A_Bundle] that name this
	 * [A_Method].
	 *
	 * @return
	 *   A tuple of message bundles.
	 */
	fun bundles(): A_Set

	/**
	 * Specify that the given [message&#32;bundle][A_Bundle] names this
	 * [A_Method].
	 *
	 * @param bundle
	 *   A message bundle.
	 */
	fun methodAddBundle(bundle: A_Bundle)

	/**
	 * Answer the tuple of [macro][MacroDefinitionDescriptor] for this method.
	 * Their order is irrelevant, but fixed for use by the macro testing tree.
	 *
	 * @return
	 *   A tuple of [macro&#32;definitions][A_Definition].
	 */
	fun macroDefinitionsTuple(): A_Tuple

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
	fun chooseBundle(currentModule: A_Module): A_Bundle

	/**
	 * Set this method's lexer to the given lexer or nil.
	 *
	 * @param lexer
	 *   Either a [lexer][LexerDescriptor] or [nil][NilDescriptor.nil].
	 */
	fun setLexer(lexer: A_Lexer)

	/**
	 * Answer this method's lexer, or [NilDescriptor.nil].
	 *
	 * @return
	 *   The [A_Lexer] or `nil`.
	 */
	fun lexer(): A_Lexer

	/**
	 * Answer this method's [LookupTree], suitable for dispatching method
	 * invocations (i.e., finding out which [A_Definition] to actually invoke.
	 *
	 * Note that when fibers are running, this testing tree may be in flux, due
	 * to lazy expansion of parts of the tree.  In general this is not usually a
	 * problem, as volatile reads are used during dispatching, and a lock is
	 * held during actual expansion.
	 *
	 * @return
	 *   The method's type dispatch tree.
	 */
	fun testingTree(): LookupTree<A_Definition, A_Tuple>
}