/**
 * XMLConfigurator.java
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

import org.xml.sax.Attributes;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;

/**
 * An {@code XMLConfigurator} obtains a {@linkplain Configuration configuration}
 * by processing an XML document.
 *
 * <p>In order to use an {@code XMLConfigurator} to obtain a configuration, a
 * client must implement three abstractions:</p>
 *
 * <ul>
 * <li>A {@link Configuration} specific to the client's requirements.</li>
 * <li>An {@linkplain Enum enumeration} that satisfies the {@link XMLElement}
 * interface. This enumeration defines all valid elements for a particular
 * document type. Members must be able to satisfy requests for their {@linkplain
 * XMLElement#qName() qualified name} and immediate {@linkplain
 * XMLElement#allowedParents() parentage}. Members are also responsible for
 * their own processing (see {@link XMLElement#startElement(
 * XMLConfiguratorState, Attributes) startElement} and {@link
 * XMLElement#endElement(XMLConfiguratorState) endElement}.</li>
 * <li>An {@link XMLConfiguratorState} that maintains any state required by the
 * {@code XMLConfigurator} during the processing of an XML document. This state
 * may be interrogated by the {@code XMLElement}s.</li>
 * </ul>
 *
 * @param <ConfigurationType>
 *        A concrete {@link Configuration} class.
 * @param <ElementType>
 *        A concrete {@link XMLElement} class.
 * @param <StateType>
 *        A concrete {@link XMLConfiguratorState} class.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class XMLConfigurator<
	ConfigurationType extends Configuration,
	ElementType extends Enum<ElementType>
		& XMLElement<ConfigurationType, ElementType, StateType>,
	StateType extends XMLConfiguratorState<
		ConfigurationType, ElementType, StateType>>
implements Configurator<ConfigurationType>
{
	/** The {@linkplain Configuration configuration}. */
	protected final ConfigurationType configuration;

	/**
	 * The {@linkplain InputStream input stream} which contains the XML
	 * document that describes the {@linkplain Configuration configuration}.
	 */
	private final InputStream documentStream;

	/**
	 * Construct a new {@link XMLConfigurator}.
	 *
	 * @param configuration
	 *        A {@linkplain Configuration configuration}.
	 * @param state
	 *        The initial {@linkplain XMLConfiguratorState configurator state}.
	 * @param elementClass
	 *        The {@linkplain XMLElement element} {@linkplain Class class}.
	 * @param documentStream
	 *        The {@linkplain InputStream input stream} which contains the XML
	 *        document that describes the {@linkplain Configuration
	 *        configuration}.
	 */
	public XMLConfigurator (
			final ConfigurationType configuration,
			final StateType state,
			final Class<ElementType> elementClass,
			final InputStream documentStream)
	{
		this.configuration = configuration;
		this.documentStream = documentStream;
		this.model = new XMLDocumentModel<>(elementClass);
		state.setDocumentModel(model);
		this.state = state;
	}

	/**
	 * Has the {@linkplain XMLConfigurator configurator} been run yet?
	 */
	private boolean isConfigured;

	/**
	 * Has the {@linkplain XMLConfigurator configurator} been run yet?
	 *
	 * @return {@code true} if the configurator has been ren, {@code false}
	 *         otherwise.
	 */
	protected boolean isConfigured ()
	{
		return isConfigured;
	}

	/** The {@linkplain XMLDocumentModel document model}. */
	private final
	XMLDocumentModel<ConfigurationType, ElementType, StateType> model;

	/** The {@linkplain XMLConfiguratorState configurator state}. */
	protected final StateType state;

	@Override
	public void updateConfiguration () throws ConfigurationException
	{
		if (!isConfigured)
		{
			try
			{
				final SAXParserFactory factory = SAXParserFactory.newInstance();
				final SAXParser parser = factory.newSAXParser();
				parser.parse(
					documentStream,
					new XMLEventHandler<>(
						model, state));
				isConfigured = true;
			}
			catch (final Exception e)
			{
				throw new ConfigurationException("configuration error", e);
			}
		}
	}

	@Override
	public ConfigurationType configuration ()
	{
		assert isConfigured;
		return configuration;
	}
}
