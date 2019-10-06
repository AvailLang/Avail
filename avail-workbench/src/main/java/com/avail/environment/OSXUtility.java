/*
 * OSXUtility.java
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

package com.avail.environment;

import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Exposes the Mac OS X-specific functionality for gracefully handling normal
 * Mac application event handling.
 */
public final class OSXUtility
{
	/** The Apple-specific Application class. */
	static final Class<?> applicationClass;

	/** The Apple-specific ApplicationListener class. */
	static final Class<?> applicationListenerClass;

	/** The Apple-specific ApplicationEvent class. */
	static final Class<?> applicationEventClass;

	/** An instance of the {@link #applicationClass}. */
	static final Object macOSXApplication;

	/** The method Application#addApplicationListener(ApplicationListener). */
	static final Method addListenerMethod;

	/** The method ApplicationEvent#getFilename(). */
	static final Method getFilenameMethod;

	/** The method Application#setEnabledAboutMenu(boolean). */
	static final Method enableAboutMethod;

	/** The method Application#setEnabledPreferencesMenu(boolean). */
	static final Method enablePrefsMethod;

	/** The method Application.setDockIconBadge(String). */
	static final Method setDockIconBadgeMethod;

	static
	{
		try
		{
			applicationClass = Class.forName("com.apple.eawt.Application");
			applicationListenerClass =
				Class.forName("com.apple.eawt.ApplicationListener");
			applicationEventClass =
				Class.forName("com.apple.eawt.ApplicationEvent");
			macOSXApplication = applicationClass.getConstructor().newInstance();
			addListenerMethod = applicationClass.getDeclaredMethod(
				"addApplicationListener", applicationListenerClass);
			getFilenameMethod = applicationEventClass.getDeclaredMethod(
				"getFilename");
			enableAboutMethod = applicationClass.getDeclaredMethod(
				"setEnabledAboutMenu", boolean.class);
			enablePrefsMethod = applicationClass.getDeclaredMethod(
				"setEnabledPreferencesMenu", boolean.class);
			setDockIconBadgeMethod = applicationClass.getDeclaredMethod(
				"setDockIconBadge", String.class);
		}
		catch (
			final ClassNotFoundException
				| IllegalAccessException
				| IllegalArgumentException
				| InvocationTargetException
				| InstantiationException
				| NoSuchMethodException
				| SecurityException e)
		{
			throw new RuntimeException(e);
		}
	}

	private OSXUtility ()
	{
		assert false : "Utility class - do not call private constructor.";
	}

	/**
	 * Pass this method an Object and Method equipped to perform application
	 * shutdown logic.  The method passed should return a boolean stating
	 * whether or not the quit should occur.
	 *
	 * @param quitHandler
	 */
	public static void setQuitHandler (
		final @Nullable Transformer1<Object, Boolean> quitHandler)
	{
		setHandler("handleQuit", quitHandler);
	}

	/**
	 * Pass this method an Object and Method equipped to display application
	 * info.  They will be called when the About menu item is selected from the
	 * application menu.
	 *
	 * @param aboutHandler
	 */
	public static void setAboutHandler (
		final @Nullable Transformer1<Object, Boolean> aboutHandler)
	{
		setHandler("handleAbout", aboutHandler);

		// If we're setting a handler, enable the About menu item by calling
		// com.apple.eawt.Application reflectively.
		try
		{
			enableAboutMethod.invoke(macOSXApplication, aboutHandler != null);
		}
		catch (final Exception ex)
		{
			System.err.println("OSXUtility could not access the About Menu");
			ex.printStackTrace();
		}
	}

	/**
	 * Pass this method an Object and a Method equipped to display application
	 * options.  They will be called when the Preferences menu item is selected
	 * from the application menu.
	 *
	 * @param prefsHandler
	 */
	public static void setPreferencesHandler (
		final @Nullable Transformer1<Object, Boolean> prefsHandler)
	{
		setHandler("handlePreferences", prefsHandler);
		// If we're setting a handler, enable the Preferences menu item by
		// calling com.apple.eawt.Application reflectively.
		try
		{
			enablePrefsMethod.invoke(macOSXApplication, prefsHandler != null);
		}
		catch (final Exception ex)
		{
			System.err.println("OSXUtility could not access the About Menu");
			ex.printStackTrace();
		}
	}

	/**
	 * Pass this method an Object and a Method equipped to handle document
	 * events from the Finder.  Documents are registered with the Finder via the
	 * CFBundleDocumentTypes dictionary in the application bundle's Info.plist.
	 *
	 * @param fileHandler
	 */
	public static void setFileHandler (
		final Transformer1<String, Boolean> fileHandler)
	{
		setHandler(
			"handleOpenFile",
			event ->
			{
				assert event != null;
				final String filename;
				try
				{
					filename = (String) getFilenameMethod.invoke(event);
				}
				catch (
					final IllegalAccessException
						| IllegalArgumentException
						| InvocationTargetException e)
				{
					throw new RuntimeException(e);
				}
				return fileHandler.value(filename);
			});
	}

	/**
	 * Create a {@link Proxy} object around the provided {@link Transformer1}
	 * to invoke it when the method with the specified name is invoked in the
	 * proxy.
	 *
	 * @param handlerMessage
	 * @param handler
	 */
	public static void setHandler (
		final String handlerMessage,
		final @Nullable Transformer1<Object, Boolean> handler)
	{
		try
		{
			final Object proxy = Proxy.newProxyInstance(
				OSXUtility.class.getClassLoader(),
				new Class<?>[] { applicationListenerClass },
				(thisProxy, method, args) ->
				{
					assert thisProxy != null;
					assert method != null;
					assert args != null;
					final boolean success;
					if (method.getName().equals(handlerMessage)
						&& handler != null)
					{
						final @Nullable Boolean s = handler.value(args[0]);
						success = Boolean.TRUE.equals(s);
					}
					else
					{
						success = false;
					}
					setApplicationEventHandled(args[0], success);
					return success;
				});
			addListenerMethod.invoke(macOSXApplication, proxy);
		}
		catch (
			final IllegalAccessException
				| IllegalArgumentException
				| InvocationTargetException e)
		{
			System.err.println(
				"This version of Mac OS X does not support the Apple EAWT.");
		}
	}

	/**
	 * It is important to mark the ApplicationEvent as handled and cancel the
	 * default behavior.  This method checks for a boolean result from the proxy
	 * method and sets the event accordingly.
	 *
	 * @param event
	 * @param handled
	 */
	static void setApplicationEventHandled (
		final Object event,
		final boolean handled)
	{
		try
		{
			final Method setHandledMethod =
				event.getClass().getDeclaredMethod("setHandled", boolean.class);
			// If the target method returns a boolean, use that as a hint
			setHandledMethod.invoke(event, handled);
		}
		catch (final Exception e)
		{
			System.err.println(
				"OSXUtility was unable to handle an ApplicationEvent: "
				+ event);
			e.printStackTrace();
		}
    }
}
