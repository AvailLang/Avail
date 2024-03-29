/*
 * TemplateExpansionsManager.kt
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

package avail.anvil.settings

import avail.anvil.AvailWorkbench
import avail.anvil.environment.GlobalEnvironmentSettings
import avail.anvil.environment.globalTemplatesFile
import avail.anvil.environment.systemDefaultTemplates
import avail.anvil.icons.ProjectManagerIcons
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.TemplateExpansion
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.json.jsonObject
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.EAST
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The [JFrame] that presents the different template expansions.
 *
 * @author Richard Arriaga
 *
 * @property workbench
 *   The active [AvailWorkbench].
 */
class TemplateExpansionsManager constructor(
	val workbench: AvailWorkbench
): JFrame("Templates")
{
	/**
	 * A tab is added for each [AvailProjectRoot].
	 */
	val tabs: JTabbedPane =
		JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT)

	/**
	 * The global [TemplateGroup].
	 */
	private val globalTemplates = GlobalEnvironmentSettings.globalTemplates

	init
	{
		workbench.availProject.roots.forEach {
			 tabs.addTab(
				 it.key,
				 TemplatesPanel(workbench, it.value.templateGroup)
				 {
					 it.value.saveTemplatesToDisk()
				 }.redrawTemplates())
		}
		tabs.addTab(
			"${workbench.availProject.name} (project)",
			TemplatesPanel(workbench, workbench.availProject.templateGroup)
			{
				workbench.availProject.saveTemplatesToDisk(
					workbench.projectConfigDirectory)
			}.redrawTemplates())
		tabs.addTab(
			"Global Environment",
			TemplatesPanel(workbench, globalTemplates)
			{
				File(globalTemplatesFile).writeText(
					globalTemplates.jsonPrettyPrintedFormattedString)
			}.redrawTemplates())
		contentPane.add(tabs)
		minimumSize = Dimension(950, 800)
		preferredSize = Dimension(950, 800)
		maximumSize = Dimension(950, 800)
		pack()
		addWindowListener(object : WindowAdapter()
		{
			override fun windowClosing(e: WindowEvent)
			{
				workbench.templateExpansionManager = null
			}
		})
		setLocationRelativeTo(workbench)
		isVisible = true
		workbench.templateExpansionManager = this
	}
}

/**
 * A [JPanel] that displays all the project-level templates for an
 * [AvailProject].
 *
 * @author Richard Arriaga
 *
 * @property workbench
 *   The active [AvailWorkbench].
 */
open class TemplatesPanel constructor(
	val workbench: AvailWorkbench,
	val templateGroup: TemplateGroup,
	val saveAction: () -> Unit
) : JPanel()
{
	/**
	 * The set of [TemplateRow]s that have their checkbox checked.
	 */
	val checkedRowsKeys = mutableSetOf<String>()

	/**
	 * The panel that contains all the shortcuts.
	 */
	private val templatesPanel = JPanel().apply {
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		workbench.availProject.templateGroup.templates.forEach {
			add(
				TemplateRow(
					it.key,
					it.value,
					templateGroup,
					workbench,
					this@TemplatesPanel,
					saveAction))
		}
	}

	/**
	 * Create a new empty [TemplateRow] that has not yet been added to the
	 * [root].
	 */
	fun newEmptyTemplateRow (): TemplateRow =
		TemplateRow(
			"",
			TemplateExpansion(""),
			templateGroup,
			workbench,
			this,
			saveAction)

	/**
	 * The currently selected [TemplateRow] or `null` if no [TemplateRow] is
	 * selected.
	 */
	var selectedRow: TemplateRow = newEmptyTemplateRow()

	/**
	 * Clear the [selectedRow].
	 */
	fun clearSelectedRow ()
	{
		selectedRow = newEmptyTemplateRow()
		templateEditPanel.templateRow = selectedRow
	}

	/**
	 * The [TemplateEditPanel] at the bottom of the [TemplatesPanel].
	 */
	val templateEditPanel = TemplateEditPanel(this, selectedRow)

	/**
	 * Redraw the [templatesPanel].
	 *
	 * @param checkAll
	 *   Check all the [TemplateRow.templateKeyCheckBox] if `true`; uncheck
	 *   otherwise.
	 * @return
	 *   This [TemplatesPanel].
	 */
	fun redrawTemplates (checkAll: Boolean = false): TemplatesPanel
	{
		SwingUtilities.invokeLater {
			templatesPanel.removeAll()
			templatesPanel.revalidate()
			templateGroup.templates.toList()
				.sortedBy { it.first }
				.forEach {
					templatesPanel.add(
						TemplateRow(
							it.first,
							it.second,
							templateGroup,
							workbench,
							this,
							saveAction
						).apply {
							if (selectedRow.templateKey == templateKey)
							{
								select()
							}
							if (checkAll)
							{
								templateKeyCheckBox.isSelected = true
								checkedRowsKeys.add(templateKey)
							}
						})
				}
			templatesPanel.repaint()
		}
		return this
	}

	/**
	 * The button that deletes all the checked rows.
	 */
	val deleteSelectedTemplate = JButton("Delete Checked").apply {
		isOpaque = true
		isEnabled = false
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 140, currentHeight + 30)
		preferredSize = Dimension(currentWidth + 140, currentHeight + 30)
		maximumSize = Dimension(currentWidth + 140, currentHeight + 30)
		toolTipText = "Delete checked rows"
		addActionListener {
			SwingUtilities.invokeLater {
				val selection = JOptionPane.showConfirmDialog(
					this@TemplatesPanel,
					"Delete Checked Rows?",
					"Delete Templates",
					JOptionPane.YES_NO_OPTION)
				if (selection == 0)
				{
					templateEditPanel.templateRow =
						newEmptyTemplateRow().apply { select() }
					checkedRowsKeys.forEach {
						templateGroup.templates.remove(it)
					}
					saveAction()
					redrawTemplates()
				}
			}
		}
	}

	/**
	 * Use this [JFileChooser] to import templates from a [TemplateGroup] file.
	 * file.
	 */
	private fun JFileChooser.importSettings ()
	{
		dialogTitle = "Select Templates File to Import From"
		fileSelectionMode = JFileChooser.FILES_ONLY
		fileFilter = FileNameExtensionFilter("*.json", "json")
		addChoosableFileFilter(
			object : FileFilter()
			{
				override fun getDescription(): String =
					"Templates File (*.json)"

				override fun accept(f: File): Boolean =
					f.isFile
						&& f.canWrite()
						&& f.absolutePath.lowercase().endsWith(".json")
			})
		val result = showDialog(
			this@TemplatesPanel,
			"Select Settings File")
		if (result == JFileChooser.APPROVE_OPTION)
		{
			val tg =
				TemplateGroup(jsonObject(selectedFile.readText()))
			this@TemplatesPanel.templateGroup.templates.putAll(tg.templates)
			SwingUtilities.invokeLater {
				redrawTemplates()
				saveAction()
			}
		}
	}

	/**
	 * Use this [JFileChooser] to export templates to a [TemplateGroup] file.
	 */
	private fun JFileChooser.exportSettings ()
	{
		dialogTitle = "Select File to Export Templates To"
		fileSelectionMode = JFileChooser.FILES_ONLY
		fileFilter = FileNameExtensionFilter("*.json", "json")
		addChoosableFileFilter(
			object : FileFilter()
			{
				override fun getDescription(): String =
					"Templates File (*.json)"

				override fun accept(f: File): Boolean =
					f.isFile
						&& f.canWrite()
						&& f.absolutePath.lowercase().endsWith(".json")
			})
		val result = showSaveDialog(this@TemplatesPanel)
		if (result == JFileChooser.APPROVE_OPTION)
		{
			val target =
				if (selectedFile.name.endsWith(".json"))
				{
					selectedFile
				}
				else
				{
					File(selectedFile.absolutePath + ".json")
				}
			target.writeText(templateGroup.jsonPrettyPrintedFormattedString)
		}
	}

	init
	{
		layout = BoxLayout(this, BoxLayout.Y_AXIS)

		val checkAllTemplate = JButton("Check All").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 140, currentHeight + 30)
			preferredSize = Dimension(currentWidth + 140, currentHeight + 30)
			maximumSize = Dimension(currentWidth + 140, currentHeight + 30)
			toolTipText = "Create a new template"
			addActionListener {
				SwingUtilities.invokeLater {
					redrawTemplates(true)
					deleteSelectedTemplate.isEnabled = true
				}
			}
		}
		val uncheckAllTemplate = JButton("Uncheck All").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 140, currentHeight + 30)
			preferredSize = Dimension(currentWidth + 140, currentHeight + 30)
			maximumSize = Dimension(currentWidth + 140, currentHeight + 30)
			toolTipText = "Create a new template"
			addActionListener {
				SwingUtilities.invokeLater {
					redrawTemplates()
					deleteSelectedTemplate.isEnabled = false
				}
			}
		}

		@Suppress("LeakingThis")
		add(JPanel().apply {
			layout = (FlowLayout(FlowLayout.LEFT))
			minimumSize = Dimension(950, 50)
			preferredSize = Dimension(950, 50)
			maximumSize = Dimension(950, 50)
			background = Color(0x3C, 0x3F, 0x41)
			add(checkAllTemplate)
			add(uncheckAllTemplate)
			add(deleteSelectedTemplate)
		})
		@Suppress("LeakingThis")
		add(JScrollPane(
			templatesPanel,
			JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
			minimumSize = Dimension(950, 400)
			preferredSize = Dimension(950, 400)
			maximumSize = Dimension(950, 400)
		})

		@Suppress("LeakingThis")
		add(templateEditPanel)

		val importSettingsTemplates = JButton("Import From File").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 165, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 165, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 165, currentHeight + 40)
			toolTipText = "Import expansion templates from settings file"
			addActionListener {
				JFileChooser().importSettings()
			}
		}

//		val importProjectTemplates = JButton("Import From Project").apply {
//			isOpaque = true
//			val currentHeight = height
//			val currentWidth = width
//			minimumSize = Dimension(currentWidth + 165, currentHeight + 40)
//			preferredSize = Dimension(currentWidth + 165, currentHeight + 40)
//			maximumSize = Dimension(currentWidth + 165, currentHeight + 40)
//			toolTipText = "Import expansion templates from project file"
//			addActionListener {
//				addActionListener {
//					JFileChooser().importFromProject()
//				}
//			}
//		}

		val importDefaultTemplates = JButton("Import Defaults").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 165, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 165, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 165, currentHeight + 40)
			toolTipText = "Import system default expansion templates"
			addActionListener {
				SwingUtilities.invokeLater {
					systemDefaultTemplates?.let {
						templateGroup.templates.putAll(it.templates)
						redrawTemplates()
						workbench.saveProjectFileToDisk()
					}
				}
			}
		}

		val exportTemplates = JButton("Export").apply {
			isOpaque = true
			val currentHeight = height
			val currentWidth = width
			minimumSize = Dimension(currentWidth + 165, currentHeight + 40)
			preferredSize = Dimension(currentWidth + 165, currentHeight + 40)
			maximumSize = Dimension(currentWidth + 165, currentHeight + 40)
			toolTipText = "Export expansion templates"
			addActionListener {
				JFileChooser().exportSettings()
			}
		}

		@Suppress("LeakingThis")
		add(JPanel().apply {
			layout = (FlowLayout(FlowLayout.RIGHT))
			minimumSize = Dimension(950, 50)
			preferredSize = Dimension(950, 50)
			maximumSize = Dimension(950, 50)
			background = Color(0x3C, 0x3F, 0x41)
			add(importSettingsTemplates)
//			add(importProjectTemplates)
			add(importDefaultTemplates)
			add(exportTemplates)
		})
	}
}

/**
 * The [JPanel] that displays a template.
 *
 * @author Richard Arriaga
 *
 * @property templateKey
 *   The unique template key corresponding the target template expansion.
 * @property expansion
 *   The [TemplateExpansion].
 * @property templateGroup
 *   The [TemplateGroup] this template belongs to.
 * @property workbench
 *   The associated [AvailWorkbench]
 * @property parentPanel
 *   The [TemplatesPanel] this [TemplateRow] is in.
 */
class TemplateRow constructor(
	var templateKey: String,
	val expansion: TemplateExpansion,
	val templateGroup: TemplateGroup,
	val workbench: AvailWorkbench,
	val parentPanel: TemplatesPanel,
	val saveAction: () -> Unit
): JPanel(GridBagLayout())
{
	/**
	 * The [GridBagConstraints] used for all components in [TemplateRow].
	 */
	private val constraints = GridBagConstraints().apply {
		anchor = GridBagConstraints.WEST
	}

	/**
	 * Select this [TemplateRow].
	 */
	internal fun select ()
	{
		parentPanel.selectedRow.border =
			BorderFactory.createEmptyBorder()
		border = BorderFactory.createLineBorder(Color.BLUE)
		parentPanel.selectedRow = this
		parentPanel.templateEditPanel.templateRow = this
		requestFocus()
	}

	/**
	 * The [MouseAdapter] used to select this [TemplateRow] when clicked on.
	 */
	private val mouseSelectAdapter =
		object : MouseAdapter()
		{
			override fun mouseClicked(e: MouseEvent)
			{
				if (e.clickCount == 1
					&& e.button == MouseEvent.BUTTON1)
				{
					e.consume()
					SwingUtilities.invokeLater { select() }
				}
			}
		}

	init
	{
		border = BorderFactory.createEmptyBorder()
		minimumSize = Dimension(925, 35)
		preferredSize = Dimension(925, 35)
		maximumSize = Dimension(925, 35)
		addMouseListener(mouseSelectAdapter)
	}

	/**
	 * The [JPanel] that displays the [templateKey].
	 */
	private val templateNamePanel: JPanel = JPanel().apply {
		border = BorderFactory.createEmptyBorder()
		layout = BoxLayout(this, BoxLayout.X_AXIS)
		add(Box.createRigidArea(Dimension(10, 0)))
		addMouseListener(mouseSelectAdapter)
	}

	/**
	 * The [templateKey].
	 */
	@Suppress("unused")
	val templateKeyCheckBox: JCheckBox =
		JCheckBox("  $templateKey").apply {
			font = font.deriveFont(font.style or Font.BOLD)
			templateNamePanel.add(this)
			addMouseListener(mouseSelectAdapter)
			addItemListener {
				if (it.stateChange == 1)
				{
					parentPanel.checkedRowsKeys.add(templateKey)
					parentPanel.deleteSelectedTemplate.isEnabled = true
				}
				else
				{
					parentPanel.checkedRowsKeys.remove(templateKey)
					if (parentPanel.checkedRowsKeys.isEmpty())
					{
						parentPanel.deleteSelectedTemplate.isEnabled = false
					}
				}
			}
		}

	init
	{
		toolTipText = expansion.expansion
		add(
			templateNamePanel,
			constraints.apply {
				weightx = 1.0
			})
	}

	/**
	 * The [JPanel] that displays the edit or delete options for this template.
	 */
	private val templateEditDeletePanel: JPanel = JPanel().apply {
		border = BorderFactory.createEmptyBorder()
		layout = BoxLayout(this, BoxLayout.X_AXIS)
		add(Box.createRigidArea(Dimension(10, 0)))
	}

	/**
	 * The [ImageIcon] representing whether or not a template is exported in an
	 * Avail artifact build.
	 */
	private var favoriteIcon =
		if (expansion.markedForArtifactInclusion)
		{
			exportIconChosen
		}
		else
		{
			exportIconNotChosen
		}

	/**
	 * The button to toggle export of the template.
	 */
	@Suppress("unused")
	private val exportWithArtifact: JButton =
		JButton(favoriteIcon).apply {
			isContentAreaFilled = false
			isBorderPainted = false
			addActionListener { _ ->
				expansion.markedForArtifactInclusion =
					!expansion.markedForArtifactInclusion
				favoriteIcon =
					if (expansion.markedForArtifactInclusion)
					{
						exportIconChosen
					}
					else
					{
						exportIconNotChosen
					}
				SwingUtilities.invokeLater {
					this@TemplateRow.workbench.saveProjectFileToDisk()
					this@TemplateRow.parentPanel.redrawTemplates()
				}
			}
			templateEditDeletePanel.add(this)
		}

	/**
	 * The button to remove the template.
	 */
	@Suppress("unused")
	private val removeTemplate: JButton =
		JButton(deleteIcon).apply {
			isContentAreaFilled = false
			isBorderPainted = false
			addActionListener {
				val selection = JOptionPane.showConfirmDialog(
					this@TemplateRow,
					"Delete '$templateKey'?",
					"Delete Template",
					JOptionPane.YES_NO_OPTION)
				if (selection == 0)
				{
					templateGroup.templates.remove(templateKey)
					if (this@TemplateRow.parentPanel.selectedRow == this@TemplateRow)
					{
						this@TemplateRow.parentPanel.clearSelectedRow()
					}
					SwingUtilities.invokeLater {
						this@TemplateRow.workbench.saveProjectFileToDisk()
						this@TemplateRow.parentPanel.redrawTemplates()
					}
				}
			}
			templateEditDeletePanel.add(this)
			this@TemplateRow.add(
				templateEditDeletePanel,
				constraints.apply {
					weightx = 1.0
					anchor = EAST
				})
		}

	companion object
	{
		/**
		 * The height to use for the favorite icons.
		 */
		private const val scaledIconHeight = 20

		/**
		 * The delete icon.
		 */
		private val deleteIcon get() =
			ProjectManagerIcons.cancelFilled(scaledIconHeight)

		/**
		 * Icon indicating the template is exported in an Avail artifact.
		 */
		private val exportIconChosen =
			ProjectManagerIcons.yellowStarFilled(scaledIconHeight)

		/**
		 * Icon indicating the template is not exported in an Avail artifact.
		 */
		private val exportIconNotChosen =
			ProjectManagerIcons.yellowStarUnfilled(scaledIconHeight)
	}
}

/**
 * A [JPanel] that displays all the templates for a specific [AvailProjectRoot].
 *
 * @author Richard Arriaga
 *
 * @property parentPanel
 *   The owning [TemplatesPanel].
 *
 * @constructor
 * Construct a new [TemplateEditPanel].
 *
 * @param parentPanel
 *   The owining [TemplatesPanel].
 * @param initialTemplateRow
 *   The [TemplateRow] to initially populate this pane with.
 */
class TemplateEditPanel constructor(
	val parentPanel: TemplatesPanel,
	initialTemplateRow: TemplateRow
) : JPanel()
{
	/**
	 * Temporary holder of template name. If saved, will override current name.
	 */
	var templateKey = initialTemplateRow.templateKey

	/**
	 * Temporary holder of template expansion. If saved, will override current
	 * expansion.
	 */
	var expansion = initialTemplateRow.expansion

	/**
	 * Validate the name and expansion of the template. Considered invalid if:
	 *  1. [templateKey] is empty
	 *  2. [expansion] is empty
	 *  3. [templateKey] is a duplicate of an existing template.
	 */
	private fun validateUpdates ()
	{
		duplicates.text = " "
		applyChanges.isEnabled = false
		when
		{
			templateKey != templateRow.templateKey ->
			{
				when
				{
					templateRow.templateGroup.templates.containsKey(templateKey) ->
					{
						duplicates.text = "Template $templateKey already exists"
					}
					else ->
					{
						applyChanges.isEnabled = true
					}
				}
			}
			expansion != templateRow.expansion ->
			{
				applyChanges.isEnabled = true
			}
			expansion.markedForArtifactInclusion !=
				templateRow.expansion.markedForArtifactInclusion ->
			{
				applyChanges.isEnabled = true
			}
		}
	}

	/** The template name. */
	private val templateNameTextField = JTextField(35).apply {
		text = templateKey
		document.addDocumentListener(
			object: DocumentListener
			{
				override fun insertUpdate(e: DocumentEvent?)
				{
					templateKey = text
					validateUpdates()
				}

				override fun removeUpdate(e: DocumentEvent?)
				{
					templateKey = text
					validateUpdates()
				}

				override fun changedUpdate(e: DocumentEvent?)
				{
					templateKey = text
					validateUpdates()
				}
			})
	}

	/**
	 * The [JTextArea] used to enter the template expansion text.
	 */
	private val expansionField = JTextArea(10, 80).apply {
		text = expansion.expansion
		document.addDocumentListener(
			object: DocumentListener
			{
				override fun insertUpdate(e: DocumentEvent?)
				{
					expansion.expansion = text
					validateUpdates()
				}

				override fun removeUpdate(e: DocumentEvent?)
				{
					expansion.expansion = text
					validateUpdates()
				}

				override fun changedUpdate(e: DocumentEvent?)
				{
					expansion.expansion = text
					validateUpdates()
				}
			})
	}

	/**
	 * The [TemplateRow] being edited/created
	 */
	var templateRow: TemplateRow = initialTemplateRow
		set(value)
		{
			field = value
			templateKey = value.templateKey
			expansion = value.expansion
			SwingUtilities.invokeLater {
				templateNameTextField.text = templateKey
				expansionField.text = expansion.expansion
			}
		}

	/**
	 * The label that indicates if the new [templateKey] already exists.
	 */
	val duplicates = JLabel(" ").apply {
		foreground = Color.RED
		font = font.deriveFont(font.style or Font.ITALIC)
	}

	/**
	 * The button that applies the changes to the selected [TemplateRow] or adds
	 * a new template if creating a new one.
	 */
	private val applyChanges = JButton("Apply Changes").apply {
		isEnabled = false
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 125, currentHeight + 30)
		preferredSize = Dimension(currentWidth + 125, currentHeight + 30)
		maximumSize = Dimension(currentWidth + 125, currentHeight + 30)
		toolTipText = "Apply Changes"
		addActionListener {
			SwingUtilities.invokeLater {
				templateRow.templateGroup.templates[templateKey] =
					expansion
				templateRow.templateKey = templateKey
				templateRow.saveAction()
				templateRow.parentPanel.redrawTemplates()
				templateRow.workbench.refreshTemplates()
			}
		}
	}

	/**
	 * The button that resets this view to create new template expansion.
	 */
	private val newTemplate = JButton("New").apply {
		isOpaque = true
		val currentHeight = height
		val currentWidth = width
		minimumSize = Dimension(currentWidth + 125, currentHeight + 30)
		preferredSize = Dimension(currentWidth + 125, currentHeight + 30)
		maximumSize = Dimension(currentWidth + 125, currentHeight + 30)
		toolTipText = "Create a new template"
		addActionListener {
			SwingUtilities.invokeLater {
				templateRow =
					this@TemplateEditPanel.parentPanel
						.newEmptyTemplateRow().apply { select() }
			}
		}
	}

	/**
	 * The [templateKey].
	 */
	@Suppress("unused")
	private val templateKeyCheckBox: JCheckBox =
		JCheckBox("Include in Avail artifact build").apply {
			font = font.deriveFont(font.style or Font.BOLD)
			toolTipText = "Checking this will cause this template to be " +
				"included in the Avail manifest for the is Avail root when the " +
				"root is packaged in an Avail artifact."
			isSelected = expansion.markedForArtifactInclusion
			addItemListener {
				expansion.markedForArtifactInclusion = it.stateChange == 1
			}
		}

	init
	{
		border = BorderFactory.createLineBorder(Color(0x616365))
		layout = BoxLayout(this, BoxLayout.Y_AXIS)
		val namePanel = JPanel().apply {
			border = BorderFactory.createEmptyBorder(15, 20, 0, 10)
			layout = BoxLayout(this, BoxLayout.X_AXIS)
		}
		JLabel("Template Key:  ").apply {
			font = font.deriveFont(font.style or Font.BOLD)
			namePanel.add(this)
		}
		templateNameTextField.apply {
			namePanel.add(this)
			requestFocusInWindow()
		}
		namePanel.add(Box.createRigidArea(Dimension(20, 25)))
		namePanel.add(applyChanges)
		namePanel.add(Box.createRigidArea(Dimension(10, 25)))
		namePanel.add(newTemplate)
		namePanel.add(Box.createRigidArea(Dimension(10, 25)))
		namePanel.add(templateKeyCheckBox)
		namePanel.add(Box.createRigidArea(Dimension(200 - newTemplate.width, 25)))
		add(namePanel)
		add(JPanel().apply {
			layout = (FlowLayout(FlowLayout.LEFT))
			minimumSize = Dimension(920, 20)
			preferredSize = Dimension(920, 20)
			maximumSize = Dimension(920, 20)
			add(duplicates)
		})
		add(JPanel().apply {
			layout = (FlowLayout(FlowLayout.LEFT))
			minimumSize = Dimension(920, 25)
			preferredSize = Dimension(920, 25)
			maximumSize = Dimension(920, 25)
			add(JLabel("Expansion").apply {
				font = font.deriveFont(font.style or Font.BOLD)
			})
		})
		add(JPanel().apply {
			layout = BoxLayout(this, BoxLayout.X_AXIS)
			BorderFactory.createEmptyBorder(15, 30, 15, 30)
			add(Box.createRigidArea(Dimension(15, 100)))
			add(JScrollPane(expansionField))
			add(Box.createRigidArea(Dimension(15, 100)))
		})

		add(Box.createRigidArea(Dimension(920, 20)))
	}
}
