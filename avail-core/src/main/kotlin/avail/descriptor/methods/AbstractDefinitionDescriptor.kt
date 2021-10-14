/*
 * AbstractDefinitionDescriptor.kt
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
package avail.descriptor.methods

import avail.annotations.HideFieldJustForPrinting
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.methods.A_Sendable.Companion.definitionModuleName
import avail.descriptor.methods.AbstractDefinitionDescriptor.ObjectSlots.BODY_SIGNATURE
import avail.descriptor.methods.AbstractDefinitionDescriptor.ObjectSlots.DEFINITION_METHOD
import avail.descriptor.methods.AbstractDefinitionDescriptor.ObjectSlots.MODULE
import avail.descriptor.methods.AbstractDefinitionDescriptor.ObjectSlots.STYLERS
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ABSTRACT_DEFINITION
import avail.serialization.SerializerOperation
import avail.utility.json.JSONWriter

/**
 * This is a specialization of [DefinitionDescriptor] that is an abstract
 * declaration of an Avail method (i.e., no implementation).
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class AbstractDefinitionDescriptor private constructor(
	mutability: Mutability
) : DefinitionDescriptor(mutability, ObjectSlots::class.java, null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * Duplicated from parent.  The method in which this definition occurs.
		 */
		@HideFieldJustForPrinting
		DEFINITION_METHOD,

		/** The [module][ModuleDescriptor] in which this definition occurs. */
		@HideFieldJustForPrinting
		MODULE,

		/**
		 * The [A_Set] of [A_Styler]s that have been added to this definition.
		 */
		@HideFieldJustForPrinting
		STYLERS,

		/**
		 * The [function&#32;type][FunctionTypeDescriptor] for which this
		 * signature is being specified.
		 */
		BODY_SIGNATURE;

		companion object
		{
			init
			{
				assert(
					DefinitionDescriptor.ObjectSlots.DEFINITION_METHOD.ordinal
						== DEFINITION_METHOD.ordinal)
				assert(
					DefinitionDescriptor.ObjectSlots.MODULE.ordinal
						== MODULE.ordinal)
				assert(
					DefinitionDescriptor.ObjectSlots.STYLERS.ordinal
						== STYLERS.ordinal)
			}
		}
	}

	override fun o_BodySignature(self: AvailObject): A_Type =
		self.slot(BODY_SIGNATURE)

	override fun o_Hash(self: AvailObject) =
		combine2(self.slot(BODY_SIGNATURE).hash(), 0x201FE782)

	override fun o_Kind(self: AvailObject): AvailObject =
		ABSTRACT_DEFINITION.o

	override fun o_IsAbstractDefinition(self: AvailObject) = true

	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.ABSTRACT_DEFINITION

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("abstract definition") }
			at("definition method") {
				self.slot(DEFINITION_METHOD).methodName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body signature") {
				self.slot(BODY_SIGNATURE).writeTo(writer)
			}
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("abstract definition") }
			at("definition method") {
				self.slot(DEFINITION_METHOD).methodName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body signature") {
				self.slot(BODY_SIGNATURE).writeSummaryTo(writer)
			}
		}

	override fun mutable() = mutable

	// There is no immutable variant.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Create a new abstract method signature from the provided arguments.
		 *
		 * @param definitionMethod
		 *   The [method][MethodDescriptor] for which this definition occurs.
		 * @param definitionModule
		 *   The module in which this definition is added.
		 * @param bodySignature
		 *   The function type at which this abstract method signature will be
		 *   stored in the hierarchy of multimethods.
		 * @return
		 *   An abstract method signature.
		 */
		fun newAbstractDefinition(
			definitionMethod: A_Method,
			definitionModule: A_Module,
			bodySignature: A_Type
		): A_Definition = mutable.createShared {
			setSlot(DEFINITION_METHOD, definitionMethod)
			setSlot(MODULE, definitionModule)
			setSlot(STYLERS, emptySet)
			setSlot(BODY_SIGNATURE, bodySignature)
		}

		/** The mutable [AbstractDefinitionDescriptor]. */
		private val mutable = AbstractDefinitionDescriptor(Mutability.MUTABLE)

		/** The shared [AbstractDefinitionDescriptor]. */
		private val shared = AbstractDefinitionDescriptor(Mutability.SHARED)
	}
}
