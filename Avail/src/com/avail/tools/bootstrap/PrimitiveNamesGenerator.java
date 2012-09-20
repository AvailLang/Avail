/**
 * PrimitiveNamesGenerator.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.tools.bootstrap.Resources.*;
import static com.avail.tools.bootstrap.Resources.Key.*;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;

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
	/* Initialize Avail. */
	static
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
	}

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
		final Set<String> keys = new HashSet<String>();
		for (
			int primitiveNumber = 1;
			primitiveNumber <= Primitive.maxPrimitiveNumber;
			primitiveNumber++)
		{
			final Primitive primitive =
				Primitive.byPrimitiveNumberOrNull(primitiveNumber);
			if (primitive != null && !primitive.hasFlag(Flag.Private))
			{
				keys.add(primitive.name());
				writer.format(
					"# %3d : _=%d%n",
					primitive.primitiveNumber,
					primitive.argCount());
				writer.print(primitive.name());
				writer.print('=');
				final String primitiveName =
					properties.getProperty(primitive.name());
				if (primitiveName != null)
				{
					writer.print(escape(primitiveName));
				}
				writer.println();
				for (int i = 1; i <= primitive.argCount(); i++)
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
					final String commentTemplate =
						preambleBundle.getString(methodCommentTemplate.name());
					final String parameters;
					if (primitive.argCount() > 0)
					{
						final String parametersTemplate =
							preambleBundle.getString(
								methodCommentParametersTemplate.name());
						final String parameterTemplate =
							preambleBundle.getString(
								methodCommentParameterTemplate.name());
						final StringBuilder builder = new StringBuilder(500);
						for (int i = 0; i < primitive.argCount(); i++)
						{
							builder.append(MessageFormat.format(
								parameterTemplate, i));
						}
						parameters = MessageFormat.format(
							parametersTemplate, builder.toString());
					}
					else
					{
						parameters = "";
					}
					final AvailObject returnType =
						primitive.blockTypeRestriction().returnType();
					final String returns;
					if (!returnType.equals(TypeDescriptor.Types.TOP.o())
						&& !returnType.equals(BottomTypeDescriptor.bottom()))
					{
						returns = preambleBundle.getString(
							methodCommentReturnsTemplate.name());
					}
					else
					{
						returns = "";
					}
					writer.print(escape(MessageFormat.format(
						commentTemplate, parameters, returns)));
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
			languages = new String[] { "en" };
		}

		for (final String language : languages)
		{
			final PrimitiveNamesGenerator generator =
				new PrimitiveNamesGenerator(new Locale(language));
			generator.generate();
		}
	}
}
