/*
 * AvailRuntime.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail

import com.avail.AvailRuntime.Companion.specialObject
import com.avail.AvailRuntimeConfiguration.availableProcessors
import com.avail.AvailRuntimeConfiguration.maxInterpreters
import com.avail.AvailThread.Companion.current
import com.avail.annotations.ThreadSafe
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoots
import com.avail.builder.ResolvedModuleName
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import com.avail.descriptor.bundles.A_Bundle.Companion.removeGrammaticalRestriction
import com.avail.descriptor.bundles.A_Bundle.Companion.removeMacro
import com.avail.descriptor.character.CharacterDescriptor
import com.avail.descriptor.fiber.A_Fiber
import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import com.avail.descriptor.functions.FunctionDescriptor.Companion.newCrashFunction
import com.avail.descriptor.functions.PrimitiveCompiledCodeDescriptor.Companion.newPrimitiveRawFunction
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.maps.A_Map.Companion.forEach
import com.avail.descriptor.maps.A_Map.Companion.hasKey
import com.avail.descriptor.maps.A_Map.Companion.mapAt
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.mapSize
import com.avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import com.avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_GrammaticalRestriction
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.A_Method
import com.avail.descriptor.methods.A_SemanticRestriction
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import com.avail.descriptor.methods.SemanticRestrictionDescriptor
import com.avail.descriptor.module.A_Module
import com.avail.descriptor.module.A_Module.Companion.closeModule
import com.avail.descriptor.module.A_Module.Companion.importedNames
import com.avail.descriptor.module.A_Module.Companion.methodDefinitions
import com.avail.descriptor.module.A_Module.Companion.moduleName
import com.avail.descriptor.module.A_Module.Companion.newNames
import com.avail.descriptor.module.A_Module.Companion.privateNames
import com.avail.descriptor.module.A_Module.Companion.removeFrom
import com.avail.descriptor.module.A_Module.Companion.visibleNames
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.two
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.exceptionAtom
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.exceptionType
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.stackDumpAtom
import com.avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import com.avail.descriptor.parsing.LexerDescriptor.Companion.lexerFilterFunctionType
import com.avail.descriptor.pojos.PojoDescriptor.Companion.nullPojo
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tokens.TokenDescriptor.StaticInit
import com.avail.descriptor.tokens.TokenDescriptor.TokenType
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import com.avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationMeta
import com.avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import com.avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import com.avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.fiberMeta
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionMeta
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.characterCodePoints
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegersMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int64
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.nybbles
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.unsignedShorts
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mapMeta
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoArrayType
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoType
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoSelfType
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoSelfTypeAtom
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClass
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClassWithTypeArguments
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.selfTypeForClass
import com.avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setMeta
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableMeta
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.Companion.allNumericCodes
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MalformedMessageException
import com.avail.files.FileManager
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import com.avail.interpreter.primitive.general.P_EmergencyExit
import com.avail.interpreter.primitive.general.P_ToString
import com.avail.io.IOSystem
import com.avail.io.TextInterface
import com.avail.io.TextInterface.Companion.systemTextInterface
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.utility.StackPrinter.Companion.trace
import com.avail.utility.evaluation.OnceSupplier
import com.avail.utility.safeWrite
import com.avail.utility.structures.EnumMap.Companion.enumMap
import java.util.ArrayDeque
import java.util.Collections
import java.util.Queue
import java.util.Timer
import java.util.TimerTask
import java.util.WeakHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write
import kotlin.math.min

/**
 * An `AvailRuntime` comprises the [modules][ModuleDescriptor],
 * [methods][MethodDescriptor], and [special objects][specialObject] that
 * define an Avail system. It also manages global resources, such as file
 * connections.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property moduleNameResolver
 *   The [module&#32;name&#32;resolver][ModuleNameResolver] that this
 *   [runtime][AvailRuntime] should use to resolve unqualified
 *   [module][ModuleDescriptor] names.
 *
 * @constructor
 * Construct a new `AvailRuntime`.
 *
 * @param moduleNameResolver
 *   The [module name resolver][ModuleNameResolver] that this `AvailRuntime`
 *   should use to resolve unqualified [module][ModuleDescriptor] names.
 * @param fileManager
 *   The [FileManager] of the running Avail.
 */
class AvailRuntime constructor(
	val moduleNameResolver: ModuleNameResolver,
	fileManager: FileManager)
{
	init
	{
		fileManager.associateRuntime(this)
	}

	/**
	 * The [IOSystem] for this runtime.
	 */
	val ioSystem: IOSystem = fileManager.ioSystem

	/** The [CallbackSystem] for this runtime. */
	private val callbackSystem = CallbackSystem()

	/**
	 * Answer this runtime's [CallbackSystem].
	 *
	 * @return
	 *   An [CallbackSystem].
	 */
	fun callbackSystem(): CallbackSystem = callbackSystem

	/**
	 * A counter from which unique interpreter indices in [0..maxInterpreters)
	 * are allotted.
	 */
	private val nextInterpreterIndex = AtomicInteger(0)

	/**
	 * Allocate the next interpreter index in [0..maxInterpreters)
	 * thread-safely.
	 *
	 * @return
	 *   A new unique interpreter index.
	 */
	@Synchronized
	fun allocateInterpreterIndex(): Int
	{
		val index = nextInterpreterIndex.getAndIncrement()
		assert(index < maxInterpreters)
		return index
	}

	/**
	 * The [thread pool executor][ThreadPoolExecutor] for this [Avail runtime][AvailRuntime].
	 */
	val executor = ThreadPoolExecutor(
		min(availableProcessors, maxInterpreters),
		maxInterpreters,
		10L,
		TimeUnit.SECONDS,
		PriorityBlockingQueue(),
		ThreadFactory { AvailThread(it, Interpreter(this)) },
		AbortPolicy())

	/**
	 * Schedule the specified [task][AvailTask] for eventual execution. The
	 * implementation is free to run the task immediately or delay its execution
	 * arbitrarily. The task is guaranteed to execute on an [Avail
	 * thread][AvailThread].
	 *
	 * @param task
	 *   A task.
	 */
	fun execute(task: AvailTask)
	{
		executor.execute(task)
	}

	/**
	 * Schedule the specified [task][AvailTask] for eventual execution. The
	 * implementation is free to run the task immediately or delay its execution
	 * arbitrarily. The task is guaranteed to execute on an
	 * [Avail&#32;thread][AvailThread].
	 *
	 * @param priority
	 *   The desired priority, a long tied to milliseconds since the current
	 *   epoch.
	 * @param body
	 *   The action to execute for this task.
	 */
	fun execute(priority: Int,body: () -> Unit)
	{
		executor.execute(AvailTask(priority, body))
	}

	/**
	 * The number of clock ticks since this [runtime][AvailRuntime] was created.
	 */
	val clock = AvailRuntimeSupport.Clock()

	/**
	 * The [timer][Timer] that managed scheduled [tasks][TimerTask] for this
	 * [runtime][AvailRuntime]. The timer thread is not an
	 * [Avail&#32;thread][AvailThread], and therefore cannot directly execute
	 * [fibers][FiberDescriptor]. It may, however, schedule fiber-related tasks.
	 */
	val timer =  fixedRateTimer("timer for Avail runtime", true, period = 10) {
		clock.increment()
	}

	/**
	 * Perform an integrity check on the parser data structures.  Report the
	 * findings to System.out.
	 */
	fun integrityCheck()
	{
		println("Integrity check:")
		runtimeLock.writeLock().lock()
		try
		{
			val atoms = mutableSetOf<A_Atom>()
			val definitions = mutableSetOf<A_Definition>()
			modules.forEach { _, module ->
				atoms.addAll(module.newNames().valuesAsTuple())
				atoms.addAll(module.visibleNames())
				var atomSets = module.importedNames().valuesAsTuple()
				atomSets = atomSets.concatenateWith(
					module.privateNames().valuesAsTuple(), true)
				for (atomSet in atomSets)
				{
					atoms.addAll(atomSet)
				}
				for (definition in module.methodDefinitions())
				{
					if (definitions.contains(definition))
					{
						println("Duplicate definition: $definition")
					}
					definitions.add(definition)
				}
			}
			val methods = mutableSetOf<A_Method>()
			for (atom in atoms)
			{
				val bundle: A_Bundle = atom.bundleOrNil()
				if (!bundle.equalsNil())
				{
					methods.add(bundle.bundleMethod())
				}
			}
			for (method in methods)
			{
				val bundleDefinitions: MutableSet<A_Definition> =
					method.definitionsTuple().toMutableSet()
				bundleDefinitions.addAll(method.macrosTuple())
				for (bundle in method.bundles())
				{
					val bundlePlans: A_Map = bundle.definitionParsingPlans()
					if (bundlePlans.mapSize() != bundleDefinitions.size)
					{
						println(
							"Mismatched definitions / plans:\n\t" +
							"bundle = $bundle\n\t" +
							"definitions# = ${bundleDefinitions.size}\n\t" +
							"plans# = ${bundlePlans.mapSize()}")
					}
				}
			}
			println("done.")
		}
		finally
		{
			runtimeLock.writeLock().unlock()
		}
	}

	/**
	 * Answer the Avail [module roots][ModuleRoots].
	 *
	 * @return
	 *   The Avail [module roots][ModuleRoots].
	 */
	@ThreadSafe
	fun moduleRoots(): ModuleRoots = moduleNameResolver.moduleRoots

	/**
	 * The [class loader][ClassLoader] that should be used to locate and load
	 * Java [classes][Class].
	 */
	val classLoader: ClassLoader = AvailRuntime::class.java.classLoader

	/**
	 * Look up the given Java class name, and create a suitable Java POJO
	 * [A_Type].  Capture the classParameters, since the Avail POJO mechanism
	 * doesn't erase generics.
	 *
	 * @param className
	 *   The full name of the Java class.
	 * @param classParameters
	 *   The type parameters for specializing the Java class.
	 * @return
	 *   An [A_Type] representing the specialized Java class.
	 * @throws AvailRuntimeException
	 *   If the class can't be found or can't be parameterized as requested, or
	 *   if it's within the forbidden com.avail namespace.
	 */
	@Throws(AvailRuntimeException::class)
	fun lookupJavaType(
		className: A_String,
		classParameters: A_Tuple): A_Type
	{
		val rawClass = lookupRawJavaClass(className)
		// Check that the correct number of type parameters have been supplied.
		// Don't bother to check the bounds of the type parameters. Incorrect
		// bounds will cause some method and constructor lookups to fail, but
		// that's fine.
		val typeVars = rawClass.typeParameters
		if (typeVars.size != classParameters.tupleSize())
		{
			throw AvailRuntimeException(
				AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		// Replace all occurrences of the pojo self type atom with actual
		// pojo self types.
		val realParameters: A_Tuple = generateObjectTupleFrom(
			classParameters.tupleSize()) {
			val parameter: A_BasicObject = classParameters.tupleAt(it)
			if (parameter.equals(pojoSelfType())) selfTypeForClass(rawClass)
			else parameter
		}
		realParameters.makeImmutable()
		// Construct and answer the pojo type.
		return pojoTypeForClassWithTypeArguments(rawClass, realParameters)
	}

	/**
	 * Look up the given Java class name, and create a suitable Java raw POJO
	 * for this raw Java class.  The raw class does not capture type parameters.
	 *
	 * @param className
	 *   The full name of the Java class.
	 * @return
	 *   The raw Java [Class] (i.e., without type parameters bound).
	 * @throws AvailRuntimeException
	 *   If the class can't be found, or if it's within the forbidden com.avail
	 *   namespace.
	 */
	fun lookupRawJavaClass(className: A_String): Class<*>
	{
		// Forbid access to the Avail implementation's packages.
		val nativeClassName = className.asNativeString()
		if (nativeClassName.startsWith("com.avail"))
		{
			throw AvailRuntimeException(
				AvailErrorCode.E_JAVA_CLASS_NOT_AVAILABLE)
		}
		// Look up the raw Java class using the interpreter's runtime's
		// class loader.
		return try
		{
			Class.forName(
				className.asNativeString(), true, classLoader)
		}
		catch (e: ClassNotFoundException)
		{
			throw AvailRuntimeException(
				AvailErrorCode.E_JAVA_CLASS_NOT_AVAILABLE)
		}
	}

	/**
	 * The [runtime][AvailRuntime]'s default [text interface][TextInterface].
	 */
	private var textInterface = systemTextInterface()

	/**
	 * A [raw pojo][RawPojoDescriptor] wrapping the [default][textInterface]
	 * [text interface][TextInterface].
	 */
	private var textInterfacePojo = identityPojo(textInterface)

	/**
	 * Answer the runtime's default [text interface][TextInterface].
	 *
	 * @return
	 *   The default text interface.
	 */
	@ThreadSafe
	fun textInterface(): TextInterface = runtimeLock.read { textInterface }

	/**
	 * Set the runtime's default [text interface][TextInterface].
	 *
	 * @param textInterface
	 *   The new default text interface.
	 */
	@ThreadSafe
	fun setTextInterface(textInterface: TextInterface) =
		runtimeLock.safeWrite {
			this.textInterface = textInterface
			textInterfacePojo = identityPojo(textInterface).makeShared()
		}

	/**
	 * A `HookType` describes an abstract missing behavior in the virtual
	 * machine, where an actual hook will have to be constructed to hold an
	 * [A_Function] within each separate `AvailRuntime`.
	 *
	 * @property functionType
	 *   The [A_Function] [A_Type] that hooks of this type use.
	 *
	 * @constructor
	 * Create a hook type.
	 *
	 * @param hookName
	 *   The name to attach to the [A_Function]s that are plugged into hooks of this type.
	 * @param functionType
	 *   The signature of functions that may be plugged into hook of this type.
	 * @param primitive
	 *   The [Primitive] around which to synthesize a default [A_Function] for
	 *   hooks of this type.  If this is `null`, a function that invokes
	 *   [P_EmergencyExit] will be synthesized instead.
	 */
	enum class HookType constructor(
		hookName: String,
		val functionType: A_Type,
		primitive: Primitive?)
	{
		/**
		 * The `HookType` for a hook that holds the stringification function.
		 */
		STRINGIFICATION(
			"«stringification»",
			functionType(tuple(Types.ANY.o), stringType()),
			P_ToString),

		/**
		 * The `HookType` for a hook that holds the function to invoke whenever
		 * an unassigned variable is read.
		 */
		READ_UNASSIGNED_VARIABLE(
			"«cannot read unassigned variable»",
			functionType(emptyTuple, bottom),
			null),

		/**
		 * The `HookType` for a hook that holds the function to invoke whenever
		 * a returned value disagrees with the expected type.
		 */
		RESULT_DISAGREED_WITH_EXPECTED_TYPE(
			"«return result disagreed with expected type»",
			functionType(
				tuple(
					mostGeneralFunctionType(),
					topMeta(),
					variableTypeFor(Types.ANY.o)),
				bottom
			),
			null),

		/**
		 * The `HookType` for a hook that holds the function to invoke whenever
		 * an [A_Method] send fails for a definitional reason.
		 */
		INVALID_MESSAGE_SEND(
			"«failed method lookup»",
			functionType(
				tuple(
					enumerationWith(
						set(AvailErrorCode.E_NO_METHOD,
						    AvailErrorCode.E_NO_METHOD_DEFINITION,
						    AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION,
						    AvailErrorCode.E_FORWARD_METHOD_DEFINITION,
						    AvailErrorCode.E_ABSTRACT_METHOD_DEFINITION)),
					Types.METHOD.o,
					mostGeneralTupleType()),
				bottom
			),
			null),

		/**
		 * The `HookType` for a hook that holds the [A_Function] to invoke
		 * whenever an [A_Variable] with
		 * [write&#32;reactors][VariableDescriptor.VariableAccessReactor] is
		 * written to when
		 * [write&#32;tracing][FiberDescriptor.TraceFlag.TRACE_VARIABLE_WRITES]
		 * is not enabled.
		 */
		IMPLICIT_OBSERVE(
			"«variable with a write reactor was written without write-tracing»",
			functionType(
				tuple(
					mostGeneralFunctionType(),
					mostGeneralTupleType()),
				Types.TOP.o
			),
			null),

		/**
		 * The `HookType` for a hook that holds the [A_Function] to invoke when
		 * an exception is caught in a Pojo invocation of a Java method or
		 * [CallbackSystem.Callback].
		 */
		RAISE_JAVA_EXCEPTION_IN_AVAIL(
			"«raise Java exception in Avail»",
			functionType(
				tuple(
					pojoTypeForClass(Throwable::class.java)),
				bottom
			),
			null),

		/**
		 * The `HookType` for a hook that holds the [A_Function] to invoke at
		 * the outermost stack frame to run a fiber.  This function is passed a
		 * function to execute and the arguments to supply to it.  The result
		 * returned by the passed function is returned from this frame.
		 */
		BASE_FRAME(
			"«base frame»",
			functionType(
				tuple(
					mostGeneralFunctionType(),
					mostGeneralTupleType()),
				Types.TOP.o
			),
			P_InvokeWithTuple);

		/** The name to attach to functions plugged into this hook.  */
		private val hookName: A_String = stringFrom(hookName).makeShared()

		/**
		 * A [supplier][OnceSupplier] of a default [A_Function] to use for this
		 * hook type.
		 */
		@Suppress("LeakingThis")
		val defaultFunctionSupplier: OnceSupplier<A_Function> =
			produceDefaultFunctionSupplier(hookName, primitive)

		open fun produceDefaultFunctionSupplier(
			hookName: String,
			primitive: Primitive?
		): OnceSupplier<A_Function> = when (primitive)
		{
			null ->
			{
				// Create an invocation of P_EmergencyExit.
				val argumentsTupleType = functionType.argsTupleType()
				val argumentTypesTuple =
					argumentsTupleType.tupleOfTypesFromTo(
						1,
						argumentsTupleType.sizeRange().upperBound()
							.extractInt())
				OnceSupplier {
					newCrashFunction(hookName, argumentTypesTuple).makeShared()
				}
			}
			else ->
			{
				val code = newPrimitiveRawFunction(primitive, nil, 0)
				code.setMethodName(this.hookName)
				OnceSupplier { createFunction(code, emptyTuple).makeShared() }
			}
		}

		/**
		 * Extract the current [A_Function] for this hook from the given
		 * runtime.
		 *
		 * @param runtime
		 *   The [AvailRuntime] to examine.
		 * @return
		 *   The [A_Function] currently in that hook.
		 */
		operator fun get(runtime: AvailRuntime): A_Function =
			runtime.hooks[this]!!

		/**
		 * Set this hook for the given runtime to the given function.
		 *
		 * @param runtime
		 *   The [AvailRuntime] to examine.
		 * @param function
		 *   The [A_Function] to plug into this hook.
		 */
		operator fun set(runtime: AvailRuntime, function: A_Function)
		{
			assert(function.isInstanceOf(functionType))
			function.code().setMethodName(hookName)
			runtime.hooks[this] = function.makeShared()
		}
	}

	/** The collection of hooks for this runtime. */
	val hooks = enumMap { hook: HookType -> hook.defaultFunctionSupplier() }

	/**
	 * Answer the [function][FunctionDescriptor] to invoke whenever the value
	 * produced by a [method][MethodDescriptor] send disagrees with the
	 * [type][TypeDescriptor] expected.
	 *
	 * The function takes the function that's attempting to return, the
	 * expected return type, and a new variable holding the actual result being
	 * returned (or unassigned if it was `nil`).
	 *
	 * @return
	 *   The requested function.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	fun resultDisagreedWithExpectedTypeFunction(): A_Function =
		HookType.RESULT_DISAGREED_WITH_EXPECTED_TYPE[this]

	/**
	 * Answer the [function][FunctionDescriptor] to invoke whenever a
	 * [variable][VariableDescriptor] with
	 * [write&#32;reactors][VariableDescriptor.VariableAccessReactor] is written
	 * when [write&#32;tracing][FiberDescriptor.TraceFlag.TRACE_VARIABLE_WRITES]
	 * is not enabled.
	 *
	 * @return
	 *  The requested function.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	fun implicitObserveFunction(): A_Function = HookType.IMPLICIT_OBSERVE[this]

	/**
	 * Answer the [function][FunctionDescriptor] to invoke whenever a
	 * [method][MethodDescriptor] send fails for a definitional reason.
	 *
	 * @return
	 *   The function to invoke whenever a message send fails dynamically
	 *   because of an ambiguous, invalid, or incomplete lookup.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	fun invalidMessageSendFunction(): A_Function =
		HookType.INVALID_MESSAGE_SEND[this]

	/**
	 * Answer the [function][FunctionDescriptor] to invoke whenever an
	 * unassigned variable is read.
	 *
	 * @return
	 *   The function to invoke whenever an attempt is made to read an
	 *   unassigned variable.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	fun unassignedVariableReadFunction(): A_Function =
		HookType.READ_UNASSIGNED_VARIABLE[this]

	/**
	 * All [fibers][A_Fiber] that have not yet
	 * [retired][FiberDescriptor.ExecutionState.RETIRED] *or* been reclaimed by
	 * garbage collection.
	 */
	private val allFibers = Collections.synchronizedSet(
		Collections.newSetFromMap(WeakHashMap<A_Fiber, Boolean>()))

	/**
	 * Add the specified [fiber][A_Fiber] to this [runtime][AvailRuntime].
	 *
	 * @param fiber
	 *   A fiber.
	 */
	fun registerFiber(fiber: A_Fiber)
	{
		allFibers.add(fiber)
	}

	/**
	 * Remove the specified [fiber][A_Fiber] from this [runtime][AvailRuntime].
	 * This should be done explicitly when a fiber retires, although the fact
	 * that [allFibers] wraps a [WeakHashMap] ensures that fibers that are no
	 * longer referenced will still be cleaned up at some point.
	 *
	 * @param fiber
	 *   A fiber to unregister.
	 */
	fun unregisterFiber(fiber: A_Fiber)
	{
		allFibers.remove(fiber)
	}

	/**
	 * Answer a [Set] of all [A_Fiber]s that have not yet
	 * [retired][FiberDescriptor.ExecutionState.RETIRED].  Retired fibers will
	 * be garbage collected when there are no remaining references.
	 *
	 * Note that the result is a set which strongly holds all extant fibers,
	 * so holding this set indefinitely would keep the contained fibers from
	 * being garbage collected.
	 *
	 * @return
	 *   All fibers belonging to this `AvailRuntime`.
	 */
	fun allFibers(): Set<A_Fiber> = allFibers.toSet()

	/**
	 * The [lock][ReentrantReadWriteLock] that protects the
	 * [runtime][AvailRuntime] data structures against dangerous concurrent
	 * access.
	 */
	private val runtimeLock = ReentrantReadWriteLock()

	companion object
	{
		/**
		 * Answer the Avail runtime associated with the current [Thread].
		 *
		 * @return
		 *   The Avail runtime of the current thread.
		 */
		fun currentRuntime(): AvailRuntime = current().runtime

		/**
		 * The [CheckedMethod] for [resultDisagreedWithExpectedTypeFunction].
		 */
		val resultDisagreedWithExpectedTypeFunctionMethod = instanceMethod(
			AvailRuntime::class.java,
			AvailRuntime::resultDisagreedWithExpectedTypeFunction.name,
			A_Function::class.java)

		/**
		 * The [CheckedMethod] for [implicitObserveFunction].
		 */
		val implicitObserveFunctionMethod = instanceMethod(
			AvailRuntime::class.java,
			AvailRuntime::implicitObserveFunction.name,
			A_Function::class.java)

		/**
		 * The [CheckedMethod] for [invalidMessageSendFunction].
		 */
		val invalidMessageSendFunctionMethod = instanceMethod(
			AvailRuntime::class.java,
			AvailRuntime::invalidMessageSendFunction.name,
			A_Function::class.java)

		/**
		 * The [CheckedMethod] for [unassignedVariableReadFunction].
		 */
		val unassignedVariableReadFunctionMethod = instanceMethod(
			AvailRuntime::class.java,
			AvailRuntime::unassignedVariableReadFunction.name,
			A_Function::class.java)

		/** A helper for constructing lists with checked positions. */
		private class NumericBuilder {
			/** The list of [AvailObject]s being built. */
			private val list = mutableListOf<AvailObject>()

			/** Verify that we're at the expected index. */
			fun at (position: Int) = assert(list.size == position)

			/** Add an item to the end. */
			fun put (value: A_BasicObject) = list.add(value.makeShared())

			/** Extract the final immutable list. */
			fun list(): List<AvailObject> = list
		}

		/**
		 * The [special objects][AvailObject] of the [runtime][AvailRuntime].
		 */
		val specialObjects = NumericBuilder().apply {
			at(0)
			put(nil)  // Special entry, not used.
			put(Types.ANY.o)
			put(booleanType)
			put(Types.CHARACTER.o)
			put(mostGeneralFunctionType())
			put(functionMeta())
			put(mostGeneralCompiledCodeType())
			put(mostGeneralVariableType())
			put(variableMeta())
			put(mostGeneralContinuationType())

			at(10)
			put(continuationMeta())
			put(Types.ATOM.o)
			put(Types.DOUBLE.o)
			put(extendedIntegers)
			put(instanceMeta(zeroOrMoreOf(anyMeta())))
			put(Types.FLOAT.o)
			put(Types.NUMBER.o)
			put(integers)
			put(extendedIntegersMeta)
			put(mapMeta())

			at(20)
			put(Types.MODULE.o)
			put(tupleFromIntegerList(allNumericCodes()))
			put(mostGeneralObjectType())
			put(mostGeneralObjectMeta())
			put(exceptionType())
			put(mostGeneralFiberType())
			put(mostGeneralSetType())
			put(setMeta())
			put(stringType())
			put(bottom)

			at(30)
			put(bottomMeta)
			put(Types.NONTYPE.o)
			put(mostGeneralTupleType())
			put(tupleMeta())
			put(topMeta())
			put(Types.TOP.o)
			put(wholeNumbers)
			put(naturalNumbers)
			put(characterCodePoints)
			put(mostGeneralMapType())

			at(40)
			put(Types.MESSAGE_BUNDLE.o)
			put(Types.MESSAGE_BUNDLE_TREE.o)
			put(Types.METHOD.o)
			put(Types.DEFINITION.o)
			put(Types.ABSTRACT_DEFINITION.o)
			put(Types.FORWARD_DEFINITION.o)
			put(Types.METHOD_DEFINITION.o)
			put(Types.MACRO_DEFINITION.o)
			put(zeroOrMoreOf(mostGeneralFunctionType()))
			put(stackDumpAtom())

			at(50)
			put(PhraseKind.PARSE_PHRASE.mostGeneralType())
			put(PhraseKind.SEQUENCE_PHRASE.mostGeneralType())
			put(PhraseKind.EXPRESSION_PHRASE.mostGeneralType())
			put(PhraseKind.ASSIGNMENT_PHRASE.mostGeneralType())
			put(PhraseKind.BLOCK_PHRASE.mostGeneralType())
			put(PhraseKind.LITERAL_PHRASE.mostGeneralType())
			put(PhraseKind.REFERENCE_PHRASE.mostGeneralType())
			put(PhraseKind.SEND_PHRASE.mostGeneralType())
			put(instanceMeta(mostGeneralLiteralTokenType()))
			put(PhraseKind.LIST_PHRASE.mostGeneralType())

			at(60)
			put(PhraseKind.VARIABLE_USE_PHRASE.mostGeneralType())
			put(PhraseKind.DECLARATION_PHRASE.mostGeneralType())
			put(PhraseKind.ARGUMENT_PHRASE.mostGeneralType())
			put(PhraseKind.LABEL_PHRASE.mostGeneralType())
			put(PhraseKind.LOCAL_VARIABLE_PHRASE.mostGeneralType())
			put(PhraseKind.LOCAL_CONSTANT_PHRASE.mostGeneralType())
			put(PhraseKind.MODULE_VARIABLE_PHRASE.mostGeneralType())
			put(PhraseKind.MODULE_CONSTANT_PHRASE.mostGeneralType())
			put(PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE.mostGeneralType())
			put(anyMeta())

			at(70)
			put(trueObject)
			put(falseObject)
			put(zeroOrMoreOf(stringType()))
			put(zeroOrMoreOf(topMeta()))
			put(zeroOrMoreOf(
				setTypeForSizesContentType(wholeNumbers, stringType())))
			put(setTypeForSizesContentType(wholeNumbers, stringType()))
			put(functionType(tuple(naturalNumbers), bottom))
			put(emptySet)
			put(negativeInfinity())
			put(positiveInfinity())

			at(80)
			put(mostGeneralPojoType())
			put(pojoBottom())
			put(nullPojo())
			put(pojoSelfType())
			put(instanceMeta(mostGeneralPojoType()))
			put(instanceMeta(mostGeneralPojoArrayType()))
			put(functionTypeReturning(Types.ANY.o))
			put(mostGeneralPojoArrayType())
			put(pojoSelfTypeAtom())
			put(pojoTypeForClass(Throwable::class.java))

			at(90)
			put(functionType(emptyTuple(), Types.TOP.o))
			put(functionType(emptyTuple(), booleanType))
			put(variableTypeFor(mostGeneralContinuationType()))
			put(mapTypeForSizesKeyTypeValueType(
				wholeNumbers, Types.ATOM.o, Types.ANY.o))
			put(mapTypeForSizesKeyTypeValueType(
				wholeNumbers, Types.ATOM.o, anyMeta()))
			put(tupleTypeForSizesTypesDefaultType(
				wholeNumbers,
				emptyTuple(),
				tupleTypeForSizesTypesDefaultType(
					singleInt(2),
					emptyTuple(),
					Types.ANY.o)))
			put(emptyMap)
			put(mapTypeForSizesKeyTypeValueType(
				naturalNumbers, Types.ANY.o, Types.ANY.o))
			put(instanceMeta(wholeNumbers))
			put(setTypeForSizesContentType(naturalNumbers, Types.ANY.o))

			at(100)
			put(tupleTypeForSizesTypesDefaultType(
				wholeNumbers, emptyTuple, mostGeneralTupleType()))
			put(nybbles)
			put(zeroOrMoreOf(nybbles))
			put(unsignedShorts)
			put(emptyTuple)
			put(functionType(tuple(bottom), Types.TOP.o))
			put(instanceType(zero))
			put(functionTypeReturning(topMeta()))
			put(tupleTypeForSizesTypesDefaultType(
				wholeNumbers,
				emptyTuple(),
				functionTypeReturning(topMeta())))
			put(functionTypeReturning(
				PhraseKind.PARSE_PHRASE.mostGeneralType()))

			at(110)
			put(instanceType(two))
			put(fromDouble(Math.E))
			put(instanceType(fromDouble(Math.E)))
			put(instanceMeta(
				PhraseKind.PARSE_PHRASE.mostGeneralType()))
			put(setTypeForSizesContentType(
				wholeNumbers, Types.ATOM.o))
			put(Types.TOKEN.o)
			put(mostGeneralLiteralTokenType())
			put(zeroOrMoreOf(anyMeta()))
			put(inclusive(zero, positiveInfinity()))
			put(zeroOrMoreOf(
				tupleTypeForSizesTypesDefaultType(
					singleInt(2),
					tuple(Types.ATOM.o), anyMeta())))

			at(120)
			put(zeroOrMoreOf(
				tupleTypeForSizesTypesDefaultType(
					singleInt(2),
					tuple(Types.ATOM.o),
					Types.ANY.o)))
			put(zeroOrMoreOf(
				PhraseKind.PARSE_PHRASE.mostGeneralType()))
			put(zeroOrMoreOf(
				PhraseKind.ARGUMENT_PHRASE.mostGeneralType()))
			put(zeroOrMoreOf(
				PhraseKind.DECLARATION_PHRASE.mostGeneralType()))
			put(variableReadWriteType(Types.TOP.o, bottom))
			put(zeroOrMoreOf(
				PhraseKind.EXPRESSION_PHRASE.create(Types.ANY.o)))
			put(PhraseKind.EXPRESSION_PHRASE.create(Types.ANY.o))
			put(functionType(
				tuple(pojoTypeForClass(Throwable::class.java)), bottom))
			put(zeroOrMoreOf(
				setTypeForSizesContentType(wholeNumbers, Types.ATOM.o)))
			put(bytes)

			at(130)
			put(zeroOrMoreOf(zeroOrMoreOf(anyMeta())))
			put(variableReadWriteType(extendedIntegers, bottom))
			put(fiberMeta())
			put(nonemptyStringType())
			put(setTypeForSizesContentType(
				wholeNumbers, exceptionType()))
			put(setTypeForSizesContentType(
				naturalNumbers, stringType()))
			put(setTypeForSizesContentType(
				naturalNumbers, Types.ATOM.o))
			put(oneOrMoreOf(Types.ANY.o))
			put(zeroOrMoreOf(integers))
			put(tupleTypeForSizesTypesDefaultType(
				integerRangeType(
					fromInt(2), true, positiveInfinity(), false),
				emptyTuple(),
				Types.ANY.o))

			// Some of these entries may need to be shuffled into earlier
			// slots to maintain reasonable topical consistency.)

			at(140)
			put(PhraseKind.FIRST_OF_SEQUENCE_PHRASE.mostGeneralType())
			put(PhraseKind.PERMUTED_LIST_PHRASE.mostGeneralType())
			put(PhraseKind.SUPER_CAST_PHRASE.mostGeneralType())
			put(SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom)
			put(SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom)
			put(SpecialAtom.ALL_TOKENS_KEY.atom)
			put(int32)
			put(int64)
			put(PhraseKind.STATEMENT_PHRASE.mostGeneralType())
			put(SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom)

			at(150)
			put(PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType())
			put(oneOrMoreOf(naturalNumbers))
			put(zeroOrMoreOf(Types.DEFINITION.o))
			put(mapTypeForSizesKeyTypeValueType(
				wholeNumbers, stringType(), Types.ATOM.o))
			put(SpecialAtom.MACRO_BUNDLE_KEY.atom)
			put(SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom)
			put(variableReadWriteType(mostGeneralMapType(), bottom))
			put(lexerFilterFunctionType())
			put(lexerBodyFunctionType())
			put(SpecialAtom.STATIC_TOKENS_KEY.atom)

			at(160)
			put(TokenType.END_OF_FILE.atom)
			put(TokenType.KEYWORD.atom)
			put(TokenType.LITERAL.atom)
			put(TokenType.OPERATOR.atom)
			put(TokenType.COMMENT.atom)
			put(TokenType.WHITESPACE.atom)
			put(inclusive(0, (1L shl 32) - 1))
			put(inclusive(0, (1L shl 28) - 1))
			put(inclusive(1L, 4L))
			put(inclusive(0L, 31L))

			at(170)
			put(continuationTypeForFunctionType(
				functionTypeReturning(Types.TOP.o)))
			put(CharacterDescriptor.nonemptyStringOfDigitsType)
			put(tupleTypeForTypes(
				zeroOrOneOf(PhraseKind.SEND_PHRASE.mostGeneralType()),
				stringType()))

			at(173)
		}.list().onEach { assert(!it.isAtom || it.isAtomSpecial()) }

		/**
		 * Answer the [special object][AvailObject] with the specified ordinal.
		 *
		 * @param ordinal
		 *   The [special object][AvailObject] with the specified ordinal.
		 * @return
		 *   An [AvailObject].
		 */
		@ThreadSafe
		fun specialObject(ordinal: Int): AvailObject = specialObjects[ordinal]

		/**
		 * The [special atoms][AtomDescriptor] known to the
		 * [runtime][AvailRuntime].
		 */
		val specialAtoms = NumericBuilder().apply {
			at(0)
			put(SpecialAtom.ALL_TOKENS_KEY.atom)
			put(SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom)
			put(SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom)
			put(SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom)
			put(SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom)
			put(SpecialAtom.FALSE.atom)
			put(SpecialAtom.FILE_KEY.atom)
			put(SpecialAtom.HERITABLE_KEY.atom)
			put(SpecialAtom.MACRO_BUNDLE_KEY.atom)
			put(SpecialAtom.OBJECT_TYPE_NAME_PROPERTY_KEY.atom)
			put(SpecialAtom.SERVER_SOCKET_KEY.atom)
			put(SpecialAtom.SOCKET_KEY.atom)
			put(SpecialAtom.STATIC_TOKENS_KEY.atom)
			put(SpecialAtom.TRUE.atom)
			put(SpecialMethodAtom.ABSTRACT_DEFINER.atom)
			put(SpecialMethodAtom.ADD_TO_MAP_VARIABLE.atom)
			put(SpecialMethodAtom.ALIAS.atom)
			put(SpecialMethodAtom.APPLY.atom)
			put(SpecialMethodAtom.ATOM_PROPERTY.atom)
			put(SpecialMethodAtom.CONTINUATION_CALLER.atom)
			put(SpecialMethodAtom.CRASH.atom)
			put(SpecialMethodAtom.CREATE_LITERAL_PHRASE.atom)
			put(SpecialMethodAtom.CREATE_LITERAL_TOKEN.atom)
			put(SpecialMethodAtom.DECLARE_STRINGIFIER.atom)
			put(SpecialMethodAtom.FORWARD_DEFINER.atom)
			put(SpecialMethodAtom.GET_RETHROW_JAVA_EXCEPTION.atom)
			put(SpecialMethodAtom.GET_VARIABLE.atom)
			put(SpecialMethodAtom.GRAMMATICAL_RESTRICTION.atom)
			put(SpecialMethodAtom.MACRO_DEFINER.atom)
			put(SpecialMethodAtom.METHOD_DEFINER.atom)
			put(SpecialMethodAtom.ADD_UNLOADER.atom)
			put(SpecialMethodAtom.PUBLISH_ATOMS.atom)
			put(SpecialMethodAtom.PUBLISH_ALL_ATOMS_FROM_OTHER_MODULE.atom)
			put(SpecialMethodAtom.RESUME_CONTINUATION.atom)
			put(SpecialMethodAtom.RECORD_TYPE_NAME.atom)
			put(SpecialMethodAtom.CREATE_MODULE_VARIABLE.atom)
			put(SpecialMethodAtom.SEAL.atom)
			put(SpecialMethodAtom.SEMANTIC_RESTRICTION.atom)
			put(SpecialMethodAtom.LEXER_DEFINER.atom)
			put(SpecialMethodAtom.PUBLISH_NEW_NAME.atom)
			put(exceptionAtom())
			put(stackDumpAtom())
			put(pojoSelfTypeAtom())
			put(TokenType.END_OF_FILE.atom)
			put(TokenType.KEYWORD.atom)
			put(TokenType.LITERAL.atom)
			put(TokenType.OPERATOR.atom)
			put(TokenType.COMMENT.atom)
			put(TokenType.WHITESPACE.atom)
			put(StaticInit.tokenTypeOrdinalKey)
		}.list().onEach { assert(it.isAtomSpecial()) }
	}

	/**
	 * The loaded Avail modules: a [A_Map] from [A_String] to [A_Module].  Must
	 * always be [Mutability.SHARED].
	 */
	private var modules = emptyMap

	/**
	 * Add the specified [module][ModuleDescriptor] to the runtime.
	 *
	 * @param module
	 *   A [module][ModuleDescriptor].
	 */
	@ThreadSafe
	fun addModule(module: A_Module)
	{
		// Ensure that the module is closed before installing it globally.
		module.closeModule()
		runtimeLock.safeWrite {
			assert(!includesModuleNamed(module.moduleName()))
			modules = modules.mapAtPuttingCanDestroy(
				module.moduleName(), module, true).makeShared()
		}
	}

	/**
	 * Remove the specified [module][ModuleDescriptor] from this runtime.  The
	 * module's code should already have been removed via [A_Module.removeFrom].
	 *
	 * @param module
	 *   The module to remove.
	 */
	fun unlinkModule(module: A_Module)
	{
		runtimeLock.safeWrite {
			assert(includesModuleNamed(module.moduleName()))
			modules = modules.mapWithoutKeyCanDestroy(
				module.moduleName(), true).makeShared()
		}
	}

	/**
	 * Does the runtime define a [module][ModuleDescriptor] with the specified
	 * [name][TupleDescriptor]?
	 *
	 * @param moduleName
	 *  A [name][TupleDescriptor].
	 * @return
	 *   `true` if the runtime defines a [module][ModuleDescriptor] with the
	 *   specified [name][TupleDescriptor], `false` otherwise.
	 */
	@ThreadSafe
	fun includesModuleNamed(moduleName: A_String): Boolean
	{
		assert(moduleName.isString)
		return runtimeLock.read {
			assert(modules.descriptor().isShared)
			modules.hasKey(moduleName)
		}
	}

	/**
	 * Answer my current [A_Map] of [A_Module]s.  It's always
	 * [Mutability.SHARED] for safety.
	 *
	 * @return
	 *   A [map][MapDescriptor] from resolved module [names][ResolvedModuleName]
	 *   ([strings][StringDescriptor]) to [modules][ModuleDescriptor].
	 */
	fun loadedModules(): A_Map = runtimeLock.read {
		assert(modules.descriptor().isShared)
		modules
	}

	/**
	 * Answer the [module][ModuleDescriptor] with the specified
	 * [name][TupleDescriptor].
	 *
	 * @param moduleName
	 *   A [name][TupleDescriptor].
	 * @return
	 *   A [module][ModuleDescriptor].
	 */
	@ThreadSafe
	fun moduleAt(moduleName: A_String): AvailObject
	{
		assert(moduleName.isString)
		return runtimeLock.read {
			assert(modules.hasKey(moduleName))
			modules.mapAt(moduleName)
		}
	}

	/**
	 * Unbind the specified [definition][DefinitionDescriptor] from the runtime
	 * system.
	 *
	 * @param definition
	 *   A definition.
	 */
	@ThreadSafe
	fun removeDefinition(definition: A_Definition)
	{
		runtimeLock.write{
			definition.definitionMethod().removeDefinition(definition)
		}
	}

	/**
	 * Unbind the specified [macro][A_Macro] from the runtime system.
	 *
	 * @param macro
	 *   An [A_Macro] to remove from its [A_Bundle].
	 */
	@ThreadSafe
	fun removeMacro(macro: A_Macro)
	{
		runtimeLock.write{
			macro.definitionBundle().removeMacro(macro)
		}
	}

	/**
	 * Add a semantic restriction to the method associated with the given method
	 * name.
	 *
	 * @param restriction
	 *   A [semantic&#32;restriction][SemanticRestrictionDescriptor] that
	 *   validates the static types of arguments at call sites.
	 */
	fun addSemanticRestriction(restriction: A_SemanticRestriction)
	{
		runtimeLock.safeWrite {
			val method = restriction.definitionMethod()
			method.addSemanticRestriction(restriction)
		}
	}

	/**
	 * Remove a semantic restriction from the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *   A [semantic restriction][SemanticRestrictionDescriptor] that validates
	 *   the static types of arguments at call sites.
	 */
	fun removeTypeRestriction(restriction: A_SemanticRestriction)
	{
		runtimeLock.safeWrite {
			val method = restriction.definitionMethod()
			method.removeSemanticRestriction(restriction)
		}
	}

	/**
	 * Remove a grammatical restriction from the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *   A [grammatical restriction][A_GrammaticalRestriction] that validates
	 *   syntactic restrictions at call sites.
	 */
	fun removeGrammaticalRestriction(restriction: A_GrammaticalRestriction)
	{
		runtimeLock.safeWrite {
			val bundle = restriction.restrictedBundle()
			bundle.removeGrammaticalRestriction(restriction)
		}
	}

	/**
	 * Add a seal to the method associated with the given method name.
	 *
	 * @param methodName
	 *   The method name, an [atom][AtomDescriptor].
	 * @param sealSignature
	 *   The tuple of types at which to seal the method.
	 * @throws MalformedMessageException
	 *   If anything is wrong with the method name.
	 */
	@Throws(MalformedMessageException::class)
	fun addSeal(methodName: A_Atom, sealSignature: A_Tuple)
	{
		assert(methodName.isAtom)
		assert(sealSignature.isTuple)
		runtimeLock.safeWrite {
			val bundle: A_Bundle = methodName.bundleOrCreate()
			val method: A_Method = bundle.bundleMethod()
			assert(method.numArgs() == sealSignature.tupleSize())
			method.addSealedArgumentsType(sealSignature)
		}
	}

	/**
	 * Remove a seal from the method associated with the given method name.
	 *
	 * @param methodName
	 *   The method name, an [atom][AtomDescriptor].
	 * @param sealSignature
	 *   The signature at which to unseal the method. There may be other seals
	 *   remaining, even at this very signature.
	 * @throws MalformedMessageException
	 *   If anything is wrong with the method name.
	 */
	@Throws(MalformedMessageException::class)
	fun removeSeal(methodName: A_Atom, sealSignature: A_Tuple)
	{
		runtimeLock.safeWrite {
			val bundle: A_Bundle = methodName.bundleOrCreate()
			val method: A_Method = bundle.bundleMethod()
			method.removeSealedArgumentsType(sealSignature)
		}
	}

	/**
	 * The [lock][ReentrantLock] that guards access to the Level One
	 * [-safe][levelOneSafeTasks] and [-unsafe][levelOneUnsafeTasks] queues and
	 * counters.
	 *
	 * For example, an [L2Chunk] may not be [invalidated][L2Chunk.invalidate]
	 * while any [fiber][FiberDescriptor] is
	 * [running][FiberDescriptor.ExecutionState.RUNNING] a Level Two chunk.
	 * These two activities are mutually exclusive.
	 */
	private val levelOneSafeLock = ReentrantLock()

	/**
	 * The [queue][Queue] of Level One-safe [tasks][Runnable]. A Level One-safe
	 * task requires that no
	 * [Level&#32;One-unsafe&#32;tasks][levelOneUnsafeTasks] are running.
	 *
	 * For example, a [Level Two chunk][L2Chunk] may not be
	 * [invalidated][L2Chunk.invalidate] while any [fiber][FiberDescriptor] is
	 * [running][FiberDescriptor.ExecutionState.RUNNING] a Level Two chunk.
	 * These two activities are mutually exclusive.
	 */
	private val levelOneSafeTasks: Queue<AvailTask> = ArrayDeque()

	/**
	 * The [queue][Queue] of Level One-unsafe [tasks][Runnable]. A Level
	 * One-unsafe task requires that no [Level One-safe
	 * tasks][levelOneSafeTasks] are running.
	 */
	private val levelOneUnsafeTasks: Queue<AvailTask> = ArrayDeque()

	/**
	 * The number of [Level&#32;One-safe&#32;tasks][levelOneSafeTasks] that have
	 * been [scheduled&#32;for&#32;execution][executor] but have not yet reached
	 * completion.
	 */
	private var incompleteLevelOneSafeTasks = 0

	/**
	 * The number of [Level&#32;One-unsafe&#32;tasks][levelOneUnsafeTasks] that
	 * have been [scheduled&#32;for&#32;execution][executor] but have not yet
	 * reached completion.
	 */
	private var incompleteLevelOneUnsafeTasks = 0

	/**
	 * Has [whenLevelOneUnsafeDo] Level One safety been requested?
	 */
	@Volatile
	private var levelOneSafetyRequested = false

	/**
	 * Has [whenLevelOneUnsafeDo] Level One safety} been requested?
	 *
	 * @return
	 *   `true` if Level One safety has been requested, `false` otherwise.
	 */
	fun levelOneSafetyRequested(): Boolean =  levelOneSafetyRequested

	/**
	 * Request that the specified continuation be executed as a Level One-unsafe
	 * task at such a time as there are no Level One-safe tasks running.
	 *
	 * @param priority
	 *   The priority of the [AvailTask] to queue.  It must be in the range
	 *   [0..255].
	 * @param unsafeAction
	 *   The action to perform when Level One safety is not required.
	 */
	fun whenLevelOneUnsafeDo(priority: Int, unsafeAction: () -> Unit)
	{
		val wrapped = AvailTask(priority)
		{
			try
			{
				unsafeAction()
			}
			catch (e: Exception)
			{
				System.err.println(
					"\n\tException in level-one-unsafe task:\n\t${trace(e)}"
						.trimIndent())
			}
			finally
			{
				levelOneSafeLock.withLock {
					incompleteLevelOneUnsafeTasks--
					if (incompleteLevelOneUnsafeTasks == 0)
					{
						assert(incompleteLevelOneSafeTasks == 0)
						incompleteLevelOneSafeTasks = levelOneSafeTasks.size
						levelOneSafeTasks.forEach(Consumer { this.execute(it) })
						levelOneSafeTasks.clear()
					}
				}
			}
		}
		levelOneSafeLock.withLock {
			// Hasten the execution of pending Level One-safe tasks by
			// postponing this task if there are any Level One-safe tasks
			// waiting to run.
			if (incompleteLevelOneSafeTasks == 0
			    && levelOneSafeTasks.isEmpty())
			{
				assert(!levelOneSafetyRequested)
				incompleteLevelOneUnsafeTasks++
				execute(wrapped)
			}
			else
			{
				levelOneUnsafeTasks.add(wrapped)
			}
		}
	}

	/**
	 * Request that the specified action be executed as a Level One-safe task at
	 * such a time as there are no Level One-unsafe tasks running.
	 *
	 * @param priority
	 *   The priority of the [AvailTask] to queue.  It must be in the range
	 *   [0..255].
	 * @param safeAction
	 *   The action to execute when Level One safety is ensured.
	 */
	fun whenLevelOneSafeDo(priority: Int, safeAction: () -> Unit)
	{
		val task = AvailTask(priority)
		{
			try
			{
				safeAction()
			}
			catch (e: Exception)
			{
				System.err.println(
					"\n\tException in level-one-safe task:\n\t${trace(e)}"
						.trimIndent())
			}
			finally
			{
				levelOneSafeLock.withLock {
					incompleteLevelOneSafeTasks--
					if (incompleteLevelOneSafeTasks == 0)
					{
						assert(incompleteLevelOneUnsafeTasks == 0)
						levelOneSafetyRequested = false
						incompleteLevelOneUnsafeTasks = levelOneUnsafeTasks.size
						for (t in levelOneUnsafeTasks)
						{
							execute(t)
						}
						levelOneUnsafeTasks.clear()
					}
				}
			}
		}
		levelOneSafeLock.withLock {
			levelOneSafetyRequested = true
			if (incompleteLevelOneUnsafeTasks == 0)
			{
				incompleteLevelOneSafeTasks++
				execute(task)
			}
			else
			{
				levelOneSafeTasks.add(task)
			}
		}
	}

	/**
	 * Destroy all data structures used by this `AvailRuntime`.  Also
	 * disassociate it from the current [Thread]'s local storage.
	 */
	fun destroy()
	{
		timer.cancel()
		executor.shutdownNow()
		ioSystem.destroy()
		callbackSystem.destroy()
		try
		{
			executor.awaitTermination(10, TimeUnit.SECONDS)
		}
		catch (e: InterruptedException)
		{
			// Ignore.
		}
		moduleNameResolver.clearCache()
		moduleNameResolver.destroy()
		modules = nil
	}
}
