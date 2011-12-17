/**
 * BootstrapGenerator.java
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

import static com.avail.tools.bootstrap.Resources.*;
import static com.avail.tools.bootstrap.Resources.Key.*;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;

/**
 * Generate the Avail system {@linkplain ModuleDescriptor modules} that
 * bind the infallible and fallible {@linkplain Primitive primitives}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class BootstrapGenerator
{
	/** The Avail special objects. */
	private static final @NotNull List<AvailObject> specialObjects;

	/**
	 * A {@linkplain Map map} from the special objects to their indices.
	 */
	private static final
	@NotNull Map<AvailObject, Integer> specialObjectIndexMap;

	/* Capture the special objects. */
	static
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
		specialObjects = new AvailRuntime(null).specialObjects();
		specialObjectIndexMap =
			new HashMap<AvailObject, Integer>(specialObjects.size());
		for (int i = 0; i < specialObjects.size(); i++)
		{
			final AvailObject specialObject = specialObjects.get(i);
			if (specialObject != null)
			{
				specialObjectIndexMap.put(specialObject, i);
			}
		}
	}

	/** The target {@linkplain Locale locale}. */
	private final @NotNull Locale locale;

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains file
	 * preambleBaseName information.
	 */
	private final @NotNull ResourceBundle preamble;

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains the Avail
	 * names of the special objects.
	 */
	private final @NotNull ResourceBundle specialObjectNames;

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains the Avail
	 * names of the {@linkplain Primitive primitives}.
	 */
	private final @NotNull ResourceBundle primitiveNames;

	/**
	 * Answer a textual representation of the specified version {@linkplain
	 * List list} that is satisfactory for use in an Avail {@linkplain
	 * ModuleDescriptor module} header.
	 *
	 * @param versions The versions.
	 * @return The version string.
	 */
	private @NotNull String versionString (
		final @NotNull List<String> versions)
	{
		final StringBuilder builder = new StringBuilder();
		for (final String version : versions)
		{
			builder.append("\n\t\"");
			builder.append(version);
			builder.append("\",");
		}
		final String versionString = builder.toString();
		return versionString.substring(0, versionString.length() - 1);
	}

	/**
	 * Generate the preamble for the pragma-containing module.
	 *
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateOriginModulePreamble (
		final @NotNull List<String> versions,
		final @NotNull PrintWriter writer)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name()),
			preamble.getString(originModuleName.name())));
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name()),
			BootstrapGenerator.class.getName(),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(originModuleHeader.name()),
			preamble.getString(originModuleName.name()),
			versionString(versions),
			preamble.getString(bootstrapDefiningMethod.name()),
			preamble.getString(bootstrapSpecialObject.name())));
	}

	/**
	 * A {@linkplain Map map} from localized names to Avail special objects.
	 */
	private final @NotNull Map<String, AvailObject> specialObjectNameMap =
		new HashMap<String, AvailObject>(specialObjects.size());

	/**
	 * Answer a textual representation of the special objects that is
	 * satisfactory for use in an Avail {@linkplain ModuleDescriptor module}
	 * header.
	 *
	 * @return The "Names" string.
	 */
	private @NotNull String specialObjectsNamesString ()
	{
		final List<String> names = new ArrayList<String>(
			new ArrayList<String>(specialObjectNameMap.keySet()));
		Collections.sort(names);
		final StringBuilder builder = new StringBuilder();
		for (final String name : names)
		{
			final AvailObject specialObject = specialObjectNameMap.get(name);
			builder.append("\n\t");
			builder.append(String.format(
				"/* %2d */", specialObjectIndexMap.get(specialObject)));
			builder.append(" \"");
			builder.append(name);
			builder.append("\",");
		}
		final String namesString = builder.toString();
		return namesString.substring(0, namesString.length() - 1);
	}

	/**
	 * Generate the preamble for the special object linking module.
	 *
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateSpecialObjectModulePreamble (
		final @NotNull List<String> versions,
		final @NotNull PrintWriter writer)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name()),
			preamble.getString(specialObjectsModuleName.name())));
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name()),
			BootstrapGenerator.class.getName(),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(primitivesModuleHeader.name()),
			preamble.getString(specialObjectsModuleName.name()),
			versionString(versions),
			String.format(
				"\n\t\"%s\"",
				preamble.getString(originModuleName.name())),
			specialObjectsNamesString()));
	}

	/**
	 * Answer the selected {@linkplain Primitive primitives}.
	 *
	 * @param fallible
	 *        {@code true} if the fallible primitives should be answered, {@code
	 *        false} if the infallible primitives should be answered.
	 * @return The selected primitives.
	 */
	private @NotNull List<Primitive> primitives (final boolean fallible)
	{
		final List<Primitive> primitives = new ArrayList<Primitive>();
		for (final Primitive primitive : Primitive.values())
		{
			if (primitive.hasFlag(Flag.CannotFail) == !fallible)
			{
				primitives.add(primitive);
			}
		}
		return primitives;
	}

	/**
	 * Answer a textual representation of the specified {@linkplain Primitive
	 * primitive} names {@linkplain List list} that is satisfactory for use in
	 * an Avail {@linkplain ModuleDescriptor module} header.
	 *
	 * @param primitives The primitives.
	 * @return The "Names" string.
	 */
	private @NotNull String primitivesNamesString (
		final @NotNull List<Primitive> primitives)
	{
		final StringBuilder builder = new StringBuilder();
		for (final Primitive primitive : primitives)
		{
			builder.append("\n\t\"");
			builder.append(primitiveNames.getString(primitive.name()));
			builder.append("\",");
		}
		final String namesString = builder.toString();
		return namesString.substring(0, namesString.length() - 1);
	}

	/**
	 * Generate the preamble for the specified {@linkplain Primitive primitive}
	 * module.
	 *
	 * @param fallible
	 *        {@code true} to indicate the fallible primitives module, {@code
	 *        false} to indicate the infallible primitives module.
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrimitiveModulePreamble (
		final boolean fallible,
		final @NotNull List<String> versions,
		final @NotNull PrintWriter writer)
	{
		final Key key = fallible
			? falliblePrimitivesModuleName
			: infalliblePrimitivesModuleName;
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name()),
			preamble.getString(key.name())));
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name()),
			BootstrapGenerator.class.getName(),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(primitivesModuleHeader.name()),
			preamble.getString(key.name()),
			versionString(versions),
			String.format(
				"\n\t\"%s\"",
				preamble.getString(originModuleName.name())),
			primitivesNamesString(primitives(fallible))));
	}

	/**
	 * Generate the {@linkplain ModuleDescriptor module} that contains the
	 * pragmas.
	 *
	 * @param versions
	 *        The supported versions.
	 * @throws IOException
	 *         If the source module could not be written.
	 */
	private void generateOriginModule (
			final @NotNull List<String> versions)
		throws IOException
	{
		final File fileName = new File(String.format(
			"src/%s/%s/%s.avail",
			generatedPackageName.replace('.', '/'),
			locale.getLanguage(),
			preamble.getString(originModuleName.name())));
		assert fileName.getPath().endsWith(".avail");
		final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
		generateOriginModulePreamble(versions, writer);
		writer.close();
	}

	/**
	 * Generate the {@linkplain ModuleDescriptor module} that binds the special
	 * objects to Avail names.
	 *
	 * @param versions
	 *        The supported versions.
	 * @throws IOException
	 *         If the source module could not be written.
	 */
	private void generateSpecialObjectsModule (
			final @NotNull List<String> versions)
		throws IOException
	{
		final File fileName = new File(String.format(
			"src/%s/%s/%s.avail",
			generatedPackageName.replace('.', '/'),
			locale.getLanguage(),
			preamble.getString(specialObjectsModuleName.name())));
		assert fileName.getPath().endsWith(".avail");
		final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
		generateSpecialObjectModulePreamble(versions, writer);
		// TODO: [TLS] Finish this!
		writer.close();
	}

	/**
	 * Generate the specified primitive {@linkplain ModuleDescriptor module}.
	 *
	 * @param fallible
	 *        {@code true} to indicate the fallible primitives module, {@code
	 *        false} to indicate the infallible primitives module.
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @throws IOException
	 *         If the source module could not be written.
	 */
	private void generatePrimitiveModule (
			final boolean fallible,
			final @NotNull List<String> versions)
		throws IOException
	{
		final Key key = fallible
			? falliblePrimitivesModuleName
			: infalliblePrimitivesModuleName;
		final File fileName = new File(String.format(
			"src/%s/%s/%s.avail",
			generatedPackageName.replace('.', '/'),
			locale.getLanguage(),
			preamble.getString(key.name())));
		assert fileName.getPath().endsWith(".avail");
		final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
		generatePrimitiveModulePreamble(fallible, versions, writer);
		// TODO: [TLS] Finish this!
		writer.close();
	}

	/**
	 * Generate the target Avail source {@linkplain ModuleDescriptor modules}.
	 *
	 * @param versions
	 *        The supported versions.
	 * @throws IOException
	 *         If any of the source modules could not be written.
	 */
	public void generate (final @NotNull List<String> versions)
		throws IOException
	{
		final File packageName = new File(String.format(
			"src/%s/%s",
			generatedPackageName.replace('.', '/'),
			locale.getLanguage()));
		packageName.mkdir();
		generateOriginModule(versions);
		generateSpecialObjectsModule(versions);
		generatePrimitiveModule(false, versions);
		generatePrimitiveModule(true, versions);
	}

	/**
	 * Construct a new {@link BootstrapGenerator}.
	 *
	 * @param locale The target {@linkplain Locale locale}.
	 */
	public BootstrapGenerator (final @NotNull Locale locale)
	{
		this.locale = locale;
		final UTF8ResourceBundleControl control =
			new UTF8ResourceBundleControl();
		this.preamble = ResourceBundle.getBundle(
			preambleBaseName,
			locale,
			BootstrapGenerator.class.getClassLoader(),
			control);
		this.specialObjectNames = ResourceBundle.getBundle(
			specialObjectsBaseName,
			locale,
			BootstrapGenerator.class.getClassLoader(),
			control);
		this.primitiveNames = ResourceBundle.getBundle(
			primitivesBaseName,
			locale,
			BootstrapGenerator.class.getClassLoader(),
			control);

		// Map localized names to the special objects.
		for (int i = 0; i < specialObjects.size(); i++)
		{
			final AvailObject specialObject = specialObjects.get(i);
			if (specialObject != null)
			{
				final String key = "specialObject" + i;
				specialObjectNameMap.put(
					specialObjectNames.getString(key), specialObject);
			}
		}
	}

	/**
	 * Generate all bootstrap {@linkplain ModuleDescriptor modules}.
	 *
	 * @param args
	 *        The command-line arguments. The first argument is a
	 *        comma-separated list of language codes that broadly specify the
	 *        {@linkplain Locale locales} for which modules should be generated.
	 *        The second argument is a comma-separated list of Avail system
	 *        versions.
	 * @throws Exception
	 *         If anything should go wrong.
	 */
	public static void main (final @NotNull String[] args)
		throws Exception
	{
		final List<String> languages = new ArrayList<String>();
		final List<String> versions = new ArrayList<String>();
		if (args.length < 1)
		{
			languages.add("en");
		}
		else
		{
			final StringTokenizer tokenizer = new StringTokenizer(args[0], ",");
			while (tokenizer.hasMoreTokens())
			{
				languages.add(tokenizer.nextToken());
			}
		}
		if (args.length < 2)
		{
			versions.add("dev");
		}
		else
		{
			final StringTokenizer tokenizer = new StringTokenizer(args[1], ",");
			while (tokenizer.hasMoreTokens())
			{
				versions.add(tokenizer.nextToken());
			}
		}

		for (final String language : languages)
		{
			final BootstrapGenerator generator =
				new BootstrapGenerator(new Locale(language));
			generator.generate(versions);
		}
	}
}
