/*
 * AvailElements.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

@file:Suppress("MemberVisibilityCanBePrivate")

package org.availlang.ide.anvil.language.psi

import avail.compiler.PragmaKind
import avail.compiler.SideEffectKind
import avail.descriptor.tokens.CommentTokenDescriptor
import avail.descriptor.tokens.LiteralTokenDescriptor
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.availlang.ide.anvil.language.AvailLanguage

////////////////////////////////////////////////////////////////////////////////
//                         Element type abstractions.                         //
////////////////////////////////////////////////////////////////////////////////

/**
 * The root [element&#32;type][IFileElementType] for an abstract syntax tree
 * (AST). The root node is always special, satisfying this type rather than
 * [AvailElementType].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object AvailRootElementType : IFileElementType("AV_ROOT", AvailLanguage)

/**
 * The base type for all non-root abstract syntax tree (AST) nodes. Custom
 * language support in IntelliJ requires that both the terminals and
 * nonterminals of Avail's formal grammar are covered by a common type, and this
 * is that type.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [AvailElementType].
 *
 * @param debugName
 *   The name of the element type, used for debugging purposes.
 */
sealed class AvailElementType(debugName: String) :
	IElementType(debugName, AvailLanguage)

/**
 * The base type for all terminals of Avail's formal grammar. Each subclass
 * corresponds directly with one of Avail's [token&#32;types][TokenType].
 *
 * @property tokenType
 *   The corresponding token type.
 * @see TokenType
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [TerminalElementType].
 *
 * @param debugName
 *   The name of the element type, used for debugging purposes.
 * @param tokenType
 *   The corresponding token type.
 */
abstract class TerminalElementType(
	debugName: String,
	val tokenType: TokenType
) : AvailElementType(debugName)

/**
 * The base type for all leaf nonterminals of Avail's formal grammar. Each
 * subclass corresponds directly with one of Avail's
 * [phrase&#32;kinds][PhraseKind].
 *
 * @property phraseKind
 *   The corresponding phrase kind.
 * @see PhraseKind
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [NonterminalElementType].
 *
 * @param debugName
 *   The name of the element type, used for debugging purposes.
 * @param phraseKind
 *   The corresponding phrase kind.
 */
abstract class NonterminalElementType(
	debugName: String,
	val phraseKind: PhraseKind
) : AvailElementType(debugName)

/**
 * The base type for all of Avail's pragmas. Each subclass corresponds directly
 * with one of Avail's [pragma&#32;kinds][PragmaKind].
 *
 * @property pragmaKind
 *   The corresponding pragma kind.
 * @see PragmaKind
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class PragmaElementType(
	debugName: String,
	val pragmaKind: PragmaKind
) : AvailElementType(debugName)

/**
 * The base type for all of Avail's top-level semantic program elements. Each
 * subclass corresponds directly with one of Avail's
 * [side-effect&#32;kinds][SideEffectKind].
 *
 * @property sideEffectKind
 *   The corresponding side effect kind.
 * @see SideEffectKind
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [TerminalElementType].
 *
 * @param debugName
 *   The name of the element type, used for debugging purposes.
 * @param sideEffectKind
 *   The corresponding side effect kind.
 * @param phraseKind
 *   The underlying phrase kind that achieves the side effect.
 */
abstract class SemanticElementType(
	debugName: String,
	val sideEffectKind: SideEffectKind,
	val phraseKind: PhraseKind
) : AvailElementType(debugName)

////////////////////////////////////////////////////////////////////////////////
//                                  Invalid.                                  //
////////////////////////////////////////////////////////////////////////////////

/**
 * A sentinel that represents an invalid or placeholder element.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETInvalid : AvailElementType("AV_INV")

////////////////////////////////////////////////////////////////////////////////
//                                 Terminals.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * A special type of token that is appended to the actual tokens of the file to
 * simplify end-of-file processing.
 *
 * @see TokenType.END_OF_FILE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETEndOfFile : TerminalElementType("AV_T_EOF", TokenType.END_OF_FILE)

/**
 * A sequence of characters suitable for an Avail identifier, which roughly
 * corresponds to characters in a Java identifier.
 *
 * @see TokenType.KEYWORD
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETKeyword : TerminalElementType("AV_T_KW", TokenType.KEYWORD)

/**
 * A literal token, detected at lexical scanning time. Only applicable for a
 * [LiteralTokenDescriptor].
 *
 * @see TokenType.LITERAL
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETLiteralToken : TerminalElementType("AV_T_LIT", TokenType.LITERAL)

/**
 * A string literal token, to support the "Search in strings…" feature. Avail
 * has no special lexical model support for string literal tokens, so Anvil must
 * synthesize these on demand.
 *
 * @see TokenType.LITERAL
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETStringLiteralToken : TerminalElementType("AV_T_SLIT", TokenType.LITERAL)

/**
 * A single operator character, which is anything that isn't whitespace, a
 * keyword character, or an Avail reserved character.
 *
 * @see TokenType.OPERATOR
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETOperator : TerminalElementType("AV_T_OP", TokenType.OPERATOR)

/**
 * A token that is the entirety of an Avail method/class comment.  This is text
 * contained between slash-asterisk and asterisk-slash.  Only applicable for
 * [CommentTokenDescriptor].
 *
 * @see TokenType.COMMENT
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETComment : TerminalElementType("AV_T_COMMENT", TokenType.COMMENT)

/**
 * A token representing one or more whitespace characters separating other
 * tokens.  These tokens are skipped by the normal parsing machinery, although
 * some day we'll provide a notation within method names to capture whitespace
 * tokens as arguments.
 *
 * @see TokenType.WHITESPACE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETWhitespace : TerminalElementType("AV_T_WS", TokenType.WHITESPACE)

////////////////////////////////////////////////////////////////////////////////
//                               Nonterminals.                                //
////////////////////////////////////////////////////////////////////////////////

/**
 * The kind of a parse marker.
 *
 * @see PhraseKind.MARKER_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETMarker :
	NonterminalElementType("AV_NT_MARKER", PhraseKind.MARKER_PHRASE)

/**
 * The kind of an assignment phrase.
 *
 * @see PhraseKind.ASSIGNMENT_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETAssignment :
	NonterminalElementType("AV_NT_ASSIGN", PhraseKind.ASSIGNMENT_PHRASE)

/**
 * The kind of a block phrase.
 *
 * @see PhraseKind.BLOCK_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETBlock :
	NonterminalElementType("AV_NT_BLOCK", PhraseKind.BLOCK_PHRASE)

/**
 * The kind of a literal phrase.
 *
 * @see PhraseKind.LITERAL_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETLiteralPhrase :
	NonterminalElementType("AV_NT_LIT", PhraseKind.LITERAL_PHRASE)

/**
 * The kind of a reference phrase.
 *
 * @see PhraseKind.REFERENCE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETReference :
	NonterminalElementType("AV_NT_REF", PhraseKind.REFERENCE_PHRASE)

/**
 * The kind of a super cast phrase.
 *
 * @see PhraseKind.ASSIGNMENT_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETSuperCast :
	NonterminalElementType("AV_NT_SUPER", PhraseKind.SUPER_CAST_PHRASE)

/**
 * The kind of a send phrase.
 *
 * @see PhraseKind.SEND_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETSend :
	NonterminalElementType("AV_NT_SEND", PhraseKind.SEND_PHRASE)

/**
 * The kind of a list phrase.
 *
 * @see PhraseKind.LIST_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETList :
	NonterminalElementType("AV_NT_LIST", PhraseKind.LIST_PHRASE)

/**
 * The kind of a permuted list phrase.
 *
 * @see PhraseKind.ASSIGNMENT_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETPermutedList :
	NonterminalElementType("AV_NT_PLIST", PhraseKind.PERMUTED_LIST_PHRASE)

/**
 * The kind of a variable use phrase.
 *
 * @see PhraseKind.VARIABLE_USE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETVariableUse :
	NonterminalElementType("AV_NT_VARUSE", PhraseKind.VARIABLE_USE_PHRASE)

/**
 * The kind of a sequence phrase.
 *
 * @see PhraseKind.SEQUENCE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETSequence :
	NonterminalElementType("AV_NT_SEQ", PhraseKind.SEQUENCE_PHRASE)

/**
 * The kind of a first-of-sequence phrase.
 *
 * @see PhraseKind.FIRST_OF_SEQUENCE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETFirstOfSequence :
	NonterminalElementType("AV_NT_1SEQ", PhraseKind.FIRST_OF_SEQUENCE_PHRASE)

/**
 * The kind of a label phrase.
 *
 * @see PhraseKind.LIST_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETLabel :
	NonterminalElementType("AV_NT_LABEL", PhraseKind.LABEL_PHRASE)

/**
 * The kind of a local variable declaration phrase.
 *
 * @see PhraseKind.LOCAL_VARIABLE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETLocalVariable :
	NonterminalElementType("AV_NT_LVAR", PhraseKind.LOCAL_VARIABLE_PHRASE)

/**
 * The kind of a local constant declaration phrase.
 *
 * @see PhraseKind.LOCAL_CONSTANT_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETLocalConstant :
	NonterminalElementType("AV_NT_LCONST", PhraseKind.LOCAL_CONSTANT_PHRASE)

/**
 * The kind of a module variable declaration phrase.
 *
 * @see PhraseKind.MODULE_VARIABLE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETModuleVariablePhrase :
	NonterminalElementType("AV_NT_MVAR", PhraseKind.MODULE_VARIABLE_PHRASE)

/**
 * The kind of a module constant declaration phrase.
 *
 * @see PhraseKind.MODULE_CONSTANT_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETModuleConstantPhrase :
	NonterminalElementType("AV_NT_MCONST", PhraseKind.MODULE_CONSTANT_PHRASE)

/**
 * The kind of a primitive failure reason variable declaration phrase.
 *
 * @see PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETPrimitiveFailureReason :
	NonterminalElementType(
		"AV_NT_PFAIL",
		PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE
	)

/**
 * A statement phrase built from an expression.  At the moment, only assignments
 * and sends can be expression-as-statement phrases.
 *
 * @see PhraseKind.LOCAL_VARIABLE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETExpressionAsStatement :
	NonterminalElementType(
		"AV_NT_EXPRSTMT",
		PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE
	)

/**
 * The result of a macro substitution.
 *
 * @see PhraseKind.LOCAL_VARIABLE_PHRASE
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETMacroSubstitution :
	NonterminalElementType("AV_NT_MSUB", PhraseKind.MACRO_SUBSTITUTION_PHRASE)

////////////////////////////////////////////////////////////////////////////////
//                                  Pragmas.                                  //
////////////////////////////////////////////////////////////////////////////////

/**
 * Assert some quality of the virtual machine.
 *
 * @see PragmaKind.PRAGMA_CHECK
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETCheck : PragmaElementType("AV_P_CHK", PragmaKind.PRAGMA_CHECK)

/**
 * Identify the bootstrap method definer.
 *
 * @see PragmaKind.PRAGMA_METHOD
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETBootstrapMethod :
	PragmaElementType("AV_P_METHOD", PragmaKind.PRAGMA_METHOD)

/**
 * Identify the bootstrap macro definer.
 *
 * @see PragmaKind.PRAGMA_MACRO
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETBootstrapMacro :
	PragmaElementType("AV_P_MACRO", PragmaKind.PRAGMA_MACRO)

/**
 * Identify the bootstrap stringifier.
 *
 * @see PragmaKind.PRAGMA_STRINGIFY
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETStringify : PragmaElementType("AV_P_STR", PragmaKind.PRAGMA_STRINGIFY)

/**
 * Identify a bootstrap lexer.
 *
 * @see PragmaKind.PRAGMA_LEXER
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETBootstrapLexer :
	PragmaElementType("AV_P_LEXER", PragmaKind.PRAGMA_LEXER)

////////////////////////////////////////////////////////////////////////////////
//                                 Semantics.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * An atom definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.ATOM_DEFINITION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETAtom :
	SemanticElementType(
		"AV_S_ATOM",
		SideEffectKind.ATOM_DEFINITION_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A method definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.METHOD_DEFINITION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETMethod :
	SemanticElementType(
		"AV_S_METHOD",
		SideEffectKind.METHOD_DEFINITION_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * An abstract method definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.ABSTRACT_METHOD_DEFINITION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETAbstractMethod :
	SemanticElementType(
		"AV_S_ABSTRACT",
		SideEffectKind.ABSTRACT_METHOD_DEFINITION_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A forward method definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.FORWARD_METHOD_DEFINITION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETForwardMethod :
	SemanticElementType(
		"AV_S_FWD",
		SideEffectKind.FORWARD_METHOD_DEFINITION_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A macro definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.MACRO_DEFINITION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETMacro :
	SemanticElementType(
		"AV_S_MACRO",
		SideEffectKind.MACRO_DEFINITION_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A semantic restriction definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.SEMANTIC_RESTRICTION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETSemanticRestriction :
	SemanticElementType(
		"AV_S_RSEM",
		SideEffectKind.SEMANTIC_RESTRICTION_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A grammatical restriction definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.GRAMMATICAL_RESTRICTION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETGrammaticalRestriction :
	SemanticElementType(
		"AV_S_RGRAM",
		SideEffectKind.GRAMMATICAL_RESTRICTION_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A seal definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.SEAL_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETSeal :
	SemanticElementType(
		"AV_S_SEAL",
		SideEffectKind.SEAL_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A lexer definition. The underlying phrase is always a send.
 *
 * @see SideEffectKind.LEXER_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETLexer :
	SemanticElementType(
		"AV_S_LEXER",
		SideEffectKind.LEXER_KIND,
		PhraseKind.SEND_PHRASE
	)

/**
 * A module constant definition. The underlying phrase is always a module
 * constant definition phrase.
 *
 * @see SideEffectKind.METHOD_DEFINITION_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETModuleConstantDefinition :
	SemanticElementType(
		"AV_S_MCONST",
		SideEffectKind.MODULE_CONSTANT_KIND,
		PhraseKind.MODULE_CONSTANT_PHRASE
	)

/**
 * A module variable definition. The underlying phrase is always a module
 * variable definition phrase.
 *
 * @see SideEffectKind.MODULE_VARIABLE_KIND
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object ETModuleVariableDefinition :
	SemanticElementType(
		"AV_S_MVAR",
		SideEffectKind.MODULE_VARIABLE_KIND,
		PhraseKind.MODULE_VARIABLE_PHRASE
	)

////////////////////////////////////////////////////////////////////////////////
//                                Token sets.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * The [token&#32;types][AvailElementType] that represent comments. [PsiBuilder]
 * uses this information to skip over comments when building Psi trees.
 */
val commentTokenTypes = TokenSet.create(ETComment)

/**
 * The [token&#32;types][AvailElementType] that represent string literals.
 * "Search in strings…" looks inside the lexemes of these tokens when searching.
 */
val stringLiteralTokenTypes = TokenSet.create(ETStringLiteralToken)
