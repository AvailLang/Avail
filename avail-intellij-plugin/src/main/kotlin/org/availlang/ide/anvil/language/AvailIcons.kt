/*
 * AvailIcons.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package org.availlang.ide.anvil.language

//import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader

/**
 * A {@code AvailIcons} is TODO: Document this!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AvailIcons
{
	/**
	 * The image file used to represent an Avail Module file.
	 */
	val moduleFileImage =
		IconLoader.getIcon("/icons/ModuleInTree.png", AvailIcons.javaClass)

	/**
	 * The image file of a 16x16 Avail logo.
	 */
	val logoSmall =
		IconLoader.getIcon("/icons/AvailLogo-small", AvailIcons.javaClass)

	/**
	 * The image file used to represent an Avail Module file.
	 */
	val projectModuleFileImage =
		IconLoader.getIcon("/icons/AvailHammer.svg", AvailIcons.javaClass)

	/**
	 * The image file used to represent an Avail Module file.
	 */
	val error16 =
		IconLoader.getIcon("/icons/error_red_16dp.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail module file.
	 */
	val availFile =
		IconLoader.getIcon("/icons/avail-file.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail module file.
	 */
	val availFileDirty =
		IconLoader.getIcon("/icons/avail-file-dirty.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail repo file.
	 */
	val repoFile =
		IconLoader.getIcon("/icons/repo-file-v2.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail library.
	 */
	val availLibrary =
		IconLoader.getIcon("/icons/avail-library.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail repo file.
	 */
	val availLogoHammerSmall =
		IconLoader.getIcon("/icons/AvailHammer-16px.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail atom.
	 */
	val atom =
		IconLoader.getIcon("/icons/atom.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail method.
	 */
	val method =
		IconLoader.getIcon("/icons/method.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail forward method.
	 */
	val forwardMethod =
		IconLoader.getIcon("/icons/forward-method.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail forward method.
	 */
	val abstractMethod =
		IconLoader.getIcon("/icons/abstract-method.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail macro.
	 */
	val macro =
		IconLoader.getIcon("/icons/macro.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail lexer.
	 */
	val lexer =
		IconLoader.getIcon("/icons/lexer.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail semantic restriction.
	 */
	val semanticRestriction =
		IconLoader.getIcon("/icons/semantic-restriction.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail variable.
	 */
	val variable =
		IconLoader.getIcon("/icons/variable.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail constant.
	 */
	val constant =
		IconLoader.getIcon("/icons/constant.svg", AvailIcons.javaClass)

	/**
	 * The image used to represent an Avail grammatical restriction.
	 */
	val grammaticalRestriction =
		IconLoader.getIcon("/icons/grammatical-restriction.svg", AvailIcons.javaClass)
}
