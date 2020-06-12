/*
 * UTF8ResourceBundleControl.kt
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
package com.avail.utility

import com.avail.utility.Casts.cast
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.util.*

/**
 * `UTF8ResourceBundleControl` permits the reading of UTF-8-encoded Java
 * properties files.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class UTF8ResourceBundleControl : ResourceBundle.Control()
{
	@Throws(IOException::class)
	override fun newBundle(
		baseName: String,
		locale: Locale,
		format: String,
		loader: ClassLoader,
		reload: Boolean): ResourceBundle?
	{
		val bundleName = toBundleName(baseName, locale)
		var bundle: ResourceBundle? = null
		if (format == "java.class")
		{
			try
			{
				val bundleClass: Class<out ResourceBundle> =
					cast(loader.loadClass(bundleName))

				// If the class isn't a ResourceBundle subclass, throw a
				// ClassCastException.
				bundle =
					if (ResourceBundle::class.java.isAssignableFrom(bundleClass))
					{
						bundleClass.newInstance()
					}
					else
					{
						throw ClassCastException(
							"${bundleClass.name} cannot be cast to ResourceBundle")
					}
			}
			catch (e: ClassNotFoundException)
			{
				// Do nothing.
			}
			catch (e: InstantiationException) { }
			catch (e: IllegalAccessException) { }
		}
		else if (format == "java.properties")
		{
			val stream: Reader?
			try
			{
				val resourceName = toResourceName(
					bundleName, "properties")
				stream = AccessController.doPrivileged(
					PrivilegedExceptionAction {
						val inputStream: InputStream
						if (reload)
						{
							val url = loader.getResource(resourceName)
								?: throw IOException("Invalid URL for resource")
							val connection = url.openConnection()
								?: throw IOException("Invalid URL for resource")
							// Disable caches to get fresh data for
							// reloading.
							connection.useCaches = false
							inputStream = connection.getInputStream()
						}
						else
						{
							inputStream =
								loader.getResourceAsStream(resourceName)!!
						}
						InputStreamReader(inputStream, StandardCharsets.UTF_8)
					})
			}
			catch (e: PrivilegedActionException)
			{
				throw (e.exception as IOException)
			}
			if (stream != null)
			{
				bundle = stream.use { PropertyResourceBundle(it) }
			}
		}
		else
		{
			throw IllegalArgumentException("unknown format: $format")
		}
		return bundle
	}
}