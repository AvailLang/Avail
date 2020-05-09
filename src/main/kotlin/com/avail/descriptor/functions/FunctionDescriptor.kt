/*
 * FunctionDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *    list of conditions and the following disclaimer in the documentation
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
package com.avail.descriptor.functions

import com.avail.annotations.AvailMethod
import com.avail.annotations.ThreadSafe
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.Descriptor
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.functions.FunctionDescriptor.ObjectSlots.CODE
import com.avail.descriptor.functions.FunctionDescriptor.ObjectSlots.OUTER_VAR_AT_
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.A_Phrase.Companion.generateInModule
import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
import com.avail.descriptor.phrases.BlockPhraseDescriptor.Companion.newBlockNode
import com.avail.descriptor.phrases.BlockPhraseDescriptor.Companion.recursivelyValidate
import com.avail.descriptor.phrases.PhraseDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.SetDescriptor.emptySet
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.emptyTuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.StringDescriptor.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.interpreter.levelOne.L1Decompiler.Companion.decompile
import com.avail.interpreter.levelOne.L1InstructionWriter
import com.avail.interpreter.levelOne.L1Operation
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.instanceMethod
import com.avail.optimizer.jvm.CheckedMethod.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*

/**
 * A function associates [compiled&#32;code][CompiledCodeDescriptor] with a
 * referencing environment that binds the code's free variables to variables
 * defined in an outer lexical scope. In this way, a function constitutes a
 * proper closure.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class FunctionDescriptor private constructor(
	mutability: Mutability
) : Descriptor(
	mutability, TypeTag.FUNCTION_TAG, ObjectSlots::class.java, null
) {
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/** The [compiled&#32;code][CompiledCodeDescriptor]. */
		CODE,

		/** The outer variables. */
		OUTER_VAR_AT_
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		aStream: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		var phrase = self.code().originatingPhrase()
		if (phrase.equalsNil()) {
			phrase = decompile(self.code())
		}
		phrase.printOnAvoidingIndent(aStream, recursionMap, indent + 1)
	}

	override fun o_Code(self: AvailObject) = self.slot(CODE)

	override fun o_Equals(self: AvailObject, another: A_BasicObject) =
		another.equalsFunction(self)

	override fun o_EqualsFunction(
		self: AvailObject,
		aFunction: A_Function
	): Boolean {
		when {
			!self.code().equals(aFunction.code()) -> return false
			(1..self.numOuterVars()).any {
				!self.outerVarAt(it).equals(aFunction.outerVarAt(it))
			} -> return false
			// They're equal, but occupy disjoint storage. If possible, then
			// replace one with an indirection to the other to reduce storage
			// costs and the frequency of detailed comparisons.
			!isShared -> self.becomeIndirectionTo(aFunction.makeImmutable())
			!aFunction.descriptor().isShared ->
				aFunction.becomeIndirectionTo(self.makeImmutable())
		}
		return true
	}

	override fun o_Hash(self: AvailObject): Int {
		// Answer a 32-bit hash value. If outer vars of mutable functions can
		// peel away when executed (last use of an outer var of a mutable
		// function can clobber that var and replace the OUTER_VAR_AT_ entry
		// with 0 or something), it's ok because nobody could know what the hash
		// value *used to be* for this function.

		// Make it immutable, in case the last reference to the object is being
		// added to a set, but subsequent execution might nil out a captured
		// value.
		self.makeImmutable()
		val code: A_RawFunction = self.slot(CODE)
		var hash = code.hash() xor 0x1386D4F6
		for (i in 1..self.numOuterVars())
		{
			hash *= multiplier
			hash += self.outerVarAt(i).hash() xor 0x3921A5F2
		}
		return hash
	}

	override fun o_IsFunction(self: AvailObject) = true

	/**
	 * Answer the object's type. Simply asks the [ ] for the [ ].
	 */
	override fun o_Kind(self: AvailObject): A_Type =
		self.slot(CODE).functionType()

	override fun o_NameForDebugger(self: AvailObject) =
		super.o_NameForDebugger(self) +
			" /* ${self.code().methodName().asNativeString()} */"

	/**
	 * Answer how many outer vars I've copied.
	 */
	override fun o_NumOuterVars(self: AvailObject) =
		self.variableObjectSlotsCount()

	override fun o_OptionallyNilOuterVar(
		self: AvailObject,
		index: Int
	): Boolean {
		if (isMutable) {
			self.setSlot(OUTER_VAR_AT_, index, nil)
			return true
		}
		return false
	}

	override fun o_OuterVarAt(
		self: AvailObject,
		subscript: Int
	) = self.slot(OUTER_VAR_AT_, subscript)

	override fun o_OuterVarAtPut(
		self: AvailObject,
		subscript: Int,
		value: AvailObject
	) = self.setSlot(OUTER_VAR_AT_, subscript, value)

	@AvailMethod
	@ThreadSafe
	override fun o_SerializerOperation(
		self: AvailObject
	) = when(self.numOuterVars()) {
		0 -> SerializerOperation.CLEAN_FUNCTION
		else -> SerializerOperation.GENERAL_FUNCTION
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("function")
		writer.write("function implementation")
		self.slot(CODE).writeSummaryTo(writer)
		writer.write("outers")
		writer.startArray()
		var i = 1
		val limit = self.variableObjectSlotsCount()
		while (i <= limit) {
			self.slot(OUTER_VAR_AT_, i).writeSummaryTo(writer)
			i++
		}
		writer.endArray()
		writer.endObject()
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) {
		writer.startObject()
		writer.write("kind")
		writer.write("function")
		writer.write("function implementation")
		self.slot(CODE).writeTo(writer)
		writer.write("outers")
		writer.startArray()
		var i = 1
		val limit = self.variableObjectSlotsCount()
		while (i <= limit) {
			self.slot(OUTER_VAR_AT_, i).writeSummaryTo(writer)
			i++
		}
		writer.endArray()
		writer.endObject()
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object {
		/** The [CheckedMethod] for [A_Function.code]. */
		@JvmField
		val functionCodeMethod: CheckedMethod = instanceMethod(
			A_Function::class.java,
			A_Function::code.name,
			A_RawFunction::class.java)

		/**
		 * Create a function that takes arguments of the specified types, then
		 * turns around and calls the function invocation method with the given
		 * function and the passed arguments assembled into a tuple.
		 *
		 * @param functionType
		 *   The type to which the resultant function should conform.
		 * @param function
		 *   The function which the new function should invoke when itself
		 *   invoked.
		 * @return
		 *   An appropriate function with the given signature.
		 */
		fun createStubWithSignature(
			functionType: A_Type,
			function: A_Function
		): A_Function {
			val argTypes = functionType.argsTupleType()
			val numArgs = argTypes.sizeRange().lowerBound().extractInt()
			val argTypesList = (1..numArgs).map { argTypes.typeAtIndex(it) }
			val functionReturnType = functionType.returnType()
			val code = with(L1InstructionWriter(nil, 0, nil)) {
				argumentTypes(*argTypesList.toTypedArray())
				returnType = functionReturnType
				write(
					0,
					L1Operation.L1_doPushLiteral,
					addLiteral(function))
				for (i in 1..numArgs) {
					write(0, L1Operation.L1_doPushLastLocal, i)
				}
				write(0, L1Operation.L1_doMakeTuple, numArgs)
				write(
					0,
					L1Operation.L1_doCall,
					addLiteral(SpecialMethodAtom.APPLY.bundle),
					addLiteral(functionReturnType))
				compiledCode()
			}
			val newFunction = createFunction(code, emptyTuple())
			newFunction.makeImmutable()
			return newFunction
		}

		/**
		 * Create a function that takes arguments of the specified types, then
		 * calls the [A_Method] for the [A_Bundle] of the given [A_Atom] with
		 * those arguments.
		 *
		 * @param functionType
		 *   The type to which the resultant function should conform.
		 * @param atom
		 *   The [A_Atom] which names the [A_Method] to be invoked by the new
		 *   function.
		 * @return
		 *   An appropriate function.
		 * @throws IllegalArgumentException
		 *   If the atom has no associated bundle/method, or the function
		 *   signature is inconsistent with the available method definitions.
		 */
		fun createStubToCallMethod(
			functionType: A_Type,
			atom: A_Atom
		): A_Function {
			val bundle: A_Bundle = atom.bundleOrNil()
			require(!bundle.equalsNil()) { "Atom to invoke has no method" }
			val method: A_Method = bundle.bundleMethod()
			val argTypes = functionType.argsTupleType()
			// Check that there's a definition, even abstract, that will catch all
			// invocations for the given function type's argument types.
			val ok = method.definitionsTuple().any { definition: AvailObject ->
				!definition.isMacroDefinition()
					&& definition.bodySignature().isSubtypeOf(functionType)
			}
			require(ok) {
				("Function signature is not strong enough to call method "
					+ "safely")
			}
			val numArgs = argTypes.sizeRange().lowerBound().extractInt()
			val argTypesList = (1..numArgs).map { argTypes.typeAtIndex(it) }
			val functionReturnType = functionType.returnType()
			return with(L1InstructionWriter(nil, 0, nil)) {
				argumentTypes(*argTypesList.toTypedArray())
				returnType = functionReturnType
				for (i in 1..numArgs) {
					write(0, L1Operation.L1_doPushLastLocal, i)
				}
				write(
					0,
					L1Operation.L1_doCall,
					addLiteral(bundle),
					addLiteral(functionReturnType))
				createFunction(compiledCode(), emptyTuple()).makeImmutable()
			}
		}

		/**
		 * Construct a function with the given code and tuple of copied
		 * variables.
		 *
		 * @param code
		 *   The code with which to build the function.
		 * @param copiedTuple
		 *   The outer variables and constants to enclose.
		 * @return
		 *   A function.
		 */
		@JvmStatic
		fun createFunction(
			code: A_BasicObject,
			copiedTuple: A_Tuple
		): A_Function {
			val copiedSize = copiedTuple.tupleSize()
			return mutable.create(copiedSize).apply {
				setSlot(CODE, code)
				if (copiedSize > 0) {
					setSlotsFromTuple(
						OUTER_VAR_AT_, 1, copiedTuple, 1, copiedSize)
				}
			}
		}

		/**
		 * Construct a function with the given code and room for the given
		 * number of outer variables.  Do not initialize any outer variable
		 * slots.
		 *
		 * @param code
		 *   The code with which to build the function.
		 * @param outersCount
		 *   The number of outer variables that will be enclosed.
		 * @return
		 *   A function without its outer variables initialized.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createExceptOuters(
			code: A_RawFunction,
			outersCount: Int
		): AvailObject =
			mutable.create(outersCount).apply { setSlot(CODE, code) }

		/**
		 * Access the [createExceptOuters] method.
		 */
		@JvmField
		val createExceptOutersMethod: CheckedMethod = staticMethod(
			FunctionDescriptor::class.java,
			::createExceptOuters.name,
			AvailObject::class.java,
			A_RawFunction::class.java,
			Int::class.javaPrimitiveType)

		/**
		 * Construct a function with the given code and one outer variable.
		 *
		 * @param code
		 *   The code with which to build the function.
		 * @param outer1
		 *   The sole outer variable that will be enclosed.
		 * @return
		 *   A function with its outer variable initialized.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createWithOuters1(
			code: A_RawFunction,
			outer1: AvailObject
		): AvailObject = createExceptOuters(code, 1).apply {
			setSlot(OUTER_VAR_AT_, 1, outer1)
		}

		/**
		 * Access the [createWithOuters1] method.
		 */
		@JvmField
		val createWithOuters1Method: CheckedMethod = staticMethod(
			FunctionDescriptor::class.java,
			::createWithOuters1.name,
			AvailObject::class.java,
			A_RawFunction::class.java,
			AvailObject::class.java)

		/**
		 * Construct a function with the given code and two outer variables.
		 *
		 * @param code
		 *   The code with which to build the function.
		 * @param outer1
		 *   The first outer variable that will be enclosed.
		 * @param outer2
		 *   The second outer variable that will be enclosed.
		 * @return
		 *   A function with its outer variables initialized.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createWithOuters2(
			code: A_RawFunction,
			outer1: AvailObject,
			outer2: AvailObject
		): AvailObject = createExceptOuters(code, 2).apply {
			setSlot(OUTER_VAR_AT_, 1, outer1)
			setSlot(OUTER_VAR_AT_, 2, outer2)
		}

		/**
		 * Access the [createWithOuters2] method.
		 */
		@JvmField
		val createWithOuters2Method: CheckedMethod = staticMethod(
			FunctionDescriptor::class.java,
			::createWithOuters2.name,
			AvailObject::class.java,
			A_RawFunction::class.java,
			AvailObject::class.java,
			AvailObject::class.java)

		/**
		 * Construct a function with the given code and three outer variables.
		 *
		 * @param code
		 *   The code with which to build the function.
		 * @param outer1
		 *   The first outer variable that will be enclosed.
		 * @param outer2
		 *   The second outer variable that will be enclosed.
		 * @param outer3
		 *   The third outer variable that will be enclosed.
		 * @return
		 *   A function with its outer variables initialized.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createWithOuters3(
			code: A_RawFunction,
			outer1: AvailObject,
			outer2: AvailObject,
			outer3: AvailObject
		): AvailObject = createExceptOuters(code, 3).apply {
			setSlot(OUTER_VAR_AT_, 1, outer1)
			setSlot(OUTER_VAR_AT_, 2, outer2)
			setSlot(OUTER_VAR_AT_, 3, outer3)
		}

		/**
		 * Access the [createWithOuters3] method.
		 */
		@JvmField
		val createWithOuters3Method: CheckedMethod = staticMethod(
			FunctionDescriptor::class.java,
			::createWithOuters3.name,
			AvailObject::class.java,
			A_RawFunction::class.java,
			AvailObject::class.java,
			AvailObject::class.java,
			AvailObject::class.java)

		/** Access the [A_Function.outerVarAt] method. */
		@JvmField
		val outerVarAtMethod: CheckedMethod = instanceMethod(
			A_Function::class.java,
			A_Function::outerVarAt.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType)

		/** Access the [A_Function.outerVarAtPut] method. */
		@JvmField
		val outerVarAtPutMethod: CheckedMethod = instanceMethod(
			A_Function::class.java,
			A_Function::outerVarAtPut.name,
			Void.TYPE,
			Int::class.javaPrimitiveType,
			AvailObject::class.java)

		/**
		 * Convert a [phrase][PhraseDescriptor] into a zero-argument
		 * [A_Function].
		 *
		 * @param phrase
		 *   The phrase to compile to a function.
		 * @param module
		 *   The [module][ModuleDescriptor] that is the context for the phrase
		 *   and function, or [nil] if there is no context.
		 * @param lineNumber
		 *   The line number to attach to the new function, or `0` if no
		 *   meaningful line number is available.
		 * @return
		 *   A zero-argument function.
		 */
		fun createFunctionForPhrase(
			phrase: A_Phrase,
			module: A_Module,
			lineNumber: Int
		): A_Function {
			val block: A_Phrase = newBlockNode(
				emptyTuple(),
				0,
				tuple(phrase),
				Types.TOP.o(),
				emptySet(),
				lineNumber,
				phrase.tokens())
			recursivelyValidate(block)
			val compiledBlock = block.generateInModule(module)
			assert(compiledBlock.numOuters() == 0)
			return createFunction(compiledBlock, emptyTuple()).makeImmutable()
		}

		/**
		 * Construct a bootstrap [A_Function] that crashes when invoked.
		 *
		 * @param messageString
		 *   The message string to prepend to the list of arguments, indicating
		 *   the basic nature of the failure.
		 * @param paramTypes
		 *   The [tuple][TupleDescriptor] of parameter [types][A_Type].
		 * @return
		 *   The requested crash function.
		 *
		 * @see SpecialMethodAtom.CRASH
		 */
		@JvmStatic
		fun newCrashFunction(
			messageString: String,
			paramTypes: A_Tuple
		): A_Function = with(L1InstructionWriter(nil, 0, nil)) {
			argumentTypesTuple(paramTypes)
			returnType = bottom()
			write(
				0,
				L1Operation.L1_doPushLiteral,
				addLiteral(stringFrom(messageString)))
			val numArgs = paramTypes.tupleSize()
			for (i in 1..numArgs) {
				write(0, L1Operation.L1_doPushLastLocal, i)
			}
			// Put the error message and arguments into a tuple.
			write(0, L1Operation.L1_doMakeTuple, numArgs + 1)
			write(
				0,
				L1Operation.L1_doCall,
				addLiteral(SpecialMethodAtom.CRASH.bundle),
				addLiteral(bottom()))
			val code: A_RawFunction = compiledCode()
			code.setMethodName(
				stringFrom("VM crash function: $messageString"))
			return createFunction(code, emptyTuple()).makeShared()
		}

		/** The mutable [FunctionDescriptor]. */
		val mutable = FunctionDescriptor(Mutability.MUTABLE)

		/** The immutable [FunctionDescriptor]. */
		private val immutable = FunctionDescriptor(Mutability.IMMUTABLE)

		/** The shared [FunctionDescriptor]. */
		private val shared = FunctionDescriptor(Mutability.SHARED)
	}
}