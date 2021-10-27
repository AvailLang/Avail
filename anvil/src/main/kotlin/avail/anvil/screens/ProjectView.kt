/*
 * ProjectView.kt
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

package avail.anvil.screens

import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import avail.anvil.Anvil
import avail.anvil.models.Project
import avail.anvil.themes.AvailColors
import avail.anvil.themes.LocalTheme
import avail.anvil.themes.anvilTheme
import kotlin.system.exitProcess

/**
 * A `ProjectView` is the overall screen displaying an interactive [Project].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @param project
 *   The [Project].
 * @param state
 *   The [WindowState].
 * @param theme
 *   The user interface theme.
 * @param onClose
 *   What to do when the workspace closes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProjectWindow (
	project: Project,
	onClose: () -> Unit = {}
)
{
	Window(
		onCloseRequest = onClose,
		title = project.descriptor.name,
		state = project.projectState.windowState
	) {
		val projectConfigEditorIsOpen =
			remember { mutableStateOf(false) }
		val newProjectConfigEditorIsOpen =
			remember { mutableStateOf(false) }
		MenuBar {
			Menu("File", mnemonic = 'P') {
				Item(
					"New Project",
					onClick = {
						newProjectConfigEditorIsOpen.value = true
					},
					shortcut = KeyShortcut(Key.E, ctrl = true, alt = true))
				Item(
					"Open Project",
					onClick = {
						Anvil.projectManagerIsOpen.value = true
					},
					shortcut = KeyShortcut(Key.E, ctrl = true, alt = true))
				Item(
					"Exit Anvil",
					onClick = {
						Anvil.saveConfigToDisk()
						exitProcess(0)
					},
					shortcut = KeyShortcut(Key.E, ctrl = true, alt = true))
			}
			Menu("Project", mnemonic = 'P') {
				Item(
					"Configure Project",
					onClick = { projectConfigEditorIsOpen.value = true },
					shortcut = KeyShortcut(Key.E, ctrl = true, alt = true))
			}
		}
		ProjectFileExplorer(project, project.projectState.windowState)
		val roots = project.descriptor.rootsCopy
		DesktopMaterialTheme(colors = LocalTheme.current)
		{
			val dialogState = rememberDialogState(
				width = 600.dp,
				height = 800.dp)
			Dialog(
				onCloseRequest = {
					projectConfigEditorIsOpen.value = false
				},
				visible = projectConfigEditorIsOpen.value,
				state = dialogState,
				title = "Configure Project",
				icon = rememberVectorPainter(Icons.Outlined.Settings)
			) {
				Surface(
					shape = RectangleShape,
					color = anvilTheme().background,
					modifier = Modifier
						.defaultMinSize(
							minWidth = dialogState.size.width,
							minHeight = dialogState.size.height)
						.fillMaxSize()
				) {
					AvailProjectEditor(
						project.descriptor,
						roots,
						projectConfigEditorIsOpen,
						tableModifier = Modifier
							.fillMaxSize()
							.defaultMinSize(
								minWidth = dialogState.size.width,
								minHeight = dialogState.size.height)
							.padding(16.dp))
				}
			}
		}
	}
}

/**
 * Present the content of a [project][Project]'s module roots in a file
 * explorer window.
 *
 * @author Richard Arriaga
 *
 * @param project
 *   The [Project] to open.
 * @param state
 *   The [WindowState].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowScope.ProjectFileExplorer (
	project: Project,
	state: WindowState)
{
	DesktopMaterialTheme(colors = LocalTheme.current)
	{
		Surface(
			shape = RectangleShape,
			color = AvailColors.BG,
			modifier = Modifier
				.background(AvailColors.BG)
				.padding(all = 7.dp)
				.fillMaxSize()
		) {
			Box {
				project.projectState.verticalScroll =
					rememberScrollState(
						project.projectState.verticalScroll.value)
				project.projectState.horizontalScroll =
					rememberScrollState(
						project.projectState.horizontalScroll.value)
				rememberScrollState()
				Box(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(project.projectState.verticalScroll)
						.padding(end = 12.dp, bottom = 12.dp)
						.horizontalScroll(project.projectState.horizontalScroll)
				) {
					Column(
						modifier = Modifier
							.fillMaxSize())
					{
						project.rootNodes.values
							.toList()
							.sorted()
							.forEach { it.draw() }
					}
				}
				VerticalScrollbar(
					modifier = Modifier.align(Alignment.CenterEnd)
						.fillMaxHeight(),
					adapter = rememberScrollbarAdapter(
						project.projectState.verticalScroll
					)
				)
				HorizontalScrollbar(
					modifier = Modifier.align(Alignment.BottomStart)
						.fillMaxWidth()
						.padding(end = 12.dp),
					adapter = rememberScrollbarAdapter(
						project.projectState.horizontalScroll)
				)
			}
		}

	}
}
