/*
 * ConfigurationContext.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

package avail.build

import org.gradle.api.tasks.testing.Test

/**
 * `Configuration` contains static data specific to building.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ConfigurationContext
{
	/**
	 * The relative path to the Kotlin source directory.
	 */
	const val kotlinSourcePath = "src/main/kotlin"

	/**
	 * The group component of the Maven identifier for this project.
	 */
	const val mavenGroup = "avail"

	/**
	 * The source path for the documentation source.
	 */
	const val documentationSrcPath = "documentation/docs/src_docs"

	/**
	 * The path for the generated documentation site.
	 */
	const val documentationSitePath = "documentation/site"
}

/**
 * Generally configure the [Test].
 */
fun Test.configureTest()
{
	useJUnitPlatform()
	minHeapSize = "4g"
	maxHeapSize = "6g"
	enableAssertions = true
}
