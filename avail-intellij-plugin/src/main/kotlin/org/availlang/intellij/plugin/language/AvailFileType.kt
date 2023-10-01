package org.availlang.intellij.plugin.language

import com.intellij.openapi.fileTypes.LanguageFileType
import org.availlang.intellij.plugin.icons.AvailIcons
import javax.swing.Icon

/**
 * The [LanguageFileType] that represents an Avail module.
 *
 * @author Richard Arriaga
 */
object AvailFileType: LanguageFileType(AvailLanguage)
{
	override fun getName(): String = AvailLanguage.displayName

	override fun getDescription(): String =
		"The Avail programming language"

	// TODO this will need to be dynamic
	override fun getDefaultExtension(): String = "avail"

	override fun getIcon(): Icon = AvailIcons.AvailModule32x32
}
