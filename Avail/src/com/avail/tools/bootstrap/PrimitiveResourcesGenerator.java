/**
 * PrimitiveResourcesGenerator.java
 * Copyright (c) 2011, Mark van Gulik.
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

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.Primitive;

/**
 * Generate a {@linkplain PropertyResourceBundle property resource bundle} that
 * specifies unbound properties for the Avail names of the {@linkplain Primitive
 * primitives}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class PrimitiveResourcesGenerator
{
	/**
	 * The base name of the {@linkplain ResourceBundle resource bundle} that
	 * contains the preamble.
	 */
	private static final @NotNull String bundleName =
		PrimitiveResourcesGenerator.class.getPackage().getName() + ".Preamble";

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains preamble
	 * information.
	 */
	private final @NotNull ResourceBundle bundle;

	/**
	 * The existing {@linkplain Properties properties}. These should be copied
	 * into the resultant {@linkplain ResourceBundle properties resource
	 * bundle}.
	 */
	private final @NotNull Properties properties;

	/**
	 * The unqualified base name of the target {@linkplain ResourceBundle
	 * resource bundle}.
	 */
	private static final @NotNull String targetName = "PrimitiveResources";

	/**
	 * The qualified base name of the target {@linkplain ResourceBundle resource
	 * bundle}.
	 */
	private static final @NotNull String qualifiedTargetName =
		Primitive.class.getPackage().getName() + "." + targetName;

	/**
	 * The keys for the "Preamble" {@linkplain ResourceBundle resource bundle}.
	 */
	@SuppressWarnings("all")
	static enum Key
	{
		copyright (targetName),
		notice (PrimitiveResourcesGenerator.class.getName());

		/** A file name. */
		final @NotNull String fileName;

		/**
		 * Construct a new {@link Key}.
		 *
		 * @param fileName A file name.
		 */
		private Key (final String fileName)
		{
			this.fileName = fileName;
		}
	}

	/**
	 * Generate the preamble for the properties file. This includes the
	 * copyright and machine generation warnings.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePreamble (final @NotNull PrintWriter writer)
	{
		writer.println(MessageFormat.format(
			bundle.getString(Key.copyright.name()),
			Key.copyright.fileName));
		writer.println(MessageFormat.format(
			bundle.getString(Key.notice.name()),
			Key.notice.fileName,
			new Date()));
	}

	/**
	 * Write the names of the properties, whose unspecified values should be
	 * the Avail names of the corresponding {@linkplain Primitive primitives}.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateProperties (final @NotNull PrintWriter writer)
	{
		for (final Primitive primitive : Primitive.values())
		{
			writer.print(primitive.name());
			writer.print('=');
			if (properties.containsKey(primitive.name()))
			{
				writer.print(properties.getProperty(primitive.name()));
			}
			writer.println();
		}
	}

	/**
	 * Construct a new {@link PrimitiveResourcesGenerator}.
	 *
	 * @param locale
	 *        The target {@linkplain Locale locale}.
	 * @param existingProperties
	 *        Any properties that already exist for the target {@linkplain
	 *        ResourceBundle resource bundle}.
	 */
	private PrimitiveResourcesGenerator (
		final @NotNull Locale locale,
		final @NotNull Properties existingProperties)
	{
		bundle = ResourceBundle.getBundle(
			bundleName,
			locale,
			PrimitiveResourcesGenerator.class.getClassLoader());
		properties = existingProperties;
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
	public static void main (final @NotNull String[] args)
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
			final File fileName = new File(String.format(
				"src/%s_%s.properties",
				qualifiedTargetName.replace('.', '/'),
				language));
			assert fileName.getPath().endsWith(".properties");
			final Properties properties = new Properties();
			try
			{
				properties.load(new InputStreamReader(
					new FileInputStream(fileName), "UTF-8"));
			}
			catch (final FileNotFoundException e)
			{
				// Ignore. It's okay if the file doesn't already exist.
			}
			final PrimitiveResourcesGenerator generator =
				new PrimitiveResourcesGenerator(
					new Locale(language), properties);
			final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
			generator.generatePreamble(writer);
			generator.generateProperties(writer);
			writer.close();
		}
	}
}
