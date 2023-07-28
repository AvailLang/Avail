/*
 * ModuleHeaderTemplateNode.kt
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
import avail.anvil.settings.editor.ModuleHeaderTemplateEditor
import java.io.File

/**
 * This is a tree node representing a module header file (`.mdh`).
 *
 * @author Richard Arriaga
 *
 * @property filePath
 *   The absolute path to the represented file.
 */
class ModuleHeaderTemplateNode constructor(
	workbench: AvailWorkbench,
	val filePath: String
): OpenableFileNode(workbench)
{
	/**
	 * The name of the file.
	 */
	private val fileName = File(filePath).name
	override fun modulePathString(): String = fileName

	override fun iconResourceName(): String = "module_header_template"

	override fun equalityText() = fileName.removeSuffix(".mhd")

	override val sortMajor: Int get() = 61

	override fun htmlStyle(selected: Boolean): String =
		fontStyle(bold = false, strikethrough = false) +
			colorStyle(selected, loaded = true, false)

	override fun open()
	{
		workbench.openFileEditor(filePath)
		{
			ModuleHeaderTemplateEditor(workbench, it)
		}
	}
}
