/*
 * Locations.kt
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

package avail.anvil.environment

import avail.anvil.environment.GlobalAvailSettings.Companion.CONFIG_FILE_NAME
import avail.anvil.manager.AvailProjectManager
import avail.anvil.settings.ShortcutSettings
import avail.anvil.settings.TemplateSettings
import avail.anvil.window.LayoutConfiguration
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.AvailEnvironment.availHome
import org.availlang.artifact.environment.AvailEnvironment.availHomeLibs
import java.io.File

// Herein lies constants that represent file locations

/**
 * The file path to the directory where the Avail Standard Libraries are stored.
 */
val stdLibHome = "$availHomeLibs/org/availlang"

/**
 * The file path to the directory where environment settings files are stored.
 */
val envSettingsHome = "$availHome/settings"

/**
 * The [GlobalAvailSettings] environment file location.
 */
val environmentConfigFile = "$envSettingsHome/$CONFIG_FILE_NAME"

/**
 * The file where the global expansion templates file is stored.
 */
val globalTemplatesFile = "$envSettingsHome/global-templates.json"

/**
 * The text file that stores the [LayoutConfiguration] information for the
 * [AvailProjectManager].
 */
val projectManagerLayoutFile = "$envSettingsHome/pm.layout"

/**
 * The file where the [ShortcutSettings] file is stored.
 */
val keyBindingsOverrideFile = "$envSettingsHome/shortcut-overrides.json"

/**
 * Set up the Avail environment on this computer.
 */
fun setupEnvironment ()
{
	AvailEnvironment.optionallyCreateAvailUserHome()
	File(stdLibHome).apply { if (!exists()) mkdirs() }
	File(envSettingsHome).apply { if (!exists()) mkdirs() }
	File(keyBindingsOverrideFile).apply {
		if (!exists())
		{
			ShortcutSettings(mutableMapOf()).saveToDisk(this)
		}
	}
	File(globalTemplatesFile).apply {
		if (!exists())
		{
			TemplateSettings(mutableMapOf()).saveToDisk(this)
		}
	}
	File(projectManagerLayoutFile).apply {
		if (!exists())
		{
			this.writeText("")
		}
	}
	File(environmentConfigFile).apply {
		if (!exists())
		{
			this.writeText(GlobalAvailSettings.emptyConfig.fileContent)
		}
	}
}

