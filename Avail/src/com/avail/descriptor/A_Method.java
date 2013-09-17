/**
 * A_Method.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import com.avail.exceptions.MethodDefinitionException;
import com.avail.exceptions.SignatureException;

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
	A_Tuple definitionsTuple ();

	/**
	 * Answer this method's {@linkplain SetDescriptor set} of {@linkplain
	 * SemanticRestrictionDescriptor semantic restrictions} which restrict the
	 * applicability and return type of relevant call sites.
	 *
	 * @return The set of semantic restrictions.
	 */
	A_Set semanticRestrictions ();

	A_Tuple sealedArgumentsTypesTuple ();

	/**
	 * Add the {@linkplain DefinitionDescriptor definition} to the receiver, a
	 * {@linkplain MethodDefinitionDescriptor method}.  Causes dependent chunks
	 * to be invalidated.
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
	 * Dispatch to the descriptor.
	 */
	List<A_Definition> filterByTypes (List<A_Type> argTypes);

	/**
	 * Dispatch to the descriptor.
	 */
	List<A_Definition> definitionsAtOrBelow (List<? extends A_Type> argTypes);

	/**
	 * Dispatch to the descriptor.
	 */
	boolean includesDefinition (A_Definition imp);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Definition lookupByTypesFromTuple (A_Tuple argumentTypeTuple)
		throws MethodDefinitionException;

	/**
	 * Dispatch to the descriptor.
	 */
	A_Definition lookupByValuesFromList (
		List<? extends A_BasicObject> argumentList);

	/**
	 * Dispatch to the descriptor.
	 */
	void removeDefinition (A_Definition definition);

	/**
	 * This method also occurs in {@link A_RawFunction}.
	 */
	int numArgs ();

	/**
	 * Dispatch to the descriptor.
	 */
	LookupTree testingTree ();

	/**
	 * Add a {@linkplain SemanticRestrictionDescriptor semantic restriction} to
	 * this method.
	 *
	 * @param restriction The semantic restriction to add.
	 */
	void addSemanticRestriction (A_SemanticRestriction restriction);

	/**
	 * Remove an extant {@linkplain SemanticRestrictionDescriptor semantic
	 * restriction} from this method.
	 *
	 * @param restriction The semantic restriction to remove.
	 */
	void removeSemanticRestriction (A_SemanticRestriction restriction);

	void addSealedArgumentsType (A_Tuple typeTuple);

	void removeSealedArgumentsType (A_Tuple typeTuple);

	/**
	 * @return
	 */
	boolean isMethodEmpty ();

	/**
	 * Answer the {@linkplain A_Tuple tuple} of {@linkplain A_Bundle message
	 * bundles} that name this method.
	 *
	 * @return A tuple of message bundles.
	 */
	A_Set bundles ();

	void methodAddBundle (A_Bundle bundle);
}
