/*
 * SettingsCategory.kt
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

package avail.anvil.settings

import avail.anvil.AvailEditor
import avail.anvil.AvailWorkbench
import avail.anvil.environment.GlobalAvailSettings

/**
 * The different settings categories that can be changed.
 *
 * @author Richard Arriaga
 */
enum class SettingsCategory
{
	/**
	 * [AvailEditor] settings.
	 */
	EDITOR
	{
		override fun update(
			settings: GlobalAvailSettings,
			workbench: AvailWorkbench)
		{
			workbench.transcript.changeFont(
				settings.font,
				settings.codePaneFontSize)
			workbench.openEditors.values.forEach { editor ->
				editor.sourcePane.changeFont(
					settings.font,
					settings.codePaneFontSize)
			}
		}
	},

	/**
	 * Expansion template settings.
	 */
	TEMPLATES
	{
		override fun update(
			settings: GlobalAvailSettings,
			workbench: AvailWorkbench)
		{
			workbench.refreshTemplates()
		}
	},

	/**
	 * Keyboard shortcut settings.
	 */
	SHORTCUTS
	{
		override fun update(
			settings: GlobalAvailSettings,
			workbench: AvailWorkbench)
		{
			workbench.openEditors.values.forEach { editor ->
				editor.refreshShortcuts()
			}
		}
	};

	/**
	 * Update the settings for the given workbench associated with this
	 * [SettingsCategory].
	 *
	 * @param settings
	 *   The [GlobalAvailSettings].
	 * @param workbench
	 *   The [AvailWorkbench] to update the settings for.
	 */
	abstract fun update (
		settings: GlobalAvailSettings,
		workbench: AvailWorkbench)
}
