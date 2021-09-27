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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Colors
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import avail.anvil.models.Project
import avail.anvil.models.ProjectRoot
import avail.anvil.themes.LocalTheme
import avail.anvil.themes.anvilTheme

////////////////////////////////////////////////////////////////////////////////
//                                Workspaces.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * Present a window housing a complete [workspace][Workspace].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param project
 *   The project.
 * @param state
 *   The [WindowState].
 * @param onClose
 *   What to do when the workspace closes.
 */
@Composable
fun WorkspaceWindow (
	project: Project,
	state: WindowState,
	onClose: () -> Unit = {}
)
{
	Window(
		onCloseRequest = onClose,
		title = project.descriptor.name,
		state = state
	) {
		Workspace(project = project, state = state)
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
 *   The project.
 * @param state
 *   The [WindowState].
 * @param theme
 *   The user interface theme.
 */
@Composable
private fun Workspace (
	project: Project,
	state: WindowState,
	theme: Colors = anvilTheme())
{
	CompositionLocalProvider(LocalTheme provides theme) {
		WorkspaceContent(project, state)
	}
}

/**
 * Present the content of a [workspace][Workspace]. The [project][Project] and
 * theme have been injected by the enclosing [Workspace].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Richard Arriaga
 *
 * @param project
 *   The [Project] to open.
 * @param state
 *   The [WindowState].
 */
@Composable
private fun WorkspaceContent (project: Project, state: WindowState)
{
	val width = state.size.width
	val height = state.size.height
	val roots = project.descriptor.rootsCopy

	DesktopMaterialTheme(colors = LocalTheme.current) {
		Surface(
			shape = RectangleShape,
			color = anvilTheme().background,
			modifier = Modifier
				.fillMaxSize()
				.defaultMinSize(minWidth = width, minHeight = height)
		) {
			var projectConfigEditorIsOpen = remember { mutableStateOf(false) }
			Button(
				onClick = { projectConfigEditorIsOpen.value = true }
			) {
				Row {
					Text("Edit Avail roots")
					Icon(
						Icons.Outlined.Edit,
						contentDescription = "Edit Avail roots"
					)
				}
			}
			Dialog(
				onCloseRequest = { projectConfigEditorIsOpen.value = false },
				visible = projectConfigEditorIsOpen.value,
				state = rememberDialogState(
					width = width,
					height = height,
					position = state.position),
				title = "Configure Avail Roots",
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
						project,
						roots,
						projectConfigEditorIsOpen,
						tableModifier = Modifier
							.fillMaxSize()
							.defaultMinSize(minWidth = width, minHeight = height)
							.padding(16.dp))
				}
			}
		}
	}
}
