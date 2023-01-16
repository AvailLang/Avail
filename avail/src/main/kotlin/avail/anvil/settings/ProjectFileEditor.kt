/*
 * AvailEditor.kt
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

package avail.anvil.settings

import avail.anvil.AvailWorkbench
import avail.anvil.CodeEditor
import avail.anvil.shortcuts.KeyboardShortcut

/**
 * An editor for an Avail project file.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [ProjectFileEditor].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param afterTextLoaded
 *   Action to perform after text has been loaded to [sourcePane].
 */
class ProjectFileEditor constructor(
	workbench: AvailWorkbench,
	afterTextLoaded: (ProjectFileEditor) -> Unit = {}
) : CodeEditor<ProjectFileEditor>(
	workbench, "Project: ${workbench.availProject.name}")
{
	override val shortcuts: List<KeyboardShortcut>
		get() = TODO("Not yet implemented")
	override val fileLocation: String get() = workbench.availProjectFilePath

	init
	{
		workbench.backupProjectFile()
		finalizeInitialization(afterTextLoaded)
	}

	override fun populateSourcePane(then: (ProjectFileEditor)->Unit)
	{
		highlightCode()
		// TODO move code population here
		then(this)
	}
}
