/*
 * AvailProjectNode.kt
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

package avail.anvil.nodes

import avail.anvil.AdaptiveColor
import avail.anvil.AvailWorkbench
import avail.anvil.settings.editor.ProjectFileEditor
import org.availlang.artifact.AvailArtifactBuildPlan
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.LocalSettings
import java.awt.Color
import java.io.File

/**
 * This is a tree node representing the [AvailProject] running in the
 * [AvailWorkbench].
 *
 * @author Richard Arriaga
 */
class AvailProjectNode constructor(
	workbench: AvailWorkbench
): OpenableFileNode(workbench)
{
	override fun open()
	{
		workbench.openFileEditor(workbench.availProjectFilePath)
		{
			ProjectFileEditor(workbench)
		}
	}

	override fun modulePathString(): String = workbench.projectName

	override fun iconResourceName(): String = "config-color"

	override fun equalityText(): String = "configuration"

	override fun htmlStyle(selected: Boolean): String
	{
		val color =
			if (selected) AdaptiveColor(light = Color.white, dark = Color.white)
			else AdaptiveColor(light = Color(0x67CACA), dark = Color(0x67CACA))

		return fontStyle(bold = true, strikethrough = false) +
			"color:${color.hex};"
	}

	val path get() = AvailEnvironment.projectConfigPath(
		workbench.projectName, workbench.projectHomeDirectory)

	init
	{
		workbench.availProject.roots.values.toMutableList()
			.apply { sortBy { it.name } }
			.forEach {
				if (it.visible)
				{
					this.add(RootConfigDirNode(workbench, it, true))
				}
			}
		val configDir = path
		val dir = File(configDir).apply {
			if (!exists()) mkdirs()
		}
		if (!dir.isDirectory)
		{
			System.err.println("$configDir is expected to be a directory")
		}
		else
		{
			dir.listFiles { f ->
				!f.isDirectory && !(
					f.name.endsWith(".backup") ||
						f.name.endsWith("local-state.json"))
			}?.apply {
				sortBy { it.name }
			}?.forEach {
				when
				{
					it.name == AvailArtifactBuildPlan.ARTIFACT_PLANS_FILE ->
						add(ArtifactPlansNode(workbench, it.absolutePath))
					it.name == LocalSettings.LOCAL_SETTINGS_FILE ->
						add(LocalSettingsNode(workbench, it.absolutePath))
					it.name == AvailProject.STYLE_FILE_NAME ->
						add(StylesNode(workbench, it.absolutePath))
					it.name == AvailProject.TEMPLATE_FILE_NAME ->
						add(TemplatesNode(workbench, it.absolutePath))
					it.name.endsWith(".mhd") ->
						add(ModuleHeaderTemplateNode(
							workbench, it.absolutePath))
				}
			}
		}
	}
}
