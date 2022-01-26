/*
 * AvailRootConfigurationStep.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package org.availlang.ide.anvil.module

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import com.intellij.ui.layout.selectedValueIs
import org.availlang.ide.anvil.ui.components.localTextFieldWithBrowseButton

/**
 * A `AvailRootConfigurationStep` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailRootConfigurationStep constructor(
	private val context: WizardContext
): ModuleWizardStep()
{
	private lateinit var nameField: Cell<JBTextField>
	private var projectLocation: String = ""
	private lateinit var projectLocationField: Cell<TextFieldWithBrowseButton>
	private var repoLocationType = RepoLocationType.DEFAULT
	private var repoLocation = ""
	enum class RepoLocationType
	{
		DEFAULT,
		PROJECT,
		CUSTOM
	}


	override fun getComponent(): JComponent =
		panel {
			row("Project Name:    ") {
				contextHelp("", "The name of this project.")
				nameField = cell(JBTextField(45))

			}
			row("Project Location:") {
				contextHelp("The project will be created at this location.")
				projectLocationField =
					cell(localTextFieldWithBrowseButton( 45) {
						projectLocation = projectLocationField.component.text
					})
			}.layout(RowLayout.INDEPENDENT)

			group ("Repository")
			{
				val selections = listOf(
					"Store in default repository location",
					"Store in project",
					"Store in custom location")
				val explanations = listOf(
					"All repository (.repo) files will be stored in this " +
						"location:<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" +
						"[USER_HOME]/.avail/repositories<br/>" +
						"This directory will be created if it doesn't " +
						"already exist.",
					"All repository (.repo) files will be stored within the " +
						"project directory",
					"All repository (.repo) files will be stored in a " +
						"location specified by the user.")
				lateinit var combo: ComboBox<String>
				row {
					combo = ComboBox(selections.toTypedArray(), 300)
					combo.addActionListener {
						when (combo.item)
						{
							selections[0] ->
							{
								repoLocationType = RepoLocationType.DEFAULT
							}
							selections[1] ->
							{
								repoLocationType = RepoLocationType.PROJECT
							}
							selections[2] ->
							{
								repoLocationType = RepoLocationType.CUSTOM
							}
							else ->
							{
								repoLocationType = RepoLocationType.DEFAULT
							}
						}
					}
					cell(combo)
				}
				indent {
					lateinit var repoCell: Cell<TextFieldWithBrowseButton>
					row {
						comment(explanations[0])
						visibleIf(combo.selectedValueIs(selections[0]))
					}
					row {
						comment(explanations[1])
						visibleIf(combo.selectedValueIs(selections[1]))
					}
					row {
						comment(explanations[2])
						visibleIf(combo.selectedValueIs(selections[2]))
					}
					row("Repository Location:") {
						repoCell = cell(localTextFieldWithBrowseButton( 42) {
							repoLocation = repoCell.component.text
							repoLocationType = RepoLocationType.CUSTOM
						})
						visibleIf(combo.selectedValueIs(selections[2]))
					}
				}
			}
		}

	override fun updateDataModel()
	{
		TODO("Not yet implemented")
	}
}
