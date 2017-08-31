/**
 * XMLEventHandler.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.xml.parsers.SAXParser;

import javax.annotation.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * An {@link XMLConfigurator} uses a {@code XMLEventHandler} to interface with
 * the {@linkplain SAXParser SAX parser}.
 *
 * @param <ConfigurationType>
 *        A concrete {@link Configuration} class.
 * @param <ElementType>
 *        A concrete {@link XMLElement} class.
 * @param <StateType>
 *        A concrete {@link XMLConfiguratorState} class.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class XMLEventHandler<
	ConfigurationType extends Configuration,
	ElementType extends Enum<ElementType>
		& XMLElement<ConfigurationType, ElementType, StateType>,
	StateType extends XMLConfiguratorState<
		ConfigurationType, ElementType, StateType>>
extends DefaultHandler
{
	/** The {@linkplain XMLDocumentModel document model}. */
	private final
	XMLDocumentModel<ConfigurationType, ElementType, StateType> model;

	/**
	 * The current {@linkplain XMLConfiguratorState state} of the {@linkplain
	 * Configurator configurator}.
	 */
	private final StateType state;

	/**
	 * Construct a new {@link XMLEventHandler}.
	 *
	 * @param model
	 *        The {@linkplain XMLDocumentModel document model}.
	 * @param state
	 *        The {@linkplain XMLConfiguratorState configurator state}.
	 */
	public XMLEventHandler (
		final XMLDocumentModel<
			ConfigurationType, ElementType, StateType> model,
		final StateType state)
	{
		this.model = model;
		this.state = state;
	}

	/**
	 * Answer a {@link SAXException}.
	 *
	 * @param forbidden
	 *        The qualified name of an {@linkplain XMLElement element} that was
	 *        not permitted to appear in some context.
	 * @param allowed
	 *        A {@linkplain Collection collection} of elements allowed in
	 *        the same context.
	 * @return A {@code SAXException}.
	 */
	private SAXException elementNotAllowed (
		final String forbidden,
		final Collection<ElementType> allowed)
	{
		final StringBuilder builder = new StringBuilder(1000);
		builder.append("found element \"");
		builder.append(forbidden.toUpperCase());
		builder.append("\" but expected one of:");
		for (final ElementType element : allowed)
		{
			builder.append("\n\t\"");
			builder.append(element.qName().toUpperCase());
			builder.append('"');
		}
		builder.append('\n');
		return new SAXException(builder.toString());
	}

	@Override
	public void startElement (
		final @Nullable String uri,
		final @Nullable String localName,
		final @Nullable String qName,
		final @Nullable Attributes attributes)
	throws SAXException
	{
		assert qName != null;
		assert attributes != null;
		final ElementType parent = state.peek();
		final Set<ElementType> allowedChildren = parent == null
			? Collections.singleton(model.rootElement())
			: model.allowedChildrenOf(parent);
		final ElementType element = model.elementWithQName(qName);
		if (!allowedChildren.contains(element))
		{
			throw elementNotAllowed(qName, allowedChildren);
		}
		state.push(element);
		element.startElement(state, attributes);
	}

	@Override
	public void endElement (
		final @Nullable String uri,
		final @Nullable String localName,
		final @Nullable String qName)
	throws SAXException
	{
		assert qName != null;
		final ElementType element = model.elementWithQName(qName);
		assert element != null;
		assert element == state.peek();
		element.endElement(state);
		state.pop();
	}

	@Override
	public void characters (
		final @Nullable char[] buffer,
		final int start,
		final int length)
	{
		assert buffer != null;
		state.accumulate(buffer, start, length);
	}
}
