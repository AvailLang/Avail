/*
 * ProjectManager.kt
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowSize
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import avail.anvil.Anvil
import avail.anvil.components.DataColumn
import avail.anvil.components.HeaderLabel
import avail.anvil.components.ModuleRootLabel
import avail.anvil.components.TableView
import avail.anvil.models.ProjectDescriptor
import avail.anvil.models.ProjectRoot
import avail.anvil.themes.LocalTheme
import avail.anvil.themes.anvilTheme

/**
 * Display the Project Manager Window.
 */
@Composable
fun ProjectManagerView (
	onClose: () -> Unit = {})
{
	val windowSize = rememberSaveable {
		WindowSize(width = 800.dp, height = 600.dp)
	}
	Window(
		onCloseRequest = onClose,
		title = "Project Manager",
		state = rememberWindowState(
			width = windowSize.width,
			height = windowSize.height)
	) {
		CompositionLocalProvider(LocalTheme provides anvilTheme()) {
			ProjectManagerWorkspaceContent(
				rememberWindowState(
					width = windowSize.width,
					height = windowSize.height),
				onClose)
		}
	}
}

/**
 * Present the content of a the project manager window.
 *
 * @author Richard Arriaga
 *
 * @param state
 *   The [WindowState].
 */
@Composable
fun ProjectManagerWorkspaceContent (
	state: WindowState,
	onClose: () -> Unit = {})
{
	val width = state.size.width
	val height = state.size.height
	val roots = mutableStateListOf<ProjectRoot>()
	DesktopMaterialTheme(colors = LocalTheme.current) {
		Surface(
			shape = RectangleShape,
			color = anvilTheme().background,
			modifier = Modifier
				.fillMaxSize()
				.defaultMinSize(minWidth = width, minHeight = height)
		) {
			val createProject = remember { mutableStateOf(false) }
			Column(
				modifier = Modifier.fillMaxSize(),
				horizontalAlignment = Alignment.End)
			{
				TableView(
					Modifier.weight(0.8f).fillMaxWidth(),
					true,
					Anvil.knownProjects.toMutableList(),
					listOf(
						DataColumn(
							"Projects",
							1f,
							headerCellView = @Composable{ header ->
								HeaderLabel(header)
							},
							dataCellView = @Composable { v, row, mutable ->
								ModuleRootLabel(
									v.name,
									modifier = Modifier.clickable {
										v.project {
											Anvil.openProject(it)
											onClose()
										}
									})
							}),
						DataColumn(
							"",
							0.1f,
							false,
							null,
							Modifier.padding(vertical = 2.dp).heightIn(min = 24.dp),
							Modifier.padding(vertical = 2.dp).heightIn(min = 24.dp),
							{ }
						) { _, row, _ ->
							Button(
								modifier = Modifier
									.padding(horizontal = 5.dp)
									.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp)
									.fillMaxSize()
									.align(Alignment.CenterEnd),
								contentPadding = PaddingValues(0.dp),
								onClick = { roots.removeAt(row) })
							{
								Icon(
									Icons.Outlined.Clear,
									contentDescription = "Remove this module root")
							}
						}
					))
				Box(modifier =
					Modifier.weight(0.2f).padding(horizontal = 20.dp))
				{
					Button(onClick = {
						createProject.value = true
					})
					{
						Text("Create")
					}
				}
			}
			if(createProject.value)
			{
				val descriptor = ProjectDescriptor("-")
				Dialog(
					onCloseRequest = { createProject.value = false },
					visible = createProject.value,
					state = rememberDialogState(
						width = width,
						height = height,
						position = state.position),
					title = "Create New Project",
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
							createProject,
							tableModifier = Modifier
								.fillMaxSize()
								.defaultMinSize(minWidth = width, minHeight = height)
								.padding(16.dp))
					}
				}
			}
		}
	}
}
