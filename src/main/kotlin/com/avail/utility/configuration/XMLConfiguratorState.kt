/*
 * ConfigurationState.kt
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

package com.avail.utility.configuration

import java.lang.Thread.State
import java.util.*

/**
 * An `XMLConfiguratorState` encapsulates the state of an [XMLConfigurator].
 *
 * @param ConfigurationType
 *   A concrete [Configuration] class.
 * @param ElementType
 *   A concrete [XMLElement] class.
 * @param StateType
 *   A concrete `XMLConfiguratorState` class.
 *
 * @property configuration
 *   The [configuration][Configuration].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [State].
 *
 * @param configuration
 *   The initial [configuration][Configuration].
 */
class XMLConfiguratorState<
	ConfigurationType : Configuration,
	ElementType,
	StateType : XMLConfiguratorState<ConfigurationType, ElementType, StateType>>
constructor(private val configuration: ConfigurationType) where
	ElementType : Enum<ElementType>,
	ElementType : XMLElement<ConfigurationType, ElementType, StateType>
{

	/** The [document model][XMLDocumentModel]. */
	internal var model:
		XMLDocumentModel<ConfigurationType, ElementType, StateType>? = null

	/** The [accumulator][StringBuilder] for text. */
	private val accumulator = StringBuilder(1000)

	/** Should the [accumulator] be collecting text?  */
	private var shouldAccumulate = false

	/** The parse [stack][Deque].  */
	private val stack = ArrayDeque<ElementType>(5)

	/**
	 * Answer the [configuration][Configuration] manipulated by this
	 * [state][XMLConfiguratorState].
	 *
	 * @return The configuration.
	 */
	fun configuration(): ConfigurationType = configuration

	/**
	 * Activate the text accumulator.
	 */
	@Suppress("unused")
	fun startAccumulator()
	{
		accumulator.setLength(0)
		shouldAccumulate = true
	}

	/**
	 * If the text accumulator is active, then append the specified range of
	 * the buffer onto the text accumulator; otherwise, do nothing.
	 *
	 * @param buffer
	 *   A buffer containing character data.
	 * @param start
	 *   The zero-based offset of the first element of the buffer that should be
	 *   copied to the accumulator.
	 * @param length
	 *   The number of characters that should be copied to the accumulator.
	 */
	fun accumulate(buffer: CharArray, start: Int, length: Int)
	{
		if (shouldAccumulate)
		{
			accumulator.append(buffer, start, length)
		}
	}

	/**
	 * The current contents of the text accumulator. This may contain leading or
	 * trailing whitespace.
	 */
	@Suppress("unused")
	val accumulatorContents get() = accumulator.toString()

	/** Deactivate the text accumulator. */
	@Suppress("unused")
	fun stopAccumulator()
	{
		shouldAccumulate = false
	}

	/**
	 * Push the specified [element][XMLElement] onto the parse stack.
	 *
	 * @param element
	 *   An element.
	 */
	fun push(element: ElementType) = stack.push(element)

	/**
	 * Answer the [top][XMLElement] of the parse stack (without consuming it).
	 *
	 * @return
	 *   The top of the parse stack.
	 */
	fun peek(): ElementType? = stack.peek()

	/**
	 * Answer the [top][XMLElement] of the parse stack (and consume it).
	 *
	 * @return
	 *   The top element of the [stack].
	 */
	fun pop(): ElementType = stack.pop()

	/**
	 * Answer the parent [element][XMLElement] of the current element.
	 *
	 * @return
	 *   The parent element.
	 */
	@Suppress("unused")
	val parentElement: ElementType?
		get()
		{
			val thisElement = stack.pop()
			try
			{
				return stack.peek()
			}
			finally
			{
				stack.push(thisElement)
			}
		}
}
