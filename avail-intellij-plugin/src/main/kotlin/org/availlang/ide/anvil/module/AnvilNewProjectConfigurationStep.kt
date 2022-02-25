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
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.selected
import javax.swing.JComponent
import com.intellij.ui.layout.selectedValueIs
import org.availlang.ide.anvil.Anvil
import org.availlang.ide.anvil.models.ProjectHome
import org.availlang.ide.anvil.models.UserHome
import org.availlang.ide.anvil.models.project.AnvilConfigData
import org.availlang.ide.anvil.models.project.AnvilConfiguration
import org.availlang.ide.anvil.ui.components.localTextFieldWithBrowseButton
import org.availlang.ide.anvil.utilities.Defaults
import org.availlang.json.jsonPrettyPrintWriter
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * The [ModuleWizardStep] for configuring a new Anvil project module.
 *
 * This can be accessed from the IntelliJ start up window (with no project open)
 * to create a brand new Anvil project. It can also be accessed from the menu:
 *
 * File -> New -> Project...
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property context
 *   The [WizardContext].
 */
class AnvilNewProjectConfigurationStep constructor(
	private val context: WizardContext
): ModuleWizardStep()
{
	/**
	 * The [NewProjectSetupData] used to preserve data set in this
	 * [AnvilNewProjectConfigurationStep] between step screens. It is also
	 * ultimately responsible for setting up the project.
	 */
	private val data = NewProjectSetupData()

	init
	{
		Anvil.initializeAvailHome()
		Anvil.identifyAvailableLibraries()
		Anvil.conditionallyAddStandardLib()
	}

	/**
	 * The field used to name the Avail module root to create.
	 */
	@Suppress("UnstableApiUsage")
	private lateinit var moduleRootName: Cell<JBTextField>

	/**
	 * The [RepoLocationType] of where the project repositories should be
	 * placed.
	 */
	private var repoLocationType = RepoLocationType.DEFAULT

	/**
	 * The path to the repository location.
	 */
	private var repoLocation = ""

	/**
	 * The chosen Avail standard library version if [selected][stdLibCheckbox].
	 */
	private var standardLibVersion = ""

	/**
	 * [JBCheckBox] when checked indicates choose an available Avail standard
	 * library from [Anvil.AVAIL_HOME]. Unchecked indicates no standard library
	 * will be used.
	 */
	private lateinit var stdLibCheckbox: JBCheckBox

	/**
	 * Represents the different [RepoLocationType]s the project can be set to.
	 *
	 * @property requiresDirPath
	 *   `true` indicates the user must provide a custom path location; `false`
	 *   otherwise.
	 */
	enum class RepoLocationType constructor(
		val requiresDirPath: Boolean = false
	)
	{
		/**
		 * The [Anvil.availRepos] standard repository location in the user's
		 * home directory.
		 */
		DEFAULT,

		/**
		 * Store the [Anvil.availRepos] in the a directory that is inside the
		 * project directory.
		 */
		PROJECT,

		/**
		 * Store the repository in a user-home relative directory of the user's
		 * choosing.
		 */
		CUSTOM(true);
	}

	/**
	 * The array of available Avail Standard Libraries in [Anvil.AVAIL_HOME].
	 */
	private val availableVersions = Anvil.stdLibVersionsAvailable

	/**
	 * This is the [JComponent] that is added to the
	 * [WizardContext.myProjectBuilder] screen.
	 */
	@Suppress("UnstableApiUsage", "DialogTitleCapitalization")
	private val componentPanel: JComponent =
		panel {
			// Primary Avail Module Root
			row("Module Root Name") {
				comment("required")
				contextHelp(
					"",
					"The name of the Avail module root to create.")
				moduleRootName = textField()
					.horizontalAlign(HorizontalAlign.FILL)
					.bindText(
						{ data.moduleName },
						{ data.moduleName = it })
			}

			// Repository Setup
			group("Repository")
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
						repoCell = cell(localTextFieldWithBrowseButton(42) {
							repoLocation = repoCell.component.text
							repoLocationType = RepoLocationType.CUSTOM
						})
						visibleIf(combo.selectedValueIs(selections[2]))
					}
				}
			}

			group("Avail Standard Library")
			{
				lateinit var libCombo: ComboBox<String>
				row {
					stdLibCheckbox =
						JBCheckBox("Use Avail Standard Library")
					cell(stdLibCheckbox)
						.bindSelected(
							{ data.useSDK },
							{ data.useSDK = it })
				}
				indent {
					row {
						libCombo = ComboBox(availableVersions, 300)
						label("Choose Avail Standard Library Version")
						cell(libCombo)
						visibleIf(stdLibCheckbox.selected)
						libCombo.addActionListener {
							standardLibVersion = libCombo.item
						}
					}
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
		else !(repoLocationType.requiresDirPath && repoLocation.isEmpty())

	@Suppress("UnstableApiUsage")
	override fun updateDataModel()
	{
		data.moduleName = moduleRootName.component.text
		data.useSDK = stdLibCheckbox.isSelected
		data.standardLibVersion = standardLibVersion
		data.repoLocationType = repoLocationType
		data.repoLocation = repoLocation
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
internal data class NewProjectSetupData constructor(
	var moduleName: String = "",
	var useSDK: Boolean = false,
	var standardLibVersion: String = "",
	var repoLocation: String = "",
	var repoLocationType: AnvilNewProjectConfigurationStep.RepoLocationType =
		AnvilNewProjectConfigurationStep.RepoLocationType.DEFAULT
): ModuleBuilder.ModuleConfigurationUpdater()
{
	override fun update(module: Module, rootModel: ModifiableRootModel)
	{
		// TODO this gets called after project window "finish" clicked.
		// TODO create .idea/anvil.config here
		val projectPath = rootModel.project.basePath
		val ideaFolder = "$projectPath/.idea"
		File(ideaFolder).mkdirs()
		val configFile =
			"$projectPath/${AnvilConfiguration.configFileLocation}"
		val repo =
			when (repoLocationType)
			{
				AnvilNewProjectConfigurationStep.RepoLocationType.DEFAULT ->
					Defaults.instance.defaultRepositoryPath
				AnvilNewProjectConfigurationStep.RepoLocationType.PROJECT ->
				{
					// Create the project local repositories
					File("$projectPath/${Anvil.AVAIL_REPOS}").mkdirs()
					ProjectHome(Anvil.AVAIL_REPOS)
				}
				AnvilNewProjectConfigurationStep.RepoLocationType.CUSTOM ->
					UserHome.fromAbsolute(repoLocation)
			}
		val configuration = AnvilConfigData(
			module.name, // The project name
			moduleName,
			repo)
		val writer =
			jsonPrettyPrintWriter {
				writeObject { configuration.writeTo(this) }
			}
		try
		{
			val descriptorFile = File(configFile)
			Files.newBufferedWriter(descriptorFile.toPath()).use { bw ->
				bw.write(writer.toString())
			}
			File("$projectPath/$moduleName").mkdirs()
		}
		catch (e: Throwable)
		{
			throw IOException(
				"Save Anvil config to file failed: $configFile",
				e)
		}
	}
}
