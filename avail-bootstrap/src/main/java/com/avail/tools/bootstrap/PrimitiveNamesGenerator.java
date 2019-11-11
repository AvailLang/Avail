/*
 * PrimitiveNamesGenerator.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;

import static com.avail.tools.bootstrap.Resources.Key.methodCommentParameterTemplate;
import static com.avail.tools.bootstrap.Resources.Key.methodCommentRaisesTemplate;
import static com.avail.tools.bootstrap.Resources.Key.methodCommentReturnsTemplate;
import static com.avail.tools.bootstrap.Resources.Key.methodCommentTemplate;
import static com.avail.tools.bootstrap.Resources.escape;
import static com.avail.tools.bootstrap.Resources.primitiveCommentKey;
import static com.avail.tools.bootstrap.Resources.primitiveParameterNameKey;
import static com.avail.tools.bootstrap.Resources.primitivesBaseName;
import static java.lang.String.format;

/**
 * Generate a {@linkplain PropertyResourceBundle property resource bundle} that
 * specifies unbound properties for the Avail names of the {@linkplain Primitive
 * primitives}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class PrimitiveNamesGenerator
extends PropertiesFileGenerator
{
	/**
	 * Write the names of the properties, whose unspecified values should be
	 * the Avail names of the corresponding {@linkplain Primitive primitives}.
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
		for (
			int primitiveNumber = 1;
			primitiveNumber <= Primitive.Companion.maxPrimitiveNumber();
			primitiveNumber++)
		{
			final @Nullable Primitive primitive =
				Primitive.Companion.byPrimitiveNumberOrNull(primitiveNumber);
			if (primitive != null && !primitive.hasFlag(Flag.Private))
			{
				// Write a comment that gives the primitive number and its
				// arity.
				keys.add(primitive.getClass().getSimpleName());
				writer.format(
					"# %s : _=%d%n",
					primitive.name(),
					primitive.getArgCount());
				// Write the primitive key and any name already associated with
				// it.
				writer.print(primitive.getClass().getSimpleName());
				writer.print('=');
				final String primitiveName = properties.getProperty(
					primitive.getClass().getSimpleName());
				if (primitiveName != null)
				{
					writer.print(escape(primitiveName));
				}
				writer.println();
				// Write each of the parameter keys and their previously
				// associated values.
				for (int i = 1; i <= primitive.getArgCount(); i++)
				{
					final String argNameKey =
						primitiveParameterNameKey(primitive, i);
					keys.add(argNameKey);
					writer.print(argNameKey);
					writer.print('=');
					final String argName = properties.getProperty(argNameKey);
					if (argName != null)
					{
						writer.print(escape(argName));
					}
					writer.println();
				}
				// Write out the comment.
				final String commentKey = primitiveCommentKey(primitive);
				keys.add(commentKey);
				writer.print(commentKey);
				writer.print('=');
				final String comment = properties.getProperty(commentKey);
				if (comment != null && !comment.isEmpty())
				{
					writer.print(escape(comment));
				}
				else
				{
					// Initialize the count of template parameters that occur in
					// the final value to 1, to account for the @method tag of
					// methodCommentTemplate.
					int templateParameters = 1;
					final String commentTemplate =
						preambleBundle.getString(methodCommentTemplate.name());
					final String parameters;
					final int argCount = primitive.getArgCount();
					if (argCount > 0)
					{
						final String parameterTemplate =
							preambleBundle.getString(
								methodCommentParameterTemplate.name());
						final StringBuilder builder = new StringBuilder(500);
						for (int i = 0; i < primitive.getArgCount(); i++)
						{
							builder.append(MessageFormat.format(
								parameterTemplate,
								format("{%d}", templateParameters),
								format("{%d}", templateParameters + argCount)));
							templateParameters++;
						}
						templateParameters += argCount;
						parameters = builder.toString();
					}
					else
					{
						parameters = "";
					}
					// The return contributes one argument to the final
					// template.
					final String returnsTemplate = preambleBundle.getString(
						methodCommentReturnsTemplate.name());
					final String returns = MessageFormat.format(
						returnsTemplate,
						format("{%d}", templateParameters));
					templateParameters++;
					// If the primitive failure type is an enumeration, then
					// exceptions contribute one argument to the final template
					// for each value. Otherwise, it just contributes one
					// argument. But if the primitive cannot fail, then no
					// arguments are contributed.
					final String raises;
					if (!primitive.hasFlag(Flag.CannotFail))
					{
						final String raisesTemplate = preambleBundle.getString(
							methodCommentRaisesTemplate.name());
						final A_Type failureType =
							primitive.getFailureVariableType();
						if (failureType.isEnumeration())
						{
							final StringBuilder builder =
								new StringBuilder(500);
							for (@SuppressWarnings("unused")
								final A_BasicObject o :
									failureType.instances())
							{
								builder.append(MessageFormat.format(
									raisesTemplate,
									format("{%d}", templateParameters)));
								templateParameters++;
							}
							raises = builder.toString();
						}
						else
						{
							raises = MessageFormat.format(
								raisesTemplate,
								format("{%d}", templateParameters));
						}
					}
					else
					{
						raises = "";
					}
					writer.print(escape(MessageFormat.format(
						commentTemplate, parameters, returns, raises)));
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
	 * Construct a new {@link PrimitiveNamesGenerator}.
	 *
	 * @param locale
	 *        The target {@linkplain Locale locale}.
	 */
	public PrimitiveNamesGenerator (final Locale locale)
	{
		super(primitivesBaseName, locale);
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

		for (final String language : languages)
		{
			final PrimitiveNamesGenerator generator =
				new PrimitiveNamesGenerator(new Locale(language));
			generator.generate();
		}
	}
}
