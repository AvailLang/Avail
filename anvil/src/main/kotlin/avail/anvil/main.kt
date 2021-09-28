/*
 * main.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowSize
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberNotification
import androidx.compose.ui.window.rememberTrayState
import avail.anvil.screens.ProjectManagerView
import java.awt.Taskbar
import javax.swing.ImageIcon

fun main()
{
	Anvil.initialize()
	application {
		val trayState = rememberTrayState()
		val notification = rememberNotification("Notification", "Welcome to Anvil")
		Tray(
			state = trayState,
			icon = painterResource("AvailHammer.svg"),
			menu = {
				Item(
					"Greeting",
					onClick = {
						trayState.sendNotification(notification)
					}
				)
				Item(
					"Exit",
					onClick = {
						Anvil.saveConfigToDisk()
						exitApplication()
					}
				)
			}
		)
		// Java AWT hack to change the app icon
		Taskbar.getTaskbar().iconImage = ImageIcon(
			this::class.java.classLoader.getResource(
				"AvailHammer.png")).image

		var beginShowingScreens by remember { Anvil.isSufficientlyLoadedFromDisk }
		if (beginShowingScreens)
		{
			if (Anvil.openProjects.isEmpty())
			{
				ProjectManagerView()
			}
			else
			{
				Anvil.openProjects.values.forEach { project ->
					project.OpenProject()
				}
			}
		}
		else
		{
			// TODO show loader...
		}
	}
}
