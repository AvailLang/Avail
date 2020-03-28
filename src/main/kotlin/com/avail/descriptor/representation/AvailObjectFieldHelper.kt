/*
 * AvailObjectFieldHelper.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

import com.avail.descriptor.A_BasicObject
import com.avail.descriptor.AbstractDescriptor
import com.avail.descriptor.AvailObject
import com.avail.utility.Casts
import com.avail.utility.StackPrinter

/**
 * This class assists with the presentation of [AvailObject]s in the
 * Eclipse debugger.  Since AvailObjects have a uniform structure consisting of
 * a descriptor, an array of AvailObjects, and an array of `int`s, it is
 * essential to the understanding of a hierarchy of Avail objects that they be
 * presented at the right level of abstraction, including the use of symbolic
 * names for conceptual subobjects.
 *
 *
 *
 * Eclipse is still kind of fiddly about these presentations, requiring explicit
 * manipulation through dialogs (well, maybe there's some way to hack around
 * with the Eclipse preference files).  Here are the minimum steps by which to
 * set up symbolic Avail descriptions:
 *
 *  1. Preferences...  Java  Debug  Logical Structures
 * Add:
 *
 *  * Qualified name: com.avail.descriptor.representation.AvailIntegerValueHelper
 *  * Description: Hide integer value field
 *  * Structure type: Single value
 *  * Code: `return new Object[0];`
 *
 *
 *  1. Preferences...  Java  Debug  Logical Structures
 * Add:
 *
 *  * Qualified name: com.avail.descriptor.AvailObject
 *  * Description: Present Avail objects
 *  * Structure type: Single value
 *  * Code: `return describeForDebugger();`
 *
 *
 *  1. Preferences...  Java  Debug  Logical Structures
 * Add:
 *
 *  * Qualified name: com.avail.interpreter.Interpreter
 *  * Description: Present Interpreter as stack frames
 *  * Structure type: Single value
 *  * Code: `return describeForDebugger();`
 *
 *
 *  1. Preferences...  Java  Debug  Logical Structures
 * Add:
 *
 *  * Qualified name: com.avail.descriptor.representation.AvailObjectFieldHelper
 *  * Description: Present helper's value's fields instead of the helper
 *  * Structure type: Single value
 *  * Code: `return value;`
 *
 *
 *  1. Preferences...  Java  Debug  Detail Formatters
 * Add:
 *
 *  * Qualified type name: com.avail.descriptor.representation.AvailObjectFieldHelper
 *  * Detail formatter code snippet: `return name();`
 *  * Enable this detail formatter: (checked)
 *  * (after OK) Show variable details: As the label for all variables
 *
 *
 *  1. In the Debug perspective, go to the Variables view.  Select the tool bar
 * icon whose hover help is Show Logical Structure.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class AvailObjectFieldHelper
/** Construct a new `AvailObjectFieldHelper`.
 *
 * @param parentObject
 * The object containing the value.
 * @param slot
 * The [slot][AbstractSlotsEnum] in which the value
 * occurs.
 * @param subscript
 * The optional subscript for a repeating slot.  Uses -1 to
 * indicate this is not a repeating slot.
 * @param value
 * The value found in that slot of the object.
 */(
	/**
	 * The object containing this field.
	 */
	private val parentObject: A_BasicObject?,
	/**
	 * The slot of the parent object in which the value occurs.
	 */
	val slot: AbstractSlotsEnum,
	/**
	 * This value's subscript within a repeated slot, or -1 if not repeated.
	 */
	val subscript: Int,
	/**
	 * The actual value being presented with the given label.
	 */
	val value: Any?) {

	/**
	 * The name to present for this field.
	 */
	private var name: String? = null

	/**
	 * Answer the string to display for this field.
	 *
	 * @return A [String].
	 */
	fun nameForDebugger(): String {
		var string = name
		if (string != null) {
			return string
		}
		string = privateComputeNameForDebugger()
		name = string
		return string
	}

	/**
	 * Produce a name for this helper in the debugger.
	 *
	 * @return A suitable [String] to present for this helper.
	 */
	private fun privateComputeNameForDebugger() = buildString {
		when {
			subscript != -1 -> {
				append(slot.fieldName(), 0, slot.fieldName().length - 1)
				append("[$subscript]")
			}
			else -> append(slot.fieldName())
		}
		when (value) {
			null -> append(" = Java null")
			is AvailObject -> append(' ').append(value.nameForDebugger())
			is AvailIntegerValueHelper -> {
				try {
					val strongSlot = Casts.cast<AbstractSlotsEnum, IntegerSlotsEnum>(slot)
					val bitFields = AbstractDescriptor.bitFieldsFor(strongSlot)
					if (bitFields.isNotEmpty()) {
						// Remove the name.
						delete(0, length)
					}
					AbstractDescriptor.describeIntegerSlot(
						parentObject as AvailObject,
						value.longValue,
						strongSlot,
						bitFields,
						this)
				} catch (e: RuntimeException) {
					append("PROBLEM DESCRIBING INTEGER FIELD:\n")
					append(StackPrinter.trace(e))
				}
			}
			is String -> append(" = Java String: $value")
			is Array<*> -> append(" = Multi-line text")
			else -> append(" = ${value.javaClass.canonicalName}")
		}
	}

	fun describeForDebugger(): Any? = when (value) {
		is AvailObject -> value.describeForDebugger()
		is AvailIntegerValueHelper -> arrayOfNulls<Any>(0)
		else -> value
	}
}