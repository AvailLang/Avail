/*
 * UTF8ResourceBundleControl.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.utility;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

/**
 * {@code UTF8ResourceBundleControl} permits the reading of UTF-8-encoded
 * Java properties files.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class UTF8ResourceBundleControl
extends Control
{
	@Override
	public @Nullable ResourceBundle newBundle (
			final @Nullable String baseName,
			final @Nullable Locale locale,
			final @Nullable String format,
			final @Nullable ClassLoader loader,
			final boolean reload)
		throws IOException
	{
		assert format != null;
		assert loader != null;
		final String bundleName = toBundleName(baseName, locale);
		@Nullable ResourceBundle bundle = null;
		if (format.equals("java.class"))
		{
			try
			{
				@SuppressWarnings("unchecked")
				final Class<? extends ResourceBundle> bundleClass =
					(Class<? extends ResourceBundle>) loader.loadClass(
						bundleName);

				// If the class isn't a ResourceBundle subclass, throw a
				// ClassCastException.
				if (ResourceBundle.class.isAssignableFrom(bundleClass))
				{
					bundle = bundleClass.newInstance();
				}
				else
				{
					throw new ClassCastException(
						bundleClass.getName()
						+ " cannot be cast to ResourceBundle");
				}
			}
			catch (final ClassNotFoundException
				| InstantiationException
				| IllegalAccessException e)
			{
				// Do nothing.
			}
		}
		else if (format.equals("java.properties"))
		{
			final Reader stream;
			try
			{
				final String resourceName = toResourceName(
					bundleName, "properties");
				stream = AccessController.doPrivileged(
					(PrivilegedExceptionAction<Reader>) () ->
					{
						final InputStream is;
						if (reload)
						{
							final @Nullable URL url = loader.getResource(resourceName);
							if (url == null)
							{
								throw new IOException(
									"Invalid URL for resource");
							}
							final URLConnection connection =
								url.openConnection();
							if (connection == null)
							{
								throw new IOException(
									"Invalid URL for resource");
							}
							// Disable caches to get fresh data for
							// reloading.
							connection.setUseCaches(false);
							is = connection.getInputStream();
						}
						else
						{
							is = loader.getResourceAsStream(resourceName);
						}
						return (Reader) new InputStreamReader(
							is, StandardCharsets.UTF_8);
					});
			}
			catch (final PrivilegedActionException e)
			{
				throw (IOException) e.getException();
			}
			if (stream != null)
			{
				try
				{
					bundle = new PropertyResourceBundle(stream);
				}
				finally
				{
					stream.close();
				}
			}
		}
		else
		{
			throw new IllegalArgumentException("unknown format: " + format);
		}
		return bundle;
	}
}
