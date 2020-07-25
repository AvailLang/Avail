/*
 * TextInterface.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.io

import com.avail.builder.AvailBuilder
import com.avail.descriptor.fiber.A_Fiber

import java.nio.charset.StandardCharsets

/**
 * A `TextInterface` represents an interface between an external process,
 * device, or user and an Avail agent (e.g., an [AvailBuilder] or
 * [fiber][A_Fiber]). As such, it combines [input][TextInputChannel],
 * [output][TextOutputChannel], and error channels, corresponding to the usual
 * notions of standard input, output, and error, respectively. These channels
 * are each text-oriented, and constrained to operate on
 * [UTF-8][StandardCharsets.UTF_8] encoded character data.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property inputChannel
 *   The [standard&#32;input channel][TextInputChannel].
 * @property outputChannel
 *   The [standard&#32;output channel][TextOutputChannel].
 * @property errorChannel
 *   The [standard&#32;error channel][TextOutputChannel].
 *
 * @constructor
 * Construct a new `TextInterface`.
 *
 * @param inputChannel
 *   The [standard&#32;input channel][TextInputChannel].
 * @param outputChannel
 *   The [standard&#32;output channel][TextOutputChannel].
 * @param errorChannel
 *   The standard error channel.
 */
class TextInterface constructor(
	val inputChannel: TextInputChannel,
	val outputChannel: TextOutputChannel,
	val errorChannel: TextOutputChannel)
{
	companion object
	{

		/**
		 * Answer a `TextInterface` bound to the [System] [input][System.in],
		 * [output][System.out], and [error][System.err] channels.
		 *
		 * @return
		 *   A text interface suitable for managing the system streams.
		 */
		@JvmStatic
		fun systemTextInterface(): TextInterface
		{
			return TextInterface(
				ConsoleInputChannel(System.`in`),
				ConsoleOutputChannel(System.out),
				ConsoleOutputChannel(System.err))
		}
	}
}
