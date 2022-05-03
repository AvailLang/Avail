/*
 * SetDocumentationPathAction.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.environment.actions

import avail.environment.AvailWorkbench
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.Action
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileFilter

/**
 * A `SetDocumentationPathAction` displays a [modal&#32;dialog][JOptionPane]
 * that prompts the user for the Stacks documentation path.
 *
 * @constructor
 * Construct a new [SetDocumentationPathAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class SetDocumentationPathAction constructor(workbench: AvailWorkbench)
	: AbstractWorkbenchAction(workbench, "Set Documentation Path…")
{
	override fun actionPerformed(event: ActionEvent)
	{
		val chooser = JFileChooser()
		chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
		chooser.selectedFile = workbench.documentationPath.toFile()
		chooser.ensureFileIsVisible(workbench.documentationPath.toFile())
		chooser.addChoosableFileFilter(
			object : FileFilter()
			{
				override fun getDescription(): String
				{
					return "Directories"
				}

				override fun accept(f: File?): Boolean
				{
					assert(f !== null)
					return f!!.isDirectory && f.canWrite()
				}
			})
		val result = chooser.showDialog(
			workbench, "Set Documentation Path")
		if (result == JFileChooser.APPROVE_OPTION)
		{
			workbench.documentationPath = chooser.selectedFile.toPath()
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION, "Set the Stacks documentation path.")
	}
}
