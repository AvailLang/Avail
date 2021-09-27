/*
 * ModuleRoots.kt
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import avail.anvil.Anvil
import avail.anvil.models.Project
import avail.anvil.models.ProjectRoot
import com.avail.builder.ModuleRoots

////////////////////////////////////////////////////////////////////////////////
//                            Module roots views.                             //
////////////////////////////////////////////////////////////////////////////////

/**
 * Editor for the Avail [module&#32;roots][ModuleRoots], allowing edits to
 * existing roots, subtraction of existing roots, and addition of new roots.
 *
 * @param project
 *   The [Project] being updated.
 * @param projectConfigEditorIsOpen
 *   The [MutableState] Boolean, `true` if this screen should be open; `false`
 *   otherwise.
 * @param isEditable
 *   Whether the view permits editing.
 * @param tableModifier
 *   The [Modifier] to use for the table of module roots.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AvailProjectEditor (
	project: Project,
	roots: MutableList<ProjectRoot>,
	projectConfigEditorIsOpen: MutableState<Boolean>,
	isEditable: Boolean = true,
	tableModifier: Modifier = Modifier
)
{
	var rootCount by mutableStateOf(roots.map { it.name }.toHashSet().size)
	var projectName by remember {
		mutableStateOf(project.descriptor.name)
	}
	var repoPath by remember {
		mutableStateOf(project.descriptor.repositoryPath)
	}
	val focusManager = LocalFocusManager.current
	val focusRequester = remember { FocusRequester() }
	val buttonModifier = Modifier
		.padding(4.dp)
		.sizeIn(minWidth = 32.dp, minHeight = 24.dp)
	val iconModifier = Modifier.size(20.dp)
	val dcProvider: (
		String,
		Float,
		@Composable BoxScope.(ProjectRoot, Int, MutableState<Boolean>)->Unit)->
	DataColumn<ProjectRoot> =
		{ header, weight, content ->
			DataColumn(
				header,
				weight,
				true,
				null, // TODO add sort action
				Modifier.padding(vertical = 2.dp).heightIn(min = 24.dp),
				Modifier.padding(vertical = 2.dp).heightIn(min = 24.dp),
				{
					HeaderLabel(
						it, Modifier.align(Alignment.CenterStart))
				})
			{ root, row, isEditing ->
				content(root, row, isEditing)
			}
		}

	val rootColumn = dcProvider("Root", 0.3f)
	{ root, _, isEditing ->
		if (isEditing.value)
		{
			var temp by remember { mutableStateOf(root.name) }
			TextField(
				value = temp,
				onValueChange = {
					temp = it
					rootCount = roots.map { it.name }.toHashSet().size
				},
				singleLine = true,
				textStyle = TextStyle.Default.copy(fontSize = 14.sp),
				colors = TextFieldDefaults.textFieldColors(
					cursorColor = Color.White, backgroundColor = Color.Black),
				modifier = Modifier
					.fillMaxSize()
					.focusRequester(focusRequester)
					.align(Alignment.CenterStart)
					.onKeyEvent {
						if (it.type == KeyUp)
						{
							when (it.key)
							{
								Key.Enter ->
								{
									root.name = temp
									isEditing.value = false
									focusManager.clearFocus()
									false
								}
								Key.Tab ->
								{
									root.name = temp
									isEditing.value = false
									focusManager.moveFocus(FocusDirection.Next)
									true
								}
								Key.Escape ->
								{
									isEditing.value = false
									focusManager.clearFocus()
									false
								}
								else -> true
							}
						}
						else true
					})
		}
		else
		{
			ModuleRootLabel(
				root.name,
				Modifier.align(Alignment.CenterStart))
		}
	}

	val sourceColumn = dcProvider("Source", 0.6f)
	{ root, _, isEditing ->
		if (isEditing.value)
		{
			var temp by remember { mutableStateOf(root.uri) }
			TextField(
				value = temp,
				onValueChange = { temp = it },
				colors = TextFieldDefaults
					.textFieldColors(cursorColor = Color.White),
				singleLine = true,
				readOnly = false,
				textStyle = TextStyle.Default.copy(fontSize = 14.sp),
				modifier = Modifier
					.align(Alignment.CenterStart)
					.focusRequester(focusRequester)
					.onKeyEvent {
						if (it.type == KeyUp)
						{
							when (it.key)
							{
								Key.Enter ->
								{
									root.uri = temp
									isEditing.value = false
									focusManager.clearFocus()
									false
								}
								Key.Tab ->
								{
									root.uri = temp
									isEditing.value = false
									focusManager.clearFocus()
									false
								}
								Key.Escape ->
								{
									isEditing.value = false
									focusManager.clearFocus()
									false
								}
								else -> true
							}
						}
						else true
					})
		}
		else
		{
			URILabel(
				root.uri,
				Modifier.align(Alignment.CenterStart))
		}
	}

	val deleteRowColumn = DataColumn<ProjectRoot>(
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
	Surface(modifier =
	Modifier.fillMaxSize().clickable { focusManager.clearFocus() })
	{
		Column(
			modifier = tableModifier.fillMaxSize(),
			horizontalAlignment = Alignment.CenterHorizontally)
		{
			// Set project name
			Row(modifier = Modifier.weight(0.1f))
			{
				Box(modifier = Modifier.weight(0.2f))
				{
					HeaderLabel("Project Name")
				}
				Box(modifier = Modifier.weight(0.8f))
				{
					TextField(
						value = projectName,
						onValueChange = { projectName = it },
						colors = TextFieldDefaults
							.textFieldColors(cursorColor = Color.White),
						singleLine = true,
						readOnly = false,
						textStyle = TextStyle.Default.copy(fontSize = 14.sp),
						modifier = Modifier
							.align(Alignment.CenterStart)
							.focusRequester(focusRequester)
							.onKeyEvent {
								if (it.type == KeyUp)
								{
									when (it.key)
									{
										Key.Enter ->
										{
											focusManager.clearFocus()
											false
										}
										Key.Tab ->
										{
											focusManager.clearFocus()
											false
										}
										Key.Escape ->
										{
											projectName =
												project.descriptor.name
											focusManager.clearFocus()
											false
										}
										else -> true
									}
								}
								else true
							})
				}
			}
			// Set repository
			Row(modifier = Modifier.weight(0.1f))
			{
				Box(modifier = Modifier.weight(0.2f))
				{
					HeaderLabel("Repository Path")
				}
				Box(modifier = Modifier.weight(0.8f))
				{
					TextField(
						value = repoPath,
						onValueChange = { repoPath = it },
						colors = TextFieldDefaults
							.textFieldColors(cursorColor = Color.White),
						singleLine = true,
						readOnly = false,
						textStyle = TextStyle.Default.copy(fontSize = 14.sp),
						modifier = Modifier
							.align(Alignment.CenterStart)
							.focusRequester(focusRequester)
							.onKeyEvent {
								if (it.type == KeyUp)
								{
									when (it.key)
									{
										Key.Enter ->
										{
											focusManager.clearFocus()
											false
										}
										Key.Tab ->
										{
											focusManager.clearFocus()
											false
										}
										Key.Escape ->
										{
											repoPath = project.descriptor
												.repositoryPath
											focusManager.clearFocus()
											false
										}
										else -> true
									}
								}
								else true
							})
				}
			}
			// Draw the table
			TableView(
				tableModifier.weight(0.7f),
				true,
				roots,
				listOf(
					rootColumn,
					sourceColumn,
					deleteRowColumn))
			if (isEditable)
			{
				Row(modifier = Modifier.weight(0.1f))
				{
					val columnModifier = Modifier.weight(1.0f)
					Column(modifier = columnModifier) {
						Spacer(modifier = Modifier.fillMaxWidth())
					}
					Column(
						modifier = columnModifier,
						horizontalAlignment = Alignment.CenterHorizontally)
					{
						Tooltip("Add a module root") {
							Button(
								modifier = buttonModifier,
								contentPadding = PaddingValues(0.dp),
								onClick = {
									roots.add(ProjectRoot(" - ", " - "))
									rootCount =
										roots.map { it.name }.toHashSet().size
								})
							{
								Icon(
									Icons.Outlined.Add,
									contentDescription = "Add a module root",
									modifier = iconModifier)
							}
						}
					}
					Column(
						modifier = columnModifier,
						horizontalAlignment = Alignment.End)
					{
						Row {
							Tooltip("Restore default module roots") {
								Button(
									modifier = buttonModifier,
									contentPadding = PaddingValues(0.dp),
									onClick = {
										roots.clear()
										Anvil.defaults.defaultModuleRoots
											.sortedBy { it.name }
											.map {
												roots.add(
													ProjectRoot(
														it.name,
														it.resolver.uri.toString()))
											}
										repoPath =
											project.descriptor.repositoryPath
										projectName =
											project.descriptor.name
									})
								{
									Icon(
										Icons.Outlined.Refresh,
										contentDescription =
										"Restore default module roots",
										modifier = iconModifier)
								}
							}
							Tooltip("Save all updates")
							{
								Button(
									modifier = buttonModifier,
									contentPadding = PaddingValues(0.dp),
									enabled = rootCount == roots.size,
									onClick = {
										val descriptor =
											project.descriptor
										descriptor.roots.clear()
										roots.forEach {
											descriptor.roots[it.id] = it
										}
										descriptor.name = projectName
										descriptor.repositoryPath =
											repoPath
										Anvil.saveConfigToDisk()
										projectConfigEditorIsOpen.value = false
										roots.clear()
										roots.addAll(descriptor.rootsCopy)
										project.stopRuntime()
										val refreshProject =
											descriptor.project()
										Anvil.openProjects[descriptor.id] =
											refreshProject
									})
								{
									Icon(
										Icons.Outlined.Done,
										contentDescription =
										"Save all updates",
										modifier = iconModifier)
								}
							}
							Tooltip("Cancel")
							{
								Button(
									modifier = buttonModifier,
									contentPadding = PaddingValues(0.dp),
									onClick =
									{
										projectConfigEditorIsOpen.value = false
										repoPath =
											project.descriptor.repositoryPath
										projectName =
											project.descriptor.name
										roots.clear()
										roots.addAll(
											project.descriptor.rootsCopy)
									})
								{
									Icon(
										Icons.Outlined.Close,
										contentDescription = "Cancel",
										modifier = iconModifier)
								}
							}
						}
					}
				}
			}
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
//                                 Previews.                                  //
////////////////////////////////////////////////////////////////////////////////
//
///**
// * Preview of light [AvailModuleRootsEditor].
// */
//@Preview
//@Composable
//private fun PreviewAvailModuleRootsViewLight () =
//	MaterialTheme(colors = anvilLightTheme) {
//		AvailModuleRootsEditor(
//			createAvailRuntime()
//				.moduleRoots()
//				.toModuleRootBuffers()
//				.toMutableList())
//	}
//
///**
// * Preview of dark [AvailModuleRootsEditor].
// */
//@Preview
//@Composable
//private fun PreviewAvailModuleRootsViewDark () =
//	MaterialTheme(colors = anvilDarkTheme) {
//		AvailModuleRootsEditor(
//			createAvailRuntime()
//				.moduleRoots()
//				.toModuleRootBuffers()
//				.toMutableList())
//	}
