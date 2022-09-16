/*
 * MenuBuilder.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.anvil

import javax.swing.Action
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu

/** A helper class for building menus. */
class MenuBuilder constructor(private val theMenu: JMenu)
{
	fun item(item: Action)
	{
		theMenu.add(item)
	}

	fun check(item: Action)
	{
		theMenu.add(JCheckBoxMenuItem(item))
	}

	fun separator(): Unit = theMenu.addSeparator()

	/** Add a pre-built menu. */
	fun submenu(submenu: JMenu)
	{
		theMenu.add(submenu)
	}

	/** Create a submenu directly. */
	fun submenu(name: String, body: MenuBuilder.()->Unit): JMenu =
		menu(name, body).also(theMenu::add)

	companion object
	{
		/**
		 * Create a menu with the given name and entries, which can be null to
		 * indicate a separator, a JMenuItem, or an Action to wrap in a
		 * JMenuItem.
		 *
		 * @param name
		 *   The name of the menu to create.
		 * @param body
		 *   A function that adds entries to the menu via the [MenuBuilder]
		 *   syntax.
		 * @return A new [JMenu].
		 */
		private inline fun menu(
			name: String = "",
			body: MenuBuilder.()->Unit
		) = JMenu(name).also { MenuBuilder(it).body() }
	}
}
