/**
 * A_Phrase.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import com.avail.annotations.Nullable;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.evaluation.*;

/**
 * {@code A_Atom} is an interface that specifies the atom-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Phrase
extends A_BasicObject
{
	/**
	 * @return
	 */
	A_Atom apparentSendName ();

	/**
	 * @return
	 */
	A_Phrase argumentsListNode ();

	/**
	 * @return
	 */
	A_Tuple argumentsTuple ();

	/**
	 * @param aBlock
	 */
	void childrenDo (Continuation1<A_Phrase> aBlock);

	/**
	 * @param aBlock
	 */
	void childrenMap (
		Transformer1<A_Phrase, A_Phrase> aBlock);

	/**
	 * @return
	 */
	A_Phrase copyMutableParseNode ();

	/**
	 * @param newParseNode
	 * @return
	 */
	A_Phrase copyWith (A_Phrase newParseNode);

	/**
	 * @return
	 */
	A_Phrase declaration ();

	/**
	 * Also declared in {@link A_Type} for {@linkplain FunctionTypeDescriptor
	 * function types}.
	 *
	 * @return The set of declared exception types.
	 */
	A_Set declaredExceptions ();

	/**
	 * @return
	 */
	AvailObject declaredType ();

	/**
	 * @param codeGenerator
	 */
	void emitEffectOn (AvailCodeGenerator codeGenerator);

	/**
	 * @param codeGenerator
	 */
	void emitValueOn (AvailCodeGenerator codeGenerator);

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	A_Phrase expression ();

	/**
	 * @return
	 */
	A_Tuple expressionsTuple ();

	/**
	 * Return the parse node's expression type, which is the type of object that
	 * will be produced by this parse node.
	 *
	 * <p>Also implemented in {@link A_Type} (for parse node types).</p>
	 *
	 * @return The {@linkplain TypeDescriptor type} of the {@link AvailObject}
	 *         that will be produced by this parse node.
	 */
	A_Type expressionType ();

	/**
	 * @param accumulatedStatements
	 */
	void flattenStatementsInto (
		List<A_Phrase> accumulatedStatements);

	/**
	 * @param module
	 * @return
	 */
	A_RawFunction generateInModule (A_Module module);

	/**
	 * @param isLastUse
	 */
	void isLastUse (boolean isLastUse);

	/**
	 * @return
	 */
	boolean isLastUse ();

	/**
	 * @return
	 */
	A_BasicObject markerValue ();

	/**
	 * Answer this send node's {@linkplain MessageBundleDescriptor message
	 * bundle}.
	 *
	 * @return The message bundle.
	 */
	A_Bundle bundle ();

	/**
	 * @return
	 */
	A_Tuple neededVariables ();

	/**
	 * @param neededVariables
	 */
	void neededVariables (A_Tuple neededVariables);

	/**
	 * @return
	 */
	A_BasicObject outputParseNode ();

	/**
	 * Also declared in {@link A_Type} for {@linkplain ParseNodeTypeDescriptor
	 * parse node types}.
	 *
	 * @return
	 */
	ParseNodeKind parseNodeKind ();

	/**
	 * @return
	 */
	int primitive ();

	/**
	 * Also declared in {@link A_Type} for {@linkplain FunctionTypeDescriptor
	 * function types}.
	 *
	 * @return The type of this {@linkplain SendNodeDescriptor send node}.
	 */
	A_Type returnType ();

	/**
	 * Also defined in {@link A_RawFunction}.
	 *
	 * @return The source code line number on which this {@linkplain
	 * BlockNodeDescriptor block} begins.
	 */
	public int startingLineNumber ();

	/**
	 * @return
	 */
	A_Tuple statements ();

	/**
	 * @return
	 */
	A_Tuple statementsTuple ();

	/**
	 * @return
	 */
	AvailObject stripMacro ();

	/**
	 * @return
	 */
	A_Token token ();

	/**
	 * @param parent
	 */
	void validateLocally (@Nullable A_Phrase parent);

	/**
	 * Answer the {@linkplain DeclarationNodeDescriptor variable declaration}
	 * being used by this {@linkplain ReferenceNodeDescriptor reference} or
	 * {@linkplain AssignmentNodeDescriptor assignment}.
	 *
	 * @return The variable.
	 */
	A_Phrase variable ();
}
