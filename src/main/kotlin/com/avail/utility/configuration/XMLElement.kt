/*
 * XMLElement.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import org.xml.sax.Attributes
import org.xml.sax.SAXException

/**
 * `XMLElement` is designed to be implemented only by [enumerations][Enum] that
 * represent XML tag sets. Concrete implementations specify the complete set of
 * behavior required to use the [XMLConfigurator] to produce
 * application-specific [configurations][Configuration].
 *
 * @param ConfigurationType
 *   A concrete [Configuration] class.
 * @param ElementType
 *   A concrete `XMLElement` class.
 * @param StateType
 *   A concrete [XMLConfiguratorState] class.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
interface XMLElement<
	ConfigurationType : Configuration,
	ElementType,
	StateType : XMLConfiguratorState<ConfigurationType, ElementType, StateType>>
	where ElementType : Enum<ElementType>,
		  ElementType : XMLElement<ConfigurationType, ElementType, StateType>
{
	/**
	 * Answer the qualified name of the [element][XMLElement].
	 *
	 * @return
	 *   The qualified name of the element.
	 */
	fun qName(): String

	/**
	 * Answer the allowed parent elements of the receiver element.
	 *
	 * @return
	 *   The allowed parent elements.
	 */
	fun allowedParents(): Set<ElementType>

	/**
	 * Receive notification of the start of an element.
	 *
	 * @param state
	 *   The current [parse state][XMLConfiguratorState].
	 * @param attributes
	 *   The attributes attached to the element.
	 * @throws SAXException
	 *   If something goes wrong.
	 */
	@Throws(SAXException::class)
	fun startElement(state: StateType, attributes: Attributes)

	/**
	 * Receive notification of the end of an element.
	 *
	 * @param state
	 *   The current [parse state][XMLConfiguratorState].
	 * @throws SAXException
	 *   If something goes wrong.
	 */
	@Throws(SAXException::class)
	fun endElement(state: StateType)
}
