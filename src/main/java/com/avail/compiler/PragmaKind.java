/*
 * PragmaKind.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.compiler;

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.interpreter.Primitive;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG;
import static com.avail.descriptor.ListPhraseDescriptor.newListNode;
import static com.avail.descriptor.LiteralPhraseDescriptor.syntheticLiteralNodeFor;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.DECLARE_STRINGIFIER;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SendPhraseDescriptor.newSendNode;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.primitiveByName;
import static java.lang.String.format;

/**
 * These are the tokens that are understood directly by the Avail compiler.
 */
public enum PragmaKind
{
	/**
	 * Module header token: Occurs in pragma strings to assert some quality
	 * of the virtual machine.
	 */
	PRAGMA_CHECK("check")
	{
		@Override
		void applyThen (
			final AvailCompiler compiler,
			final A_Token pragmaToken,
			final String pragmaValue,
			final ParserState state,
			final Continuation0 success)
		{
			final String[] parts = pragmaValue.split("=", 2);
			if (parts.length != 2)
			{
				state.expected(
					STRONG,
					format(
						"pragma to have the form '%1$s=<property>=<value>'",
						name()));
				return;
			}
			final String propertyName = parts[0].trim();
			final String propertyValue = parts[1].trim();
			compiler.pragmaCheckThen(
				state, propertyName, propertyValue, success);
		}
	},

	/**
	 * Module header token: Occurs in pragma strings to define bootstrap
	 * method.
	 */
	PRAGMA_METHOD("method")
	{
		@Override
		void applyThen (
			final AvailCompiler compiler,
			final A_Token pragmaToken,
			final String pragmaValue,
			final ParserState state,
			final Continuation0 success)
		{
			final String[] parts = pragmaValue.split("=", 2);
			if (parts.length != 2)
			{
				state.expected(
					STRONG,
					"method pragma to have the form "
						+ "'method=<primitiveName>=<name>'");
				return;
			}
			final String primName = parts[0].trim();
			final String methodName = parts[1].trim();
			compiler.bootstrapMethodThen(
				state, pragmaToken, methodName, primName, success);
		}
	},

	/**
	 * Module header token: Occurs in pragma strings to define bootstrap
	 * macros.
	 */
	PRAGMA_MACRO("macro")
	{
		@Override
		void applyThen (
			final AvailCompiler compiler,
			final A_Token pragmaToken,
			final String pragmaValue,
			final ParserState state,
			final Continuation0 success)
		{
			final String[] parts = pragmaValue.split("=", 2);
			if (parts.length != 2)
			{
				state.expected(
					STRONG,
					"macro pragma to have the form "
						+ "'macro=<comma-separated prefix and body primitive "
						+ "names>=<macro name>");
				return;
			}
			final String pragmaPrim = parts[0].trim();
			final String macroName = parts[1].trim();
			final String[] primNameStrings = pragmaPrim.split(",");
			final String[] primNames = new String[primNameStrings.length];
			for (int i = 0; i < primNames.length; i++)
			{
				final String primName = primNameStrings[i];
				final @Nullable Primitive prim = primitiveByName(primName);
				if (prim == null)
				{
					state.expected(
						STRONG,
						format(
							"macro pragma to reference a valid primitive, "
								+ "not %s",
							primName));
					return;
				}
				primNames[i] = primName;
			}
			compiler.bootstrapMacroThen(
				state, pragmaToken, macroName, primNames, success);
		}
	},


	/**
	 * Module header token: Occurs in a pragma string to define the
	 * stringification method.
	 */
	PRAGMA_STRINGIFY("stringify")
	{
		@Override
		void applyThen (
			final AvailCompiler compiler,
			final A_Token pragmaToken,
			final String pragmaValue,
			final ParserState state,
			final Continuation0 success)
		{
			final CompilationContext compilationContext =
				compiler.compilationContext;
			final A_String availName = stringFrom(pragmaValue);
			final A_Set atoms =
				compilationContext.module().trueNamesForStringName(availName);
			if (atoms.setSize() == 0)
			{
				state.expected(
					STRONG,
					"this module to introduce or import the stringification "
						+ "atom having this name");
				return;
			}
			if (atoms.setSize() > 1)
			{
				state.expected(
					STRONG,
					"this stringification atom name to be unambiguous");
				return;
			}
			final A_Atom atom = atoms.asTuple().tupleAt(1);
			final A_Phrase send = newSendNode(
				emptyTuple(),
				DECLARE_STRINGIFIER.bundle,
				newListNode(tuple(syntheticLiteralNodeFor(atom))),
				TOP.o());
			compiler.evaluateModuleStatementThen(
				state, state, send, new HashMap<>(), success);
		}
	},

	/**
	 * Module header token: Occurs in a pragma string to define a lexer.
	 */
	PRAGMA_LEXER("lexer")
	{
		@Override
		void applyThen (
			final AvailCompiler compiler,
			final A_Token pragmaToken,
			final String pragmaValue,
			final ParserState state,
			final Continuation0 success)
		{
			final String[] parts = pragmaValue.split("=", 2);
			if (parts.length != 2)
			{
				state.expected(
					STRONG,
					"lexer pragma to have the form "
						+ "'lexer=<filter primitive>,<body primitive>=<name>', "
						+ "but the second '=' was not found");
				return;
			}
			final String primNames = parts[0].trim();
			final String[] primParts = primNames.split(",", 2);
			if (primParts.length != 2)
			{
				state.expected(
					STRONG,
					"lexer pragma to have the form "
						+ "'lexer=<filter primitive>,<body primitive>=<name>', "
						+ "but the comma was not found");
				return;
			}
			final String filterPrimitiveName = primParts[0];
			final String bodyPrimitiveName = primParts[1];
			final String lexerName = parts[1].trim();
			final A_String availName = stringFrom(lexerName);
			final A_Module module = state.lexingState.getCompilationContext().module();
			final A_Set atoms = module.trueNamesForStringName(availName);
			if (atoms.setSize() == 0)
			{
				state.expected(
					STRONG,
					"lexer name to be introduced in this module");
				return;
			}
			if (atoms.setSize() > 1)
			{
				state.expected(
					STRONG,
					"lexer name to be unambiguous in this module");
				return;
			}
			final A_Atom lexerAtom = atoms.iterator().next();

			compiler.bootstrapLexerThen(
				state,
				pragmaToken,
				lexerAtom,
				filterPrimitiveName,
				bodyPrimitiveName,
				success);
		}
	};


	/** Key the instances by lexeme. */
	private static final Map<String, PragmaKind> kindsByLexeme =
		new HashMap<>();

	static
	{
		for (final PragmaKind kind : PragmaKind.values())
		{
			kindsByLexeme.put(kind.lexeme, kind);
		}
	}

	/**
	 * Answer the PragmaKind having the given lexeme, or {@code null} if it's
	 * not recognized.
	 *
	 * @param lexeme
	 *        The lexeme to look up.
	 * @return The corresponding PragmaKind.
	 */
	public static @Nullable PragmaKind pragmaKindByLexeme (final String lexeme)
	{
		return kindsByLexeme.get(lexeme);
	}

	/** The Java {@link String} form of the lexeme. */
	public final String lexeme;

	/**
	 * Answer the Java {@link String} form of this pragma's lexeme.
	 *
	 * @return The lexeme {@link String}.
	 */
	public final String lexeme ()
	{
		return lexeme;
	}

	/**
	 * Construct a new {@code PragmaKind}.
	 *
	 * @param lexeme
	 *        The Java {@linkplain String lexeme string}, i.e. the text
	 *        of the token.
	 */
	PragmaKind (
		final String lexeme)
	{
		this.lexeme = lexeme;
	}

	/**
	 * A pragma of this form was found.  Process it.
	 *
	 * @param compiler
	 *        The {@link AvailCompiler} with which to process the pragma.
	 * @param pragmaToken
	 *        The literal string token containing the pragma.
	 * @param pragmaValue
	 *        The content of the pragma after the first "=".
	 * @param state
	 *        The {@link ParserState} where the literal string token occurs.
	 * @param success
	 *        What to do if and when the pragma processing is successful.
	 */
	abstract void applyThen (
		final AvailCompiler compiler,
		final A_Token pragmaToken,
		final String pragmaValue,
		final ParserState state,
		final Continuation0 success);
}
