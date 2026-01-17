/*
 * MethodDefinitionDescriptor.kt
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
import avail.descriptor.atoms.A_Atom.Companion.issuingModule
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.methods.A_Method.Companion.bundles
import avail.descriptor.methods.A_Sendable.Companion.bodyBlock
import avail.descriptor.methods.A_Sendable.Companion.definitionModuleName
import avail.descriptor.methods.MethodDefinitionDescriptor.ObjectSlots.BODY_BLOCK
import avail.descriptor.methods.MethodDefinitionDescriptor.ObjectSlots.DEFINITION_METHOD
import avail.descriptor.methods.MethodDefinitionDescriptor.ObjectSlots.MODULE
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.allAncestors
import avail.descriptor.module.A_Module.Companion.shortModuleNameNative
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine2
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.types.A_Type
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.METHOD_DEFINITION
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * An object instance of `MethodDefinitionDescriptor` represents a function in
 * the collection of available functions for this method hierarchy.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class MethodDefinitionDescriptor private constructor(
	mutability: Mutability
) : DefinitionDescriptor(mutability, ObjectSlots::class.java, null) {
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * Duplicated from parent.  The method in which this definition occurs.
		 */
		@HideFieldJustForPrinting
		DEFINITION_METHOD,

		/**
		 * The [module][ModuleDescriptor] in which this definition occurs.
		 */
		@HideFieldJustForPrinting
		MODULE,

		/**
		 * The [function][FunctionDescriptor] to invoke when this
		 * message is sent with applicable arguments.
		 */
		BODY_BLOCK;

		companion object {
			init {
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
		recursionMap: IdentityHashMap<A_BasicObject, Unit>,
		indent: Int)
	{
		printObjectHeaderOn(builder)
		with(builder)
		{
			append('(')
			self[DEFINITION_METHOD].bundles
				.sortedBy {
					when (val module = it.message.issuingModule)
					{
						nil -> Int.MAX_VALUE
						else -> module.allAncestors.setSize
					}
				}
				.joinTo(this, " a.k.a. ") { it.message.toString() }
			val code: A_RawFunction = self[BODY_BLOCK].code()
			val module = code.module
			if (module.notNil)
			{
				append(", ")
				append(module.shortModuleNameNative)
				append(':')
				append(code.codeStartingLineNumber)
			}
			append(": ")
			append(code.functionType())
			append(")")
		}
	}

	override fun o_BodySignature(self: AvailObject): A_Type =
		self.bodyBlock().kind()

	override fun o_BodyBlock(self: AvailObject): A_Function =
		self[BODY_BLOCK]

	override fun o_Hash(self: AvailObject) =
		combine2(self.bodyBlock().hash(), 0x70B2B1A9)

	override fun o_Kind(self: AvailObject): A_Type {
		return METHOD_DEFINITION()
	}

	override fun o_IsMethodDefinition(self: AvailObject) = true

	override fun o_SerializerOperation(self: AvailObject) =
		SerializerOperation.METHOD_DEFINITION

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("method definition") }
			at("definition method") {
				self[DEFINITION_METHOD].methodName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body block") { self[BODY_BLOCK].writeTo(writer) }
		}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) =
		writer.writeObject {
			at("kind") { write("method definition") }
			at("definition method") {
				self[DEFINITION_METHOD].methodName.writeTo(writer)
			}
			at("definition module") {
				self.definitionModuleName().writeTo(writer)
			}
			at("body block") { self[BODY_BLOCK].writeSummaryTo(writer) }
		}

	override fun mutable() = mutable

	override fun immutable() = shared

	override fun shared() = shared

	companion object {
		/**
		 * Create a new method signature from the provided arguments.
		 *
		 * @param definitionMethod
		 *   The [method][MethodDescriptor] for which to create a new method
		 *   definition.
		 * @param definitionModule
		 *   The module in which this definition is added.
		 * @param bodyBlock
		 *   The body of the signature.  This will be invoked when the message
		 *   is sent, assuming the argument types match and there is no more
		 *   specific version.
		 * @return
		 *   A method signature.
		 */
		fun newMethodDefinition(
			definitionMethod: A_Method,
			definitionModule: A_Module,
			bodyBlock: A_Function
		): A_Definition = mutable.createShared {
			setSlot(DEFINITION_METHOD, definitionMethod)
			setSlot(MODULE, definitionModule)
			setSlot(BODY_BLOCK, bodyBlock)
		}

		/** The mutable [MethodDefinitionDescriptor]. */
		private val mutable = MethodDefinitionDescriptor(Mutability.MUTABLE)

		/** The shared [MethodDefinitionDescriptor]. */
		private val shared = MethodDefinitionDescriptor(Mutability.SHARED)
	}
}
