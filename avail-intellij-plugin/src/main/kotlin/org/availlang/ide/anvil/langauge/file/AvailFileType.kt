package org.availlang.ide.anvil.langauge.file

import com.intellij.openapi.fileTypes.LanguageFileType
import org.availlang.ide.anvil.langauge.AvailIcons
import org.availlang.ide.anvil.langauge.AvailLanguage
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

	override fun getIcon(): Icon = AvailIcons.moduleFileImage
}
