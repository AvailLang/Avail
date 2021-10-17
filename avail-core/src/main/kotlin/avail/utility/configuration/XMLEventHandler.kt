/*
 * XMLEventHandler.kt
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

package avail.utility.configuration

import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParser

/**
 * An [XMLConfigurator] uses a `XMLEventHandler` to interface with the
 * [SAX&#32;parser][SAXParser].
 *
 * @param ConfigurationType
 *   A concrete [Configuration] class.
 * @param ElementType
 *   A concrete [XMLElement] class.
 * @param StateType
 *   A concrete [XMLConfiguratorState] class.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property model
 *   The [document&#32;model][XMLDocumentModel].
 * @property state
 *   The current [state][XMLConfiguratorState] of the
 *   [configurator][Configurator].
 *
 * @constructor
 * Construct a new `XMLEventHandler`.
 *
 * @param model
 * The [document&#32;model][XMLDocumentModel].
 * @param state
 * The [configurator&#32;state][XMLConfiguratorState].
 */
internal class XMLEventHandler<
	ConfigurationType : Configuration,
	ElementType,
	StateType : XMLConfiguratorState<ConfigurationType, ElementType, StateType>>
constructor (
	private val model: XMLDocumentModel<ConfigurationType, ElementType, StateType>,
	private val state: StateType) : DefaultHandler()
where
	ElementType : Enum<ElementType>,
	ElementType : XMLElement<ConfigurationType, ElementType, StateType>
{
	/**
	 * Answer a [SAXException].
	 *
	 * @param forbidden
	 *   The qualified name of an [element][XMLElement] that was not permitted
	 *   to appear in some context.
	 * @param allowed
	 *   A [collection][Collection] of elements allowed in the same context.
	 * @return
	 *   A `SAXException`.
	 */
	private fun elementNotAllowed(
		forbidden: String, allowed: Collection<ElementType>): SAXException
	{
		val builder = StringBuilder(1000)
		builder.append("found element \"")
		builder.append(forbidden.uppercase())
		builder.append("\" but expected one of:")
		for (element in allowed)
		{
			builder.append("\n\t\"")
			builder.append(element.qName.uppercase())
			builder.append('"')
		}
		builder.append('\n')
		return SAXException(builder.toString())
	}

	@Throws(SAXException::class)
	override fun startElement(
		uri: String, localName: String, qName: String, attributes: Attributes)
	{
		val parent = state.peek()
		val allowedChildren : Set<ElementType> =
			if (parent === null) setOf(model.rootElement())
			else model.allowedChildrenOf(parent)!!
		val element = model.elementWithQName(qName)
		if (!allowedChildren.contains(element))
		{
			throw elementNotAllowed(qName, allowedChildren)
		}
		state.push(element!!)
		element.startElement(state, attributes)
	}

	@Throws(SAXException::class)
	override fun endElement(uri: String?, localName: String?, qName: String)
	{
		val element = model.elementWithQName(qName)!!
		assert(element === state.peek())
		element.endElement(state)
		state.pop()
	}

	override fun characters(buffer: CharArray?, start: Int, length: Int)
	{
		state.accumulate(buffer!!, start, length)
	}
}
