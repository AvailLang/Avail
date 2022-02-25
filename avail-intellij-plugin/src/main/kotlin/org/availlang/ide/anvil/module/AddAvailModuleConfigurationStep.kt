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

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.ProjectBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.selected
import javax.swing.JComponent
import org.availlang.ide.anvil.models.ProjectHome
import org.availlang.ide.anvil.models.UserHome
import org.availlang.ide.anvil.models.project.AnvilConfiguration
import org.availlang.ide.anvil.models.project.AnvilProjectRoot
import org.availlang.ide.anvil.models.project.AnvilProjectService
import org.availlang.ide.anvil.models.project.anvilProjectService
import org.availlang.ide.anvil.ui.components.localTextFieldWithBrowseButton
import java.io.File

/**
 * The [ModuleWizardStep] for adding a new Avail Module root to an existing
 * Anvil project.
 *
 * This can be accessed from the IntelliJ menu:
 *
 * File -> New -> Project...
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property context
 *   The [WizardContext].
 * @property project
 *   The existing [Project].
 */
class AddAvailModuleConfigurationStep constructor(
	private val context: WizardContext,
	private val project: Project
): ModuleWizardStep()
{
	/**
	 * The [AddModuleSetupData] used to preserve data set in this
	 * [AddAvailModuleConfigurationStep] between step screens.
	 */
	private val data: AddModuleSetupData

	init
	{
		val service = project.anvilProjectService
		data = AddModuleSetupData(
			service,
			service.anvilProject.configuration)
	}

	/**
	 * The field used to name the Avail module root to create/import.
	 */
	@Suppress("UnstableApiUsage")
	private lateinit var moduleRootName: Cell<JBTextField>

	/**
	 * The path to the repository location.
	 */
	private var existingRootLocation = ""

	/**
	 *
	 */
	private lateinit var importRootCheckbox: JBCheckBox

	/**
	 * This is the [JComponent] that is added to the
	 * [WizardContext.myProjectBuilder] screen.
	 */
	@Suppress("UnstableApiUsage", "DialogTitleCapitalization")
	private val componentPanel: JComponent =
		panel {
			row {
				label("Module Root Name")
				comment("required")
				contextHelp(
					"",
					"The name of the Avail module root to create/import.")
				moduleRootName = textField()
					.horizontalAlign(HorizontalAlign.FILL)
					.bindText(
						{ data.moduleName },
						{ data.moduleName = it })
			}
			row {
				importRootCheckbox = JBCheckBox("Import Existing Root")
				cell(importRootCheckbox)
			}
			indent {
				lateinit var locationCell: Cell<TextFieldWithBrowseButton>
				row("Module Root Location:") {
					locationCell = cell(localTextFieldWithBrowseButton(20) {
						existingRootLocation = locationCell.component.text
					}).horizontalAlign(HorizontalAlign.FILL)
					visibleIf(importRootCheckbox.selected)
				}
			}
		}

	override fun getComponent(): JComponent = componentPanel

	@Suppress("UnstableApiUsage")
	override fun validate(): Boolean =
		if (moduleRootName.component.text.isEmpty())
		{
			false
		}
		else !(importRootCheckbox.isSelected && existingRootLocation.isEmpty())

	@Suppress("UnstableApiUsage")
	override fun updateDataModel()
	{
		data.moduleName = moduleRootName.component.text
		data.isNew = !importRootCheckbox.isSelected
		data.existingModuleLocation = existingRootLocation
		val projectBuilder = context.projectBuilder
		// TODO not sure why this check occurs?
		if (projectBuilder is AnvilModuleTypeBuilder)
		{
			projectBuilder.addModuleConfigurationUpdater(data)
		}
	}
}
/**
 * The [ModuleBuilder.ModuleConfigurationUpdater] used to capture the Anvil
 * configuration information. It creates/updates `anvil.config` on
 * [project creation][ProjectBuilder.createProject] (Finish).
 *
 * @author Richard Arriaga
 *
 * @property moduleName
 *   The name of the Avail module to create.
 */
internal data class AddModuleSetupData constructor(
	val service: AnvilProjectService,
	val anvilConfig: AnvilConfiguration,
	var moduleName: String = "",
	var isNew: Boolean = true,
	var existingModuleLocation: String = "",
): ModuleBuilder.ModuleConfigurationUpdater()
{
	override fun update(module: Module, rootModel: ModifiableRootModel)
	{
		val location =
			if (isNew)
			{
				module.
				File("${service.project.basePath!!}/$moduleName").mkdirs()
				ProjectHome(moduleName)
			}
			else
			{
				UserHome.fromAbsolute(existingModuleLocation)
			}
		anvilConfig.addRoot(
			AnvilProjectRoot(
				service,
				moduleName,
				location,
				false))
		service.saveConfigToDisk()
	}
}
