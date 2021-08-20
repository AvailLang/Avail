/*
 * AvailObjectFieldHelper.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.descriptor.representation

import com.avail.descriptor.representation.AbstractDescriptor.Companion.bitFieldsFor
import com.avail.descriptor.representation.AbstractDescriptor.Companion.describeIntegerSlot
import com.avail.utility.StackPrinter
import com.avail.utility.cast
import org.jetbrains.annotations.Debug.Renderer

/**
 * This class assists with the presentation of [AvailObject]s in the IntelliJ
 * debugger.  Since AvailObjects have a uniform structure consisting of a
 * [descriptor][AbstractDescriptor], an array of `AvailObject`s, and an array of
 * `long`s, it is essential to the understanding of a hierarchy of Avail objects
 * that they be presented at the right level of abstraction, including the use
 * of symbolic names for conceptual subobjects.
 *
 * `[`The following steps are for Eclipse, but there's a similar mechanism for
 * IntelliJ.]
 *
 * Eclipse is still kind of fiddly about these presentations, requiring explicit
 * manipulation through dialogs (well, maybe there's some way to hack around
 * with the Eclipse preference files).  Here are the minimum steps by which to
 * set up symbolic Avail descriptions:
 *
 * 1. Preferences...  Java  Debug  Logical Structures
 *
 *    Add:
 *    * Qualified name:
 *      com.avail.descriptor.representation.AvailIntegerValueHelper
 *    * Description: Hide integer value field
 *    * Structure type: Single value
 *    * Code: `return new Object[0];`
 *
 * 1. Preferences...  Java  Debug  Logical Structures
 *
 *     Add:
 *    * Qualified name: com.avail.descriptor.representation.AvailObject
 *    * Description: Present Avail objects
 *    * Structure type: Single value
 *    * Code: `return describeForDebugger();`
 *
 * 1. Preferences...  Java  Debug  Logical Structures
 *
 *    Add:
 *    * Qualified name: com.avail.interpreter.execution.Interpreter
 *    * Description: Present Interpreter as stack frames
 *    * Structure type: Single value
 *    * Code: `return describeForDebugger();`
 *
 * 1. Preferences...  Java  Debug  Logical Structures
 *
 *    Add:
 *    * Qualified name:
 *      com.avail.descriptor.representation.AvailObjectFieldHelper
 *    * Description: Present helper's value's fields instead of the helper
 *    * Structure type: Single value
 *    * Code: `return value;`
 *
 * 1. Preferences...  Java  Debug  Detail Formatters
 *
 *    Add:
 *    * Qualified type name:
 *      com.avail.descriptor.representation.AvailObjectFieldHelper
 *    * Detail formatter code snippet: `return name();`
 *    * Enable this detail formatter: (checked)
 *    * (after OK) Show variable details: As the label for all variables
 *
 * 1. In the Debug perspective, go to the Variables view.  Select the tool bar
 *    icon whose hover help is Show Logical Structure.
 *
 * @param parentObject
 *   The object containing the value.
 * @param slot
 *   The [slot][AbstractSlotsEnum] in which the value occurs.
 * @param subscript
 *   The optional subscript for a repeating slot.  Uses -1 to indicate this is
 *   not a repeating slot.
 * @param value
 *   The value being presented in that slot.
 * @param forcedName
 *   When set to non-`null`, forces this exact name to be presented, regardless
 *   of the [value].
 * @param forcedChildren
 *   When set to non-`null`, forces the given [Array] to be presented as the
 *   children of this node in the debugger.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Renderer(
	text = "nameForDebugger",
	childrenArray = "describeForDebugger")
class AvailObjectFieldHelper(
	private val parentObject: A_BasicObject?,
	val slot: AbstractSlotsEnum,
	val subscript: Int,
	val value: Any?,
	val forcedName: String? = null,
	val forcedChildren: Array<*>? = null
) {
	/**
	 * The name to present for this field.
	 */
	val name by lazy { privateComputeNameForDebugger() }

	/**
	 * Answer the string to display for this field.
	 *
	 * @return A [String].
	 */
	fun nameForDebugger() = name

	/**
	 * Produce a name for this helper in the debugger.
	 *
	 * @return A suitable [String] to present for this helper.
	 */
	private fun privateComputeNameForDebugger() = buildString {
		if (forcedName !== null)
		{
			append(forcedName)
			return@buildString
		}
		when {
			subscript != -1 -> {
				val name = slot.fieldName()
				append(name)
				if (name.endsWith("_")) setLength(length - 1)
				append("[$subscript]")
			}
			else -> append(slot.fieldName())
		}
		when (value) {
			null -> append(" = Java null")
			is AvailObject -> append(' ').append(value.nameForDebugger())
			is AvailIntegerValueHelper -> {
				try
				{
					val strongSlot: IntegerSlotsEnum = slot.cast()
					val bitFields = bitFieldsFor(strongSlot)
					if (bitFields.isNotEmpty()) {
						// Remove the name.
						delete(0, length)
					}
					describeIntegerSlot(
						parentObject as AvailObject,
						value.longValue,
						strongSlot,
						bitFields,
						this)
				}
				catch (e: Throwable)
				{
					append("PROBLEM DESCRIBING INTEGER FIELD:\n")
					append(StackPrinter.trace(e))
				}
			}
			is String -> append(" = Java String: $value")
			is Array<*> -> append(" = Multi-line text")
			else -> append(" = ${value.javaClass.canonicalName}")
		}
	}

	@Suppress("unused")
	fun describeForDebugger(): Any? = when {
		forcedChildren !== null -> forcedChildren
		value is AvailObject -> value.describeForDebugger()
		value is AvailIntegerValueHelper -> emptyArray<Any>()
		else -> value
	}
}
