/*
 * OptimizerTestHelper.kt
 * Copyright © 1993-2025, The Avail Foundation, LLC.
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

package avail.test.optimizer

import avail.AvailRuntime
import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.compiler.splitter.MessageSplitter
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.atoms.A_Atom.Companion.setAtomBundle
import avail.descriptor.atoms.A_Atom.Companion.setAtomProperty
import avail.descriptor.atoms.AtomDescriptor.Companion.createAtom
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.MessageBundleDescriptor.Companion.newBundle
import avail.descriptor.fiber.A_Fiber.Companion.setSuccessAndFailure
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.Companion.createFiber
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.literalAt
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numLiterals
import avail.descriptor.functions.A_RawFunction.Companion.numOuters
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.functions.PrimitiveCompiledCodeDescriptor.Companion.newPrimitiveRawFunction
import avail.descriptor.maps.A_Map.Companion.forEachInMap
import avail.descriptor.maps.MapDescriptor.Companion.mapWithBindings
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Method.Companion.lookupByTypesFromTuple
import avail.descriptor.methods.A_Method.Companion.methodAddDefinition
import avail.descriptor.methods.A_Sendable.Companion.bodySignature
import avail.descriptor.methods.AbstractDefinitionDescriptor.Companion.newAbstractDefinition
import avail.descriptor.methods.MethodDefinitionDescriptor.Companion.newMethodDefinition
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.objectTypeFromMap
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.setNameForType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.fieldTypeMap
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelOne.L1Operation.L1_doCall
import avail.interpreter.levelOne.L1Operation.L1_doClose
import avail.interpreter.levelOne.L1Operation.L1_doPushLiteral
import avail.interpreter.primitive.Primitive
import avail.optimizer.L2Optimizer
import java.io.StringReader
import java.util.concurrent.SynchronousQueue

/**
 * A helper class for setting up and running tests of features of the
 * [L2Optimizer].
 */
class OptimizerTestHelper(
	val testName: String)
{
	/**
	 * A simple [L1InstructionWriter] variant that allows instructions to be
	 * added via [L1Operation] function calls.
	 */
	inner class SimpleWriter : L1InstructionWriter(nil, 0, nil)
	{
		/**
		 * Names of arguments, locals, constants, and outers, in that order,
		 * that can be optionally set once.
		 */
		var forcedNames: MutableList<String>? = null
			private set

		/**
		 * When a [SimpleWriter] is a receiver, allow an [L1Operation] to be
		 * used as a function to write an instruction with the supplied
		 * operands.  Use 0 for the line number.
		 *
		 * @param operands
		 *   The operands to the instruction.
		 */
		operator fun L1Operation.invoke(vararg operands: Int)
		{
			write(0, this, *operands)
		}

		fun bundle(name: String): A_Bundle = lookup(name)

		fun pushLiteral(value: A_BasicObject)
		{
			L1_doPushLiteral(addLiteral(value))
		}

		/**
		 * Create a dummy function with the specified outers, a vararg [Array]
		 * of [Pair]s from outer name [String] to outer type [A_Type].  The
		 * outers are not actually used by the function, but are captured to
		 * ensure the regression tests being optimized reproduce the problematic
		 * behavior accurately.  The raw function should not actually be
		 * invoked.
		 *
		 * Emit an [L1_doClose] instruction to close it, consuming the requested
		 * number of previously pushed outers.
		 *
		 * @param arguments
		 *   The [List] of argument names ([String]) and [A_Type]s for the new
		 *   raw function.
		 * @param outers
		 *   The [List] of [Pair]s from outer name ([String]) to outer [A_Type].
		 * @param returnType
		 *   The return type of the new raw function.
		 */
		fun close(
			arguments: List<Pair<String, A_Type>> = emptyList(),
			outers: List<Pair<String, A_Type>> = emptyList(),
			returnType: A_Type)
		{
			val rawFunction = createDummyRawFunction(
				arguments, outers, returnType)
			L1_doClose(outers.size, addLiteral(rawFunction))
		}

		fun call(bundleName: String, returnType: A_Type)
		{
			L1_doCall(
				addLiteral(bundle(bundleName)),
				addLiteral(returnType))
		}

		/**
		 * Call a dummy method synthesized for just this call site.
		 */
		fun callNewDummy(
			bundleName: String,
			argumentTypes: List<A_Type>,
			returnType: A_Type)
		{
			defineMethod(bundleName, returnType) {
				argumentTypes(*argumentTypes.toTypedArray())
				pushLiteral(stringFrom("$bundleName is a stub"))
				call("Crash:_", bottom)
			}
			L1_doCall(
				addLiteral(bundle(bundleName)),
				addLiteral(returnType))
		}

		/**
		 * Used to set up the names of arguments, locals, constants, and outers,
		 * in that order.  Each call adds one name and returns its one-based
		 * index.
		 *
		 * @param string
		 *   The name to add.
		 * @return
		 *   The index of the new name.
		 */
		fun declareName(string: String) : Int
		{
			if (forcedNames == null)
				forcedNames = mutableListOf()
			forcedNames!!.add(string)
			return forcedNames!!.size
		}
	}

	/**
	 * The [AvailRuntime] in which the tests will execute.  Note that no actual
	 * source code is loaded, so the runtime is only suitable for testing the
	 * optimizer.
	 */
	val runtime = run {
		val roots = ModuleRoots.moduleRootsForTest()
		val resolver = RenamesFileParser(StringReader(""), roots).parse()
		AvailRuntime(resolver, roots.fileManager)
	}

	/**
	 * The [Primitive]s that have been defined in this runtime, structured as
	 * a map from Kotlin [String] to the corresponding [A_Bundle].
	 */
	private val allBundles = mutableMapOf<String, A_Bundle>()

	/**
	 * The method definitions that should not be pre-looked up before performing
	 * test optimizaations.
	 */
	private val noLookupDefinitions = mutableSetOf<A_Definition>()

	/**
	 * Look up the [A_Bundle] with the specified name, creating it if it does
	 * not already exist.
	 *
	 * @param name
	 *   The name of the desired bundle.
	 * @return
	 *   The corresponding [A_Bundle].
	 */
	fun lookupOrCreate(name: String): A_Bundle =
		allBundles.computeIfAbsent(name) {
			createAtom(stringFrom(name), nil).bundleOrCreate()
		}

	/**
	 * Given the name of an existing bundle, create an alias with the specified
	 * name.
	 *
	 * @param originalName
	 *  The name of the existing bundle.
	 * @param aliasName
	 *  The name of the alias to create.
	 */
	fun addAlias(originalName: String, aliasName: String)
	{
		val originalBundle = lookup(originalName)
		val aliasString = stringFrom(aliasName)
		val aliasAtom = createAtom(aliasString, nil)
		val aliasBundle = newBundle(
			aliasAtom,
			originalBundle.bundleMethod,
			MessageSplitter.split(aliasString))
		aliasAtom.setAtomBundle(aliasBundle)
		allBundles[aliasName] = aliasBundle
	}

	/**
	 * Look up the [A_Bundle] with the specified name, which must have been
	 * defined via [definePrimitives].
	 *
	 * @param name
	 *   The name of the desired bundle.
	 * @return
	 *   The corresponding [A_Bundle].
	 */
	fun lookup(name: String): A_Bundle = allBundles.getValue(name)

	/**
	 * Define the specified primitives in the runtime, associating each with a
	 * freshly created method.  If multiple primitives are specified with the
	 * same name, they will share the same method.
	 *
	 * @param namedPrimitives
	 *   A vararg list of [Pair]s, each being a method name [String], and the
	 *   [Primitive] for which to synthesize a definition with that name.
	 */
	fun definePrimitives(vararg namedPrimitives: Pair<String, Primitive>)
	{
		namedPrimitives.forEach { (name, prim) ->
			val method = lookupOrCreate(name).bundleMethod
			val code = newPrimitiveRawFunction(prim, nil, 0)
			code.methodName = stringFrom(name)
			val function = createFunction(code, emptyTuple)
			val definition = newMethodDefinition(method, nil, function)
			method.methodAddDefinition(definition)
		}
	}

	/**
	 * Create a [A_RawFunction] whose body is generated by the given lambda,
	 * which is applied to an implicit [SimpleWriter].
	 *
	 * @param returnType
	 *   The return type of the resulting [A_RawFunction].
	 * @param addInstructions
	 *   A lambda that takes an [SimpleWriter] receiver, and uses it to add
	 *   instructions.
	 * @return
	 *   The assembled [A_RawFunction].
	 */
	private fun createRawFunction(
		returnType: A_Type,
		addInstructions: SimpleWriter.()->Unit
	): A_RawFunction
	{
		val writer = SimpleWriter()
		writer.returnType = returnType
		writer.returnTypeIfPrimitiveFails = returnType
		writer.addInstructions()
		return writer.compiledCode(writer.forcedNames)
	}

	/**
	 * Create a [A_RawFunction] via [createRawFunction], then name it based on
	 * the helper's captured test name.
	 *
	 * @param returnType
	 *   The return type of the resulting [A_RawFunction].
	 * @param addInstructions
	 *   A lambda that takes an [SimpleWriter] receiver, and uses it to add
	 *   instructions.
	 * @return
	 *   The assembled [A_RawFunction].
	 */
	fun rawFunction(
		returnType: A_Type,
		addInstructions: SimpleWriter.()->Unit
	): A_RawFunction = createRawFunction(returnType, addInstructions)
		.apply { methodName = stringFrom(testName) }

	/**
	 * Create a dummy function with the specified outers, a vararg [Array] of
	 * [Pair]s from outer name [String] to outer type [A_Type].  The outers are
	 * not actually used by the function, but are captured to ensure the
	 * regression tests being optimized reproduce the problematic behavior
	 * accurately.  The raw function should not actually be invoked.
	 *
	 * @param arguments
	 *   The [List] of argument names ([String]) and [A_Type]s for the new raw
	 *   function.
	 * @param outers
	 *   The [List] of [Pair]s from outer name ([String]) to outer [A_Type].
	 * @param returnType
	 *   The return type of the new raw function.
	 * @return
	 *   The [A_RawFunction] which, if it were invoked, would call "Crash:_".
	 */
	fun createDummyRawFunction(
		arguments: List<Pair<String, A_Type>> = emptyList(),
		outers: List<Pair<String, A_Type>> = emptyList(),
		returnType: A_Type
	): A_RawFunction = createRawFunction(returnType) {
		argumentTypes(*arguments.map(Pair<*, A_Type>::second).toTypedArray())
		arguments.forEach { declareName(it.first) }
		outers.forEach { (name, type) ->
			createOuter(type)
			declareName(name)
		}
		// Stubbed for simplicity, since we won't run it.
		// Make sure this function isn't itself marked as a
		// primitive, to avoid inlining.
		pushLiteral(stringFrom("stub function – don't actually invoke"))
		call("Crash:_", bottom)
	}

	/**
	 * Define an abstract method definition with the specified name, argument
	 * types, and return type.
	 *
	 * @param name
	 *   The name of the method to define.
	 * @param argumentTypes
	 *   The [List] of argument [A_Type]s.
	 * @param returnType
	 *   The return [A_Type] for the abstract method definition.
	 * @param suppressLookup
	 *   If true, do not pre-fetch the method definition from the lookup tree.
	 *   This is useful for reproducing problems that only show up when some
	 *   paths in an inlined dispatch have to fall back to a slow lookup.
	 */
	fun defineAbstractMethod(
		name: String,
		argumentTypes: List<A_Type>,
		returnType: A_Type,
		suppressLookup: Boolean = false)
	{
		val method = lookupOrCreate(name).bundleMethod
		val abstractDefinition = newAbstractDefinition(
			method,
			nil,
			functionType(tupleFromList(argumentTypes), returnType))
		if (suppressLookup) noLookupDefinitions += abstractDefinition
		method.methodAddDefinition(abstractDefinition)
	}

	/**
	 * Define a method with the specified name, and return type, using the given
	 * [SimpleWriter] lambda to generate the body of the method.
	 *
	 * @param name
	 *   The name of the method to define.
	 * @param returnType
	 *   The return type of the method.
	 * @param suppressLookup
	 *   If true, do not pre-fetch the method definition from the lookup tree.
	 *   This is useful for reproducing problems that only show up when some
	 *   paths in an inlined dispatch have to fall back to a slow lookup.
	 * @param functionBuilder
	 *   A lambda that takes an [SimpleWriter] receiver, and uses it to add
	 *   instructions to the method body.
	 */
	fun defineMethod(
		name: String,
		returnType: A_Type,
		suppressLookup: Boolean = false,
		functionBuilder: SimpleWriter.()->Unit)
	{
		val method = lookupOrCreate(name).bundleMethod
		val rawFunction = createRawFunction(returnType, functionBuilder)
		rawFunction.methodName = stringFrom(name)
		val function = createFunction(rawFunction, emptyTuple)
		val definition = newMethodDefinition(method, nil, function)
		if (suppressLookup) noLookupDefinitions += definition
		method.methodAddDefinition(definition)
	}

	/**
	 * Execute the given [A_RawFunction] in a fresh [fiber][FiberDescriptor],
	 * and return the resulting [AvailObject].  Rethrows an exception if one
	 * is thrown and captured by the fiber's handler while executing.
	 *
	 * @param rawFunction
	 *   The [A_RawFunction] to execute.  It must take no arguments and have no
	 *   outers.
	 * @return
	 *   The result of executing the function.
	 * @throws InterruptedException
	 *   If the current thread is interrupted while waiting for the function to
	 *   complete.
	 */
	fun executeRawFunction(rawFunction: A_RawFunction): AvailObject
	{
		assert(rawFunction.numOuters == 0)
		assert(rawFunction.numArgs() == 0)
		val fiber = createFiber(
			TOP(),
			runtime,
			null,
			runtime.textInterface(),
			FiberDescriptor.commandPriority)
		{
			stringFrom("Executing function for executeRawFunction().")
		}
		val queue = SynchronousQueue<AvailObject>()
		var error: Throwable? = null
		fiber.setSuccessAndFailure(
			onSuccess = queue::put,
			onFailure = {
				error = it
				queue.put(nil)
			})
		val function = createFunction(rawFunction, emptyTuple)
		runtime.runOutermostFunction(fiber, function, emptyList(), false)
		val result = queue.take()
		// If there was an error, rethrow it now, allowing the test framework to
		// report it.
		error?.let { throw it }
		return result
	}

	/**
	 * Optimize the specified [A_RawFunction] in place.
	 *
	 * @param rawFunction
	 *   The [A_RawFunction] to optimize.
	 */
	fun testOptimize(rawFunction: A_RawFunction)
	{
		// Expand the lookup trees for any referenced bundles.
		(1..rawFunction.numLiterals).forEach {
			val literal = rawFunction.literalAt(it)
			if (literal.isInstanceOfKind(Types.MESSAGE_BUNDLE()))
			{
				val method = literal.bundleMethod
				method.definitionsTuple.forEach { def: A_Definition ->
					// Don't force lookups if the definition said it shouldn't
					// warm up that part of the lookup tree.
					if (def in noLookupDefinitions) return@forEach
					val signature = def.bodySignature()
					val argumentsType = signature.argsTupleType
					val argumentTypes = argumentsType.tupleOfTypesFromTo(
						1, argumentsType.sizeRange.upperBound.extractInt)
					// Force a lookup to populate the tree.  This won't be quite
					// right for some types of [DecisionStep]s, since there may
					// be a different subtrees for type-based rather than
					// value-based lookups, but it should be close enough for
					// now.  If a particular test requires more precision, it
					// can manually look up method definitions by example
					// values.
					method.lookupByTypesFromTuple(argumentTypes)
				}
			}
		}
		val optimizerFunction = createRawFunction(TOP()) {
			pushLiteral(rawFunction)
			call(
				"PrivateForceOptimizationForTests:_",
				Types.TOP())
		}
		val ignored = executeRawFunction(optimizerFunction)
		assert(ignored.isNil)
	}

	/**
	 * Define a series of abstract methods from a vararg array whose entries can
	 * be specified as `"name" to listOf(argType...) to resultType`.
	 *
	 * @param pairs
	 *  A vararg list of [Pair]s, each being a [Pair] whose first element is
	 *  itself a [Pair] containing the method name [String] and a [List] of
	 *  argument [A_Type]s, and whose second element is the return [A_Type].
	 */
	fun defineAbstractMethods(
		vararg pairs: Pair<Pair<String, List<A_Type>>, A_Type>)
	{
		pairs.forEach { (nameAndArgTypes, returnType) ->
			val (name, argTypes) = nameAndArgTypes
			defineAbstractMethod(name, argTypes, returnType)
		}
	}

	/** Create an atom to use as a field in an object type. */
	fun fieldAtom(name: String, type: A_Type): A_Atom
	{
		val atom = createAtom(stringFrom(name), nil)
		atom.setAtomProperty(
			SpecialAtom.OBJECT_FIELD_RESTRICTION_KEY.atom,
			type.makeShared())
		return atom.makeShared()
	}

	/**
	 * Create an atom to use as an explicit-subclass field in an object type.
	 */
	fun explicitSubclassAtom(name: String): A_Atom
	{
		val atom = createAtom(stringFrom(name), nil)
		atom.setAtomProperty(
			SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom,
			trueObject)
		return atom.makeShared()
	}

	class ObjectTypeBuilder(private val supertype: A_Type?)
	{
		private val fields = mutableMapOf<A_Atom, A_Type>()

		init
		{
			supertype?.run {
				fieldTypeMap.forEachInMap { k, v -> fields[k] = v }
			}
		}

		operator fun A_Atom.invoke(fieldType: A_Type)
		{
			fields[this] = fieldType
		}

		operator fun String.invoke(fieldType: A_Type)
		{
			val atom = createAtom(stringFrom(this), nil)
			atom.setAtomProperty(
				SpecialAtom.OBJECT_FIELD_RESTRICTION_KEY.atom,
				fieldType.makeShared())
			fields[atom] = fieldType
		}

		val objectType: A_Type get() = objectTypeFromMap(
			mapWithBindings(
				tupleFromList(fields.map { (k, v) -> tuple(k, v) })))
	}

	fun objectType(
		name: String,
		explicit: Boolean,
		supertype: A_Type? = null,
		setup: ObjectTypeBuilder.()->Unit
	): A_Type
	{
		val builder = ObjectTypeBuilder(supertype)
		builder.setup()
		if (explicit)
		{
			val explicitAtom = explicitSubclassAtom("explicit-$name")
			builder.run {
				explicitAtom(instanceType(explicitAtom))
			}
		}
		val objectType = builder.objectType.makeShared()
		setNameForType(objectType, stringFrom(name), false)
		return objectType
	}
}
