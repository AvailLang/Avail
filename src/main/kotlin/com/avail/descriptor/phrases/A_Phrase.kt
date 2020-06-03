/*
 * A_Phrase.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.phrases

import com.avail.compiler.AvailCodeGenerator
import com.avail.compiler.AvailCompiler
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.dispatch
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tokens.A_Token
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.PhraseTypeDescriptor
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.interpreter.Primitive

/**
 * An `A_Phrase` is generally produced when the [AvailCompiler] parses source
 * code of an [A_Module].  Avail defines a variety of phrases, which are
 * implemented by the subclasses of [PhraseDescriptor].  [A_Phrase] is a
 * sub-interface of [A_BasicObject], and defines the operations that phrases
 * implement.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Phrase : A_BasicObject {
	companion object {
		/**
		 * Answer the [A_Atom] that this phrase is a
		 * [send][SendPhraseDescriptor] of.  If this is a
		 * [macro][MacroSubstitutionPhraseDescriptor] invocation, answer the
		 * [A_Atom] that the original expression, prior to macro transformation,
		 * was an apparent send of.  If the phrase is neither a send
		 * nor a macro invocation, answer [nil].
		 *
		 * @return
		 *   The [A_Atom] apparently sent by this phrase.
		 */
		fun A_Phrase.apparentSendName(): A_Atom =
			dispatch { o_ApparentSendName(it) }

		/**
		 * Answer the [list&#32;phrase][ListPhraseDescriptor] that provides
		 * arguments to this [send][SendPhraseDescriptor] phrase.  If this is a
		 * [macro][MacroSubstitutionPhraseDescriptor] invocation, these are the
		 * arguments supplied to the macro's output, which must be a send
		 * phrase.
		 *
		 * This method is only applicable to send phrases and macros that
		 * produce send phrases.
		 *
		 * @return
		 *   This [send][SendPhraseDescriptor] phrase's arguments list.
		 */
		fun A_Phrase.argumentsListNode(): A_Phrase =
			dispatch { o_ArgumentsListNode(it) }

		/**
		 * Answer the [tuple][A_Tuple] of [argument][PhraseKind.ARGUMENT_PHRASE]
		 * declaration phrases of this [block][BlockPhraseDescriptor] phrase.
		 *
		 * @return
		 *   The tuple of argument declarations for this block phrase.
		 */
		fun A_Phrase.argumentsTuple(): A_Tuple =
			dispatch { o_ArgumentsTuple(it) }

		/**
		 * Answer this send phrase's message [bundle][A_Bundle].  If this is a
		 * [macro][MacroSubstitutionPhraseDescriptor] invocation, answer the
		 * message bundle that is the one sent by the resulting send phrase.
		 *
		 * This method is only applicable to send phrases or macros that produce
		 * send phrases.
		 *
		 * @return
		 *   The message bundle that will be invoked.
		 */
		fun A_Phrase.bundle(): A_Bundle =
			dispatch { o_Bundle(it) }

		/**
		 * Perform the given action for each child phrase of this phrase.
		 *
		 * @param action
		 *   The action to perform with this phrase's direct subphrases.
		 */
		fun A_Phrase.childrenDo(action: (A_Phrase) -> Unit) =
			dispatch { o_ChildrenDo(it, action) }

		/**
		 * Perform the given transformation for each child phrase of this
		 * phrase, writing the result of the transformation back into this
		 * phrase.  This phrase must be [mutable][Mutability.MUTABLE].
		 *
		 * @param transformer
		 *   How to transform each child phrase.
		 */
		fun A_Phrase.childrenMap(transformer: (A_Phrase) -> A_Phrase) =
			dispatch { o_ChildrenMap(it, transformer) }

		/**
		 * If the receiver is mutable, make its subobjects immutable and return
		 * it. Otherwise, make a mutable copy of the receiver and return it.
		 *
		 * @return
		 *   The mutable receiver or a mutable copy of it.
		 */
		fun A_Phrase.copyMutablePhrase(): A_Phrase =
			dispatch { o_CopyMutablePhrase(it) }

		/**
		 * Create a new [list&#32;phrase][ListPhraseDescriptor] like the
		 * receiver, but with one more phrase added to the end of the list.
		 *
		 * @param newPhrase
		 *   The phrase to append.
		 * @return
		 *   A new list phrase with the given phrase appended.
		 */
		fun A_Phrase.copyWith(newPhrase: A_Phrase): A_Phrase =
			dispatch { o_CopyWith(it, newPhrase) }

		/**
		 * Create a new [list&#32;phrase][ListPhraseDescriptor] with the
		 * elements of the given list phrase appended to the ones in the
		 * receiver.
		 *
		 * @param newListPhrase
		 *   The list phrase containing phrases to append.
		 * @return
		 *   A new list phrase with the given list phrase's subphrases appended.
		 */
		fun A_Phrase.copyConcatenating(newListPhrase: A_Phrase): A_Phrase =
			dispatch { o_CopyConcatenating(it, newListPhrase) }

		/**
		 * Answer the [declaration][DeclarationPhraseDescriptor] phrase
		 * referenced by this [variable&#32;use][VariableUsePhraseDescriptor]
		 * phrase.  If this phrase is a
		 * [macro][MacroSubstitutionPhraseDescriptor] invocation, answer the
		 * declaration referenced by the variable use produced by the macro.
		 *
		 * @return
		 *   The declaration referenced by this variable use.
		 */
		fun A_Phrase.declaration(): A_Phrase =
			dispatch { o_Declaration(it) }

		/**
		 * Answer the [set][A_Set] of exception types that are declared by this
		 * [block][BlockPhraseDescriptor] phrase.
		 *
		 * A method by the same name is also defined for function
		 * [types][A_Type].
		 *
		 * @return The set of declared exception types for this block phrase.
		 */
		fun A_Phrase.declaredExceptions(): A_Set =
			dispatch { o_DeclaredExceptions(it) }

		/**
		 * Answer the type of the variable, constant, or argument declared by
		 * this [declaration&#32;phrase][DeclarationPhraseDescriptor].
		 *
		 * @return
		 *   The declaration phrase's type.
		 */
		fun A_Phrase.declaredType(): A_Type =
			dispatch { o_DeclaredType(it) }

		/**
		 * Emit code to push each value produced by the expressions of a
		 * [list&#32;phrase][ListPhraseDescriptor] or
		 * [permuted&#32;list&#32;phrase][PermutedListPhraseDescriptor].
		 *
		 * @param codeGenerator
		 *   Where to write the L1 instructions.
		 */
		fun A_Phrase.emitAllValuesOn(codeGenerator: AvailCodeGenerator) =
			dispatch { o_EmitAllValuesOn(it, codeGenerator) }

		/**
		 * Emit code to perform the effect of this phrase, leaving the operand
		 * stack the same as before these instructions.
		 *
		 * @param codeGenerator
		 *   Where to write the L1 instructions.
		 */
		fun A_Phrase.emitEffectOn(codeGenerator: AvailCodeGenerator) =
			dispatch { o_EmitEffectOn(it, codeGenerator) }

		/**
		 * Emit code to perform this phrase, leaving its result on the stack.
		 *
		 * @param codeGenerator
		 *   Where to write the L1 instructions.
		 */
		fun A_Phrase.emitValueOn(codeGenerator: AvailCodeGenerator) =
			dispatch { o_EmitValueOn(it, codeGenerator) }

		/**
		 * If this phrase is an [assignment][AssignmentPhraseDescriptor], answer
		 * the expression that produces the value to assign.  If this phrase is
		 * an [expression-as-statement][ExpressionAsStatementPhraseDescriptor],
		 * answer the expression to treat as a statement.  If this phrase is a
		 * [super-cast][SuperCastPhraseDescriptor], answer the expression that
		 * produces the actual value to send.  If this phrase is a
		 * [macro][MacroSubstitutionPhraseDescriptor], use the above rules on
		 * the output of the macro.
		 *
		 * @return
		 *   The phrase's 'expression', which varies by phrase kind.
		 */
		fun A_Phrase.expression(): A_Phrase =
			dispatch { o_Expression(it) }

		/**
		 * Extract the Nth expression of this [list][ListPhraseDescriptor]. If
		 * this is a [permuted][PermutedListPhraseDescriptor] list phrase, do
		 * not transform the index.
		 *
		 * @param index
		 *   Which expression to extract.
		 * @return
		 *   The chosen phrase.
		 */
		fun A_Phrase.expressionAt(index: Int): A_Phrase =
			dispatch { o_ExpressionAt(it, index) }

		/**
		 * Answer the number of expressions in this [list][ListPhraseDescriptor]
		 * phrase.
		 *
		 * @return
		 *   The list's size.
		 */
		fun A_Phrase.expressionsSize(): Int =
			dispatch { o_ExpressionsSize(it) }

		/**
		 * Answer the tuple of expressions in this [list][ListPhraseDescriptor]
		 * phrase.
		 *
		 * @return
		 *   The list's expressions, as a [tuple][A_Tuple].
		 */
		fun A_Phrase.expressionsTuple(): A_Tuple =
			dispatch { o_ExpressionsTuple(it) }

		/**
		 * Return the phrase's expression type, which is the type of object that
		 * will be produced by this phrase.
		 *
		 * Also implemented in [A_Type], for phrase types.
		 *
		 * @return
		 *   The [type][TypeDescriptor] of the [AvailObject] that will be
		 *   produced by this phrase.
		 */
		fun A_Phrase.expressionType(): A_Type =
			dispatch { o_ExpressionType(it) }

		/**
		 * If this phrase is a [sequence][SequencePhraseDescriptor], take any
		 * statements within it that are also sequences, and flatten them all
		 * into one sequence, returning the [List] of statements.  Handle
		 * [first-of-sequence][FirstOfSequencePhraseDescriptor] phrases
		 * correctly, allowing the resulting structure to be up to two layers
		 * deep.
		 *
		 * @param accumulatedStatements
		 *   The flattened list of my statements.
		 */
		fun A_Phrase.flattenStatementsInto(
			accumulatedStatements: MutableList<A_Phrase>
		) = dispatch { o_FlattenStatementsInto(it, accumulatedStatements) }

		/**
		 * Compile this [block][BlockPhraseDescriptor] phrase into a
		 * [raw&#32;function][CompiledCodeDescriptor], recording within it that
		 * it was compiled within the given [module][A_Module].
		 *
		 * @param module
		 *   The [A_Module] in which the compilation happened.
		 * @return
		 *   An [A_RawFunction] based on the block phrase.
		 */
		fun A_Phrase.generateInModule(module: A_Module): A_RawFunction =
			dispatch { o_GenerateInModule(it, module) }

		/**
		 * This is an expression acting as an argument, a recursive
		 * [list][ListPhraseDescriptor] phrase of arguments (perhaps
		 * [permuted][PermutedListPhraseDescriptor]), or a
		 * [super-cast][SuperCastPhraseDescriptor].  Answer whether it either is
		 * or contains within the recursive list structure a super-cast phrase.
		 *
		 * @return
		 *   Whether this is a super-cast phrase or a recursive list or permuted
		 *   list containing one.
		 */
		fun A_Phrase.hasSuperCast(): Boolean =
			dispatch { o_HasSuperCast(it) }

		/**
		 * Answer the [phrase][PhraseDescriptor] producing the value to be
		 * assigned during initialization of a
		 * [variable][PhraseKind.VARIABLE_USE_PHRASE]
		 * [declaration][DeclarationPhraseDescriptor].  If there is no
		 * initialization expression, answer [nil].
		 *
		 * @return
		 *   The variable declaration's expression that produces a value for its
		 *   initializing assignment.
		 */
		fun A_Phrase.initializationExpression(): AvailObject =
			dispatch { o_InitializationExpression(it) }

		/**
		 * Alter whether this [variable&#32;use][VariableUsePhraseDescriptor] is
		 * the chronologically last time its variable will be used within this
		 * block. This may be performed on immutable phrases.  Do not expose
		 * this function to the Avail language, as it is only intended as a
		 * convenience for bookkeeping during code generation.
		 *
		 * @param isLastUse
		 *   What to set the variable use's last-use flag to.
		 */
		fun A_Phrase.isLastUse(isLastUse: Boolean) =
			dispatch { o_IsLastUse(it, isLastUse) }

		/**
		 * Answer whether this [variable&#32;use][VariableUsePhraseDescriptor]
		 * is the chronologically last time its variable will be used within
		 * this block.
		 *
		 * @return
		 *   The value of this variable use's last-use flag.
		 */
		fun A_Phrase.isLastUse(): Boolean =
			dispatch { o_IsLastUse(it) }

		/**
		 * Answer whether this phrase is a
		 * [macro][MacroSubstitutionPhraseDescriptor] substitution phrase.
		 *
		 * @return
		 *   Whether this is a phrase at which macro substitution took place.
		 */
		fun A_Phrase.isMacroSubstitutionNode(): Boolean =
			dispatch { o_IsMacroSubstitutionNode(it) }

		/**
		 * Extract the last expression of this [list[ListPhraseDescriptor]
		 * phrase. The client must ensure there is at least one, and that this
		 * is a list phrase.
		 *
		 * @return
		 *   The last phrase of the list.
		 */
		fun A_Phrase.lastExpression(): A_Phrase =
			dispatch { o_LastExpression(it) }

		/**
		 * Answer the [list][ListPhraseDescriptor] phrase contained within this
		 * [permuted][PermutedListPhraseDescriptor] list phrase.  The order of
		 * the elements is the order in which they occurred in the source.
		 *
		 * @return
		 *   The permuted list phrase's underlying list phrase.
		 */
		fun A_Phrase.list(): A_Phrase =
			dispatch { o_List(it) }

		/**
		 * This [module&#32;constant's][PhraseKind.MODULE_CONSTANT_PHRASE]
		 * actual value, or this
		 * [module&#32;variable's][PhraseKind.MODULE_VARIABLE_PHRASE] actual
		 * [variable][A_Variable].
		 *
		 * @return
		 *   The module constant's value or module variable's actual variable.
		 */
		fun A_Phrase.literalObject(): A_BasicObject =
			dispatch { o_LiteralObject(it) }

		/**
		 * The receiver is a [macro][MacroSubstitutionPhraseDescriptor].  Answer
		 * the [send][SendPhraseDescriptor] that was transformed by the macro
		 * body.
		 *
		 * @return
		 *   The original send phrase of this macro substitution.
		 */
		fun A_Phrase.macroOriginalSendNode(): A_Phrase =
			dispatch { o_MacroOriginalSendNode(it) }

		/**
		 * Answer a [marker&#32;phrase's][MarkerPhraseDescriptor] marker value,
		 * which can be any [AvailObject].
		 *
		 * @return
		 *   The marker phrase's marker value.
		 */
		fun A_Phrase.markerValue(): A_BasicObject =
			dispatch { o_MarkerValue(it) }

		/**
		 * The [tuple][A_Tuple] of [declaration][DeclarationPhraseDescriptor]
		 * phrases accessed by this [block][BlockPhraseDescriptor] phrase.  This
		 * value is set during code generation, even if the block phrase is
		 * immutable.  It should not be made visible to the Avail language.
		 *
		 * @return
		 *   The tuple of "outer" declarations that will need to be captured by
		 *   this block when the resulting raw function is closed into a
		 *   function.
		 */
		fun A_Phrase.neededVariables(): A_Tuple =
			dispatch { o_NeededVariables(it) }

		/**
		 * Update the [tuple][A_Tuple] of
		 * [declaration][DeclarationPhraseDescriptor] phrases accessed by this
		 * [block][BlockPhraseDescriptor] phrase.  This value is set during code
		 * generation, even if the block phrase is immutable.  It should not be
		 * made visible to the Avail language.
		 *
		 * @param neededVariables
		 *   The tuple of outer declarations that this block accesses.
		 */
		fun A_Phrase.neededVariables(neededVariables: A_Tuple) =
			dispatch { o_NeededVariables(it, neededVariables) }

		/**
		 * The phrase that this [macro][MacroSubstitutionPhraseDescriptor]
		 * phrase generated to use as its replacement.
		 *
		 * @return
		 *   This macro phrase's output.
		 */
		fun A_Phrase.outputPhrase(): A_Phrase =
			dispatch { o_OutputPhrase(it) }

		/**
		 * Answer this phrase's [PhraseKind].
		 *
		 * Also declared in [A_Type] for
		 * [phrase&#32;types][PhraseTypeDescriptor].
		 *
		 * @return The [PhraseKind] of this phrase.
		 */
		fun A_Phrase.phraseKind(): PhraseKind =
			dispatch { o_PhraseKind(it) }

		/**
		 * Test whether this phrase has a [PhraseKind] that is equal to or a
		 * subkind of the given [PhraseKind].
		 *
		 * Also declared in A_Type, so the same operation applies both to
		 * phrases and to phrase types.
		 *
		 * @param expectedPhraseKind
		 *   The [PhraseKind] to test this phrase for.
		 * @return
		 *   Whether the receiver, a phrase, has a type whose [phraseKind] is at
		 *   or below the specified [PhraseKind].
		 */
		fun A_Phrase.phraseKindIsUnder(
			expectedPhraseKind: PhraseKind
		): Boolean = dispatch { o_PhraseKindIsUnder(it, expectedPhraseKind) }

		/**
		 * Answer the permutation from a
		 * [permutation][PermutedListPhraseDescriptor] list phrase.  The
		 * permutation is a [tuple][A_Tuple] of integers between `1` and the
		 * number of elements, where each value occurs exactly once, and the
		 * tuple is *not* in ascending order.
		 *
		 * @return
		 *   The permutation list phrase's permutation.
		 */
		fun A_Phrase.permutation(): A_Tuple =
			dispatch { o_Permutation(it) }

		/**
		 * Answer either `null` or the [Primitive] from a block phrase.
		 *
		 * @return
		 *   The block phrase's primitive or `null`.
		 */
		fun A_Phrase.primitive(): Primitive? =
			dispatch { o_Primitive(it) }

		/**
		 * Answer this [block][BlockPhraseDescriptor] phrase's starting line
		 * number in the source.
		 *
		 * Also defined in [A_RawFunction].
		 *
		 * @return
		 *   The source code line number on which this
		 *   [block][BlockPhraseDescriptor] phrase begins in the source.
		 */
		fun A_Phrase.startingLineNumber(): Int =
			dispatch { o_StartingLineNumber(it) }

		/**
		 * Answer the [tuple][A_Tuple] of statement phrases in this
		 * [sequence][SequencePhraseDescriptor] phrase.
		 *
		 * @return
		 *   A tuple of phrases.
		 */
		fun A_Phrase.statements(): A_Tuple =
			dispatch { o_Statements(it) }

		/**
		 * Iterate through each [sequence][PhraseKind.SEQUENCE_PHRASE]
		 * recursively within the receiver, applying the action to each
		 * contained [statement][PhraseKind.STATEMENT_PHRASE] phrase.
		 *
		 * @param continuation
		 *   What to do with each statement.
		 */
		fun A_Phrase.statementsDo(
			continuation: (A_Phrase) -> Unit
		) = dispatch { o_StatementsDo(it, continuation) }

		/**
		 * Answer this [block][BlockPhraseDescriptor] phrase's [tuple][A_Tuple]
		 * of [statement][PhraseKind.STATEMENT_PHRASE] phrase.
		 *
		 * @return
		 *   The tuple of statements in this block phrase.
		 */
		fun A_Phrase.statementsTuple(): A_Tuple =
			dispatch { o_StatementsTuple(it) }

		/**
		 * Given a [list][ListPhraseDescriptor] phrase, a
		 * [permuted][PermutedListPhraseDescriptor] list phrase, or a
		 * [macro][MacroSubstitutionPhraseDescriptor] substitution phrase, find
		 * all macro substitution phrases at or recursively inside it (but only
		 * within the nested list structure), answering a duplicate like the
		 * original, but with the macro phrases replaced by their output
		 * phrases.
		 *
		 * @return
		 *   The receiver list structure with its macro phrases replaced by
		 *   their outputs.
		 */
		fun A_Phrase.stripMacro(): A_Phrase =
			dispatch { o_StripMacro(it) }

		/**
		 * If this is a [super&#32;cast&#32;phrase][SuperCastPhraseDescriptor],
		 * then answer the type by which this argument should be looked up.  If
		 * it is not and does not contain a super cast phrase in its recursive
		 * list phrase structure, then answer bottom.  Otherwise create a
		 * (recursive) tuple type where elements that are supercasts provide
		 * their lookup types and the
		 * rest provide bottom.
		 *
		 * @return
		 *   A tuple type with which to compute a type union with the runtime
		 *   argument types to use for looking up a method definition at a call
		 *   site.  May be bottom.
		 */
		fun A_Phrase.superUnionType(): A_Type =
			dispatch { o_SuperUnionType(it) }

		/**
		 * Answer the [token][A_Token] which was used in the construction of a
		 * [variable&#32;use][VariableUsePhraseDescriptor] phrase, a
		 * [declaration][DeclarationPhraseDescriptor] phrase, a
		 * [literal][LiteralPhraseDescriptor], or a macro that resolves to one
		 * of those.
		 *
		 * @return
		 *   The token used in this phrase.
		 */
		fun A_Phrase.token(): A_Token =
			dispatch { o_Token(it) }

		/**
		 * Answer the [tuple][A_Token] of [tokens][A_Token] that contributed to
		 * the phrase.
		 *
		 * @return
		 *   The requested tuple of [tokens][A_Token].
		 */
		fun A_Phrase.tokens(): A_Tuple =
			dispatch { o_Tokens(it) }

		/**
		 * Answer the [phrase][A_Phrase] that produced the type of the
		 * declaration. Answer [nil][NilDescriptor.nil] if there was no such
		 * phrase.
		 *
		 * @return
		 *   The requested [phrase][A_Phrase] or [nil].
		 */
		fun A_Phrase.typeExpression(): A_Phrase =
			dispatch { o_TypeExpression(it) }

		/**
		 * Validate this phrase, without also validating
		 * [block][BlockPhraseDescriptor] phrases that occur within this phrase.
		 *
		 * @param parent
		 *   The phrase that contains this phrase, or `null`.
		 */
		fun A_Phrase.validateLocally(parent: A_Phrase?) =
			dispatch { o_ValidateLocally(it, parent) }

		/**
		 * Answer the [variable&#32;use][VariableUsePhraseDescriptor] phrase
		 * that is used by this [reference][ReferencePhraseDescriptor] or
		 * [assignment][AssignmentPhraseDescriptor].
		 *
		 * @return
		 *   The variable use phrase.
		 */
		fun A_Phrase.variable(): A_Phrase =
			dispatch { o_Variable(it) }
	}
}
