/*
 * Workspaces.kt
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

package avail.anvil.components

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
import androidx.compose.material.Colors
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import avail.anvil.Anvil
import avail.anvil.models.Project
import avail.anvil.models.ProjectDescriptor
import avail.anvil.screens.ProjectManagerView
import avail.anvil.themes.AlternatingRowColor
import avail.anvil.themes.AvailColors
import avail.anvil.themes.LocalTheme
import avail.anvil.themes.anvilTheme
import kotlin.system.exitProcess

////////////////////////////////////////////////////////////////////////////////
//                                Workspaces.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * Present a window housing a complete [workspace][Workspace].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga
 *
 * @param descriptor
 *   The [Project].
 * @param state
 *   The [WindowState].
 * @param onClose
 *   What to do when the workspace closes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WorkspaceWindow (
	descriptor: ProjectDescriptor,
	state: WindowState,
	onClose: () -> Unit = {}
)
{
	Window(
		onCloseRequest = onClose,
		title = descriptor.name,
		state = state
	) {
		val projectConfigEditorIsOpen = remember { mutableStateOf(false) }
		val projectManagerIsOpen = remember { mutableStateOf(false) }
		val newProjectConfigEditorIsOpen = remember { mutableStateOf(false) }
		MenuBar {
			Menu("File", mnemonic = 'P') {
				Item(
					"New Project…",
					onClick = {
						newProjectConfigEditorIsOpen.value = true
					},
					shortcut = KeyShortcut(Key.E, ctrl = true, alt = true))
				Item(
					"Open Project…",
					onClick = {
						projectManagerIsOpen.value = true
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
					"Edit Roots",
					onClick = { projectConfigEditorIsOpen.value = true },
					shortcut = KeyShortcut(Key.E, ctrl = true, alt = true))
			}
		}
		Workspace(descriptor, projectConfigEditorIsOpen, state)
		if (newProjectConfigEditorIsOpen.value)
		{
			Workspace(
				ProjectDescriptor("-"),
				newProjectConfigEditorIsOpen,
				state)
		}
		if(projectManagerIsOpen.value)
		{
			ProjectManagerView {
				projectManagerIsOpen.value = false
			}
		}
	}
}

/**
 * Present a complete workspace. A workspace focuses on a particular
 * [project][Project], providing all views, editors, and interactivity required
 * to manage the Avail development experience.
 *
 * Provides the project and the theme to transitive subcomponents as
 * [CompositionLocal]s.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga
 *
 * @param project
 *   The [Project].
 * @param state
 *   The [WindowState].
 * @param theme
 *   The user interface theme.
 */
@Composable
fun Workspace (
	descriptor: ProjectDescriptor,
	projectConfigEditorIsOpen: MutableState<Boolean>,
	state: WindowState,
	theme: Colors = anvilTheme())
{
	CompositionLocalProvider(LocalTheme provides theme) {
		WorkspaceContent(descriptor, state, projectConfigEditorIsOpen, )
	}
}

/**
 * Present the content of a [workspace][Workspace]. The [project][Project] and
 * theme have been injected by the enclosing [Workspace].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga
 *
 * @param descriptor
 *   The [ProjectDescriptor] to open.
 * @param state
 *   The [WindowState].
 */
@Composable
private fun WorkspaceContent (
	descriptor: ProjectDescriptor,
	state: WindowState,
	projectConfigEditorIsOpen: MutableState<Boolean>)
{
	val width = state.size.width
	val height = state.size.height
	val roots = descriptor.rootsCopy

	DesktopMaterialTheme(colors = LocalTheme.current) {
		Surface(
			shape = RectangleShape,
			color = AvailColors.BG,
			modifier = Modifier
				.background(AvailColors.BG)
				.padding(all = 7.dp)
				.fillMaxSize()
				.defaultMinSize(minWidth = width, minHeight = height)
		) {
			// Box(
			//        modifier = Modifier.fillMaxSize()
			//            .background(color = Color(180, 180, 180))
			//            .padding(10.dp)
			//    ) {
			//        val stateVertical = rememberScrollState(0)
			//        val stateHorizontal = rememberScrollState(0)
			//
			//        Box(
			//            modifier = Modifier
			//                .fillMaxSize()
			//                .verticalScroll(stateVertical)
			//                .padding(end = 12.dp, bottom = 12.dp)
			//                .horizontalScroll(stateHorizontal)
			//        ) {
			//            Column {
			//                for (item in 0..30) {
			//                    TextBox("Item #$item")
			//                    if (item < 30) {
			//                        Spacer(modifier = Modifier.height(5.dp))
			//                    }
			//                }
			//            }
			//        }
			//        VerticalScrollbar(
			//            modifier = Modifier.align(Alignment.CenterEnd)
			//                .fillMaxHeight(),
			//            adapter = rememberScrollbarAdapter(stateVertical)
			//        )
			//        HorizontalScrollbar(
			//            modifier = Modifier.align(Alignment.BottomStart)
			//                .fillMaxWidth()
			//                .padding(end = 12.dp),
			//            adapter = rememberScrollbarAdapter(stateHorizontal)
			//        )
			//    }

			Box {
				val stateVertical = rememberScrollState()
				val stateHorizontal = rememberScrollState()
				Box(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(stateVertical)
						.padding(end = 12.dp, bottom = 12.dp)
						.horizontalScroll(stateHorizontal)
				) {
					Column(
						modifier = Modifier
							.fillMaxSize())
					{
						Anvil.openProjects[descriptor.id]?.let {
							it.rootNodes.values.toList().sorted()
								.forEach { root ->
									root.draw(AlternatingRowColor.ROW1)
								}
						}
					}
				}
				VerticalScrollbar(
					modifier = Modifier.align(Alignment.CenterEnd)
						.fillMaxHeight(),
					adapter = rememberScrollbarAdapter(stateVertical)
				)
				HorizontalScrollbar(
					modifier = Modifier.align(Alignment.BottomStart)
						.fillMaxWidth()
						.padding(end = 12.dp),
					adapter = rememberScrollbarAdapter(stateHorizontal)
				)
			}

			Dialog(
				onCloseRequest = { projectConfigEditorIsOpen.value = false },
				visible = projectConfigEditorIsOpen.value,
				state = rememberDialogState(
					width = width,
					height = height,
					position = state.position),
				title = "Configure Project",
				icon = rememberVectorPainter(Icons.Outlined.Settings)
			) {
				Surface(
					shape = RectangleShape,
					color = anvilTheme().background,
					modifier = Modifier
						.defaultMinSize(minWidth = width, minHeight = height)
						.fillMaxSize()
				) {
					AvailProjectEditor(
						descriptor,
						roots,
						projectConfigEditorIsOpen,
						tableModifier = Modifier
							.fillMaxSize()
							.defaultMinSize(
								minWidth = width,
								minHeight = height)
							.padding(16.dp))
				}
			}
		}
	}
}
