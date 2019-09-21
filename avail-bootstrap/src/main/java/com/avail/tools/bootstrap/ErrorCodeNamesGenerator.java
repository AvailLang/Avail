/*
 * ErrorCodeNamesGenerator.java
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

package com.avail.tools.bootstrap;

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;

import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.VariableTypeDescriptor.mostGeneralVariableType;
import static com.avail.tools.bootstrap.Resources.errorCodeCommentKey;
import static com.avail.tools.bootstrap.Resources.errorCodeKey;
import static com.avail.tools.bootstrap.Resources.errorCodesBaseName;
import static com.avail.tools.bootstrap.Resources.escape;

/**
 * Generate a {@linkplain PropertyResourceBundle property resource bundle} that
 * specifies unbound properties for the Avail names of the {@linkplain
 * AvailErrorCode primitive error codes}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ErrorCodeNamesGenerator
extends PropertiesFileGenerator
{
	/**
	 * Check if all {@linkplain AvailErrorCode error codes} are reachable from
	 * {@linkplain Primitive primitive} {@linkplain
	 * Primitive#failureVariableType() failure variable types}.
	 *
	 * @return {@code true} if all error codes are reachable, {@code false}
	 *         otherwise.
	 */
	private static boolean allErrorCodesAreReachableFromPrimitives ()
	{
		// This forces initialization of Avail.
		//noinspection ResultOfMethodCallIgnored
		AvailRuntime.specialObjects();
		A_Set allErrorCodes = emptySet();
		for (final AvailErrorCode code : AvailErrorCode.values())
		{
			if (!code.isCausedByInstructionFailure())
			{
				allErrorCodes = allErrorCodes.setWithElementCanDestroy(
					code.numericCode(),
					true);
			}
		}
		A_Set reachableErrorCodes = emptySet();
		for (
			int primitiveNumber = 1;
			primitiveNumber <= Primitive.maxPrimitiveNumber();
			primitiveNumber++)
		{
			final @Nullable Primitive primitive =
				Primitive.byPrimitiveNumberOrNull(primitiveNumber);
			if (primitive != null && !primitive.hasFlag(Flag.CannotFail))
			{
				final A_Type failureType = primitive.failureVariableType();
				if (failureType.isEnumeration())
				{
					reachableErrorCodes =
						reachableErrorCodes.setUnionCanDestroy(
							failureType.instances(),
							true);
				}
				else if (failureType.isSubtypeOf(mostGeneralVariableType()))
				{
					// This supports P_CatchException, which hides its error
					// codes inside a variable type.
					reachableErrorCodes =
						reachableErrorCodes.setUnionCanDestroy(
							failureType.readType().instances(),
							true);
				}
			}
		}
		final A_Set unreachableErrorCodes =
			allErrorCodes.setMinusCanDestroy(reachableErrorCodes, true);
		if (unreachableErrorCodes.setSize() != 0)
		{
			final EnumSet<AvailErrorCode> unreachable =
				EnumSet.noneOf(AvailErrorCode.class);
			for (final A_Number code : unreachableErrorCodes)
			{
				unreachable.add(
					AvailErrorCode.byNumericCode(code.extractInt()));
			}
			System.err.printf(
				"some error codes are unreachable: %s%n",
				unreachable);
			return false;
		}
		return true;
	}

	/**
	 * Write the names of the properties, whose unspecified values should be
	 * the Avail names of the corresponding {@linkplain AvailErrorCode
	 * primitive error codes}.
	 *
	 * @param properties
	 *        The existing {@linkplain Properties properties}. These should be
	 *        copied into the resultant {@linkplain ResourceBundle properties
	 *        resource bundle}.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	@Override
	protected void generateProperties (
		final Properties properties,
		final PrintWriter writer)
	{
		final Set<String> keys = new HashSet<>();
		for (final AvailErrorCode code : AvailErrorCode.values())
		{
			if (code.nativeCode() > 0)
			{
				writer.print("# ");
				writer.print(code.nativeCode());
				writer.print(" : ");
				writer.print(code.name());
				writer.println();
				final String key = errorCodeKey(code);
				keys.add(key);
				writer.print(key);
				writer.print('=');
				final String errorCodeName = properties.getProperty(key);
				if (errorCodeName != null)
				{
					writer.print(escape(errorCodeName));
				}
				// Plug in sensible English language defaults if appropriate.
				else if (locale.getLanguage().equals("en"))
				{
					writer.print(
						code.name().substring(2).toLowerCase()
							.replace('_', '-'));
					writer.print(" code");
				}
				writer.println();
				final String commentKey = errorCodeCommentKey(code);
				keys.add(commentKey);
				writer.print(commentKey);
				writer.print('=');
				final String comment = properties.getProperty(commentKey);
				if (comment != null)
				{
					writer.print(escape(comment));
				}
				writer.println();
			}
		}
		for (final Object property : properties.keySet())
		{
			final String key = (String) property;
			if (!keys.contains(key))
			{
				keys.add(key);
				writer.print(key);
				writer.print('=');
				writer.println(escape(properties.getProperty(key)));
			}
		}
	}

	/**
	 * Construct a new {@code ErrorCodeNamesGenerator}.
	 *
	 * @param locale
	 *        The target {@linkplain Locale locale}.
	 */
	public ErrorCodeNamesGenerator (final Locale locale)
	{
		super(errorCodesBaseName, locale);
	}

	/**
	 * Generate the specified {@linkplain ResourceBundle resource bundles}.
	 *
	 * @param args
	 *        The command-line arguments, an array of language codes that
	 *        broadly specify the {@linkplain Locale locales} for which
	 *        resource bundles should be generated.
	 * @throws Exception
	 *         If anything should go wrong.
	 */
	public static void main (final String[] args)
		throws Exception
	{
		final String[] languages;
		if (args.length > 0)
		{
			languages = args;
		}
		else
		{
			languages = new String[] { System.getProperty("user.language") };
		}

		if (allErrorCodesAreReachableFromPrimitives())
		{
			for (final String language : languages)
			{
				final ErrorCodeNamesGenerator generator =
					new ErrorCodeNamesGenerator(new Locale(language));
				generator.generate();
			}
		}
	}
}
