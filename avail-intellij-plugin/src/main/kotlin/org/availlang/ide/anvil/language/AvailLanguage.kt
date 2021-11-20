package org.availlang.ide.anvil.language

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import org.availlang.ide.anvil.language.file.AvailFileType

/**
 * The Avail [Language].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailLanguage: Language("Avail")
{
	override fun getAssociatedFileType(): LanguageFileType = AvailFileType

	override fun isCaseSensitive(): Boolean = true

	override fun getDisplayName() = "Avail"
}
