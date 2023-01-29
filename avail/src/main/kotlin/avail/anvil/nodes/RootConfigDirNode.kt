/*
 * RootConfigDirNode.kt
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

import avail.anvil.AvailWorkbench
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.LocalSettings.Companion.LOCAL_SETTINGS_FILE
import java.io.File

/**
 * This is a tree node representing an [AvailProjectRoot] config directory.
 *
 * @author Richard Arriaga
 *
 * @property workbench
 *   The [AvailWorkbench] this
 * @property visible
 *   `true` indicates this node is visible; `false` otherwise.
 */
class RootConfigDirNode constructor(
	val workbench: AvailWorkbench,
	val root: AvailProjectRoot,
	val visible: Boolean
) : AbstractBuilderFrameTreeNode(workbench.availBuilder)
{
	override fun modulePathString(): String = root.name

	override fun iconResourceName(): String = "root-config-color"

	override fun text(selected: Boolean) = root.name

	override fun htmlStyle(selected: Boolean): String =
		fontStyle(bold = true, strikethrough = !visible) +
			colorStyle(selected, loaded = visible, false)

	val path get() = AvailEnvironment.projectRootConfigPath(
		workbench.projectName, root.name, workbench.projectHomeDirectory)

	override val sortMajor: Int get() = 50

	init
	{
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
				!f.isDirectory && !f.name.endsWith(".backup")
			}?.apply {
				sortBy { it.name }
			}?.forEach {
				when
				{
					it.name == "styles.json" ->
					{
						add(StylesNode(workbench, it.absolutePath))
					}
					it.name == "templates.json" ->
					{
						add(TemplatesNode(workbench, it.absolutePath))
					}
					it.name == LOCAL_SETTINGS_FILE ->
					{
						add(LocalSettingsNode(
							workbench, it.absolutePath))
					}
					it.name.endsWith(".mhd") ->
					{
						add(ModuleHeaderTemplateNode(
							workbench, it.absolutePath))
					}
				}
			}
		}
	}
}
