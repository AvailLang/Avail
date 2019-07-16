/*
 * AvailRuntimeConfiguration.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.MacroDefinitionDescriptor;
import com.avail.interpreter.Interpreter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.StringDescriptor.stringFrom;

/**
 * This class contains static state and methods related to the current running
 * configuration.
 */
public final class AvailRuntimeConfiguration
{
	/** Prevent instantiation. */
	private AvailRuntimeConfiguration ()
	{
		//Do not instantiate.
	}

	/** The build version, set by the build process. */
	private static final String buildVersion;

	/*
	 * Initialize the build version from a resource bundled with the
	 * distribution JAR.
	 */
	static
	{
		String version = "dev";
		try (
			final @Nullable InputStream resourceStream =
				ClassLoader.getSystemResourceAsStream(
					"resources/build.time.txt"))
		{
			if (resourceStream != null)
			{
				try (final Scanner scanner = new Scanner(resourceStream))
				{
					version = scanner.nextLine();
				}
			}
		}
		catch (final IOException e)
		{
			version = "UNKNOWN";
		}
		buildVersion = version;
	}

	/**
	 * Answer the build version, as set by the build process.
	 *
	 * @return The build version, or {@code "dev"} if Avail is not running from
	 *         a distribution JAR.
	 */
	@SuppressWarnings("unused")
	public static String buildVersion ()
	{
		return buildVersion;
	}

	/**
	 * The active versions of the Avail virtual machine. These are the versions
	 * for which the virtual machine guarantees compatibility.
	 */
	private static final String[] activeVersions = {"1.3.0 DEV 2019-07-16"};

	/**
	 * Answer the active versions of the Avail virtual machine. These are the
	 * versions for which the virtual machine guarantees compatibility.
	 *
	 * @return The active versions.
	 */
	public static A_Set activeVersions ()
	{
		A_Set versions = emptySet();
		for (final String version : activeVersions)
		{
			versions = versions.setWithElementCanDestroy(
				stringFrom(version), true);
		}
		return versions;
	}

	/** The number of available processors. */
	public static final int availableProcessors =
		Runtime.getRuntime().availableProcessors();

	/**
	 * The maximum number of {@link Interpreter}s that can be constructed for
	 * this runtime.
	 */
	public static final int maxInterpreters = availableProcessors;

	/**
	 * Whether to show all {@link MacroDefinitionDescriptor macro} expansions as
	 * they happen.
	 */
	public static boolean debugMacroExpansions = false;

	/**
	 * Whether to show detailed compiler trace information.
	 */
	public static boolean debugCompilerSteps = false;
}
