/*
 * AvailRuntime.kt
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
package avail

import avail.AvailRuntime.Companion.specialObject
import avail.AvailRuntimeConfiguration.availableProcessors
import avail.AvailRuntimeConfiguration.maxInterpreters
import avail.AvailThread.Companion.current
import avail.annotations.ThreadSafe
import avail.builder.ModuleNameResolver
import avail.builder.ModuleRoots
import avail.builder.ResolvedModuleName
import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import avail.descriptor.atoms.A_Atom.Companion.isAtomSpecial
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import avail.descriptor.bundles.A_Bundle.Companion.definitionParsingPlans
import avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import avail.descriptor.bundles.A_Bundle.Companion.removeGrammaticalRestriction
import avail.descriptor.bundles.A_Bundle.Companion.removeMacro
import avail.descriptor.character.CharacterDescriptor
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.decreaseCountdownToReoptimizeFromPoll
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import avail.descriptor.functions.FunctionDescriptor.Companion.newCrashFunction
import avail.descriptor.functions.PrimitiveCompiledCodeDescriptor.Companion.newPrimitiveRawFunction
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.forEach
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.maps.A_Map.Companion.mapWithoutKeyCanDestroy
import avail.descriptor.maps.A_Map.Companion.valuesAsTuple
import avail.descriptor.maps.MapDescriptor
import avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import avail.descriptor.methods.A_Definition
import avail.descriptor.methods.A_Definition.Companion.definitionMethod
import avail.descriptor.methods.A_GrammaticalRestriction
import avail.descriptor.methods.A_Macro
import avail.descriptor.methods.A_Method
import avail.descriptor.methods.A_Method.Companion.addSealedArgumentsType
import avail.descriptor.methods.A_Method.Companion.addSemanticRestriction
import avail.descriptor.methods.A_Method.Companion.bundles
import avail.descriptor.methods.A_Method.Companion.definitionsTuple
import avail.descriptor.methods.A_Method.Companion.numArgs
import avail.descriptor.methods.A_Method.Companion.removeDefinition
import avail.descriptor.methods.A_Method.Companion.removeSealedArgumentsType
import avail.descriptor.methods.A_Method.Companion.removeSemanticRestriction
import avail.descriptor.methods.A_SemanticRestriction
import avail.descriptor.methods.A_Sendable
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom
import avail.descriptor.methods.SemanticRestrictionDescriptor
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.importedNames
import avail.descriptor.module.A_Module.Companion.methodDefinitions
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.A_Module.Companion.moduleState
import avail.descriptor.module.A_Module.Companion.newNames
import avail.descriptor.module.A_Module.Companion.privateNames
import avail.descriptor.module.A_Module.Companion.removeFrom
import avail.descriptor.module.A_Module.Companion.visibleNames
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.module.ModuleDescriptor.State.Loaded
import avail.descriptor.module.ModuleDescriptor.State.Loading
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.two
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Exceptions
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Styles
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.Styles.stylerFunctionType
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerBodyFunctionType
import avail.descriptor.parsing.LexerDescriptor.Companion.lexerFilterFunctionType
import avail.descriptor.pojos.PojoDescriptor.Companion.nullPojo
import avail.descriptor.pojos.RawPojoDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tokens.TokenDescriptor.StaticInit
import avail.descriptor.tokens.TokenDescriptor.TokenType
import avail.descriptor.tuples.A_String
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.tupleFromIntegerList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationMeta
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FiberTypeDescriptor.Companion.fiberMeta
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionMeta
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeReturning
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.anyMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.bytes
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.characterCodePoints
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegersMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.nybbles
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.unsignedShorts
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import avail.descriptor.types.MapTypeDescriptor.Companion.mapMeta
import avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoArrayType
import avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoType
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoSelfType
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoSelfTypeAtom
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClass
import avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClassWithTypeArguments
import avail.descriptor.types.PojoTypeDescriptor.Companion.selfTypeForClass
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.SetTypeDescriptor.Companion.setMeta
import avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.oneOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleMeta
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import avail.descriptor.types.TypeDescriptor
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableMeta
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.Companion.allNumericCodes
import avail.exceptions.AvailRuntimeException
import avail.exceptions.MalformedMessageException
import avail.files.FileManager
import avail.interpreter.Primitive
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.primitive.controlflow.P_InvokeWithTuple
import avail.interpreter.primitive.general.P_EmergencyExit
import avail.interpreter.primitive.general.P_ToString
import avail.io.IOSystem
import avail.io.TextInterface
import avail.io.TextInterface.Companion.systemTextInterface
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.utility.StackPrinter.Companion.trace
import avail.utility.WorkStealingQueue
import avail.utility.evaluation.OnceSupplier
import avail.utility.javaNotifyAll
import avail.utility.javaWait
import avail.utility.safeWrite
import avail.utility.structures.EnumMap.Companion.enumMap
import java.util.Collections
import java.util.Timer
import java.util.TimerTask
import java.util.WeakHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.read
import kotlin.concurrent.withLock
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
	 * An array of AtomicReference<Interpreter>, each of which is initially
	 * null, but is populated as the interpreter thread pool adds more workers.
	 * After an entry has been set, it is never changed or reset to null.
	 */
	private val interpreterHolders = Array(maxInterpreters) {
		AtomicReference<Interpreter>()
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
	 * The [ThreadPoolExecutor] for running tasks and fibers of this
	 * [AvailRuntime].
	 */
	val executor = ThreadPoolExecutor(
		min(availableProcessors, maxInterpreters),
		maxInterpreters,
		10L,
		TimeUnit.SECONDS,
		WorkStealingQueue(maxInterpreters),
		{
			val interpreter = Interpreter(this)
			interpreterHolders[interpreter.interpreterIndex].set(interpreter)
			AvailThread(it, interpreter)
		},
		AbortPolicy())

	/**
	 * Schedule the specified [task][AvailTask] for eventual execution. The
	 * implementation is free to run the task immediately or delay its execution
	 * arbitrarily. The task is guaranteed to execute on an [AvailThread].
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
	fun execute(priority: Int, body: ()->Unit)
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
	 *
	 * Additionally, we help drive the dynamic optimization of [A_RawFunction]s
	 * into [L2Chunk]s by iterating over the existing Interpreters, asking each
	 * one to significantly decrease the countdown for whatever raw function is
	 * running (being careful not to cross zero).
	 */
	val timer = fixedRateTimer("timer for Avail runtime", true, period = 10) {
		clock.increment()
		interpreterHolders.forEach { holder ->
			holder.get()
				?.pollActiveRawFunction()
				?.decreaseCountdownToReoptimizeFromPoll(
					L2Chunk.decrementForPolledActiveCode)
		}
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
				atoms.addAll(module.newNames.valuesAsTuple)
				atoms.addAll(module.visibleNames)
				var atomSets = module.importedNames.valuesAsTuple
				atomSets = atomSets.concatenateWith(
					module.privateNames.valuesAsTuple, true)
				for (atomSet in atomSets)
				{
					atoms.addAll(atomSet)
				}
				for (definition in module.methodDefinitions)
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
				val bundle: A_Bundle = atom.bundleOrNil
				if (bundle.notNil)
				{
					methods.add(bundle.bundleMethod)
				}
			}
			for (method in methods)
			{
				val bundleDefinitions: MutableSet<A_Sendable> =
					method.definitionsTuple.toMutableSet()
				for (bundle in method.bundles)
				{
					val withMacros = bundleDefinitions.toMutableSet()
					withMacros.addAll(bundle.macrosTuple)
					val bundlePlans: A_Map = bundle.definitionParsingPlans
					if (bundlePlans.mapSize != withMacros.size)
					{
						println(
							"Mismatched definitions / plans:\n\t" +
								"bundle = $bundle\n\t" +
								"definitions# = ${withMacros.size}\n\t" +
								"plans# = ${bundlePlans.mapSize}")
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
	 *   if it's within the forbidden avail namespace.
	 */
	@Throws(AvailRuntimeException::class)
	fun lookupJavaType(
		className: A_String,
		classParameters: A_Tuple
	): A_Type
	{
		val rawClass = lookupRawJavaClass(className)
		// Check that the correct number of type parameters have been supplied.
		// Don't bother to check the bounds of the type parameters. Incorrect
		// bounds will cause some method and constructor lookups to fail, but
		// that's fine.
		val typeVars = rawClass.typeParameters
		if (typeVars.size != classParameters.tupleSize)
		{
			throw AvailRuntimeException(
				AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		// Replace all occurrences of the pojo self type atom with actual
		// pojo self types.
		val realParameters: A_Tuple = generateObjectTupleFrom(
			classParameters.tupleSize
		) {
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
	 *   If the class can't be found, or if it's within the forbidden avail
	 *   namespace.
	 */
	fun lookupRawJavaClass(className: A_String): Class<*>
	{
		// Forbid access to the Avail implementation's packages.
		val nativeClassName = className.asNativeString()
		if (nativeClassName.startsWith("avail"))
		{
			throw AvailRuntimeException(
				AvailErrorCode.E_JAVA_CLASS_NOT_AVAILABLE)
		}
		// Look up the raw Java class using the runtime's class loader.
		return try
		{
			Class.forName(className.asNativeString(), true, classLoader)
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
			functionType(tuple(Types.ANY.o), stringType),
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
				bottom),
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
						set(
							AvailErrorCode.E_NO_METHOD,
							AvailErrorCode.E_NO_METHOD_DEFINITION,
							AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION,
							AvailErrorCode.E_FORWARD_METHOD_DEFINITION,
							AvailErrorCode.E_ABSTRACT_METHOD_DEFINITION)),
					Types.METHOD.o,
					mostGeneralTupleType),
				bottom),
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
					mostGeneralTupleType),
				Types.TOP.o),
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
				bottom),
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
					mostGeneralTupleType),
				Types.TOP.o),
			P_InvokeWithTuple);

		/** The name to attach to functions plugged into this hook. */
		private val hookName: A_String = stringFrom(hookName).makeShared()

		/**
		 * A [supplier][OnceSupplier] of a default [A_Function] to use for this
		 * hook type.
		 */
		@Suppress("LeakingThis")
		val defaultFunctionSupplier: OnceSupplier<A_Function> =
			produceDefaultFunctionSupplier(hookName, primitive)

		private fun produceDefaultFunctionSupplier(
			hookName: String,
			primitive: Primitive?
		): OnceSupplier<A_Function> = when (primitive)
		{
			null ->
			{
				// Create an invocation of P_EmergencyExit.
				val argumentsTupleType = functionType.argsTupleType
				val argumentTypesTuple =
					argumentsTupleType.tupleOfTypesFromTo(
						1,
						argumentsTupleType.sizeRange.upperBound.extractInt)
				OnceSupplier {
					newCrashFunction(hookName, argumentTypesTuple).makeShared()
				}
			}
			else ->
			{
				val code = newPrimitiveRawFunction(primitive, nil, 0)
				code.methodName = this.hookName
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
			runtime.hooks[this]!!.function

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
			function.code().methodName = hookName
			runtime.hooks[this]!!.function = function.makeShared()
		}
	}

	/** A volatile holder, to ensure visibility of writes to readers. */
	data class VolatileHookEntry(
		/** The assignable function for some hook. */
		@Volatile
		var function: A_Function)

	/** The collection of hooks for this runtime. */
	val hooks = enumMap { hook: HookType ->
		VolatileHookEntry(hook.defaultFunctionSupplier())
	}

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
	 * An object that gets notified when the last fiber of the runtime has been
	 * [unregistered][unregisterFiber].
	 */
	private val noFibersMonitor = Any()

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
		// If a race happens between these lines, due to a new fiber starting,
		// we're already in the realm of unspecified behavior.  Even if waiters
		// don't get notified because the momentary emptiness isn't detected,
		// they'll still get notified when the last fiber actually ends.
		//
		// HOWEVER, if the last fiber disappears due to garbage collection
		// instead of fiber completion, there will be no corresponding signal.
		// This circumstance doesn't appear to be something that would actually
		// happen in a properly functioning VM, however.  Also, a fiber lost to
		// garbage collection would have (if not for garbage collection) stuck
		// around in this set infinitely long anyhow, so this behavior seems
		// reasonable.
		if (allFibers.isEmpty())
		{
			synchronized(noFibersMonitor)
			{
				noFibersMonitor.javaNotifyAll()
			}
		}
	}

	/**
	 * Block the current [Thread] until there are no fibers that can run.  If
	 * nothing outside the VM is actively creating and running new fibers, this
	 * is a permanent state.  After this method returns, there will be no more
	 * [AvailTask]s added to the executor.
	 *
	 * Note (MvG, 2021-07-23): This mechanism will have to be revisited if we
	 * rework the [L2Chunk] optimizer to queue optimization tasks, which may run
	 * even after the last fiber has ended.  Wrapping those tasks in their own
	 * fibers would be a sufficient solution.
	 */
	fun awaitNoFibers()
	{
		synchronized(noFibersMonitor)
		{
			while (allFibers.isNotEmpty())
			{
				noFibersMonitor.javaWait()
			}
		}
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
	val runtimeLock = ReentrantReadWriteLock()

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
		private class NumericBuilder
		{
			/** The list of [AvailObject]s being built. */
			private val list = mutableListOf<AvailObject>()

			/** Verify that we're at the expected index. */
			fun at(position: Int) = assert(list.size == position)

			/** Add an item to the end. */
			fun put(value: A_BasicObject) = list.add(value.makeShared())

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
			put(mostGeneralVariableType)
			put(mostGeneralVariableMeta)
			put(mostGeneralContinuationType)

			at(10)
			put(continuationMeta)
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
			put(mostGeneralObjectType)
			put(mostGeneralObjectMeta)
			put(Exceptions.exceptionType)
			put(mostGeneralFiberType())
			put(mostGeneralSetType())
			put(setMeta())
			put(stringType)
			put(bottom)

			at(30)
			put(bottomMeta)
			put(Types.NONTYPE.o)
			put(mostGeneralTupleType)
			put(tupleMeta)
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
			put(Exceptions.stackDumpAtom)

			at(50)
			put(PhraseKind.PARSE_PHRASE.mostGeneralType)
			put(PhraseKind.SEQUENCE_PHRASE.mostGeneralType)
			put(PhraseKind.EXPRESSION_PHRASE.mostGeneralType)
			put(PhraseKind.ASSIGNMENT_PHRASE.mostGeneralType)
			put(PhraseKind.BLOCK_PHRASE.mostGeneralType)
			put(PhraseKind.LITERAL_PHRASE.mostGeneralType)
			put(PhraseKind.REFERENCE_PHRASE.mostGeneralType)
			put(PhraseKind.SEND_PHRASE.mostGeneralType)
			put(instanceMeta(mostGeneralLiteralTokenType()))
			put(PhraseKind.LIST_PHRASE.mostGeneralType)

			at(60)
			put(PhraseKind.VARIABLE_USE_PHRASE.mostGeneralType)
			put(PhraseKind.DECLARATION_PHRASE.mostGeneralType)
			put(PhraseKind.ARGUMENT_PHRASE.mostGeneralType)
			put(PhraseKind.LABEL_PHRASE.mostGeneralType)
			put(PhraseKind.LOCAL_VARIABLE_PHRASE.mostGeneralType)
			put(PhraseKind.LOCAL_CONSTANT_PHRASE.mostGeneralType)
			put(PhraseKind.MODULE_VARIABLE_PHRASE.mostGeneralType)
			put(PhraseKind.MODULE_CONSTANT_PHRASE.mostGeneralType)
			put(PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE.mostGeneralType)
			put(anyMeta())

			at(70)
			put(trueObject)
			put(falseObject)
			put(zeroOrMoreOf(stringType))
			put(zeroOrMoreOf(topMeta()))
			put(
				zeroOrMoreOf(
					setTypeForSizesContentType(wholeNumbers, stringType)))
			put(setTypeForSizesContentType(wholeNumbers, stringType))
			put(functionType(tuple(naturalNumbers), bottom))
			put(emptySet)
			put(negativeInfinity)
			put(positiveInfinity)

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
			put(variableTypeFor(mostGeneralContinuationType))
			put(
				mapTypeForSizesKeyTypeValueType(
					wholeNumbers, Types.ATOM.o, Types.ANY.o))
			put(
				mapTypeForSizesKeyTypeValueType(
					wholeNumbers, Types.ATOM.o, anyMeta()))
			put(
				tupleTypeForSizesTypesDefaultType(
					wholeNumbers,
					emptyTuple(),
					tupleTypeForSizesTypesDefaultType(
						singleInt(2),
						emptyTuple(),
						Types.ANY.o)))
			put(emptyMap)
			put(
				mapTypeForSizesKeyTypeValueType(
					naturalNumbers, Types.ANY.o, Types.ANY.o))
			put(instanceMeta(wholeNumbers))
			put(setTypeForSizesContentType(naturalNumbers, Types.ANY.o))

			at(100)
			put(
				tupleTypeForSizesTypesDefaultType(
					wholeNumbers, emptyTuple, mostGeneralTupleType))
			put(nybbles)
			put(zeroOrMoreOf(nybbles))
			put(unsignedShorts)
			put(emptyTuple)
			put(functionType(tuple(bottom), Types.TOP.o))
			put(instanceType(zero))
			put(functionTypeReturning(topMeta()))
			put(
				tupleTypeForSizesTypesDefaultType(
					wholeNumbers,
					emptyTuple(),
					functionTypeReturning(topMeta())))
			put(
				functionTypeReturning(
					PhraseKind.PARSE_PHRASE.mostGeneralType))

			at(110)
			put(instanceType(two))
			put(fromDouble(Math.E))
			put(instanceType(fromDouble(Math.E)))
			put(
				instanceMeta(
					PhraseKind.PARSE_PHRASE.mostGeneralType))
			put(
				setTypeForSizesContentType(
					wholeNumbers, Types.ATOM.o))
			put(Types.TOKEN.o)
			put(mostGeneralLiteralTokenType())
			put(zeroOrMoreOf(anyMeta()))
			put(inclusive(zero, positiveInfinity))
			put(
				zeroOrMoreOf(
					tupleTypeForSizesTypesDefaultType(
						singleInt(2),
						tuple(Types.ATOM.o), anyMeta())))

			at(120)
			put(
				zeroOrMoreOf(
					tupleTypeForSizesTypesDefaultType(
						singleInt(2),
						tuple(Types.ATOM.o),
						Types.ANY.o)))
			put(zeroOrMoreOf(PhraseKind.PARSE_PHRASE.mostGeneralType))
			put(zeroOrMoreOf(PhraseKind.ARGUMENT_PHRASE.mostGeneralType))
			put(zeroOrMoreOf(PhraseKind.DECLARATION_PHRASE.mostGeneralType))
			put(variableReadWriteType(Types.TOP.o, bottom))
			put(zeroOrMoreOf(PhraseKind.EXPRESSION_PHRASE.create(Types.ANY.o)))
			put(PhraseKind.EXPRESSION_PHRASE.create(Types.ANY.o))
			put(
				functionType(
					tuple(pojoTypeForClass(Throwable::class.java)), bottom))
			put(
				zeroOrMoreOf(
					setTypeForSizesContentType(wholeNumbers, Types.ATOM.o)))
			put(bytes)

			at(130)
			put(zeroOrMoreOf(zeroOrMoreOf(anyMeta())))
			put(variableReadWriteType(extendedIntegers, bottom))
			put(fiberMeta())
			put(nonemptyStringType)
			put(
				setTypeForSizesContentType(
					wholeNumbers,
					Exceptions.exceptionType))
			put(setTypeForSizesContentType(naturalNumbers, stringType))
			put(setTypeForSizesContentType(naturalNumbers, Types.ATOM.o))
			put(oneOrMoreOf(Types.ANY.o))
			put(zeroOrMoreOf(integers))
			put(
				tupleTypeForSizesTypesDefaultType(
					integerRangeType(fromInt(2), true, positiveInfinity, false),
					emptyTuple(),
					Types.ANY.o))

			// Some of these entries may need to be shuffled into earlier
			// slots to maintain reasonable topical consistency.)

			at(140)
			put(PhraseKind.FIRST_OF_SEQUENCE_PHRASE.mostGeneralType)
			put(PhraseKind.PERMUTED_LIST_PHRASE.mostGeneralType)
			put(PhraseKind.SUPER_CAST_PHRASE.mostGeneralType)
			put(SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom)
			put(SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom)
			put(SpecialAtom.ALL_TOKENS_KEY.atom)
			put(int32)
			put(int64)
			put(PhraseKind.STATEMENT_PHRASE.mostGeneralType)
			put(SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom)

			at(150)
			put(PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType)
			put(oneOrMoreOf(naturalNumbers))
			put(zeroOrMoreOf(Types.DEFINITION.o))
			put(
				mapTypeForSizesKeyTypeValueType(
					wholeNumbers, stringType, Types.ATOM.o))
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
			put(inclusive(1L, 4L))
			put(inclusive(0L, 31L))
			put(
				continuationTypeForFunctionType(
					functionTypeReturning(Types.TOP.o)))
			put(CharacterDescriptor.nonemptyStringOfDigitsType)

			at(170)
			put(
				tupleTypeForTypes(
					zeroOrOneOf(PhraseKind.SEND_PHRASE.mostGeneralType),
					stringType))
			put(stylerFunctionType)
			put(
				enumerationWith(
					set(
						TokenType.WHITESPACE.atom,
						TokenType.COMMENT.atom,
						TokenType.OPERATOR.atom,
						TokenType.KEYWORD.atom,
						TokenType.END_OF_FILE.atom)))
			put(zeroOrMoreOf(Types.TOKEN.o))
			put(PhraseKind.MARKER_PHRASE.mostGeneralType)
			put(
				oneOrMoreOf(
					tupleTypeForTypes(
						// Imported module name (Uses).
						stringType,
						// Optional import names list.
						zeroOrOneOf(
							// Import names list.
							tupleTypeForTypes(
								zeroOrMoreOf(
									tupleTypeForTypes(
										// Negated import.
										booleanType,
										// Imported name.
										nonemptyStringType,
										// Optional rename.
										zeroOrOneOf(nonemptyStringType))),
								// Wildcard.
								booleanType)))))
			put(PhraseKind.SEQUENCE_AS_EXPRESSION_PHRASE.mostGeneralType)

			at(177)
		}.list().onEach { assert(!it.isAtom || it.isAtomSpecial) }

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
		 * The special [atoms][AtomDescriptor] known to the
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
			put(Exceptions.exceptionAtom)
			put(Exceptions.stackDumpAtom)
			put(pojoSelfTypeAtom())
			put(TokenType.END_OF_FILE.atom)
			put(TokenType.KEYWORD.atom)
			put(TokenType.LITERAL.atom)
			put(TokenType.OPERATOR.atom)
			put(TokenType.COMMENT.atom)
			put(TokenType.WHITESPACE.atom)
			put(StaticInit.tokenTypeOrdinalKey)
			put(Styles.subclassAtom)
			put(Styles.semanticClassifierAtom)
			put(Styles.methodNameAtom)
			put(Styles.sourceModuleAtom)
			put(Styles.generatedAtom)
			put(Styles.lineNumberAtom)
		}.list().onEach { assert(it.isAtomSpecial) }
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
		assert(module.moduleState == Loading)
		module.moduleState = Loaded

		runtimeLock.safeWrite {
			assert(!includesModuleNamed(module.moduleName))
			modules = modules.mapAtPuttingCanDestroy(
				module.moduleName, module, true
			).makeShared()
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
			assert(includesModuleNamed(module.moduleName))
			modules = modules.mapWithoutKeyCanDestroy(
				module.moduleName, true
			).makeShared()
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
		runtimeLock.safeWrite {
			definition.definitionMethod.removeDefinition(definition)
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
		runtimeLock.safeWrite {
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
			val method: A_Method = bundle.bundleMethod
			assert(method.numArgs == sealSignature.tupleSize)
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
			val method: A_Method = bundle.bundleMethod
			method.removeSealedArgumentsType(sealSignature)
		}
	}

	/**
	 * The [ReentrantLock] that guards access to the [safePointTasks] and
	 * [interpreterTasks] and their associated incomplete execution counters.
	 *
	 * For example, an [L2Chunk] may not be invalidated while any [A_Fiber] is
	 * actively running. These two activities are mutually exclusive.
	 */
	private val safePointLock = ReentrantLock()

	/**
	 * The [List] of tasks to run at the next safe point.  Such tasks may only
	 * execute when there are no [interpreterTasks] running.
	 *
	 * For example, an [L2Chunk] may not be invalidated while any [A_Fiber] is
	 * actively running. These two activities are mutually exclusive.
	 */
	@GuardedBy("safePointLock")
	private val safePointTasks = mutableListOf<AvailTask>()

	/**
	 * The [List] of tasks that run when interpreters are allowed to run.  This
	 * includes tasks that actually run fibers, but may include any other task
	 * which must not execute at the same time as any [safePointTasks].
	 */
	@GuardedBy("safePointLock")
	private val interpreterTasks = mutableListOf<AvailTask>()

	/**
	 * The number of [safePointTasks] that have been scheduled for
	 * [execution][executor] but have not yet reached completion.  This also
	 * includes safe-point tasks that have been added to the executor but not
	 * yet started.
	 */
	@GuardedBy("safePointLock")
	private var incompleteSafePointTasks = 0

	/**
	 * The number of [interpreterTasks] that have been scheduled for
	 * [execution][executor] but have not yet reached completion.  This also
	 * includes interpreter tasks that have been added to the executor but not
	 * yet started.
	 */
	@GuardedBy("safePointLock")
	private var incompleteInterpreterTasks = 0

	/**
	 * Has a safe-point been requested?
	 */
	@Volatile
	private var safePointRequested = false

	/**
	 * Has a safe point been requested?  During a safe point, no interpreters
	 * are allowed to be running fibers.
	 *
	 * @return
	 *   `true` if a safe point has been requested, `false` otherwise.
	 */
	fun safePointRequested(): Boolean = safePointRequested

	/**
	 * Assert that we're currently inside a safe point.  This should only be
	 * checked while running a safe point task (good) or an interpreter task
	 * (should fail).
	 */
	fun assertInSafePoint()
	{
		val isSafe = safePointLock.withLock { incompleteSafePointTasks > 0 }
		assert(isSafe)
	}

	/**
	 * Request that the specified [action] be executed when interpreter tasks
	 * are allowed to run.
	 *
	 * @param priority
	 *   The priority of the [AvailTask] to queue.  It must be in the range
	 *   [0..255].
	 * @param action
	 *   The action to perform when interpreters may run.
	 */
	fun whenRunningInterpretersDo(priority: Int, action: ()->Unit)
	{
		val wrapped = AvailTask(priority)
		{
			try
			{
				action()
			}
			catch (e: Exception)
			{
				System.err.println(
					"\n\tException in running-interpreter task:\n\t${trace(e)}"
						.trimIndent())
			}
			finally
			{
				safePointLock.withLock {
					incompleteInterpreterTasks--
					if (incompleteInterpreterTasks == 0)
					{
						assert(incompleteSafePointTasks == 0)
						incompleteSafePointTasks = safePointTasks.size
						// Run all queued safe point tasks sequentially.
						safePointTasks.forEach(this::execute)
						safePointTasks.clear()
					}
				}
			}
		}
		safePointLock.withLock {
			// Hasten the execution of safe-point tasks by postponing this task
			// if there are any safe-point tasks waiting to run.
			if (incompleteSafePointTasks == 0 && safePointTasks.isEmpty())
			{
				assert(!safePointRequested)
				incompleteInterpreterTasks++
				execute(wrapped)
			}
			else
			{
				interpreterTasks.add(wrapped)
			}
		}
	}

	/**
	 * Request that the specified action be executed at the next safe point.
	 * No interpreter tasks are running at a safe point.
	 *
	 * @param priority
	 *   The priority of the [AvailTask] to queue.  It must be in the range
	 *   [0..255].
	 * @param safeAction
	 *   The action to execute at the next safe point.
	 */
	fun whenSafePointDo(priority: Int, safeAction: ()->Unit)
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
					"\n\tException in safe point task:\n\t${trace(e)}"
						.trimIndent())
			}
			finally
			{
				safePointLock.withLock {
					incompleteSafePointTasks--
					if (incompleteSafePointTasks == 0)
					{
						assert(incompleteInterpreterTasks == 0)
						safePointRequested = false
						incompleteInterpreterTasks = interpreterTasks.size
						interpreterTasks.forEach(this::execute)
						interpreterTasks.clear()
					}
				}
			}
		}
		safePointLock.withLock {
			safePointRequested = true
			if (incompleteInterpreterTasks == 0)
			{
				incompleteSafePointTasks++
				execute(task)
			}
			else
			{
				safePointTasks.add(task)
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
		modules = nil
	}

	/**
	 * A call-out to allow tools like debuggers to intercept breakpoints. Only
	 * one breakpoint handler can be active for this runtime.  The function will
	 * be invoked from within a safe point.
	 */
	@Volatile
	var breakpointHandler: (A_Fiber)->Unit = {  }
}
