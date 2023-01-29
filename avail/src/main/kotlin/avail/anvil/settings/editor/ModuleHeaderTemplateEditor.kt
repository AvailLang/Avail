/*
 * ModuleHeaderTemplateEditor.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.settings.editor

import avail.anvil.AvailWorkbench
import avail.anvil.CodeEditor
import avail.anvil.shortcuts.KeyboardShortcut
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * An editor for a [ModuleHeaderTemplateEditor] file.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [ModuleHeaderTemplateEditor].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param fileLocation
 *   The absolute path of the source code file.
 * @param autoSave
 *   Whether to auto save the backing file to disk after changes.
 * @param afterTextLoaded
 *   Action to perform after text has been loaded to [sourcePane].
 */
class ModuleHeaderTemplateEditor constructor(
	workbench: AvailWorkbench,
	fileLocation: String,
	override val autoSave: Boolean = false,
	afterTextLoaded: (ModuleHeaderTemplateEditor) -> Unit = {}
) : CodeEditor<ModuleHeaderTemplateEditor>(workbench, fileLocation, fileLocation)
{
	override val shortcuts: List<KeyboardShortcut> = listOf()

	init
	{
		finalizeInitialization(afterTextLoaded)
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent?)
			{
				workbench.openFileEditors.remove(fileLocation)
			}
		})
	}

	override fun populateSourcePane(then: (ModuleHeaderTemplateEditor)->Unit)
	{
		highlightCode()
		// TODO move code population here
		then(this)
	}
}
