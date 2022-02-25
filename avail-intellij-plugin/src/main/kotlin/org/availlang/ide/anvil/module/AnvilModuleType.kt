/*
 * AvailModuleType.kt
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

package org.availlang.ide.anvil.module

import com.intellij.openapi.module.ModuleType
import org.availlang.ide.anvil.ui.AnvilIcons
import javax.swing.Icon

/**
 * `AnvilModuleType` is the [ModuleType] of an Avail IntelliJ project module.
 *
 * This is used in the process of creating a new Avail Module when:
 *  * Creating a new project; or
 *  * Adding an Avail Module to an existing project.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object AnvilModuleType: ModuleType<AnvilModuleTypeBuilder>("ANVIL_MODULE")
{
	override fun createModuleBuilder(): AnvilModuleTypeBuilder =
		AnvilModuleTypeBuilder()

	override fun getName(): String = "Avail"

	override fun getDescription(): String =
		"IntelliJ Anvil project module"

	override fun getNodeIcon(isOpened: Boolean): Icon =
		AnvilIcons.logoSmall
}
