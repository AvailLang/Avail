/*
 * SpecialObjectNamesGenerator.java
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
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.*;

import static com.avail.tools.bootstrap.Resources.Key.*;
import static com.avail.tools.bootstrap.Resources.*;

/**
 * Generate a {@linkplain PropertyResourceBundle property resource bundle} that
 * specifies unbound properties for the Avail names of the special objects.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class SpecialObjectNamesGenerator
extends PropertiesFileGenerator
{
	/**
	 * Write the names of the properties, whose unspecified values should be
	 * the Avail names of the corresponding special objects.
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
		final List<AvailObject> specialObjects = AvailRuntime.specialObjects();
		final Set<String> keys = new HashSet<>();
		for (int i = 0; i < specialObjects.size(); i++)
		{
			final A_BasicObject specialObject = specialObjects.get(i);
			if (specialObject != null)
			{
				// Write a primitive descriptive of the special object as a
				// comment, to assist a human translator.
				final String text =
					specialObject.toString().replace("\n", "\n#");
				writer.print("# ");
				writer.print(text);
				writer.println();
				// Write the method name of the special object.
				final String key = specialObjectKey(i);
				keys.add(key);
				writer.print(key);
				writer.print('=');
				final String specialObjectName = properties.getProperty(key);
				if (specialObjectName != null)
				{
					writer.print(escape(specialObjectName));
				}
				writer.println();
				// Write the preferred alias that Stacks should indicate.
				final String typeKey = specialObjectTypeKey(i);
				keys.add(typeKey);
				writer.print(typeKey);
				writer.print('=');
				final String type = properties.getProperty(typeKey, "");
				writer.print(escape(type));
				writer.println();
				// Write the Stacks comment.
				final String commentKey = specialObjectCommentKey(i);
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
						preambleBundle.getString(
							specialObjectCommentTemplate.name());
					final String template;
					if (specialObject.isType())
					{
						template = specialObjectCommentTypeTemplate.name();
					}
					else
					{
						template = specialObjectCommentValueTemplate.name();
					}
					writer.print(escape(
						MessageFormat.format(
							commentTemplate,
							preambleBundle.getString(template))));
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
	 * Construct a new {@link SpecialObjectNamesGenerator}.
	 *
	 * @param locale
	 *        The target {@linkplain Locale locale}.
	 */
	public SpecialObjectNamesGenerator (final Locale locale)
	{
		super(specialObjectsBaseName, locale);
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
			final SpecialObjectNamesGenerator generator =
				new SpecialObjectNamesGenerator(new Locale(language));
			generator.generate();
		}
	}
}
