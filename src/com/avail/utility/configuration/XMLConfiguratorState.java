/*
 * ConfigurationState.java
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

package com.avail.utility.configuration;

import javax.annotation.Nullable;
import java.lang.Thread.State;
import java.util.ArrayDeque;
import java.util.Deque;

import static com.avail.utility.Nulls.stripNull;

/**
 * An {@code XMLConfiguratorState} encapsulates the state of an
 * {@link XMLConfigurator}.
 *
 * @param <ConfigurationType>
 *        A concrete {@link Configuration} class.
 * @param <ElementType>
 *        A concrete {@link XMLElement} class.
 * @param <StateType>
 *        A concrete {@code XMLConfiguratorState} class.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class XMLConfiguratorState<
	ConfigurationType extends Configuration,
	ElementType extends Enum<ElementType>
		& XMLElement<ConfigurationType, ElementType, StateType>,
	StateType extends XMLConfiguratorState<
		ConfigurationType, ElementType, StateType>>
{
	/** The {@linkplain Configuration configuration}. */
	private final ConfigurationType configuration;

	/** The {@linkplain XMLDocumentModel document model}. */
	private @Nullable
	XMLDocumentModel<ConfigurationType, ElementType, StateType> model;

	/**
	 * Answer the {@linkplain XMLDocumentModel document model}.
	 *
	 * @return The document model.
	 */
	public XMLDocumentModel<ConfigurationType, ElementType, StateType>
	documentModel ()
	{
		return stripNull(model);
	}

	/**
	 * Set the {@linkplain XMLDocumentModel document model}.
	 *
	 * @param model The document model.
	 */
	public void setDocumentModel (
		final XMLDocumentModel<
			ConfigurationType, ElementType, StateType> model)
	{
		this.model = model;
	}

	/**
	 * Answer the {@linkplain Configuration configuration} manipulated by this
	 * {@linkplain XMLConfiguratorState state}.
	 *
	 * @return The configuration.
	 */
	public ConfigurationType configuration ()
	{
		return configuration;
	}

	/** The {@linkplain StringBuilder accumulator} for text. */
	private final StringBuilder accumulator =
		new StringBuilder(1000);

	/** Should the {@linkplain #accumulator} be collecting text? */
	private boolean shouldAccumualate = false;

	/**
	 * Activate the text accumulator.
	 */
	public void startAccumulator ()
	{
		accumulator.setLength(0);
		shouldAccumualate = true;
	}

	/**
	 * If the text accumulator is active, then append the specified range of
	 * the buffer onto the text accumulator; otherwise, do nothing.
	 *
	 * @param buffer
	 *        A buffer containing character data.
	 * @param start
	 *        The zero-based offset of the first element of the buffer that
	 *        should be copied to the accumulator.
	 * @param length
	 *        The number of characters that should be copied to the
	 *        accumulator.
	 */
	public void accumulate (
		final char[] buffer,
		final int start,
		final int length)
	{
		if (shouldAccumualate)
		{
			accumulator.append(buffer, start, length);
		}
	}

	/**
	 * Answer the current contents of the text accumulator. This may contain
	 * leading or trailing whitespace.
	 *
	 * @return The text contained within the accumulator.
	 */
	public String accumulatorContents ()
	{
		return accumulator.toString();
	}

	/**
	 * Deactivate the text accumulator.
	 */
	public void stopAccumulator ()
	{
		shouldAccumualate = false;
	}

	/** The parse {@linkplain Deque stack}. */
	private final Deque<ElementType> stack =
		new ArrayDeque<>(5);

	/**
	 * Push the specified {@linkplain XMLElement element} onto the parse stack.
	 *
	 * @param element
	 *        An element.
	 */
	public final void push (final ElementType element)
	{
		stack.push(element);
	}

	/**
	 * Answer the {@linkplain XMLElement top} of the parse stack (without
	 * consuming it).
	 *
	 * @return The top of the parse stack.
	 */
	public final @Nullable ElementType peek ()
	{
		return stack.peek();
	}

	/**
	 * Answer the {@linkplain XMLElement top} of the parse stack (and consume
	 * it).
	 */
	public final void pop ()
	{
		stack.pop();
	}

	/**
	 * Answer the parent {@linkplain XMLElement element} of the current
	 * element.
	 *
	 * @return The parent element.
	 */
	public ElementType parentElement ()
	{
		final ElementType thisElement = stack.pop();
		try
		{
			return stack.peek();
		}
		finally
		{
			stack.push(thisElement);
		}
	}

	/**
	 * Construct a new {@link State}.
	 *
	 * @param configuration
	 *        The initial {@linkplain Configuration configuration}.
	 */
	protected XMLConfiguratorState (
		final ConfigurationType configuration)
	{
		this.configuration = configuration;
	}
}
