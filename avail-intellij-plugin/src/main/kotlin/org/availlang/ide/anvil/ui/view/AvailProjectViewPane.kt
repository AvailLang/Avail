/*
 * AvailProjectViewPane.kt
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

package org.availlang.ide.anvil.ui.view

import com.intellij.ide.SelectInTarget
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import org.availlang.ide.anvil.language.AnvilIcons
import java.util.jar.JarFile
import javax.swing.Icon
import javax.swing.JComponent

/**
 * A `AvailProjectViewPane` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailProjectViewPane constructor (
	project: Project
): AbstractProjectViewPane (project)
{
	override fun getTitle(): String = "Avail"

	override fun getIcon(): Icon = AnvilIcons.logoSmall

	override fun getId(): String = "Avail Project View"

	override fun createComponent(): JComponent
	{
		JarFile("").manifest
		TODO("Not yet implemented")
	}

	override fun updateFromRoot(restoreExpandedPaths: Boolean): ActionCallback
	{
		TODO("Not yet implemented")
	}

	override fun select(
		element: Any?,
		file: VirtualFile?,
		requestFocus: Boolean)
	{
		TODO("Not yet implemented")
	}

	override fun getWeight(): Int
	{
		TODO("Not yet implemented")
	}

	override fun createSelectInTarget(): SelectInTarget
	{
		TODO("Not yet implemented")
	}
}
