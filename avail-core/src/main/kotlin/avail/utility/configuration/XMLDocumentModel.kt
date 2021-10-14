/*
 * XMLElement.kt
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

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

/**
 * An [XMLConfigurator] relies on an `XMLDocumentModel` to provide a schematic
 * description of the class of XML documents supported by a particular
 * [XMLElement] implementation. It offers:
 *
 *  * Access to the [root&#32;element][rootElement].
 *  * [Lookup][elementWithQName] of an element by its qualified name.
 *  * Access to the [allowed&#32;parent][allowedParentsOf] of a specified
 *    element.
 *  * Access to the [allowed&#32;child][allowedChildrenOf] of a specified
 *    element.
 *
 * @param ConfigurationType
 *   A concrete [Configuration] class.
 * @param ElementType
 *   A concrete [XMLElement] class.
 * @param StateType
 *   A concrete [XMLConfiguratorState] class.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new [XMLDocumentModel].
 *
 * @param elementClass
 *   The [element][XMLElement] [class][Class].
 */
class XMLDocumentModel<
	ConfigurationType : Configuration,
	ElementType,
	StateType : XMLConfiguratorState<ConfigurationType, ElementType, StateType>>
internal constructor(elementClass: Class<ElementType>) where
	ElementType : Enum<ElementType>,
	ElementType : XMLElement<ConfigurationType, ElementType, StateType>
{
	/**
	 * A [map][Map] from the qualified names of XML elements to the elements
	 * themselves.
	 */
	private val elementsByQName: MutableMap<String, ElementType>

	/** The root [element][XMLElement]. */
	private val rootElement: ElementType

	/**
	 * A [map][Map] from the [elements][XMLElement] to their allowed children.
	 */
	private val allowedChildren: MutableMap<ElementType, Set<ElementType>>

	/**
	 * Answer the [element][XMLElement] with the specified qualified name.
	 *
	 * @param qName
	 *   The qualified name of an element.
	 * @return
	 *   The element, or `null` if there is no such element.
	 */
	fun elementWithQName(qName: String): ElementType? =
		elementsByQName[qName]

	/**
	 * Answer the root [element][XMLElement].
	 *
	 * @return
	 *   The root element.
	 */
	fun rootElement(): ElementType
	{
		return rootElement
	}

	/**
	 * Answer the allowed child [elements][XMLElement] of the specified element.
	 *
	 * @param element
	 *   An element.
	 * @return
	 *   The allowed child elements.
	 */
	fun allowedChildrenOf(element: ElementType): Set<ElementType>? =
		allowedChildren[element]

	/**
	 * Answer the allowed parent [elements][XMLElement] of the specified
	 * element.
	 *
	 * @param element
	 *   An element.
	 * @return
	 *   The allowed parent elements.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun allowedParentsOf( element: ElementType): Set<ElementType> =
		element.allowedParents

	init
	{
		assert(elementClass.isEnum)
		// Capture the elements of the enumeration.
		var elements: Array<ElementType>? = null
		try
		{
			val valuesMethod = elementClass.getMethod("values")
			assert(Modifier.isStatic(valuesMethod.modifiers))
			@Suppress("UNCHECKED_CAST")
			elements = valuesMethod(null) as Array<ElementType>
		}
		catch (e: SecurityException)
		{
			// This never happens.
			assert(false)
		}
		catch (e: NoSuchMethodException)
		{
			assert(false)
		}
		catch (e: IllegalArgumentException)
		{
			assert(false)
		}
		catch (e: IllegalAccessException)
		{
			assert(false)
		}
		catch (e: InvocationTargetException)
		{
			assert(false)
		}

		// Initialize other data structures.
		elementsByQName = mutableMapOf()
		allowedChildren = mutableMapOf()
		var root: ElementType? = null
		elements!!.forEach { element ->
			elementsByQName[element.qName] = element
			if (element.allowedParents.isEmpty())
			{
				assert(root === null)
				root = element
			}
			val children = elements
				.asSequence()
				.filter { it.allowedParents.contains(element) }
				.toSet()
			allowedChildren[element] = children
		}
		rootElement = root!!
	}
}
