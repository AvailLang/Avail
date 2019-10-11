/*
 * PragmaKind.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.compiler

import com.avail.descriptor.A_Token
import java.util.HashMap

import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.ListPhraseDescriptor.newListNode
import com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.DECLARE_STRINGIFIER
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SendPhraseDescriptor.newSendNode
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.interpreter.Primitive.Companion.primitiveByName
import java.lang.String.format

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
 *   The Java [lexeme string][String], i.e. the text of the token.
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
			state: ParserState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.expected(
					STRONG,
					format(
						"pragma to have the form '%1\$s=<property>=<value>'",
						name))
				return
			}
			val propertyName = parts[0].trim { it <= ' ' }
			val propertyValue = parts[1].trim { it <= ' ' }
			compiler.pragmaCheckThen(
				state, propertyName, propertyValue, success)
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
			state: ParserState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.expected(
					STRONG,
					"method pragma to have the form "
						+ "'method=<primitiveName>=<name>'")
				return
			}
			val primName = parts[0].trim { it <= ' ' }
			val methodName = parts[1].trim { it <= ' ' }
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
			state: ParserState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.expected(
					STRONG,
					"macro pragma to have the form "
					+ "'macro=<comma-separated prefix and body primitive "
					+ "names>=<macro name>")
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
				if (prim == null)
				{
					state.expected(
						STRONG,
						format(
							"macro pragma to reference a valid "
							+ "primitive, not %s",
							primName))
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
			state: ParserState,
			success: () -> Unit)
		{
			val compilationContext = compiler.compilationContext
			val availName = stringFrom(pragmaValue)
			val atoms =
				compilationContext.module.trueNamesForStringName(availName)
			if (atoms.setSize() == 0)
			{
				state.expected(
					STRONG,
					"this module to introduce or import the "
						+ "stringification atom having this name")
				return
			}
			if (atoms.setSize() > 1)
			{
				state.expected(
					STRONG,
					"this stringification atom name to be unambiguous")
				return
			}
			val atom = atoms.asTuple().tupleAt(1)
			val send = newSendNode(
				emptyTuple(),
				DECLARE_STRINGIFIER.bundle,
				newListNode(tuple(syntheticLiteralNodeFor(atom))),
				TOP.o())
			compiler.evaluateModuleStatementThen(
				state, state, send, HashMap(), success)
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
			state: ParserState,
			success: () -> Unit)
		{
			val parts = pragmaValue.split("=".toRegex(), 2).toTypedArray()
			if (parts.size != 2)
			{
				state.expected(
					STRONG,
					"lexer pragma to have the form "
					+ "'lexer=<filter primitive>,<body primitive>=<name>', "
					+ "but the second '=' was not found")
				return
			}
			val primNames = parts[0].trim { it <= ' ' }
			val primParts = primNames.split(",".toRegex(), 2).toTypedArray()
			if (primParts.size != 2)
			{
				state.expected(
					STRONG,
					"lexer pragma to have the form "
					+ "'lexer=<filter primitive>,<body primitive>=<name>', "
					+ "but the comma was not found")
				return
			}
			val filterPrimitiveName = primParts[0]
			val bodyPrimitiveName = primParts[1]
			val lexerName = parts[1].trim { it <= ' ' }
			val availName = stringFrom(lexerName)
			val module = state.lexingState.compilationContext.module
			val atoms = module.trueNamesForStringName(availName)
			if (atoms.setSize() == 0)
			{
				state.expected(
					STRONG,
					"lexer name to be introduced in this module")
				return
			}
			if (atoms.setSize() > 1)
			{
				state.expected(
					STRONG,
					"lexer name to be unambiguous in this module")
				return
			}
			val lexerAtom = atoms.iterator().next()

			compiler.bootstrapLexerThen(
				state,
				pragmaToken,
				lexerAtom,
				filterPrimitiveName,
				bodyPrimitiveName,
				success)
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
	 *   The [ParserState] where the literal string token occurs.
	 * @param success
	 *   What to do if and when the pragma processing is successful.
	 */
	internal abstract fun applyThen(
		compiler: AvailCompiler,
		pragmaToken: A_Token,
		pragmaValue: String,
		state: ParserState,
		success: () -> Unit)

	companion object
	{
		/** Key the instances by lexeme.  */
		private val kindsByLexeme = HashMap<String, PragmaKind>()

		init
		{
			for (kind in values())
			{
				kindsByLexeme[kind.lexeme] = kind
			}
		}

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
