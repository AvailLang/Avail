/*
 * StylesNode.kt
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
import avail.anvil.settings.editor.StylesFileEditor
import org.availlang.artifact.environment.project.StylingGroup

/**
 * This is a tree node representing a [StylingGroup] file.
 *
 * @author Richard Arriaga
 *=
 * @property filePath
 *   The absolute path to the represented file.
 */
class StylesNode constructor(
	workbench: AvailWorkbench,
	private val filePath: String
): OpenableFileNode(workbench)
{
	override fun modulePathString(): String = "stylesheet"

	override fun iconResourceName(): String = "palette_full_colors"

	override fun text(selected: Boolean) = "stylesheet"

	override fun htmlStyle(selected: Boolean): String =
		fontStyle(bold = false, strikethrough = false) +
			colorStyle(selected, loaded = true, false)

	override val sortMajor: Int get() = 60

	override fun open()
	{
		workbench.openFileEditor(filePath)
		{
			StylesFileEditor(workbench, it, it)
		}
	}
}
