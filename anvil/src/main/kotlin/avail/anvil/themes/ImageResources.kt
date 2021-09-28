/*
 * ImageResources.kt
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

package avail.anvil.themes

import java.io.InputStream
import java.net.URL

/**
 * {@code ImageResources} manages access to all resource images.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ImageResources
{
	/**
	 * Answer the URL for the given resource.
	 *
	 * @param resource
	 *   The resources-relative path to the resource.
	 * @return
	 *   The resource [URL].
	 */
	fun resource (resource: String): InputStream =
		this.javaClass.classLoader.getResourceAsStream(resource)
			?: error("Cound not locate resource $resource")

	/**
	 * The image file of an Avail logo with a hammer.
	 */
	val availHammer = "AvailHammer.svg"

	/**
	 * The image file used to represent an Avail Module file.
	 */
	val moduleFileImage = "ModuleInTree.png"

	/**
	 * The image file used to represent an Avail Module package.
	 */
	val packageFileImage = "PackageInTree.png"

	/**
	 * The image file used to represent an Avail root.
	 */
	val resourceFileImage = "file_resource_24dp.svg"

	/**
	 * The image file used to represent an Avail root.
	 */
	val rootFileImage = "folder_blue_24dp.svg"

	/**
	 * The image file used to represent an Avail root.
	 */
	val resourceDirectoryImage = "folder_yellow_24dp.svg"

	/**
	 * The image file used to represent an Avail root.
	 */
	val expandedDirectoryImage = "expand_more_grey_24dp.svg"

	/**
	 * The image file used to represent an Avail root.
	 */
	val collapsedDirectoryImage = "chevron_right_grey_24dp.svg"

	/**
	 * The image file used to represent an Avail root.
	 */
	val expandedModuleImage = "unfold_more_blue_24dp.svg"

	/**
	 * The image file used to represent an Avail root.
	 */
	val collapsedModuleImage = "unfold_less_blue_24dp.svg"

	/**
	 * The image file used to represent an Avail root.
	 */
	val playArrowImage = "play_arrow_green_24dp.svg"
}
