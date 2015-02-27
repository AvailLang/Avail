/**
 * A_Method.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import java.util.List;
import com.avail.descriptor.MethodDescriptor.LookupTree;
import com.avail.exceptions.AvailErrorCode;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.utility.MutableOrNull;

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
	 * A_Definition definitions} of this {@linkplain A_Method method}.
	 *
	 * @return The current definitions of this method.
	 */
	A_Tuple definitionsTuple ();

	/**
	 * Answer the {@linkplain A_Set set} of {@linkplain A_SemanticRestriction
	 * semantic restrictions} which restrict the applicability and return type
	 * of this {@linkplain A_Method method} at relevant call sites.
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
	 * Add the {@linkplain A_Definition definition} to this {@linkplain A_Method
	 * method}. Causes dependent {@linkplain L2Chunk chunks} to be invalidated.
	 *
	 * Macro signatures and non-macro signatures should not be combined in the
	 * same method.
	 *
	 * @param definition The definition to be added.
	 * @throws SignatureException
	 *         If the definition could not be added.
	 */
	void methodAddDefinition (A_Definition definition)
		throws SignatureException;

	/**
	 * Answer all {@linkplain A_Definition definitions} of this {@linkplain
	 * A_Method method} that could match the given argument types.
	 *
	 * @param argTypes
	 *        The {@linkplain A_Type types} of the formal parameters, ordered by
	 *        position.
	 * @return A {@linkplain List list} of definitions.
	 */
	List<A_Definition> filterByTypes (List<? extends A_Type> argTypes);

	/**
	 * Answer all {@linkplain A_Definition definitions} of this {@linkplain
	 * A_Method method} that could match arguments conforming to the given
	 * {@linkplain A_Type types}, i.e., the definitions that could be invoked
	 * at runtime for a call site with the given static types.
	 *
	 * @param argTypes
	 *        The static types of the proposed arguments, ordered by position.
	 * @return A {@linkplain List list} of definitions.
	 */
	List<A_Definition> definitionsAtOrBelow (List<? extends A_Type> argTypes);

	/**
	 * Is the given {@linkplain A_Definition definition} present in this
	 * {@linkplain A_Method method}?
	 *
	 * @param imp
	 *        A definition.
	 * @return {@code true} if the definition is present in this method, {@code
	 *         false} otherwise.
	 */
	boolean includesDefinition (A_Definition imp);

	/**
	 * Answer the {@linkplain A_Definition definition} of this {@linkplain
	 * A_Method method} that should be invoked for the given {@linkplain
	 * A_Type argument types}. Use the {@linkplain #testingTree() testing tree}
	 * to select a definition.
	 *
	 * @param argumentTypeTuple
	 *        The {@linkplain A_Tuple tuple} of argument types, ordered by
	 *        position.
	 * @return The selected definition.
	 * @throws MethodDefinitionException
	 *         If {@linkplain MethodDefinitionException#noMethodDefinition() no
	 *         definition is applicable} or if {@linkplain
	 *         MethodDefinitionException#ambiguousMethodDefinition() multiple
	 *         definitions are applicable}.
	 */
	A_Definition lookupByTypesFromTuple (A_Tuple argumentTypeTuple)
		throws MethodDefinitionException;

	/**
	 * Answer the {@linkplain A_Definition definition} of this {@linkplain
	 * A_Method method} that should be invoked for the given values. Use the
	 * {@linkplain #testingTree() testing tree} to select a definition. If
	 * lookup fails, then write an appropriate {@linkplain AvailErrorCode error
	 * code} into {@code errorCode} and answer {@linkplain NilDescriptor#nil()
	 * nil}.
	 *
	 * @param argumentList
	 *        The {@linkplain A_Tuple tuple} of arguments, ordered by position.
	 * @param errorCode
	 *        A {@linkplain MutableOrNull container} for an {@linkplain
	 *        AvailErrorCode error code} in the event of failure. Possible
	 *        error codes are {@link AvailErrorCode#E_NO_METHOD_DEFINITION
	 *        E_NO_METHOD_DEFINITION} and {@link
	 *        AvailErrorCode#E_AMBIGUOUS_METHOD_DEFINITION
	 *        E_AMBIGUOUS_METHOD_DEFINITION}.
	 * @return The selected definition, or nil if no definition is applicable or
	 *         if multiple definitions are applicable.
	 */
	A_Definition lookupByValuesFromList (
		List<? extends A_BasicObject> argumentList,
		MutableOrNull<AvailErrorCode> errorCode);

	/**
	 * Remove the specified {@linkplain A_Definition definition} from this
	 * {@linkplain A_Method method}. Behaves idempotently.
	 *
	 * @param definition
	 *        A non-bootstrap definition.
	 */
	void removeDefinition (A_Definition definition);

	/**
	 * Answer the arity of this {@linkplain A_Method method}.
	 *
	 * @return The arity of this method.
	 */
	int numArgs ();

	/**
	 * Answer the {@linkplain LookupTree testing tree}, computing it first if
	 * necessary.
	 *
	 * @return The testing tree.
	 */
	LookupTree testingTree ();

	/**
	 * Add a {@linkplain A_SemanticRestriction semantic restriction} to
	 * this {@linkplain A_Method method}. Behaves idempotently.
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
	 * Add a seal to this {@linkplain A_Method method}. Behaves idempotently.
	 *
	 * @param typeTuple
	 *        A {@linkplain A_Tuple tuple} of formal parameter {@linkplain
	 *        A_Type types} that specifies the location of the seal.
	 */
	void addSealedArgumentsType (A_Tuple typeTuple);

	/**
	 * Remove a seal from this {@linkplain A_Method method}. Behaves
	 * idempotently.
	 *
	 * @param typeTuple
	 *        A {@linkplain A_Tuple tuple} of formal parameter {@linkplain
	 *        A_Type types} that specifies the location of the seal.
	 */
	void removeSealedArgumentsType (A_Tuple typeTuple);

	/**
	 * Is this {@linkplain A_Method method} empty? A method is empty if it
	 * comprises no {@linkplain A_Definition definitions}, no {@linkplain
	 * A_SemanticRestriction semantic restrictions}, and no seals.
	 *
	 * @return {@code true} if this method is empty, {@code false} otherwise.
	 */
	boolean isMethodEmpty ();

	/**
	 * Answer the {@linkplain A_Tuple tuple} of {@linkplain A_Bundle message
	 * bundles} that name this {@linkplain A_Method method}.
	 *
	 * @return A tuple of message bundles.
	 */
	A_Set bundles ();

	/**
	 * Specify that the given {@linkplain A_Bundle message bundle} names this
	 * {@linkplain A_Method method}.
	 *
	 * @param bundle
	 *        A message bundle.
	 */
	void methodAddBundle (A_Bundle bundle);

	/**
	 * Answer the {@linkplain TupleDescriptor tuple} of tuples of macro prefix
	 * {@linkplain FunctionDescriptor functions} for this method.  These
	 * functions allow parsing actions to be performed while parsing is still
	 * occurring, typically to bring new variable and constant {@linkplain
	 * DeclarationNodeDescriptor declarations} into scope.
	 *
	 * @return This method's macro prefix function lists.
	 */
	A_Tuple prefixFunctions ();

	/**
	 * Replace the method's tuple of macro prefix function tuples.
	 *
	 * @param functionTuples A tuple of tuples of functions.
	 */
	void prefixFunctions (A_Tuple functionTuples);

	/**
	 * @return
	 */
	A_Tuple macroDefinitionsTuple ();

	/**
	 * @param argumentPhraseTuple
	 * @param errorCode
	 * @return
	 */
	A_Definition lookupMacroByPhraseTuple (
		A_Tuple argumentPhraseTuple,
		MutableOrNull<AvailErrorCode> errorCode);

	/**
	 * @return
	 */
	LookupTree macroTestingTree ();
}
