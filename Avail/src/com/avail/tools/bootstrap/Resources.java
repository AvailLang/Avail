/**
 * com.avail.tools.bootstrap/Resources.java
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

import java.util.ResourceBundle;
import com.avail.annotations.NotNull;

/**
 * {@code Resources} centralizes {@linkplain ResourceBundle resource bundle}
 * paths and keys.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
final class Resources
{
	/**
	 * The base name of the {@linkplain ResourceBundle resource bundle} that
	 * contains the preamble.
	 */
	public static final @NotNull String preambleBaseName =
		Resources.class.getPackage().getName() + ".Preamble";

	/**
	 * The name of the package that should contain the generated output.
	 */
	public static final @NotNull String generatedPackageName =
		Resources.class.getPackage().getName() + ".generated";

	/**
	 * The base name of the {@linkplain ResourceBundle resource bundle} that
	 * contains the Avail names of the special objects.
	 */
	public static final @NotNull String specialObjectsBaseName =
		generatedPackageName + ".SpecialObjectNames";

	/**
	 * The base name of the target {@linkplain ResourceBundle resource bundle}
	 * that contains the Avail names of the primitives.
	 */
	public static final @NotNull String primitivesBaseName =
		generatedPackageName + ".PrimitiveNames";

	/**
	 * Answer the local name of the specified {@linkplain ResourceBundle
	 * resource bundle} base name.
	 *
	 * @param bundleName A resource bundle base name.
	 * @return The local name, e.g. the name following the last period (.).
	 */
	public static @NotNull String localName (final @NotNull String bundleName)
	{
		return bundleName.substring(bundleName.lastIndexOf('.') + 1);
	}

	@SuppressWarnings("all")
	public static enum Key
	{
		propertiesCopyright,
		generatedPropertiesNotice,
		availCopyright,
		generatedModuleNotice,
		originModuleName,
		originModuleHeader,
		specialObjectsModuleName,
		infalliblePrimitivesModuleName,
		falliblePrimitivesModuleName,
		primitivesModuleHeader,
		bootstrapDefiningMethod,
		bootstrapSpecialObject,
		definingMethodUse,
		specialObjectUse
	}
}
