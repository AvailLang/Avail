/*
 * CreateProjectPanel.kt
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

package avail.anvil.manager

import avail.anvil.components.DirectoryChooser
import avail.anvil.components.TextFieldWithLabel
import avail.anvil.components.TextFieldWithLabelAndButton
import avail.anvil.environment.AVAIL_STDLIB_ROOT_NAME
import avail.anvil.icons.ProjectManagerIcons
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.environment.availStandardLibraries
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLibraries
import org.availlang.artifact.environment.location.AvailRepositories
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.AvailProjectV1
import org.availlang.artifact.environment.project.LocalSettings
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.json.JSONWriter
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * A [JPanel] used to create a new [AvailProject].
 *
 * @author Richard Arriaga
 *
 * @property config
 *   The [GlobalEnvironmentSettings] for this machine.
 * @property onCreate
 *   The function that accepts the newly created [AvailProject].
 * @property onCancel
 *   The function to call if creating a new project is canceled.
 */
class CreateProjectPanel constructor(
	internal val config: GlobalEnvironmentSettings,
	private val onCreate: (AvailProject, String) -> Unit,
	private val onCancel: () -> Unit
): JPanel(GridBagLayout())
{
	/**
	 * The Avail standard libraries available on this machine.
	 */
	private val standardLibraries =
		availStandardLibraries.associateBy { it.name }

	/**
	 * The Array of standard library names.
	 */
	private val standardLibraryNames: Array<String> get() =
		arrayOf("None") + standardLibraries.keys.toTypedArray()

	/**
	 * The selected Avail standard library to use or `null` if none chosen.
	 */
	private var selectedLibrary: File? = null

	/**
	 * Create the Avail project.
	 */
	fun create ()
	{
		val fileName = projectFileName.textField.text
		val projLocation = projectLocation.textField.text
		val projectFilePath = "$projLocation/$fileName.json"
		val configPath =
			AvailEnvironment.projectConfigPath(fileName, projLocation)
		AvailProject.optionallyInitializeConfigDirectory(configPath)
		val localSettings = LocalSettings.from(File(configPath))
		AvailProjectV1(
			fileName,
			true,
			AvailRepositories(rootNameInJar = null),
			localSettings
		).apply {
			File(projLocation).mkdirs()
			val rootsDir = rootsDirField.textField.text
			val rootName = rootNameField.textField.text
			val rootLocationDir =
				if (rootsDir.isNotEmpty())
				{
					"$rootsDir/$rootName"
				}
				else
				{
					rootName
				}
			File("$projLocation/$rootLocationDir").mkdirs()

			val rootConfigDir = AvailEnvironment.projectRootConfigPath(
				fileName,
				rootName,
				projLocation)
			val root = AvailProjectRoot(
				rootConfigDir,
				projLocation,
				rootName,
				ProjectHome(
					rootLocationDir,
					Scheme.FILE,
					projLocation,
					null),
					LocalSettings(rootConfigDir),
					StylingGroup(),
					TemplateGroup())
			addRoot(root)
			optionallyInitializeConfigDirectory(rootConfigDir)
			selectedLibrary?.let { lib ->
				val libName =
					libraryNameField.input.ifEmpty { AVAIL_STDLIB_ROOT_NAME }
				val libConfigDir = AvailEnvironment.projectRootConfigPath(
					fileName, libName, projLocation)
				optionallyInitializeConfigDirectory(libConfigDir)
				val jar = AvailArtifactJar(lib.toURI())

				val rootManifest = jar.manifest.roots[AVAIL_STDLIB_ROOT_NAME]
				var sg =
					if(importStyles.isSelected)
					{
						jar.manifest.stylesFor(AVAIL_STDLIB_ROOT_NAME)
							?: StylingGroup()
					}
					else
					{
						StylingGroup()
					}
				var tg =
					if(importTemplates.isSelected)
					{
						jar.manifest.templatesFor(AVAIL_STDLIB_ROOT_NAME)
							?: TemplateGroup()
					}
					else
					{
						TemplateGroup()
					}
				val extensions = mutableListOf<String>()
				var description = ""
				rootManifest?.let {
					sg = it.styles
					tg = it.templates
					extensions.addAll(it.availModuleExtensions)
					description = it.description
				}
				val stdLib = AvailProjectRoot(
					libConfigDir,
					projLocation,
					libName,
					AvailLibraries(
					"org/availlang/${lib.name}",
						Scheme.JAR,
						AVAIL_STDLIB_ROOT_NAME),
					LocalSettings(
						AvailEnvironment.projectRootConfigPath(
							fileName,
							libName,
							projLocation)),
					sg,
					tg,
					extensions)
				stdLib.description = description
				stdLib.saveLocalSettingsToDisk()
				stdLib.saveTemplatesToDisk()
				stdLib.saveStylesToDisk()
				addRoot(stdLib)
			}

			if (projectFilePath.isNotEmpty())
			{
				// Update the backing project file.
				val writer = JSONWriter.newPrettyPrinterWriter()
				writeTo(writer)
				File(projectFilePath).writeText(writer.contents())
				config.add(this, projectFilePath)
				onCreate(this, projectFilePath)
			}
		}
	}

	/**
	 * The [TextFieldWithLabel] used to set the project name.
	 */
	private val projectNameField = TextFieldWithLabel("Project Name: ")

	/**
	 * The field for configuring the [AvailProject] file.
	 */
	private val projectFileName =
		TextFieldWithLabelAndButton("Project Config File Name: ")
			.apply panel@{
				toolTipText = "The name applied to the JSON project file."
				textField.text = "avail-config"
				button.apply {
					isContentAreaFilled = false
					isBorderPainted = false
					text = ""
					icon = ProjectManagerIcons.refresh(23)
					addActionListener {
						this@panel.textField.text = "avail-config"
					}
				}
			}

	/**
	 * The [DirectoryChooser] used to pick the directory where the project is
	 * to be created.
	 */
	private val projectLocation =
		DirectoryChooser(
			"Project Directory: ","Select Project Directory" )

	/**
	 * The [TextFieldWithLabel] used to set the project name.
	 */
	private val rootNameField = TextFieldWithLabel("Create Root Name: ")

	/**
	 * The [TextFieldWithLabel] used to set the project name.
	 */
	private val rootsDirField =
		TextFieldWithLabel("Roots Directory Name (optional): ").apply {
			toolTipText =
				"Leaving blank will create roots at top level of project"
		}

	/**
	 * A checkbox to whether or not [StylingGroup] should be imported from the
	 * Avail Standard library.
	 */
	val importStyles = JCheckBox("Import Styles", false).apply {
		isEnabled = false
		toolTipText = "Imports styles packaged with standard library"
	}

	/**
	 * A checkbox to whether or not [TemplateGroup] should be imported from the
	 * Avail Standard library.
	 */
	val importTemplates = JCheckBox("Import Templates", false).apply {
		isEnabled = false
		toolTipText = "Imports templates packaged with standard library"
	}

	/**
	 * The [TextFieldWithLabel] used to set the standard library root name.
	 */
	private val libraryNameField =
		TextFieldWithLabel("Standard Library Root Name: ").apply {
			textField.text = "avail"
		}

	/**
	 * Updated the [JComboBox] used to pick a standard library to add as a root.
	 */
	private val libraryPicker = JComboBox(standardLibraryNames).apply {
		addActionListener {
			selectedItem?.toString()?.let { key ->
				standardLibraries[key]?.let { lib ->
					selectedLibrary = lib
					importTemplates.isEnabled = true
					importStyles.isEnabled = true
				} ?: run {
					selectedLibrary = null
					importTemplates.isEnabled = false
					importTemplates.isSelected = false
					importStyles.isEnabled = false
					importStyles.isSelected = false
				}
			}
		}
	}

	// TODO add form validation

	/**
	 * The button to use to show the create screen.
	 */
	private val createButton = JButton("Create").apply {
		isOpaque = true
		border = BorderFactory.createLineBorder(
			Color(0xBB, 0xBB, 0xBB), 1, true)
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
		preferredSize = Dimension(currentWidth + 100, currentHeight + 40)
		maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
		addActionListener { create() }
	}

	/**
	 * The button to use to show the create screen.
	 */
	val cancel = JButton("Cancel").apply {
		isOpaque = true
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 100, currentHeight + 40)
		preferredSize = Dimension(currentWidth + 100, currentHeight + 40)
		maximumSize = Dimension(currentWidth + 100, currentHeight + 40)
		addActionListener {
			onCancel()
		}
	}

	/**
	 * The top panel that has sorting options and can open a project.
	 */
	private val bottomPanel = JPanel().apply {
		layout = (FlowLayout(FlowLayout.RIGHT))
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(600, 50)
		maximumSize = Dimension(600, 50)
		add(cancel)
		add(createButton)
	}

	init
	{
		minimumSize = Dimension(600, 50)
		preferredSize = Dimension(600, 50)
		maximumSize = Dimension(600, 50)
		add(
			projectNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 0
				gridwidth = 2
			})
		add(projectFileName,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 1
				gridwidth = 2
			})
		add(projectLocation,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 2
				gridwidth = 2
			})
		add(rootNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 3
				gridwidth = 2
			})
		add(rootsDirField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 4
				gridwidth = 2
			})
		add(libraryNameField,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 5
				gridwidth = 1
			})
		add(libraryPicker,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 1
				gridy = 5
				gridwidth = 1
			})
		add(importStyles,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 1
				gridy = 6
				gridwidth = 1
			})
		add(importTemplates,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 1
				gridy = 7
				gridwidth = 1
			})
		add(
			bottomPanel,
			GridBagConstraints().apply {
				weightx = 1.0
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 8
				gridwidth = 2
			})
	}
}
