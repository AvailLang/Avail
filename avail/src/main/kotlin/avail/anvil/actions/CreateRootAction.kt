/*
 * CreateRootAction.kt
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

package avail.anvil.actions

import avail.anvil.AvailWorkbench
import avail.anvil.scroll
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLocation.LocationType
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.LocalSettings
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.artifact.jar.AvailArtifactJar
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileFilter

/**
 * Add a root to the current project configuration.
 *
 * @constructor
 * Construct a new [CreateRootAction].
 *
 * @param workbench
 *   The owning [AvailWorkbench].
 */
class CreateRootAction
constructor (
	workbench: AvailWorkbench,
) : AbstractWorkbenchAction(
	workbench,
	"Create root…")
{
	override fun updateIsEnabled(busy: Boolean)
	{
		isEnabled = !busy
	}

	override fun actionPerformed(event: ActionEvent)
	{
		val editor = AvailRootEditor(workbench)
		editor.locationType = LocationType.project
		// Choose the current selected root's path, if any,
		// otherwise the working directory.
		val initialPath =
			workbench.selectedModuleRoot()?.resolver?.uri?.path ?:
			System.getProperty("user.dir")
		editor.chooser.currentDirectory = File(initialPath)
		editor.isVisible = true
		// Continues here only when dialog is ready to close.
		editor.dispose()
		if (!editor.approved) return
		val locationType = editor.locationType ?: return
		val relativePath = editor.relativePath()
		val schema = when (relativePath.substringAfterLast('.', ""))
		{
			"jar" -> Scheme.JAR
			else -> Scheme.FILE
		}
		val rootNameInJar = editor.rootNameInJar.selectedValue
		val location = locationType.location(
			workbench.projectHomeDirectory,
			editor.relativePath(),
			schema,
			rootNameInJar)
		val rootName = editor.nameField.text
		val rootConfigPath = AvailEnvironment.projectRootConfigPath(
			workbench.projectName,
			rootName,
			workbench.projectHomeDirectory)
		workbench.availProject
			.optionallyInitializeConfigDirectory(rootConfigPath)
		var sg = StylingGroup()
		var tg = TemplateGroup()
		val extensions = mutableListOf<String>()
		var description = ""
		if (schema == Scheme.JAR && rootNameInJar.isNotBlank())
		{
			val jar = AvailArtifactJar(editor.chooser.selectedFile.toURI())
			jar.manifest.roots[rootNameInJar]?.let {
				sg = it.styles
				tg = it.templates
				extensions.addAll(it.availModuleExtensions)
				description = it.description
			}
		}
		val newProjectRoot = AvailProjectRoot(
			rootConfigPath,
			workbench.projectHomeDirectory,
			rootName,
			location,
			LocalSettings(rootConfigPath),
			sg,
			tg,
			extensions,
			editable = editor.editable.isSelected,
			visible = editor.visible.isSelected)
		newProjectRoot.description = description
		newProjectRoot.saveLocalSettingsToDisk()
		newProjectRoot.saveTemplatesToDisk()
		newProjectRoot.saveStylesToDisk()
		val project = workbench.availProject
		project.addRoot(newProjectRoot)
		// Update the runtime as well.

		val moduleRoots = workbench.runtime.moduleRoots()
		moduleRoots.addRoot(
			newProjectRoot.name,
			location.fullPath
		) { allFailures ->
			SwingUtilities.invokeLater {
				// Alert the user about the tracing failures.
				if (allFailures.isNotEmpty())
				{
					System.err.println(allFailures.joinToString("\n"))
				}
				workbench.saveProjectFileToDisk()
				workbench.refreshAction.runAction()
			}
		}
	}

	init
	{
		putValue(
			Action.SHORT_DESCRIPTION,
			"Add a module root to the project.")
	}
}

class AvailRootEditor
constructor(
	val workbench: AvailWorkbench,
	initialLocationType: LocationType = LocationType.project
): JDialog(workbench, /*modal=*/ true)
{
	var approved = false

	private val projectFilePath = workbench.availProjectFilePath.ifEmpty {
		System.getProperty("user.dir")
	}

	/**
	 * Produce a [List] of [String]s, naming the Avail roots within the
	 * specified jar file.  If none (or not a jar, or any other problem), answer
	 * an empty list.
	 */
	private fun extractRootsFromJar(file: File): List<String>
	{
		if (!file.isFile) return emptyList()
		if (file.extension != "jar") return emptyList()
		val jar = AvailArtifactJar(file.toURI())
		return try
		{
			val manifest = jar.manifest
			manifest.roots.keys.sorted().toList()
		}
		finally
		{
			jar.close()
		}
	}

	/** The JFileChooser being augmented. */
	val chooser = JFileChooser(projectFilePath).apply {
		dialogType = JFileChooser.CUSTOM_DIALOG
		approveButtonText = "Create root"
		addActionListener {
			// Honor the accept/cancel buttons of the chooser.
			if (it.actionCommand == JFileChooser.APPROVE_SELECTION)
			{
				val newRootName = nameField.text
				if (newRootName.isEmpty()
					|| '/' in newRootName
					|| '\\' in newRootName
					|| '#' in newRootName)
				{
					JOptionPane.showMessageDialog(
						this@AvailRootEditor,
						"Malformed root name.  It must be non-empty, and not" +
							"contain '/', '\\', or '#'.",
						"Malformed root name",
						JOptionPane.ERROR_MESSAGE)
					return@addActionListener
				}
				if (workbench.availProject.roots.values
						.any { root -> root.name == newRootName})
				{
					JOptionPane.showMessageDialog(
						this@AvailRootEditor,
						"There is already a root with the name " +
							"\"$newRootName\".",
						"Root name collides",
						JOptionPane.ERROR_MESSAGE)
					return@addActionListener
				}
				// Check that the selected directory or jar exists, and if it's
				// a jar, check that the rootNameInJar is present.
				val file = selectedFile
				if (!file.exists())
				{
					JOptionPane.showMessageDialog(
						this@AvailRootEditor,
						"That file or directory does not exist.",
						"No such file or directory",
						JOptionPane.ERROR_MESSAGE)
					return@addActionListener
				}
				if (file.extension == "jar")
				{
					// Check that a rootNameInJar is selected.  It was populated
					// from the jar, so no need to check that the root exists.
					rootNameInJar.selectedValue ?: run {
						JOptionPane.showMessageDialog(
							this@AvailRootEditor,
							"You must select a root within this jar file.",
							"No root selected",
							JOptionPane.ERROR_MESSAGE)
						return@addActionListener
					}
				}
				approved = true
			}
			this@AvailRootEditor.isVisible = false
		}
		addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY) {
			val file = selectedFile
			val newList = file?.let(::extractRootsFromJar) ?: emptyList()
			val rootsListModel = rootNameInJar.model as DefaultListModel
			val oldList = rootsListModel.toArray().toList()
			if (newList != oldList)
			{
				var rootToSelect = rootNameInJar.selectedValue
				if (rootToSelect !in newList) rootToSelect = null
				if (newList.size == 1) rootToSelect = newList[0]
				rootsListModel.clear()
				rootsListModel.addAll(newList)
				if (newList.size == 1)
				{
					rootNameInJar.setSelectedValue(rootToSelect, true)
				}
			}
		}

		fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
		val filter = object : FileFilter()
		{
			override fun getDescription() =
				"Avail library (*.jar) or directory"

			override fun accept(f: File): Boolean =
				f.isDirectory ||
					(f.isFile && f.extension.lowercase() == "jar")
		}
		addChoosableFileFilter(filter)
		fileFilter = filter
	}

	/** The [ButtonGroup] that coordinates the radio button selections. */
	private val buttonGroup = ButtonGroup().apply {
		listOf(
			LocationType.availLibraries to "Avail Libraries",
			LocationType.project to "Project Relative",
			LocationType.absolute to "Absolute Path"
		).forEach { (type, label) ->
			val button = JRadioButton(label).apply {
				model.actionCommand = type.name
				isSelected = initialLocationType == type
				addActionListener { e ->
					assert(e.actionCommand == type.name)
					changedLocationType()
				}
			}
			add(button)
		}
	}

	/** A JPanel containing the radio buttons. */
	private val buttons = Box(BoxLayout.Y_AXIS).apply {
		val buttonsList = buttonGroup.elements.toList()
		buttonsList.forEach { button ->
			button.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
			add(button)
		}
		border = BorderFactory.createEtchedBorder()
	}

	/** The entry field for the root name. */
	val nameField = JTextField("", 10)

	/** A checkbox to indicate the root is editable. */
	val editable = JCheckBox("Editable", true)

	/** A checkbox to indicate the root is visible. */
	val visible = JCheckBox("Visible", true)

	/**
	 * The [JList] showing root names within the current selected jar file.
	 * Cleared if a jar file is not selected, and populated if one is selected,
	 * choosing the sole root name if there's only one.
	 */
	val rootNameInJar = JList<String>(DefaultListModel())

	init
	{
		title = "Select an Avail module root directory or library jar"
		val extras = JPanel().apply {
			layout = GridBagLayout()
			GridBagConstraints().apply {
				anchor = GridBagConstraints.FIRST_LINE_START
				fill = GridBagConstraints.HORIZONTAL
				gridx = 0
				gridy = 0
				insets = Insets(5, 5, 5, 5)
				gridwidth = 2
				add(buttons, this)
				gridy++
				// Put the label to the left of the name.
				gridwidth = 1
				fill = GridBagConstraints.BASELINE_LEADING
				add(JLabel("Root:"), this)
				gridx++
				fill = GridBagConstraints.NONE
				nameField.minimumSize = nameField.preferredSize
				add(nameField, this)
				gridx = 0
				gridy++
				// Continue with the checkboxes
				gridwidth = 2
				fill = GridBagConstraints.HORIZONTAL
				add(editable, this)
				gridy++
				add(visible, this)
				// All the slack space goes at the bottom.
				fill = GridBagConstraints.BOTH
				weighty = 1.0
				// Put the gap here.
				add(JPanel(), this)
				// Don't allow the list to stretch vertically.
				weighty = 0.0
				gridy++
				gridwidth = 1
				fill = GridBagConstraints.BASELINE_LEADING
				add(JLabel("Roots in jar:"), this)
				gridx++
				fill = GridBagConstraints.BOTH
				rootNameInJar.visibleRowCount = 3
				rootNameInJar.prototypeCellValue = "long root name"
				rootNameInJar.scroll().let { scroll ->
					scroll.minimumSize = scroll.preferredSize
					add(scroll, this)
				}
				pack()
			}
		}

		layout = GridBagLayout()
		GridBagConstraints().apply {
			//anchor = GridBagConstraints.FIRST_LINE_START
			fill = GridBagConstraints.VERTICAL
			gridx = 0
			gridy = 0
			weighty = 1.0
			add(extras, this)
			gridx++
			weightx = 1.0
			fill = GridBagConstraints.BOTH
			add(chooser, this)
		}

		minimumSize = Dimension(450, 275)
		pack()
	}

	private fun changedLocationType()
	{
		val dir = when (locationType)
		{
			LocationType.availLibraries -> AvailEnvironment.availHomeLibs
			LocationType.project -> workbench.projectHomeDirectory
			else -> return  // No change.
		}
		chooser.currentDirectory = File(dir)
	}

	/** Expose the current [LocationType] as a property. */
	var locationType: LocationType?
		get() = buttonGroup.selection?.let {
			LocationType.valueOf(it.actionCommand)
		}
		set(value) {
			buttonGroup.elements.asSequence()
				.firstOrNull { it.actionCommand == value?.name }
				?.isSelected = true
		}

	/**
	 * Answer the chosen file or directory's path, relative to the
	 * [locationType]'s base.
	 */
	fun relativePath(): String
	{
		val fullPath = chooser.selectedFile
		val basePath = when (locationType)
		{
			LocationType.availLibraries -> AvailEnvironment.availHomeLibs
			LocationType.project -> workbench.projectHomeDirectory
			LocationType.absolute -> return fullPath.absolutePath
			else -> return fullPath.absolutePath  // Sure, why not.
		}
		return fullPath.relativeTo(File(basePath)).toString()
	}
}
