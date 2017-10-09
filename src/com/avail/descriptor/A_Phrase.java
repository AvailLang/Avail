/**
 * A_Phrase.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.Primitive;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import java.util.List;

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
	 * Answer this send node's {@linkplain MessageBundleDescriptor message
	 * bundle}.
	 *
	 * @return The message bundle.
	 */
	A_Bundle bundle ();

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
	 * Emit code to push each value produced by the expressions of a {@linkplain
	 * ListNodeDescriptor list node} or {@linkplain PermutedListNodeDescriptor
	 * permuted list node}.
	 *
	 * @param codeGenerator Where to write the L1 instructions.
	 */
	void emitAllValuesOn (AvailCodeGenerator codeGenerator);

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
	 * Extract the N<sup>th</sup> expression.
	 *
	 * @param index Which expression to extract.
	 * @return The chosen phrase.
	 */
	A_Phrase expressionAt (int index);

	/**
	 * Answer the number of expressions in this list phrase.
	 *
	 * @return The list's size.
	 */
	int expressionsSize ();

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
	 * This is an expression acting as an argument, a recursive {@linkplain
	 * ListNodeDescriptor list node} of arguments, a recursive {@linkplain
	 * PermutedListNodeDescriptor permuted list node} of arguments, or a
	 * {@linkplain SuperCastNodeDescriptor super-cast node}.  Answer whether it
	 * either is or contains (within the recursive list structure) a super-cast
	 * node.
	 *
	 * @return Whether this is a super-cast node or a recursive list or permuted
	 *         list containing one.
	 */
	boolean hasSuperCast ();

	/**
	 * @return
	 */
	AvailObject initializationExpression ();

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
	boolean isMacroSubstitutionNode ();

	/**
	 * @return
	 */
	A_Phrase list ();

	/**
	 * @return
	 */
	A_BasicObject literalObject ();

	/**
	 * The receiver is a {@link ParseNodeKind#MACRO_SUBSTITUTION macro
	 * substitution phrase}.  Answer the {@link ParseNodeKind#SEND_NODE send
	 * phrase} that was transformed by the macro body.
	 *
	 * @return The original send phrase of this macro substitution.
	 */
	A_Phrase macroOriginalSendNode ();

	/**
	 * @return
	 */
	A_BasicObject markerValue ();

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
	A_Phrase outputParseNode ();

	/**
	 * Also declared in {@link A_Type} for {@linkplain ParseNodeTypeDescriptor
	 * parse node types}.
	 *
	 * @return
	 */
	ParseNodeKind parseNodeKind ();

	/**
	 * Also declared in A_Type, so the same operation applies both to phrases
	 * and to phrase types.
	 *
	 * @param expectedParseNodeKind
	 *        The {@link ParseNodeKind} to test this phrase for.
	 * @return Whether the receiver, a phrase, has a type whose {@link
	 *         #parseNodeKind()} is at or below the specified {@link
	 *         ParseNodeKind}.
	 */
	boolean parseNodeKindIsUnder (
		ParseNodeKind expectedParseNodeKind);

	/**
	 * @return
	 */
	A_Tuple permutation ();

	/**
	 * @return
	 */
	@Nullable Primitive primitive ();

	/**
	 * Also defined in {@link A_RawFunction}.
	 *
	 * @return The source code line number on which this {@linkplain
	 * BlockNodeDescriptor block} begins.
	 */
	int startingLineNumber ();

	/**
	 * @return
	 */
	A_Tuple statements ();

	/**
	 * Iterate through each {@linkplain ParseNodeKind#SEQUENCE_NODE statement
	 * phrase} recursively within the receiver, applying the {@linkplain
	 * Continuation1NotNull continuation} to each.
	 *
	 * @param continuation
	 *        A continuation.
	 */
	void statementsDo (Continuation1NotNull<A_Phrase> continuation);

	/**
	 * @return
	 */
	A_Tuple statementsTuple ();

	/**
	 * @return
	 */
	A_Phrase stripMacro ();

	/**
	 * If this is a {@link SuperCastNodeDescriptor super cast node}, then answer
	 * the type by which this argument should be looked up.  If it is not and
	 * does not contain a super cast node in its recursive list node structure,
	 * then answer bottom.  Otherwise create a (recursive) tuple type where
	 * elements that are supercasts provide their lookup types and the rest
	 * provide bottom.
	 *
	 * @return A tuple type with which to compute a type union with the runtime
	 *         argument types to use for looking up a method definition at a
	 *         call site.  May be bottom.
	 */
	A_Type superUnionType ();

	/**
	 * @return
	 */
	A_Token token ();

	/**
	 * Answer the {@linkplain A_Token tuple} of {@linkplain A_Token tokens} that
	 * comprise the {@linkplain SendNodeDescriptor send phrase}.
	 *
	 * @return The requested {@linkplain A_Token tokens}.
	 */
	A_Tuple tokens ();

	/**
	 * Answer the {@linkplain A_Phrase phrase} that produced the type of the
	 * declaration.  Answer {@link NilDescriptor#nil nil} if there was no such
	 * phrase.
	 *
	 * @return The requested {@linkplain A_Phrase phrase} or nil.
	 */
	A_Phrase typeExpression ();

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
