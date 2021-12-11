/*
 * AsyncImage.kt
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

package avail.anvil.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import avail.anvil.themes.ImageResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import java.io.IOException

/**
 * Load an SVG image asynchronously.
 *
 * @param resource
 *   The resource to load.
 * @param contentDescription
 *   The [Image] `contentDescription` input that describes the image.
 * @param modifier
 *   The [Modifier] for the [Image] composable.
 * @param contentScale
 *   Represents a rule to apply to scale a source rectangle to be inscribed into
 *   a destination.
 */
@Composable
fun AsyncImageBitmap(
	resource: String,
	contentDescription: String = "",
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit, )
{
	AsyncImage(
		load = {
			val input = ImageResources.resource(resource)
			loadImageBitmap(input)
		},
		painterFor = { remember { BitmapPainter(it) } },
		contentDescription = contentDescription,
		modifier = modifier,
		contentScale = contentScale)
}

/**
 * Load an SVG image asynchronously.
 *
 * @param resource
 *   The resource to load.
 * @param contentDescription
 *   The [Image] `contentDescription` input that describes the image.
 * @param modifier
 *   The [Modifier] for the [Image] composable.
 * @param contentScale
 *   Represents a rule to apply to scale a source rectangle to be inscribed into
 *   a destination.
 */
@Composable
fun AsyncSvg(
	resource: String,
	contentDescription: String = "",
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit, )
{
	val density = LocalDensity.current
	AsyncImage(
		load = {
			val input = ImageResources.resource(resource)
			loadSvgPainter(input, density)
		},
		painterFor = { remember { it } },
		contentDescription = contentDescription,
		modifier = modifier,
		contentScale = contentScale)
}

/**
 * Load an [ImageVector] image asynchronously.
 *
 * @param resource
 *   The resource to load.
 * @param contentDescription
 *   The [Image] `contentDescription` input that describes the image.
 * @param modifier
 *   The [Modifier] for the [Image] composable.
 * @param contentScale
 *   Represents a rule to apply to scale a source rectangle to be inscribed into
 *   a destination.
 */
@Composable
fun AsyncImageVector(
	resource: String,
	contentDescription: String = "",
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit, )
{
	val density = LocalDensity.current
	AsyncImage(
		load =
		{
			val input = ImageResources.resource(resource)
			loadXmlImageVector(InputSource(input), density)
		},
		painterFor = { rememberVectorPainter(it) },
		contentDescription = contentDescription,
		modifier = modifier,
		contentScale = contentScale)
}

/**
 * Load an image asynchronously.
 *
 * The following code provides example usage:
 *
 * ```
 * Column {
 *   val density = LocalDensity.current
 *   val samplePngUrl =
 *      this.javaClass.classLoader.getResource("sample.png")!!.file
 *   val sampleSvgUrl =
 *      this.javaClass.classLoader.getResource("sample.svg")!!.file
 *   val sampleXmlUrl =
 *      this.javaClass.classLoader.getResource("sample.xml")!!.file
 *   AsyncImage(
 *      load = { loadImageBitmap(File(samplePngUrl)) },
 *      painterFor = { remember { BitmapPainter(it) } },
 *      contentDescription = "Sample",
 *      modifier = Modifier.width(200.dp))
 *   AsyncImage(
 *      load = { loadSvgPainter(File(sampleSvgUrl), density) },
 *      painterFor = { it },
 *      contentDescription = "Idea logo",
 *      contentScale = ContentScale.FillWidth,
 *      modifier = Modifier.width(200.dp))
 *   AsyncImage(
 *      load = { loadXmlImageVector(File(sampleXmlUrl), density) },
 *      painterFor = { rememberVectorPainter(it) },
 *      contentDescription = "Compose logo",
 *      contentScale = ContentScale.FillWidth,
 *      modifier = Modifier.width(200.dp))
 * }
 * ```
 *
 * @param T
 *   The file type being loaded.
 * @param load
 *   The suspend function that answers the asynchronously loaded image.
 * @param painterFor
 *   The [Composable] lambda that accepts `T` and answers a [Painter] that wraps
 *   the image.
 * @param contentDescription
 *   The [Image] `contentDescription` input that describes the image.
 * @param modifier
 *   The [Modifier] for the [Image] composable.
 * @param contentScale
 *   Represents a rule to apply to scale a source rectangle to be inscribed into
 *   a destination.
 */
@Composable
fun <T> AsyncImage(
	load: suspend () -> T,
	painterFor: @Composable (T) -> Painter,
	contentDescription: String = "",
	modifier: Modifier = Modifier,
	contentScale: ContentScale = ContentScale.Fit,
) {
	val image: T? by produceState<T?>(null)
	{
		withContext(Dispatchers.IO)
		{
			value = try
			{
				load()
			}
			catch (e: IOException)
			{
				e.printStackTrace()
				null
			}
		}
	}
	if (image != null)
	{
		Image(
			painter = painterFor(image!!),
			contentDescription = contentDescription,
			contentScale = contentScale,
			modifier = modifier
		)
	}
}
