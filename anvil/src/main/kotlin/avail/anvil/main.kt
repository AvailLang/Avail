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

import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowSize
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import avail.anvil.components.WorkspaceWindow
import avail.anvil.models.ProjectDescriptor

fun main()
{
	Anvil.initialize()
	application {
		DesktopMaterialTheme {
			val windowSize = rememberSaveable {
				WindowSize(width = 800.dp, height = 600.dp)
			}
			if (Anvil.openProjects.isEmpty())
			{
				// TODO open dialog that lists known projects that can be
				//  opened, with option to create new project. The new project
				//  dialog can just be effectively the edit config project.

				if (Anvil.knownProjects.isEmpty())
				{
					Anvil.addKnownProject(ProjectDescriptor("«test»"))
				}
				// TODO REMOVE
				Anvil.knownProjects.forEach {
					Anvil.openProjects[it.id] = it.project()
				}
			}
			else
			{
				// TODO open projects?
			}

			// TODO remove this and associate the project config editor with the
			//  project
			Anvil.openProjects.values.toList().sorted().forEach { project ->
				WorkspaceWindow(
					project = project,
					state = rememberWindowState(
						width = windowSize.width,
						height = windowSize.height))
				{
					Anvil.saveConfigToDisk()
					exitApplication()
				}
			}
		}
	}
}

