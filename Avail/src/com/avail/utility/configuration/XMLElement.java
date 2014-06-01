/**
 * XMLElement.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.utility.configuration;

import java.util.Set;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * {@code XMLElement} is designed to be implemented only by {@linkplain Enum
 * enumerations} that represent XML tag sets. Concrete implementations specify
 * the complete set of behavior required to use the {@link XMLConfigurator} to
 * produce application-specific {@linkplain Configuration configurations}.
 *
 * @param <ConfigurationType>
 *        A concrete {@link Configuration} class.
 * @param <ElementType>
 *        A concrete {@link XMLElement} class.
 * @param <StateType>
 *        A concrete {@link XMLConfiguratorState} class.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public interface XMLElement<
	ConfigurationType extends Configuration,
	ElementType extends Enum<ElementType>
		& XMLElement<ConfigurationType, ElementType, StateType>,
	StateType extends XMLConfiguratorState<
		ConfigurationType, ElementType, StateType>>
{
	/**
	 * Answer the qualified name of the {@linkplain XMLElement element}.
	 *
	 * @return The qualified name of the element.
	 */
	public String qName ();

	/**
	 * Answer the allowed parent elements of the receiver element.
	 *
	 * @return The allowed parent elements.
	 */
	public Set<ElementType> allowedParents ();

	/**
	 * Receive notification of the start of an element.
	 *
	 * @param state
	 *        The current {@linkplain XMLConfiguratorState parse state}.
	 * @param attributes
	 *        The attributes attached to the element.
	 * @throws SAXException
	 *         If something goes wrong.
	 */
	public void startElement (
			final StateType state,
			final Attributes attributes)
		throws SAXException;

	/**
	 * Receive notification of the end of an element.
	 *
	 * @param state
	 *        The current {@linkplain XMLConfiguratorState parse state}.
	 * @throws SAXException
	 *         If something goes wrong.
	 */
	public void endElement (final StateType state)
		throws SAXException;
}
