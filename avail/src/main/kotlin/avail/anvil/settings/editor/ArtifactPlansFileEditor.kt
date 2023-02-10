/*
 * ArtifactPlansFileEditor.kt
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

package avail.anvil.settings.editor

import avail.anvil.AbstractJSONFileEditor
import avail.anvil.AvailWorkbench
import avail.anvil.shortcuts.KeyboardShortcut
import org.availlang.artifact.AvailArtifactBuildPlan
import org.availlang.artifact.AvailArtifactBuildPlan.Companion.ARTIFACT_PLANS_FILE
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

/**
 * An [AbstractJSONFileEditor] for a [AvailArtifactBuildPlan]s file.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [ArtifactPlansFileEditor].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param frameTitle
 *   The window title.
 * @param autoSave
 *   Whether to auto save the backing file to disk after changes.
 * @param afterTextLoaded
 *   Action to perform after text has been loaded to [sourcePane].
 */
class ArtifactPlansFileEditor constructor(
	workbench: AvailWorkbench,
	frameTitle: String,
	override val autoSave: Boolean = false,
	afterTextLoaded: (ArtifactPlansFileEditor) -> Unit = {}
) : AbstractJSONFileEditor<ArtifactPlansFileEditor>(
	workbench,
	"${workbench.projectConfigDirectory}${File.separator}$ARTIFACT_PLANS_FILE",
	frameTitle,
	afterTextLoaded)
{
	override val shortcuts: List<KeyboardShortcut> = listOf()

	init
	{
		workbench.backupProjectFile()
		finalizeInitialization(afterTextLoaded)
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent?)
			{
				workbench.openFileEditors.remove(fileLocation)
			}
		})
	}

	override fun populateSourcePane(then: (ArtifactPlansFileEditor)->Unit)
	{
		styleCode()
		// TODO move code population here
		then(this)
	}
}
