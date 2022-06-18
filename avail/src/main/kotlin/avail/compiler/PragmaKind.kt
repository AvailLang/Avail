/*
 * PragmaKind.kt
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

package avail.compiler

import avail.compiler.scanning.LexingState
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom.DECLARE_STRINGIFIER
import avail.descriptor.module.A_Module.Companion.trueNamesForStringName
import avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.syntheticLiteralNodeFor
import avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.tokens.A_Token
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.Primitive.PrimitiveHolder.Companion.primitiveByName

/**
 * These are the tokens that are understood directly by the Avail compiler.
 *
 * @property lexeme
 *   The Java [String] form of the lexeme.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `PragmaKind`.
 *
 * @param lexeme
 *   The Java [lexeme&#32;string][String], i.e. the text of the token.
 */
enum class PragmaKind constructor(val lexeme: String)
{
	/**
	 * Module header token: Occurs in pragma strings to assert some quality of
	 * the virtual machine.
	 */
	PRAGMA_CHECK("check")
	{
		override fun applyThen(
			compiler: AvailCompiler,
			pragmaToken: A_Token,
			pragmaValue: String,
			state: LexingState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Malformed pragma at %s on line %d:",
					"Pragma should have the form 'check=version=<version>")
				return
			}
			val propertyName = parts[0].trim { it <= ' ' }
			val propertyValue = parts[1].trim { it <= ' ' }
			compiler.pragmaCheckThen(
				state, pragmaToken, propertyName, propertyValue, success)
		}
	},

	/**
	 * Module header token: Occurs in pragma strings to define bootstrap method.
	 */
	PRAGMA_METHOD("method")
	{
		override fun applyThen(
			compiler: AvailCompiler,
			pragmaToken: A_Token,
			pragmaValue: String,
			state: LexingState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Malformed pragma at %s on line %d:",
					"method pragma to have the form "
						+ "'method=<primitiveName>=<name>'")
				return
			}
			val primName = parts[0].trim { it <= ' ' }
			val methodName = parts[1].trim { it <= ' ' }
			val prim = primitiveByName(primName)
			if (prim === null)
			{
				state.compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Malformed pragma at %s on line %d:",
					"method pragma to reference a valid primitive, " +
						"not '$primName'")
				return
			}
			compiler.bootstrapMethodThen(
				state, pragmaToken, methodName, primName, success)
		}
	},

	/**
	 * Module header token: Occurs in pragma strings to define bootstrap macros.
	 */
	PRAGMA_MACRO("macro")
	{
		override fun applyThen(
			compiler: AvailCompiler,
			pragmaToken: A_Token,
			pragmaValue: String,
			state: LexingState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Malformed pragma at %s on line %d:",
					"macro pragma should have the form "
						+ "'macro=<comma-separated prefix and body "
						+ "primitive names>=<macro name>")
				return
			}
			val pragmaPrim = parts[0].trim { it <= ' ' }
			val macroName = parts[1].trim { it <= ' ' }
			val primNameStrings = pragmaPrim.split(",")
			val primNames = arrayOfNulls<String>(primNameStrings.size)
			for (i in primNames.indices)
			{
				val primName = primNameStrings[i]
				val prim = primitiveByName(primName)
				if (prim === null)
				{
					state.compilationContext.diagnostics.reportError(
						pragmaToken.nextLexingState(),
						"Malformed pragma at %s on line %d:",
						"macro pragma to reference a valid primitive, " +
							"not '$primName'")
					return
				}
				primNames[i] = primName
			}
			@Suppress("UNCHECKED_CAST")
			compiler.bootstrapMacroThen(
				state,
				pragmaToken,
				macroName,
				primNames as Array<String>,
				success)
		}
	},

	/**
	 * Module header token: Occurs in a pragma string to define the
	 * stringification method.
	 */
	PRAGMA_STRINGIFY("stringify")
	{
		override fun applyThen(
			compiler: AvailCompiler,
			pragmaToken: A_Token,
			pragmaValue: String,
			state: LexingState,
			success: () -> Unit)
		{
			val compilationContext = compiler.compilationContext
			val availName = stringFrom(pragmaValue)
			val atoms =
				compilationContext.module.trueNamesForStringName(availName)
			when (atoms.setSize)
			{
				0 ->
				{
					state.compilationContext.diagnostics.reportError(
						pragmaToken.nextLexingState(),
						"Malformed pragma at %s on line %d:",
						"This module should have introduced or imported the "
							+ "stringification atom named $availName.")
					return

				}
				1 ->
				{
					val atom = atoms.single()
					val send = newSendNode(
						emptyTuple,
						DECLARE_STRINGIFIER.bundle,
						newListNode(tuple(syntheticLiteralNodeFor(atom))),
						TOP.o)
					compiler.evaluateModuleStatementThen(
						state, state, send, mutableMapOf(), success)
				}
				else ->
				{
					state.compilationContext.diagnostics.reportError(
						pragmaToken.nextLexingState(),
						"Malformed pragma at %s on line %d:",
						"The stringification name $availName should have been"
							+ " unambiguous")
					return
				}
			}
		}
	},

	/**
	 * Module header token: Occurs in a pragma string to define a lexer.
	 */
	PRAGMA_LEXER("lexer")
	{
		override fun applyThen(
			compiler: AvailCompiler,
			pragmaToken: A_Token,
			pragmaValue: String,
			state: LexingState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Malformed pragma at %s on line %d:",
					"lexer pragma should have the form "
						+ "'lexer="
						+ "<filter primitive>,<body primitive>=<name>'"
						+ ", but the wrong number of '=' signs were "
						+ "encountered")
				return
			}
			val primNames = parts[0].trim { it <= ' ' }
			val primParts = primNames.split(",".toRegex(), 2).toTypedArray()
			if (primParts.size != 2)
			{
				state.compilationContext.diagnostics.reportError(
					pragmaToken.nextLexingState(),
					"Malformed pragma at %s on line %d:",
					"lexer pragma should have the form "
						+ "'lexer="
						+ "<filter primitive>,<body primitive>=<name>', "
						+ "but the wrong number of commas were encountered")
				return
			}
			val (filterPrimitiveName, bodyPrimitiveName) = primParts
			val lexerName = parts[1].trim { it <= ' ' }
			val availName = stringFrom(lexerName)
			val module = state.compilationContext.module
			val atoms = module.trueNamesForStringName(availName)
			when (atoms.setSize)
			{
				0 ->
				{
					state.compilationContext.diagnostics.reportError(
						pragmaToken.nextLexingState(),
						"Malformed pragma at %s on line %d:",
						"lexer name $availName should have been introduced in "
							+ "this module")
					return
				}
				1 -> compiler.bootstrapLexerThen(
					state,
					pragmaToken,
					atoms.single(),
					filterPrimitiveName,
					bodyPrimitiveName,
					success)
				else ->
				{
					state.compilationContext.diagnostics.reportError(
						pragmaToken.nextLexingState(),
						"Malformed pragma at %s on line %d:",
						"lexer name $availName should have been unambiguous in "
							+ "this module")
					return
				}
			}
		}
	};

	/**
	 * A pragma of this form was found.  Process it.
	 *
	 * @param compiler
	 *   The [AvailCompiler] with which to process the pragma.
	 * @param pragmaToken
	 *   The literal string token containing the pragma.
	 * @param pragmaValue
	 *   The content of the pragma after the first "=".
	 * @param state
	 *   The [LexingState] where the literal string token occurs.
	 * @param success
	 *   What to do if and when the pragma processing is successful.
	 */
	internal abstract fun applyThen(
		compiler: AvailCompiler,
		pragmaToken: A_Token,
		pragmaValue: String,
		state: LexingState,
		success: () -> Unit)

	companion object
	{
		/** Key the instances by lexeme. */
		private val kindsByLexeme = values().associateBy { it.lexeme }

		/**
		 * Answer the PragmaKind having the given lexeme, or `null` if it's not
		 * recognized.
		 *
		 * @param lexeme
		 *   The lexeme to look up.
		 * @return
		 *   The corresponding PragmaKind.
		 */
		fun pragmaKindByLexeme(lexeme: String): PragmaKind? =
			kindsByLexeme[lexeme]
	}
}
