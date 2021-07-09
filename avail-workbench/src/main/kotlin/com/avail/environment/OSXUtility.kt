/*
 * OSXUtility.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.environment

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Exposes the Mac OS X-specific functionality for gracefully handling normal
 * Mac application event handling.
 */
object OSXUtility
{
	/** The Apple-specific Application class. */
	internal val applicationClass: Class<*>

	/** The Apple-specific ApplicationListener class. */
	internal val applicationListenerClass: Class<*>

	/** The Apple-specific ApplicationEvent class. */
	internal val applicationEventClass: Class<*>

	/** An instance of the [applicationClass]. */
	internal val macOSXApplication: Any

	/** The method Application#addApplicationListener(ApplicationListener). */
	internal val addListenerMethod: Method

	/** The method ApplicationEvent#getFilename(). */
	internal val getFilenameMethod: Method

	/** The method Application#setEnabledAboutMenu(boolean). */
	internal val enableAboutMethod: Method

	/** The method Application#setEnabledPreferencesMenu(boolean). */
	internal val enablePreferencesMethod: Method

	/** The method Application.setDockIconBadge(String). */
	internal val setDockIconBadgeMethod: Method

	init
	{
		try
		{
			applicationClass =
				Class.forName("com.apple.eawt.Application")
			applicationListenerClass =
				Class.forName("com.apple.eawt.ApplicationListener")
			applicationEventClass =
				Class.forName("com.apple.eawt.ApplicationEvent")
			macOSXApplication =
				applicationClass.getConstructor().newInstance()
			addListenerMethod =
				applicationClass.getDeclaredMethod(
				"addApplicationListener", applicationListenerClass)
			getFilenameMethod =
				applicationEventClass.getDeclaredMethod("getFilename")
			enableAboutMethod =
				applicationClass.getDeclaredMethod(
				"setEnabledAboutMenu", Boolean::class.javaPrimitiveType)
			enablePreferencesMethod =
				applicationClass.getDeclaredMethod(
					"setEnabledPreferencesMenu",
					Boolean::class.javaPrimitiveType)
			setDockIconBadgeMethod =
				applicationClass.getDeclaredMethod(
					"setDockIconBadge", String::class.java)
		}
		catch (e: ClassNotFoundException)
		{
			throw RuntimeException(e)
		}
		catch (e: IllegalAccessException)
		{
			throw RuntimeException(e)
		}
		catch (e: IllegalArgumentException)
		{
			throw RuntimeException(e)
		}
		catch (e: InvocationTargetException)
		{
			throw RuntimeException(e)
		}
		catch (e: InstantiationException)
		{
			throw RuntimeException(e)
		}
		catch (e: NoSuchMethodException)
		{
			throw RuntimeException(e)
		}
		catch (e: SecurityException)
		{
			throw RuntimeException(e)
		}
	}

	/**
	 * Pass this method an Object and Method equipped to perform application
	 * shutdown logic.  The method passed should return a boolean stating
	 * whether or not the quit should occur.
	 *
	 * @param quitHandler
	 */
	fun setQuitHandler(quitHandler: (Any) -> Boolean)
	{
		setHandler("handleQuit", quitHandler)
	}

	/**
	 * Pass this method an Object and Method equipped to display application
	 * info.  They will be called when the About menu item is selected from
	 * the application menu.
	 *
	 * @param aboutHandler
	 */
	fun setAboutHandler(aboutHandler: (Any) -> Boolean)
	{
		setHandler("handleAbout", aboutHandler)

		// If we're setting a handler, enable the About menu item by calling
		// com.apple.eawt.Application reflectively.
		try
		{
			enableAboutMethod(macOSXApplication, true)
		}
		catch (ex: Exception)
		{
			System.err.println("OSXUtility could not access the About Menu")
			ex.printStackTrace()
		}
	}

	/**
	 * Pass this method an Object and a Method equipped to display
	 * application options. They will be called when the Preferences menu
	 * item is selected from the application menu.
	 *
	 * @param preferences
	 */
	fun setPreferencesHandler(preferences: (Any)-> Boolean)
	{
		setHandler("handlePreferences", preferences)
		// If we're setting a handler, enable the Preferences menu item by
		// calling com.apple.eawt.Application reflectively.
		try
		{
			enablePreferencesMethod(macOSXApplication, true)
		}
		catch (ex: Exception)
		{
			System.err.println("OSXUtility could not access the About Menu")
			ex.printStackTrace()
		}

	}

	/**
	 * Pass this method an Object and a Method equipped to handle document
	 * events from the Finder.  Documents are registered with the Finder via
	 * the CFBundleDocumentTypes dictionary in the application bundle's
	 * Info.plist.
	 *
	 * // TODO: MvG
	 *
	 * @param fileHandler
	 */
	@Suppress("unused")
	fun setFileHandler(fileHandler: (String) -> Boolean)
	{
		setHandler("handleOpenFile") { event ->
			val filename: String
			try
			{
				filename = getFilenameMethod(event) as String
			}
			catch (e: IllegalAccessException)
			{
				throw RuntimeException(e)
			}
			catch (e: IllegalArgumentException)
			{
				throw RuntimeException(e)
			}
			catch (e: InvocationTargetException)
			{
				throw RuntimeException(e)
			}

			fileHandler(filename)
		}
	}

	/**
	 * Create a [Proxy] object around the provided transformer to invoke it when
	 * the method with the specified name is invoked in the proxy.
	 *
	 * @param handlerMessage
	 * @param handler
	 */
	private fun setHandler(handlerMessage: String, handler: (Any) -> Boolean)
	{
		try
		{
			val proxy = Proxy.newProxyInstance(
				OSXUtility::class.java.classLoader,
				arrayOf(applicationListenerClass))
			{ thisProxy, method, args ->
				assert(thisProxy !== null)
				val success: Boolean = when (method.name)
				{
					handlerMessage ->
						java.lang.Boolean.TRUE == handler(args[0])
					else -> false
				}
				setApplicationEventHandled(args[0], success)
				success
			}
			addListenerMethod(macOSXApplication, proxy)
		}
		catch (e: IllegalAccessException)
		{
			System.err.println(
				"This version of Mac OS X does not support the Apple EAWT.")
		}
		catch (e: IllegalArgumentException)
		{
			System.err.println(
				"This version of Mac OS X does not support the Apple EAWT.")
		}
		catch (e: InvocationTargetException)
		{
			System.err.println(
				"This version of Mac OS X does not support the Apple EAWT.")
		}
	}

	/**
	 * It is important to mark the ApplicationEvent as handled and cancel
	 * the default behavior.  This method checks for a boolean result from
	 * the proxy method and sets the event accordingly.
	 *
	 * @param event
	 * @param handled
	 */
	private fun setApplicationEventHandled(event: Any, handled: Boolean)
	{
		try
		{
			val setHandledMethod =
				event.javaClass.getDeclaredMethod(
					"setHandled", Boolean::class.javaPrimitiveType)
			// If the target method returns a boolean, use that as a hint
			setHandledMethod(event, handled)
		}
		catch (e: Exception)
		{
			System.err.println(
				"OSXUtility was unable to handle an ApplicationEvent: $event")
			e.printStackTrace()
		}
	}
}
