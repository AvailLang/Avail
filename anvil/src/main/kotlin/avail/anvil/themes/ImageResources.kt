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

import java.io.File
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
	fun resource (resource: String): URL =
		this.javaClass.classLoader.getResource(resource)
			?: error("Cound not locate resource $resource")

	/**
	 * The image file of an Avail logo with a hammer.
	 */
	val availHammer by lazy {
		File(
			this.javaClass.classLoader
				.getResource("AvailHammer.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail Module file.
	 */
	val moduleFileImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("ModuleInTree.png")!!.file)
	}

	/**
	 * The image file used to represent an Avail Module package.
	 */
	val packageFileImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("PackageInTree.png")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val resourceFileImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("file_resource_24dp.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val rootFileImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("folder_blue_24dp.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val resourceDirectoryImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("folder_yellow_24dp.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val expandedDirectoryImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("expand_more_grey_24dp.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val collapsedDirectoryImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("chevron_right_grey_24dp.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val expandedModuleImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("unfold_more_blue_24dp.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val collapsedModuleImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("unfold_less_blue_24dp.svg")!!.file)
	}

	/**
	 * The image file used to represent an Avail root.
	 */
	val playArrowImage by lazy {
		File(
			this.javaClass.classLoader
				.getResource("play_arrow_green_24dp.svg")!!.file)
	}
}
