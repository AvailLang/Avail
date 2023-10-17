/*
 * AvailModuleBuilder.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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
package org.availlang.intellij.plugin.module

import avail.anvil.environment.GlobalEnvironmentSettingsV1
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.module.Module
import javax.swing.JComponent

/**
 * The [ModuleWizardStep] used to set up a new Avail project.
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailModuleWizardStep].
 *
 * @param builder
 *   The [AvailModuleBuilder] used to construct the Avail [Module].
 */
class AvailModuleWizardStep(builder: AvailModuleBuilder) : ModuleWizardStep()
{

  init
  {
    // this listener is called after you choose a location and a name
    // even though we ask for the Avail module roots and config first
    builder.addListener { module ->
      AvailProjectTemplate.create(module.config)
    }
  }

  /**
   * Given a Module and the [createProjectPanel] fields, create the
   * [AvailProjectTemplate.Config] for the project to be created.
   */
  private val Module.config
    get() =
      createProjectPanel.let { panel ->
        AvailProjectTemplate.Config(
          projectLocation = moduleFilePath.substringBeforeLast("/"),
          fileName = name,
          rootsDir = panel.rootsDirField.input.ifEmpty { "roots" },
          rootName = panel.rootNameField.input.ifEmpty { name },
          importStyles = panel.importStyles.isSelected,
          importTemplates = panel.importTemplates.isSelected,
          libraryName = panel.libraryNameField.input.ifEmpty { null },
          selectedLibrary = panel.selectedLibrary,
          environmentSettings = GlobalEnvironmentSettingsV1()
        )
      }

  /**
   * Represents the panel used to create a new project.
   *
   * @property createProjectPanel
   *   The instance of the CreateAvailProjectPanel class used to display the
   *   create project form.
   */
  private val createProjectPanel: CreateAvailProjectPanel =
    CreateAvailProjectPanel()

  override fun getComponent(): JComponent =
    createProjectPanel

  override fun updateDataModel()
  {
  }
}
