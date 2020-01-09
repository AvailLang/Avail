/*
 * A_Method.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.descriptor.methods;

import com.avail.descriptor.*;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.parsing.A_Lexer;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.dispatch.LookupTree;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;

import java.util.List;

/**
 * {@code A_Method} is an interface that specifies behavior specific to Avail
 * {@linkplain MethodDescriptor methods} that an {@link AvailObject} must
 * implement.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public interface A_Method
extends A_ChunkDependable
{
	/**
	 * Answer a {@linkplain A_Tuple tuple} that comprises all {@linkplain
	 * A_Definition definitions} of this {@code A_Method}.
	 *
	 * @return The current definitions of this method.
	 */
	A_Tuple definitionsTuple ();

	/**
	 * Answer the {@linkplain A_Set set} of {@linkplain A_SemanticRestriction
	 * semantic restrictions} which restrict the applicability and return type
	 * of this {@code A_Method} at relevant call sites.
	 *
	 * @return The set of semantic restrictions on this method.
	 */
	A_Set semanticRestrictions ();

	/**
	 * Answer a {@linkplain A_Tuple tuple} that comprises all {@linkplain
	 * TupleTypeDescriptor tuple types} below which new {@linkplain A_Definition
	 * definitions} may no longer be added.
	 *
	 * @return The current seals of this method.
	 */
	A_Tuple sealedArgumentsTypesTuple ();

	/**
	 * Add the {@linkplain A_Definition definition} to this {@code A_Method}.
	 * Causes dependent {@linkplain L2Chunk chunks} to be invalidated. Answer
	 * the {@link A_DefinitionParsingPlan}s that were created for the new
	 * definition.
	 *
	 * @param definition The definition to be added.
	 * @throws SignatureException If the definition could not be added.
	 */
	void methodAddDefinition (A_Definition definition)
		throws SignatureException;

	/**
	 * Answer all {@linkplain A_Definition definitions} of this {@code A_Method}
	 * that could match the given argument types.
	 *
	 * @param argTypes
	 *        The {@linkplain A_Type types} of the formal parameters, ordered by
	 *        position.
	 * @return A {@linkplain List list} of definitions.
	 */
	List<A_Definition> filterByTypes (List<? extends A_Type> argTypes);

	/**
	 * Answer all {@linkplain A_Definition definitions} of this {@code A_Method}
	 * that could match arguments conforming to the given {@linkplain A_Type
	 * types}, i.e., the definitions that could be invoked at runtime for a call
	 * site with the given static types.
	 *
	 * @param argRestrictions
	 *        The static types of the proposed arguments, ordered by position.
	 * @return A {@linkplain List list} of definitions.
	 */
	List<A_Definition> definitionsAtOrBelow (
		List<TypeRestriction> argRestrictions);

	/**
	 * Is the given {@linkplain A_Definition definition} present in this
	 * {@code A_Method}?
	 *
	 * @param imp
	 *        A definition.
	 * @return {@code true} if the definition is present in this method, {@code
	 *         false} otherwise.
	 */
	boolean includesDefinition (A_Definition imp);

	/**
	 * Answer the {@linkplain A_Definition definition} of this {@code A_Method}
	 * that should be invoked for the given {@linkplain A_Type argument types}.
	 * Use the testing tree to select a definition.
	 *
	 * @param argumentTypeTuple
	 *        The {@linkplain A_Tuple tuple} of argument types, ordered by
	 *        position.
	 * @return The selected definition.
	 * @throws MethodDefinitionException
	 *         In the event the lookup doesn't produce exactly one definition.
	 *         Possible error codes are {@link
	 *         AvailErrorCode#E_NO_METHOD_DEFINITION} and {@link
	 *         AvailErrorCode#E_AMBIGUOUS_METHOD_DEFINITION}.
	 */
	A_Definition lookupByTypesFromTuple (A_Tuple argumentTypeTuple)
	throws MethodDefinitionException;

	/**
	 * Answer the {@linkplain A_Definition definition} of this {@code A_Method}
	 * that should be invoked for the given values. Use the testing tree to
	 * select a definition. If lookup fails, then write an appropriate
	 * {@linkplain AvailErrorCode error code} into {@code errorCode} and answer
	 * {@linkplain NilDescriptor#nil nil}.
	 *
	 * @param argumentList
	 *        The {@linkplain List} of arguments, ordered by position.
	 * @return The selected definition if it's unique.
	 * @throws MethodDefinitionException
	 *         In the event the lookup doesn't produce exactly one definition.
	 *         Possible error codes are {@link
	 *         AvailErrorCode#E_NO_METHOD_DEFINITION} and {@link
	 *         AvailErrorCode#E_AMBIGUOUS_METHOD_DEFINITION}.
	 */
	A_Definition lookupByValuesFromList (
		List<? extends A_BasicObject> argumentList)
	throws MethodDefinitionException;

	/**
	 * Look up the macro {@link A_Definition} to invoke, given an {@link
	 * A_Tuple} of argument phrases.  Use the {@code A_Method}'s macro testing
	 * tree to find the macro definition to invoke.  Answer the {@link A_Tuple}
	 * of applicable macro definitions.
	 *
	 * <p>Note that this testing tree approach is only applicable if all of the
	 * macro definitions are visible (defined in the current module or an
	 * ancestor.  That should be the <em>vast</em> majority of the use of
	 * macros, but when it isn't, other lookup approaches are necessary.</p>
	 *
	 * @param argumentPhraseTuple
	 *        The argument phrases destined to be transformed by the macro.
	 * @return The selected macro definitions.
	 */
	A_Tuple lookupMacroByPhraseTuple (A_Tuple argumentPhraseTuple);

	/**
	 * Remove the specified {@linkplain A_Definition definition} from this
	 * {@code A_Method}. Behaves idempotently.
	 *
	 * @param definition
	 *        A non-bootstrap definition.
	 */
	void removeDefinition (A_Definition definition);

	/**
	 * Answer the arity of this {@code A_Method}.
	 *
	 * @return The arity of this method.
	 */
	int numArgs ();

	/**
	 * Add a {@linkplain A_SemanticRestriction semantic restriction} to
	 * this {@code A_Method}. Behaves idempotently.
	 *
	 * @param restriction
	 *        The semantic restriction to add.
	 */
	void addSemanticRestriction (A_SemanticRestriction restriction);

	/**
	 * Remove an extant {@linkplain A_SemanticRestriction semantic
	 * restriction} from this method. Behaves idempotently.
	 *
	 * @param restriction
	 *        The semantic restriction to remove.
	 */
	void removeSemanticRestriction (A_SemanticRestriction restriction);

	/**
	 * Add a seal to this {@code A_Method}. Behaves idempotently.
	 *
	 * @param typeTuple
	 *        A {@linkplain A_Tuple tuple} of formal parameter {@linkplain
	 *        A_Type types} that specifies the location of the seal.
	 */
	void addSealedArgumentsType (A_Tuple typeTuple);

	/**
	 * Remove a seal from this {@code A_Method}. Behaves idempotently.
	 *
	 * @param typeTuple
	 *        A {@linkplain A_Tuple tuple} of formal parameter {@linkplain
	 *        A_Type types} that specifies the location of the seal.
	 */
	void removeSealedArgumentsType (A_Tuple typeTuple);

	/**
	 * Is this {@code A_Method} empty? A method is empty if it comprises no
	 * {@linkplain A_Definition definitions}, no {@linkplain
	 * A_SemanticRestriction semantic restrictions}, and no seals.
	 *
	 * @return {@code true} if this method is empty, {@code false} otherwise.
	 */
	boolean isMethodEmpty ();

	/**
	 * Answer the {@linkplain A_Tuple tuple} of {@linkplain A_Bundle message
	 * bundles} that name this {@code A_Method}.
	 *
	 * @return A tuple of message bundles.
	 */
	A_Set bundles ();

	/**
	 * Specify that the given {@linkplain A_Bundle message bundle} names this
	 * {@code A_Method}.
	 *
	 * @param bundle
	 *        A message bundle.
	 */
	void methodAddBundle (A_Bundle bundle);

	/**
	 * Answer the tuple of {@linkplain MacroDefinitionDescriptor macro
	 * definitions} for this method.  Their order is irrelevant, but fixed for
	 * use by the macro testing tree.
	 *
	 * @return A tuple of {@link A_Definition macro definitions}.
	 */
	A_Tuple macroDefinitionsTuple ();

	/**
	 * Of this method's bundles, choose one that is visible in the current
	 * module.  The current module is determined by the current {@link A_Fiber
	 * fiber}'s {@link AvailLoader}.  If none are visible, choose one at random.
	 *
	 * @param currentModule The current module being loaded.
	 * @return One of this method's {@link A_Bundle bundles}.
	 */
	A_Bundle chooseBundle (final A_Module currentModule);

	/**
	 * Set this method's lexer to the given lexer or nil.
	 *
	 * @param lexer
	 *        Either a {@linkplain LexerDescriptor lexer} or {@link
	 *        NilDescriptor#nil nil}.
	 */
	void setLexer (final A_Lexer lexer);

	/**
	 * Answer this method's lexer, or {@link NilDescriptor#nil}.
	 *
	 * @return The {@link A_Lexer} or {@code nil}.
	 */
	A_Lexer lexer ();

	/**
	 * Answer this method's {@link LookupTree}, suitable for dispatching method
	 * invocations (i.e., finding out which {@link A_Definition} to actually
	 * invoke.
	 *
	 * <p>Note that when fibers are running, this testing tree may be in flux,
	 * due to lazy expansion of parts of the tree.  In general this is not
	 * usually a problem, as volatile reads are used during dispatching, and a
	 * lock is held during actual expansion.</p>
	 *
	 * @return The method's type dispatch tree.
	 */
	LookupTree<A_Definition, A_Tuple> testingTree ();
}
