/*
 * ForwardDefinitionDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.methods

import avail.annotations.HideFieldJustForPrinting
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.methods.A_Method.Companion.chooseBundle
import avail.descriptor.methods.A_Sendable.Companion.definitionModuleName
import avail.descriptor.methods.ForwardDefinitionDescriptor.ObjectSlots.BODY_SIGNATURE
import avail.descriptor.methods.ForwardDefinitionDescriptor.ObjectSlots.DEFINITION_METHOD
import avail.descriptor.methods.ForwardDefinitionDescriptor.ObjectSlots.MODULE
import avail.descriptor.module.A_Module
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine3
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.FORWARD_DEFINITION
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * This is a forward declaration of a method.  An actual method must be defined
 * with the same signature before the end of the current module.
 *
 * While a call with this method signature can be compiled after the forward
 * declaration, an attempt to actually call the method will result in an error
 * indicating this problem.
 *
 * Because of the nature of forward declarations, it is meaningless to forward
 * declare a macro, so this facility is not provided.  It's meaningless because
 * a "call-site" for a macro causes the body to execute immediately.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ForwardDefinitionDescriptor private constructor(
	mutability: Mutability
) : DefinitionDescriptor(
	mutability, ObjectSlots::class.java, null)
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
			}
		}
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) = builder.brief {
		self[DEFINITION_METHOD]
			.chooseBundle(self[MODULE])
			.message
			.printOnAvoidingIndent(this, recursionMap, indent)
		append(' ')
		self[BODY_SIGNATURE].printOnAvoidingIndent(
			this, recursionMap, indent + 1)
	}

	override fun o_BodySignature(self: AvailObject): A_Type =
		self[BODY_SIGNATURE]

	override fun o_Hash(self: AvailObject) = combine3(
		self[BODY_SIGNATURE].hash(),
		self[DEFINITION_METHOD].hash(),
		0x17d95098)

	override fun o_Kind(self: AvailObject): A_Type =
		FORWARD_DEFINITION.o

	override fun o_IsForwardDefinition(self: AvailObject) = true

	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.FORWARD_DEFINITION

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("forward definition") }
			at("definition method") {
				self[DEFINITION_METHOD].methodName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body signature") { self[BODY_SIGNATURE].writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("forward definition") }
			at("definition method") {
				self[DEFINITION_METHOD].methodName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body signature") {
				self[BODY_SIGNATURE].writeSummaryTo(writer)
			}
		}

	override fun mutable() = mutable

	// There is no immutable variant.
	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Create a forward declaration signature for the given
		 * [method][MethodDescriptor] and
		 * [function&#32;type][FunctionTypeDescriptor].
		 *
		 * @param definitionMethod
		 *   The method for which to declare a forward definition.
		 * @param definitionModule
		 *   The module in which this definition is added.
		 * @param bodySignature
		 *   The function type at which this forward definition should occur.
		 * @return
		 *   The new forward declaration signature.
		 */
		fun newForwardDefinition(
			definitionMethod: A_BasicObject,
			definitionModule: A_Module,
			bodySignature: A_Type
		): A_Definition = mutable.createShared {
			setSlot(DEFINITION_METHOD, definitionMethod)
			setSlot(MODULE, definitionModule)
			setSlot(BODY_SIGNATURE, bodySignature)
		}

		/** The mutable [ForwardDefinitionDescriptor]. */
		private val mutable = ForwardDefinitionDescriptor(Mutability.MUTABLE)

		/** The shared [ForwardDefinitionDescriptor]. */
		private val shared = ForwardDefinitionDescriptor(Mutability.SHARED)
	}
}
