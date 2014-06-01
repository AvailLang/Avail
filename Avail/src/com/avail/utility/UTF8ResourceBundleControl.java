/**
 * UTF8ResourceBundleControl.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.ResourceBundle.Control;
import com.avail.annotations.*;

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
		throws IllegalAccessException, InstantiationException, IOException
	{
		final String bundleName = toBundleName(baseName, locale);
		ResourceBundle bundle = null;
		assert format != null;
		assert loader != null;
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
			catch (final ClassNotFoundException e)
			{
				// Do nothing.
			}
		}
		else if (format.equals("java.properties"))
		{
			final String resourceName =
				toResourceName(bundleName, "properties");
			final ClassLoader classLoader = loader;
			final boolean reloadFlag = reload;
			Reader stream = null;
			try
			{
				stream = AccessController.doPrivileged(
					new PrivilegedExceptionAction<Reader>()
					{
						@Override
						public Reader run () throws IOException
						{
							InputStream is = null;
							if (reloadFlag)
							{
								final URL url =
									classLoader.getResource(resourceName);
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
								is = classLoader.getResourceAsStream(
									resourceName);
							}
							final Reader reader =
								new InputStreamReader(is, "UTF-8");
							return reader;
						}
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
