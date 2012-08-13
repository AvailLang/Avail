/**
 * ErrorCodeNamesGenerator.java
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
import java.io.*;
import java.util.*;
import com.avail.exceptions.AvailErrorCode;

/**
 * Generate a {@linkplain PropertyResourceBundle property resource bundle} that
 * specifies unbound properties for the Avail names of the {@linkplain
 * AvailErrorCode primitive error codes}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class ErrorCodeNamesGenerator
extends PropertiesFileGenerator
{
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
		final Set<String> keys = new HashSet<String>();
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
							.replace('_', ' '));
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
	 * Construct a new {@link ErrorCodeNamesGenerator}.
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
			languages = new String[] { "en" };
		}

		for (final String language : languages)
		{
			final ErrorCodeNamesGenerator generator =
				new ErrorCodeNamesGenerator(new Locale(language));
			generator.generate();
		}
	}
}
