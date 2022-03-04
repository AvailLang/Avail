/*
 * AvailProjectProblemsViewPanelProvider.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package org.availlang.ide.anvil.ui.toolwindow

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanelProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab
import com.intellij.analysis.problemsView.toolWindow.Root
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import java.awt.event.ActionEvent

/**
 * A `ProjectProblemsViewPanelProvider` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailProjectProblemsViewPanelProvider constructor(
	private val project: Project
) : ProblemsViewPanelProvider
{
	companion object
	{
		const val ID = "AvailProjectErrors"
	}

	private val ACTION_IDS = listOf("CompileDirty", "InspectCode")

	override fun create(): ProblemsViewTab
	{
		val state = ProblemsViewState.getInstance(project)
		val panel =
			ProblemsViewPanel(project, ID, state) { "Avail Project Errors" }
		panel.treeModel.root = CollectorBasedRoot(panel)

		val status = panel.tree.emptyText
		status.text = "No errors found by the IDE"

		if (Registry.`is`("ide.problems.view.empty.status.actions"))
		{
			val or = "or"
			var index = 0
			for (id in ACTION_IDS)
			{
				val action = ActionUtil.getAction(id) ?: continue
				val text = action.templateText
				if (text == null || text.isBlank()) continue
				if (index == 0)
				{
					status.appendText(".")
					status.appendLine("")
				}
				else
				{
					status.appendText(" ").appendText(or).appendText(" ")
				}
				status.appendText(
					text,
					SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES)
				{ event: ActionEvent? ->
					ActionUtil.invokeAction(
						action,
						panel,
						"ProblemsView",
						null,
						null)
				}
				val shortcut = KeymapUtil.getFirstKeyboardShortcutText(action)
				if (!shortcut.isBlank())
				{
					status.appendText(" (")
						.appendText(shortcut).appendText(")")
				}
				index++
			}
		}

		return panel
	}
}

class CollectorBasedRoot constructor(
	panel: ProblemsViewPanel,
	val collector: ProblemsCollector
): Root(panel)
{
	internal constructor(panel: ProblemsViewPanel) :
		this(panel, ProblemsCollector.getInstance(panel.project))
	{
		panel.project.messageBus.connect(this)
			.subscribe(ProblemsListener.TOPIC, this)
	}

	override fun getProblemCount() = collector.getProblemCount()
	override fun getProblemFiles() = collector.getProblemFiles()

	override fun getFileProblemCount(file: VirtualFile) =
		collector.getFileProblemCount(file)

	override fun getFileProblems(file: VirtualFile) =
		collector.getFileProblems(file)

	override fun getOtherProblemCount() = collector.getOtherProblemCount()
	override fun getOtherProblems() = collector.getOtherProblems()
}
