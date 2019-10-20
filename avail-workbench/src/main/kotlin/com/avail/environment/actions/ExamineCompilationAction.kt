/*
 * ExamineCompilationAction.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.environment.actions

import com.avail.AvailRuntime
import com.avail.descriptor.FiberDescriptor
import com.avail.environment.AvailWorkbench
import com.avail.persistence.IndexedRepositoryManager.ModuleCompilation
import com.avail.persistence.IndexedRepositoryManagerDescriber
import javax.swing.*
import java.awt.event.ActionEvent

import com.avail.utility.Casts.nullableCast
import com.avail.utility.Nulls.stripNull

/**
 * A `ExamineCompilationAction` presents information about a specific
 * compilation of the selected module.
 *
 * @property runtime
 *   The active [AvailRuntime].
 *
 * @constructor
 * Construct a new `ExamineRepositoryAction`.
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 * @param runtime
 *   The active [AvailRuntime].
 */
class ExamineCompilationAction constructor (
		workbench: AvailWorkbench, private val runtime: AvailRuntime)
	: AbstractWorkbenchAction(workbench, "Examine compilation")
{
	override fun actionPerformed(event: ActionEvent?)
	{
		workbench.clearTranscript()
		runtime.execute(FiberDescriptor.commandPriority)
		execute@{
			val moduleName =
				stripNull(workbench.selectedModule())
			moduleName.repository.use { repository ->
				repository.reopenIfNecessary()
				val compilations = ArrayList<ModuleCompilation>()
				val archive =
					repository.getArchive(moduleName.rootRelativeName)
				for ((_, version) in archive.allKnownVersions)
				{
					// final ModuleVersionKey versionKey = entry.getKey();
					compilations.addAll(version.allCompilations)
				}
				val compilationsArray =
					compilations.toTypedArray()
				val selectedCompilation =
					nullableCast<Any, ModuleCompilation>(
						JOptionPane.showInputDialog(
							workbench,
							"Select module compilation to examine",
							"Examine compilation",
							JOptionPane.PLAIN_MESSAGE, null,
							compilationsArray,
							if (compilationsArray.isNotEmpty())
							{ compilationsArray[0] }
							else
							{ null }))
					?: return@execute // Nothing was selected, so abort the command silently.

				val describer = IndexedRepositoryManagerDescriber(repository)
				val description = describer.describeCompilation(
					selectedCompilation.recordNumber)
				workbench.outputStream.println(description)
			}
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Disassemble an existing module compilation")
	}
}
