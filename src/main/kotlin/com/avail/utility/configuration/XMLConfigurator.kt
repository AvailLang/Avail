/*
 * XMLConfigurator.kt
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

import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/**
 * An `XMLConfigurator` obtains a [configuration][Configuration] by processing
 * an XML document.
 *
 * In order to use an `XMLConfigurator` to obtain a configuration, a client must
 * implement three abstractions:
 *
 *  * A [Configuration] specific to the client's requirements.
 *  * An [enumeration][Enum] that satisfies the [XMLElement] interface.
 *  This enumeration defines all valid elements for a particular document type.
 *  Members must be able to satisfy requests for their
 *  [qualified name][XMLElement.qName] and immediate
 *  [parentage][XMLElement.allowedParents]. Members are also responsible for
 *  their own processing (see [startElement][XMLElement.startElement] and
 *  [endElement][XMLElement.endElement].
 *  * An [XMLConfiguratorState] that maintains any state required by the
 *  `XMLConfigurator` during the processing of an XML document. This state
 *  may be interrogated by the `XMLElement`s.
 *
 * @param ConfigurationType
 *   A concrete [Configuration] class.
 * @param ElementType
 *   A concrete [XMLElement] class.
 * @param StateType
 *   A concrete [XMLConfiguratorState] class.
 *
 * @property state
 *   The [configurator state][XMLConfiguratorState].
 * @property documentStream
 *   The [input stream][InputStream] which contains the XML document that
 *   describes the [configuration][Configuration].
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [XMLConfigurator].
 *
 * @param configuration
 *   A [configuration][Configuration].
 * @param state
 *   The initial [configurator state][XMLConfiguratorState].
 * @param elementClass
 *   The [element][XMLElement] [class][Class].
 * @param documentStream
 *   The [input stream][InputStream] which contains the XML document that
 *   describes the [configuration][Configuration].
 */
class XMLConfigurator<
	ConfigurationType : Configuration,
	ElementType,
	StateType : XMLConfiguratorState<ConfigurationType, ElementType, StateType>>
constructor(
	override val configuration: ConfigurationType,
	val state: StateType,
	elementClass: Class<ElementType>,
	private val documentStream: InputStream)
: Configurator<ConfigurationType>
	where ElementType : Enum<ElementType>,
		  ElementType : XMLElement<ConfigurationType, ElementType, StateType>
{
	/**
	 * Has the [configurator][XMLConfigurator] been run yet?
	 *
	 * @return
	 *   `true` if the configurator has been ren, `false` otherwise.
	 */
	private var isConfigured: Boolean = false

	/** The [document model][XMLDocumentModel].  */
	private val model:
		XMLDocumentModel<ConfigurationType, ElementType, StateType> =
			XMLDocumentModel(elementClass)

	init
	{
		state.model = model
	}

	@Throws(ConfigurationException::class)
	override fun updateConfiguration()
	{
		if (!isConfigured)
		{
			try
			{
				val factory = SAXParserFactory.newInstance()
				val parser = factory.newSAXParser()
				parser.parse(documentStream, XMLEventHandler(model, state))
				isConfigured = true
			}
			catch (e: Exception)
			{
				throw ConfigurationException("configuration error", e)
			}
		}
	}
}
