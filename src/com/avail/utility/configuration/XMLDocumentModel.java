/**
 * XMLElement.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An {@link XMLConfigurator} relies on an {@code XMLDocumentModel} to provide
 * a schematic description of the class of XML documents supported by a
 * particular {@link XMLElement} implementation. It offers:
 *
 * <ul>
 * <li>Access to the {@linkplain #rootElement() root element}.<li>
 * <li>{@linkplain #elementWithQName(String) Lookup} of an element by its
 * qualified name.</li>
 * <li>Access to the {@linkplain #allowedParentsOf(Enum) allowed parent
 * elements} of a specified element.</li>
 * <li>Access to the {@linkplain #allowedChildrenOf(Enum) allowed child
 * elements} of a specified element.</li>
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
public final class XMLDocumentModel<
	ConfigurationType extends Configuration,
	ElementType extends Enum<ElementType>
		& XMLElement<ConfigurationType, ElementType, StateType>,
	StateType extends XMLConfiguratorState<
		ConfigurationType, ElementType, StateType>>
{
	/**
	 * The {@linkplain Class class} of the {@linkplain Enum enumeration} of
	 * the actual elements.
	 */
	private final Class<ElementType> elementClass;

	/** The elements. */
	private final ElementType[] elements;

	/**
	 * A {@linkplain Map map} from the qualified names of XML elements to
	 * the elements themselves.
	 */
	private final Map<String, ElementType> elementsByQName;

	/**
	 * Answer the {@linkplain XMLElement element} with the specified qualified
	 * name.
	 *
	 * @param qName
	 *        The qualified name of an element.
	 * @return The element, or {@code null} if there is no such element.
	 */
	public ElementType elementWithQName (final String qName)
	{
		return elementsByQName.get(qName);
	}

	/**
	 * Answer the root {@linkplain XMLElement element}.
	 *
	 * @returns The root element.
	 */
	private final ElementType rootElement;

	/**
	 * Answer the root {@linkplain XMLElement element}.
	 *
	 * @return The root element.
	 */
	public ElementType rootElement ()
	{
		return rootElement;
	}

	/**
	 * A {@linkplain Map map} from the {@linkplain XMLElement elements} to their
	 * allowed children.
	 */
	private final Map<ElementType, Set<ElementType>> allowedChildren;

	/**
	 * Answer the allowed child {@linkplain XMLElement elements} of the
	 * specified element.
	 *
	 * @param element
	 *        An element.
	 * @return The allowed child elements.
	 */
	public Set<ElementType> allowedChildrenOf (
		final ElementType element)
	{
		return allowedChildren.get(element);
	}

	/**
	 * Answer the allowed parent {@linkplain XMLElement elements} of the
	 * specified element.
	 *
	 * @param element
	 *        An element.
	 * @return The allowed parent elements.
	 */
	public Set<ElementType> allowedParentsOf (
		final ElementType element)
	{
		return element.allowedParents();
	}

	/**
	 * Construct a new {@link XMLDocumentModel}.
	 *
	 * @param elementClass
	 *        The {@linkplain XMLElement element} {@linkplain Class class}.
	 */
	@SuppressWarnings("unchecked")
	XMLDocumentModel (final Class<ElementType> elementClass)
	{
		assert elementClass.isEnum();
		this.elementClass = elementClass;
		// Capture the elements of the enumeration.
		ElementType[] values = null;
		try
		{
			final Method valuesMethod = this.elementClass.getMethod("values");
			assert Modifier.isStatic(valuesMethod.getModifiers());
			values = (ElementType[]) valuesMethod.invoke(null);
		}
		catch (final SecurityException
			| NoSuchMethodException
			| IllegalArgumentException
			| IllegalAccessException
			| InvocationTargetException e)
		{
			// This never happens.
			assert false;
		}
		assert values != null;
		elements = values;
		// Initialize other data structures.
		elementsByQName = new HashMap<String, ElementType>(elements.length);
		allowedChildren =
			new HashMap<ElementType, Set<ElementType>>(elements.length);
		ElementType root = null;
		for (final ElementType element : elements)
		{
			elementsByQName.put(element.qName(), element);
			if (element.allowedParents().isEmpty())
			{
				assert root == null;
				root = element;
			}
			final Set<ElementType> children = new HashSet<ElementType>();
			for (final ElementType child : elements)
			{
				if (child.allowedParents().contains(element))
				{
					children.add(child);
				}
			}
			allowedChildren.put(element, children);
		}
		assert root != null;
		rootElement = root;
	}
}
