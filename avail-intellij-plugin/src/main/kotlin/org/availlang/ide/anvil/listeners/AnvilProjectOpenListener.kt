/*
 * AvailProjectOpenListener.kt
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

package org.availlang.ide.anvil.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.availlang.ide.anvil.models.project.AnvilConfiguration
import org.availlang.ide.anvil.models.project.AnvilProjectService
import org.availlang.ide.anvil.models.project.anvilProjectService
import java.io.File

/**
 * `AvailProjectOpenListener` is a [ProjectManagerListener] that starts up an
 * [AnvilProjectService] if the opened project is an Avail project.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AnvilProjectOpenListener: ProjectManagerListener
{
	override fun projectOpened(project: Project)
	{
		super.projectOpened(project)
		project.basePath?.let {
			val descriptorFile =
				File("$it/${AnvilConfiguration.configFileLocation}")
			if (descriptorFile.exists())
			{
				project.anvilProjectService
			}
		}
	}

	override fun projectClosing(project: Project)
	{
		project.basePath?.let {
			val descriptorFile =
				File("$it/${AnvilConfiguration.configFileLocation}")
			if (descriptorFile.exists())
			{
				project.anvilProjectService.saveConfigToDisk()
			}
		}
		super.projectClosing(project)
	}

}
