/*
 * Labels.kt
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

import avail.anvil.Anvil.defaults
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avail.AvailRuntimeConfiguration
import avail.anvil.themes.anvilDarkTheme
import avail.anvil.themes.anvilLightTheme
import avail.anvil.themes.moduleRootColor
import avail.anvil.themes.versionNumberColor
import com.avail.builder.ModuleRoot
import java.net.URI

////////////////////////////////////////////////////////////////////////////////
//                              Version labels.                               //
////////////////////////////////////////////////////////////////////////////////

/**
 * Simple view of an Avail version.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param version
 *   The version summary to present. Expected to conform to
 *   `"$library-$major.$minor.$patch"`, where `$library` denotes the library,
 *   `$major` its major number, `$minor` its minor number, and `$patch` its
 *   patch level. Defaults to the
 *   [preferred&#32;Avail&#32;version][AvailRuntimeConfiguration.activeVersionSummary].
 */
@Composable
fun AvailVersionLabel (
	version: String = AvailRuntimeConfiguration.activeVersionSummary
) = Text(
	buildAnnotatedString {
		val (prefix, dotted) = version.split("-", limit = 2)
		append(prefix)
		addStyle(
			SpanStyle(color = MaterialTheme.colors.onBackground),
			start = 0,
			end = length
		)
		append("-")
		append(dotted)
		addStyle(
			SpanStyle(color = versionNumberColor),
			start = prefix.length + 1,
			end = length
		)
	},
	textAlign = TextAlign.Center,
	style = TextStyle(background = MaterialTheme.colors.background)
)

/**
 * Preview of light [AvailVersionLabel].
 */
@Preview
@Composable
private fun PreviewAvailVersionLabelLight () =
	MaterialTheme(colors = anvilLightTheme) {
		AvailVersionLabel()
	}

/**
 * Preview of dark [AvailVersionLabel].
 */
@Preview
@Composable
private fun PreviewAvailVersionLabelDark () =
	MaterialTheme(colors = anvilDarkTheme) {
		AvailVersionLabel()
	}

////////////////////////////////////////////////////////////////////////////////
//                               Header labels.                               //
////////////////////////////////////////////////////////////////////////////////

/**
 * A label suitable for presentation in the header of a table.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param text
 *   The text of the label.
 */
@Composable
fun HeaderLabel (text: String, modifier: Modifier = Modifier) = Text(
	text,
	style = TextStyle(fontWeight = FontWeight.Bold),
	textAlign = TextAlign.Center,
	modifier = modifier.padding(vertical = 5.dp)
)

/**
 * Preview of light [HeaderLabel].
 */
@Preview
@Composable
private fun PreviewHeaderLabelLight () =
	MaterialTheme(colors = anvilLightTheme) {
		HeaderLabel("Header")
	}


/**
 * Preview of dark [HeaderLabel].
 */
@Preview
@Composable
private fun PreviewHeaderLabelDark () =
	MaterialTheme(colors = anvilDarkTheme) {
		HeaderLabel("Header")
	}

////////////////////////////////////////////////////////////////////////////////
//                            Module root labels.                             //
////////////////////////////////////////////////////////////////////////////////

/**
 * Simple view of a [module&#32;root][ModuleRoot].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param root
 *   The [ModuleRoot] to present.
 * @param modifier
 *   The [Modifier].
 */
@Composable
fun ModuleRootLabel (
	root: ModuleRoot,
	modifier: Modifier = Modifier,
	overflow: TextOverflow = TextOverflow.Ellipsis
) = Text(
	root.name,
	textAlign = TextAlign.Left,
	style = TextStyle(color = moduleRootColor, fontWeight = FontWeight.Bold),
	modifier = modifier,
	overflow = overflow
)

/**
 * Simple view of a [module&#32;root][ModuleRoot] name.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param root
 *   The [ModuleRoot] to present.
 * @param modifier
 *   The [Modifier].
 */
@Composable
fun ModuleRootLabel (
	root: String,
	modifier: Modifier = Modifier,
	overflow: TextOverflow = TextOverflow.Ellipsis
) = Text(
	root,
	textAlign = TextAlign.Left,
	style = TextStyle(color = moduleRootColor, fontWeight = FontWeight.Bold),
	modifier = modifier,
	overflow = overflow
)

/**
 * Preview of light [ModuleRootLabel].
 */
@Preview
@Composable
private fun PreviewModuleRootLabelLight () =
	MaterialTheme(colors = anvilLightTheme) {
		ModuleRootLabel(
			defaults.defaultModuleNameResolver.moduleRoots.moduleRootFor("avail")!!)
	}

/**
 * Preview of dark [ModuleRootLabel].
 */
@Preview
@Composable
private fun PreviewModuleRootLabelDark () =
	MaterialTheme(colors = anvilDarkTheme) {
		ModuleRootLabel(
			defaults.defaultModuleNameResolver.moduleRoots.moduleRootFor("avail")!!)
	}

////////////////////////////////////////////////////////////////////////////////
//                                URI labels.                                 //
////////////////////////////////////////////////////////////////////////////////

/**
 * Simple view of a [URI].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param uri
 *   The [URI] to present.
 * @param modifier
 *   The [Modifier].
 * @param overflow
 *   The [TextOverflow].
 */
@Composable
fun URILabel (
	uri: URI,
	modifier: Modifier = Modifier,
	overflow: TextOverflow = TextOverflow.Ellipsis
) = Text(
	uri.toString(),
	textAlign = TextAlign.Left,
	style = TextStyle(fontStyle = FontStyle.Italic),
	modifier = modifier,
	overflow = overflow
)

/**
 * Simple view of a [URI].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param uri
 *   The [URI] to present.
 * @param modifier
 *   The [Modifier].
 * @param overflow
 *   The [TextOverflow].
 */
@Composable
fun URILabel (
	uri: String,
	modifier: Modifier = Modifier,
	overflow: TextOverflow = TextOverflow.Ellipsis
) = Text(
	uri,
	textAlign = TextAlign.Left,
	style = TextStyle(fontStyle = FontStyle.Italic),
	modifier = modifier,
	overflow = overflow
)

/**
 * Preview of light [URILabel].
 */
@Preview
@Composable
private fun PreviewURILabelLabelLight () =
	MaterialTheme(colors = anvilLightTheme) {
		URILabel(URI("file:///somewhere"))
	}

/**
 * Preview of dark [URILabel].
 */
@Preview
@Composable
private fun PreviewURILabelLabelDark () =
	MaterialTheme(colors = anvilDarkTheme) {
		URILabel(URI("file:///somewhere"))
	}
