package org.availlang.ide.anvil.language.file

import com.intellij.openapi.fileTypes.LanguageFileType
import org.availlang.ide.anvil.language.AnvilIcons
import org.availlang.ide.anvil.language.AvailLanguage
import javax.swing.Icon

/**
 * `AvailFileType` is the [LanguageFileType] for the [AvailLanguage].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailFileType: LanguageFileType(AvailLanguage)
{
	// Must match <fileType name="..."> in plugin.xml
	override fun getName(): String = "Avail File"

	override fun getDescription(): String = "Avail language file"

	override fun getDefaultExtension(): String = "avail"

	override fun getIcon(): Icon =
		AnvilIcons.availFile
}
