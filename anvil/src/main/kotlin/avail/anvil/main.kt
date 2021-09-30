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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalFontLoader
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowSize
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberNotification
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import avail.anvil.components.AsyncImage
import avail.anvil.components.AsyncSvg
import avail.anvil.screens.ProjectManagerView
import avail.anvil.themes.ImageResources
import avail.anvil.themes.LocalTheme
import avail.anvil.themes.anvilTheme
import java.awt.Taskbar
import javax.swing.ImageIcon
import kotlin.system.exitProcess

fun main()
{
	Anvil.initialize()
	application {
		val trayState = rememberTrayState()
		val openProjectManager = remember { Anvil.projectManagerIsOpen }
		val notification =
			rememberNotification("Notification", "Welcome to Anvil")
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
						exitProcess(0)
					}
				)
			}
		)
		// Java AWT hack to change the app icon
		Taskbar.getTaskbar().iconImage = ImageIcon(
			this::class.java.classLoader.getResource(
				ImageResources.trayIcon)).image
		CompositionLocalProvider(LocalTheme provides anvilTheme()) {
			var beginShowingScreens by remember { Anvil.isSufficientlyLoadedFromDisk }

			if (Anvil.projectManagerIsOpen.value)
			{
				ProjectManagerView {
					Anvil.projectManagerIsOpen.value = false
				}
			}
			else
			{
				if (!beginShowingScreens)
				{
					val windowSize = rememberSaveable {
						WindowSize(width = 300.dp, height = 240.dp)
					}
					Window(
						onCloseRequest = { exitApplication() },
						undecorated = true,
						state = rememberWindowState(
							width = windowSize.width,
							height = windowSize.height,
							position = WindowPosition.Aligned(Alignment.Center)),
						title = "")
					{
						Surface(
							color = Color(0xFF00824F),
							modifier = Modifier
								.defaultMinSize(
									minWidth = 100.dp,
									minHeight = 100.dp)
								.fillMaxSize()
						) {
							Box(
								contentAlignment = Alignment.Center,
								modifier = Modifier
									.fillMaxSize())
							{
								AsyncSvg(
									resource = ImageResources.availHammer,
									modifier = Modifier.padding(20.dp)
										.size(100.dp))
								CircularProgressIndicator(
									color = Color(0x33000000),
									modifier = Modifier.size(120.dp))
								Text(
									"Opening Projects…",
									Modifier
										.align(Alignment.BottomCenter)
										.padding(bottom = 10.dp),
									Color(0x88000000))
							}
						}
					}
				}
				Anvil.openProjects.values.forEach { project ->
					project.OpenProject()
				}
			}
		}
	}
}
