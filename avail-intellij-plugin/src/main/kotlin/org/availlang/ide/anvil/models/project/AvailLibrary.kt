/*
 * AvailLibrary.kt
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

package org.availlang.ide.anvil.models.project

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import org.availlang.ide.anvil.ui.AnvilIcons
import javax.swing.Icon

/**
 * A `AvailLibrary` is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailLibrary constructor(
	val root: AnvilProjectRoot,
	private val sourceRoots: Set<VirtualFile>,
	private val excludedRoots: Set<VirtualFile>,
	private val icon: Icon
)
: SyntheticLibrary(), ItemPresentation
{
	override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots

	override fun getExcludedRoots(): Set<VirtualFile> = excludedRoots

	override fun getPresentableText(): String = root.name

	override fun getIcon(unused: Boolean): Icon = AnvilIcons.logoSmall

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is AvailLibrary) return false
		if (!super.equals(other)) return false

		if (root != other.root) return false

		return true
	}

	override fun hashCode(): Int
	{
		var result = super.hashCode()
		result = 31 * result + root.hashCode()
		return result
	}
}
