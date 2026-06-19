/*
 * L2_BIT_LOGIC_OP.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
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

package avail.interpreter.levelTwo.operation.numbers

import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.typeIntersection
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.JavaLibrary.intMaxMethod
import avail.interpreter.JavaLibrary.intMinMethod
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.L2WriteIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT_INT
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.RuleEvaluationContext
import avail.interpreter.levelTwo.operation.numbers.Pattern.BinaryOp
import avail.interpreter.levelTwo.operation.numbers.Pattern.C
import avail.interpreter.levelTwo.operation.numbers.Pattern.Constant
import avail.interpreter.levelTwo.operation.numbers.Pattern.Constant.K1
import avail.interpreter.levelTwo.operation.numbers.Pattern.Constant.K2
import avail.interpreter.levelTwo.operation.numbers.Pattern.Constant.K3
import avail.interpreter.levelTwo.operation.numbers.Pattern.Constant.K4
import avail.interpreter.levelTwo.operation.numbers.Pattern.Variable
import avail.interpreter.levelTwo.operation.numbers.Pattern.Variable.W
import avail.interpreter.levelTwo.operation.numbers.Pattern.Variable.X
import avail.interpreter.levelTwo.operation.numbers.Pattern.Variable.Y
import avail.interpreter.levelTwo.operation.numbers.Pattern.Variable.Z
import avail.interpreter.levelTwo.register.INTEGER_KIND
import avail.interpreter.primitive.Primitive
import avail.interpreter.primitive.integers.P_BitShiftLeft
import avail.interpreter.primitive.integers.P_BitShiftRight
import avail.interpreter.primitive.integers.P_BitwiseAnd
import avail.interpreter.primitive.integers.P_BitwiseOr
import avail.interpreter.primitive.integers.P_BitwiseXor
import avail.interpreter.primitive.numbers.P_Addition
import avail.interpreter.primitive.numbers.P_Division
import avail.interpreter.primitive.numbers.P_Multiplication
import avail.interpreter.primitive.numbers.P_Subtraction
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2ControlFlowGraph
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2GeneratorInterface.Companion.readTwoInts
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticBoxedValue.Companion.unboxedInt
import avail.optimizer.values.L2SemanticUnboxedInt.Companion.boxed
import avail.utility.cast
import org.objectweb.asm.Opcodes
import kotlin.math.max
import kotlin.math.min

/**
 * My instances are logic operations that take two [Int]s and produce an [Int].
 * They must not overflow.
 *
 * @constructor
 *   Instantiate a two-argument [i32] logic operation for a particular purpose,
 *   such as addition or exclusive-or.  Some operations require stronger
 *   restrictions on the inputs.
 * @property bitOperation
 *   The [BitOperation] to perform on the [i32]s.
 * @property input1
 *   The source of the first operand.
 * @property input2
 *   The source of the second operand.
 * @property output
 *   Where to write the result of the logical operation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_BIT_LOGIC_OP
constructor(
	operation: BitOperation,
	var input1: L2ReadIntOperand,
	var input2: L2ReadIntOperand,
	var output: L2WriteIntOperand
): L2Instruction()
{
	var bitOperation = L2ArbitraryConstantOperand(operation)

	override val name get() = "${super.name}(${bitOperation.constant.name})"

	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		ignoreMisconnections: Boolean,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(' ')
		append(output.registerString())
		append(" ← ")
		append(input1.registerString())
		append(" $bitOperation ")
		append(input2.registerString())
	}

	override fun isBitLogicOperation(op: BitOperation) =
		bitOperation.constant == op

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// Handle fresh folding opportunities that arise from passes like code
		// splitting.
		input1.constantOrNull?.let { constant1 ->
			input2.constantOrNull?.let { constant2 ->
				// Calculate it now (i.e., fold the constants).
				val folded = bitOperation.constant.folder(
					constant1.extractInt,
					constant2.extractInt)
				+L2_MOVE_CONSTANT_INT(L2IntImmediateOperand(folded), output)
				return
			}
		}
		val newBound =
			bitOperation.constant.bounder(input1.type(), input2.type())
		+L2_BIT_LOGIC_OP(
			bitOperation.constant,
			input1,
			input2,
			L2WriteIntOperand(
				output.semanticValues(),
				output.restriction().intersectionWithType(newBound)))
	}

	override fun JVMTranslator.translateToJVM()
	{
		// :: output = input1 op input2;
		load(input1)
		load(input2)
		bitOperation.constant.jvmGenerator(this)
		store(output.register())
	}

	class RulesSyntax constructor(
		val thisOperation: BitOperation,
		val rules: MutableList<Rule> = mutableListOf())
	{
		fun rule(
			left: Pattern,
			right: Pattern,
			condition: RuleEvaluationContext.() -> Boolean = { true },
			producer: RuleEvaluationContext.() -> Pattern)
		{
			val pattern = thisOperation(left, right)
			rules.add(Rule(pattern, producer))
		}
	}

	/**
	 * A [RuleEvaluationContext] is created when attempting to apply rules to an
	 * actual control flow graph.  The corresponding matched [Pattern] is used
	 * to determine the sources ([L2ReadIntOperand]s) of [Variable]s, and the
	 * [Int] values of [Constant]s.  This context is then used as an implied
	 * receiver during the rule evaluation, allowing access to [k1]..[k4] for
	 * the int values that were captured due to appearing as [K1]..[K4] in the
	 * match [Pattern], as well as the analagous [L2ReadIntOperand]s in [w]..[z]
	 * corresponding with variables [W]..[Z] in the pattern.  The rule lambda
	 * then produces a new transformed [Pattern], or `null` if it was determined
	 * by the lambda to be inapplicable.
	 *
	 * @constructor
	 *   Constructed by an optimizer performing transformations on the
	 *   [L2ControlFlowGraph], for the purpose of rewriting [Int] calculations
	 *   to be more efficient.
	 *
	 * @property variablesMap
	 *   A [Map] from [Variable] to [L2ReadIntOperand], populated for all
	 *   [Variable]s that occured in the match [Pattern].
	 * @property constantsMap
	 *   A [Map] from [Constant] to [Int] from the actual [L2ControlFlowGraph],
	 *   populated for each [Constant] that occured in the match [Pattern].
	 */
	class RuleEvaluationContext(
		private val variablesMap: Map<Variable, L2ReadIntOperand>,
		private val constantsMap: Map<Constant, Int>)
	{
		val k1: Int get() = constantsMap[K1]!!
		val k2: Int get() = constantsMap[K2]!!
		val k3: Int get() = constantsMap[K3]!!
		val k4: Int get() = constantsMap[K4]!!
		val w: L2ReadIntOperand get() = variablesMap[W]!!
		val x: L2ReadIntOperand get() = variablesMap[X]!!
		val y: L2ReadIntOperand get() = variablesMap[Y]!!
		val z: L2ReadIntOperand get() = variablesMap[Z]!!
		val L2ReadIntOperand.low: Int get() = type().lowerBound.extractInt
		val L2ReadIntOperand.high: Int get() = type().upperBound.extractInt
	}

	/**
	 * The meat of an [L2_BIT_LOGIC_OP], controlling what JVM code gets
	 * generated, and what reductions are allowed.
	 *
	 * @constructor
	 *   Create a new [BitOperation] enumeration value.
	 * @property jvmGenerator
	 *   A function that emits JVM code to process the two int values that have
	 *   already been pushed.  It should have the effect of removing them from
	 *   the JVM operand stack and leaving a single int as the result.
	 * @property folder
	 *   A function that folds this operation on two constants.
	 * @property bounder
	 *   A function for computing a type bound on the result, given type bounds
	 *   for the two arguments.
	 * @property ruleCreator
	 *   A lambda that is able to produce rules, via [RulesSyntax], once all the
	 *   enum values have been created, since rules for an operation refer to
	 *   other operations, sometimes cyclically.
	 */
	enum class BitOperation private constructor(
		val jvmGenerator: JVMTranslator.()->Unit,
		val folder: (Int, Int)->Int,
		val bounder: (A_Type, A_Type)->A_Type,
		private val ruleCreator: RulesSyntax.()->Unit)
	{
		/**
		 * Compute the bit-wise [Int.and] of two [Int]s.
		 */
		And(Opcodes.IAND, Int::and, P_BitwiseAnd::binaryBound, {
			rule(C(0), X) { C(0) }
			rule(X, C(0)) { C(0) }
			rule(C(-1), X) { X }
			rule(X, C(-1)) { X }
			rule(X, X) { X }
			rule(And(K1, X), K2) { And(X, C(k1 and k2)) }
			rule(And(X, K1), K2) { And(X, C(k1 and k2)) }
			rule(K1, And(K2, X)) { And(X, C(k1 and k2)) }
			rule(K1, And(X, K2)) { And(X, C(k1 and k2)) }
		}),

		/**
		 * Compute the bit-wise [Int.or] of two [Int]s.
		 */
		Or(Opcodes.IOR, Int::or, P_BitwiseOr::binaryBound, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			rule(C(-1), X) { C(-1) }
			rule(X, C(-1)) { C(-1) }
			rule(X, X) { X }
			rule(Or(K1, X), K2) { Or(X, C(k1 or k2)) }
			rule(Or(X, K1), K2) { Or(X, C(k1 or k2)) }
			rule(K1, Or(K2, X)) { Or(X, C(k1 or k2)) }
			rule(K1, Or(X, K2)) { Or(X, C(k1 or k2)) }
		}),

		/**
		 * Compute the bit-wise [Int.xor] of two [Int]s.
		 */
		Xor(Opcodes.IXOR, Int::xor, P_BitwiseXor::binaryBound, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			rule(X, X) { C(0) }
			rule(X, Xor(X, Y)) { Y }
			rule(X, Xor(Y, X)) { Y }
			rule(Xor(X, Y), X) { Y }
			rule(Xor(Y, X), X) { Y }
		}),

		/**
		 * Compute the bit-wise sum of two [Int]s, wrapping around with 2's
		 * complement semantics as needed.
		 */
		Add(Opcodes.IADD, Int::plus, P_Addition::binaryBound, {
			rule(C(0), X) { X }
			rule(X, C(0)) { X }
			// Bring together constants.
			rule(K1, Add(X, K2)) { Add(X, C(k1 + k2)) }
			rule(Add(X, K1), K2) { Add(X, C(k1 + k2)) }
			rule(X, X) { Shl(X, C(1)) }
			rule(X, Mul(X, K1)) { Mul(X, C(k1 + 1)) }
			rule(Mul(X, K1), X) { Mul(X, C(k1 + 1)) }
			rule(Mul(X, K1), Mul(X, K2)) { Mul(X, C(k1 + k2)) }
		}),

		/**
		 * Compute the difference of two [Int]s (the first minus the second),
		 * wrapping around with 2's complement semantics as needed.
		 */
		Sub(Opcodes.ISUB, Int::minus, P_Subtraction::binaryBound, {
			rule(X, C(0)) { X }
			rule(X, K1) { Add(X, C(-k1)) }
			rule(Sub(X, K1), K2) { Add(X, C(-k1 - k2)) }
			rule(Add(X, K1), K2) { Add(X, C(k1 - k2)) }
			rule(K1, Sub(X, K2)) { Sub(C(k1 + k2), X) }
			rule(K1, Add(X, K2)) { Sub(C(k1 - k2), X) }
			rule(X, Sub(Y, Z)) { Sub(Add(X, Z), Y) }
			// x - (y * (x / y))  --> x mod y
			rule(X, Mul(Y, Div(X, Y))) { Mod(X, Y) }
			rule(X, Mul(Div(X, Y), Y)) { Mod(X, Y) }
		}),

		/**
		 * Compute the product of two [Int]s, wrapping around with 2's
		 * complement semantics as needed.
		 */
		Mul(Opcodes.IMUL, Int::times, P_Multiplication::binaryBound, {
			rule(X, C(-1)) { Sub(C(0), X) }
			rule(C(-1), X) { Sub(C(0), X) }
			rule(X, C(0)) { C(0) }
			rule(C(0), X) { C(0) }
			rule(X, C(1)) { X }
			rule(C(1), X) { X }
			rule(K1, X, {k1.takeLowestOneBit() == k1}) {
				Shl(X, C(k1.countTrailingZeroBits()))
			}
			rule(X, K1, {k1.takeLowestOneBit() == k1}) {
				Shl(X, C(k1.countTrailingZeroBits()))
			}
		}),

		/**
		 * Compute the ratio of two [Int]s (i.e., intA / intB), wrapping around
		 * with 2's complement semantics as needed.  The numerator must be ≥ 0,
		 * and the denominator must be > 0.
		 */
		Div(Opcodes.IDIV, Int::div, P_Division::binaryBound, {
			rule(C(0), X) { C(0) }
			rule(X, C(1)) { X }
			rule(X, K1, {k1.takeLowestOneBit() == k1}) {
				Shr(X, C(k1.countTrailingZeroBits()))
			}
		}),

		/**
		 * Compute the remainder after dividng two [Int]s (i.e., `intA - (intA /
		 * intB)`), wrapping around with 2's complement semantics as needed.
		 * The numerator must be ≥ 0, and the denominator must be > 0.
		 */
		Mod(Opcodes.IREM, Int::mod, ::modBound, {
			rule(C(0), X) { C(0) }
			rule(C(1), X) { C(1) }
			rule(X, X) { C(0) }
			rule(Mul(X, Y), X) { C(0) }
			rule(Mul(Y, X), X) { C(0) }
			rule(X, K1, {k1.takeLowestOneBit() == k1}) { And(X, C(k1 - 1)) }
			// X mod (k<<z) where k=2^n --> X & ((k<<z)-1)
			rule(X, Shl(K1, Z), {k1.takeLowestOneBit() == k1}) {
				And(X, Sub(Shl(K1, Z), C(1)))
			}
		}),

		/**
		 * Shift an [Int] rightward by the specified number of bit positions,
		 * treating it as unsigned.  The second operand should be between 0 and
		 * 31, otherwise only the bottom five bits will be used.  The result can
		 * be negative if the first argument is negative and the shift is zero.
		 */
		Ushr(Opcodes.IUSHR, Int::ushr, ::ushrBound, {
			rule(X, C(0)) { X }
			rule(Ushr(X, K1), K2, {k1 + k2 <= 31}) { Ushr(X, C(k1 + k2)) }
			rule(Ushr(X, K1), K2, {k1 + k2 > 31}) { C(0) }
		}),

		/**
		 * Shift an [Int] rightward by the specified number of bit positions,
		 * respecting its sign.  The second operand should be between 0 and 31,
		 * otherwise only the bottom five bits will be used.
		 */
		Shr(Opcodes.ISHR, Int::shr, P_BitShiftRight::binaryBound, {
			rule(X, C(0)) { X }
			rule(Shr(X, K1), K2, {k1 + k2 <= 31}) { Shr(X, C(k1 + k2)) }
			rule(X, Y, {x.type().isSubtypeOf(inclusive(-1, 0))}) { X }
		}),

		/**
		 * Shift an [Int] leftward by the specified number of bit positions,
		 * respecting its sign.  The second operand should be between 0 and 31,
		 * otherwise only the bottom five bits will be used.
		 */
		Shl(Opcodes.ISHL, Int::shl, P_BitShiftLeft::binaryBound, {
			rule(X, C(0)) { X }
			rule(Shl(X, K1), K2, {k1 + k2 <= 31}) { Shl(X, C(k1 + k2)) }
			rule(Shr(X, K1), K1) { And(X, C(-1 shl k1)) }
			rule(Ushr(X, K1), K1) { And(X, C(-1 shl k1)) }
			rule(X, Y, {x.type().isSubtypeOf(inclusive(-1, 0))}) { X }
		}),

		/**
		 * Select the larger of the two [Int]s.
		 */
		Max(intMaxMethod::generateCall, Math::max, ::maxBound, {
			rule(X, X) { X }
			rule(K1, K2) { C(max(k1, k2)) }
			rule(X, Y, {x.high <= y.low}) { Y }
			rule(X, Y, {x.low >= y.high}) { X }
		}),

		/**
		 * Select the smaller of the two [Int]s.
		 */
		Min(intMinMethod::generateCall, Math::min, ::minBound, {
			rule(X, X) { X }
			rule(K1, K2) { C(min(k1, k2)) }
			rule(X, Y, {x.high <= y.low}) { X }
			rule(X, Y, {x.low >= y.high}) { Y }
		}),

		/**
		 * Select a single bit into either 0 or 1 as the output.  This is done
		 * as a shift and mask.  B must be non-negative, but may exceed 31, at
		 * which point the replicated sign bit of A is used.
		 */
		SelectBit(
			jvmGenerator = {
				// :: a, b
				method.visitLdcInsn(31)
				// :: a, b, 31
				intMinMethod.generateCall(this)
				// :: a, min(b,31)
				method.visitInsn(Opcodes.IUSHR)
				// :: a>>min(b,31)
				method.visitLdcInsn(1)
				// :: a>>min(b,31), 1
				method.visitInsn(Opcodes.IAND)
				// :: (a>>min(b,31)) & 1
			},
			folder = { a, b -> (a ushr min(b, 31)) and 1 },
			bounder = { a, b -> enumerationWith(set(zero, one)) },
			ruleCreator = {
				rule(X, C(0)) { And(X, C(1)) }
			})

		;

		/**
		 * Construct a [BitOperation] using a simple JVM opcode.
		 *
		 * @constructor
		 *   Create a new [BitOperation] enumeration value.
		 * @param jvmOpcode
		 *   An Int opcode to emit for this logic operation, once the two int
		 *   values have been pushed.  It should have the effect of removing
		 *   them from the JVM operand stack and leaving a single int as the
		 *   result.
		 * @param folder
		 *   A function that folds this operation on two constants.
		 * @param bounder
		 *   A function for computing a type bound on the result, given type
		 *   bounds for the two arguments.
		 * @param ruleCreator
		 *   A lambda that is able to produce rules, via [RulesSyntax], once all
		 *   the enum values have been created, since rules for an operation
		 *   refer to other operations, sometimes cyclically.
		 */
		private constructor(
			jvmOpcode: Int,
			folder: (Int, Int)->Int,
			bounder: (A_Type, A_Type)->A_Type,
			ruleCreator: RulesSyntax.()->Unit
		) : this(
			{ method.visitInsn(jvmOpcode) },
			folder,
			bounder,
			ruleCreator)

		/**
		 * The [List] of [Rule]s by which an [L2ControlFlowGraph] can be
		 * transformed.  This happens when an [L2_BIT_LOGIC_OP] is asked to
		 * optimize itself, visiting its [L2_BIT_LOGIC_OP.bitOperation] and
		 * pattern matching its [BitOperation.rules].
		 */
		val rules: List<Rule> by lazy {
			val syntax = RulesSyntax(this)
			syntax.ruleCreator()
			syntax.rules
		}

		/**
		 * A syntactic convenience within pattern and rule definitions, allowing
		 * the [BitOperation] itself to appear to be a function applied to two
		 * arguments to construct a [Pattern] that's a binary [BinaryOp].
		 */
		operator fun invoke(left: Pattern, right: Pattern): BinaryOp =
			BinaryOp(this, left, right)

		/**
		 * Generate code (via the [callSiteHelper] that performs this operation,
		 * and ensures the boxed form of the result is available as the result
		 * of the call site.
		 *
		 * @param primitive
		 *   The [Primitive] being effectively invoked.
		 * @param arguments
		 *   The two boxed input arguments.
		 * @param argumentTypes
		 *   The [List] of [A_Type]s of the arguments.
		 * @param callSiteHelper
		 *   The [CallSiteHelper] that assists creation of the current method
		 *   call site.
		 * @param typeGuaranteeFunction
		 *   A lambda that produces an output type bound, given two input types.
		 * @param fallbackBody
		 *   How to generate the boxed form of this call along a path where the
		 *   inputs could not both be unboxed to [i32] values.
		 * @return
		 *   Whether any code was output.  If no code was output, the caller is
		 *   responsible for producing a more general primitive invocation or
		 *   call.
		 */
		fun L2GeneratorInterface.generateBinaryIntOperation(
			primitive: Primitive,
			arguments: List<L2ReadBoxedOperand>,
			argumentTypes: List<A_Type>,
			callSiteHelper: CallSiteHelper,
			typeGuaranteeFunction: (List<A_Type>) -> A_Type,
			fallbackBody: L1Translator.() -> Unit
		): Boolean
		{
			val (a, b) = arguments
			val (aType, bType) = argumentTypes
			// If either of the argument types does not intersect with int32,
			// then fall back to the primitive invocation.
			if (aType.typeIntersection(i32).isBottom
				|| bType.typeIntersection(i32).isBottom)
			{
				return false
			}

			// Attempt to unbox the arguments.
			val fallback = createBasicBlock("fall back to boxed logic")
			try
			{
				val (intA, intB) = readTwoInts(
					a.semanticValue().unboxedInt,
					b.semanticValue().unboxedInt,
					fallback)
				{
					return false
				}
				if (currentlyReachable())
				{
					// The happy path is reachable.  In this region, the output
					// is guaranteed to be an Int.
					val semanticPrimitive = primitive.semanticInvocation(
						a.semanticValue(), b.semanticValue())
					val intSemanticPrimitive = semanticPrimitive.unboxedInt
					val typeGuarantee = typeGuaranteeFunction(
						listOf(
							aType.typeIntersection(i32),
							bType.typeIntersection(i32)))
					// See if we've already computed an equivalent value in
					// either the boxed or unboxed form.
					currentManifest.equivalentSemanticValue(
						semanticPrimitive
					)?.let {
						moveRegister(it, listOf(semanticPrimitive))
						currentManifest.updateRestriction(semanticPrimitive) {
							intersectionWithType(typeGuarantee)
						}
						callSiteHelper.useAnswer(
							readBoxed(semanticPrimitive),
							false)
						return true
					}
					currentManifest.equivalentSemanticValue(
						intSemanticPrimitive
					)?.let {
						moveRegister(it, listOf(intSemanticPrimitive))
						currentManifest.updateRestriction(intSemanticPrimitive) {
							intersectionWithType(typeGuarantee)
						}
						callSiteHelper.useAnswer(
							readBoxed(intSemanticPrimitive.boxed),
							false)
						return true
					}
					when (val lower = typeGuarantee.lowerBound)
					{
						// It's a constant value.
						typeGuarantee.upperBound ->
						{
							+INTEGER_KIND.moveConstant(
								lower,
								setOf(
									intSemanticPrimitive,
									INTEGER_KIND.createSemanticConstant(lower.cast())))
						}
						else ->
						{
							val tempWriter = intWrite(
								setOf(intSemanticPrimitive),
								intRestrictionForType(typeGuarantee))
							+L2_BIT_LOGIC_OP(this@BitOperation, intA, intB, tempWriter)
						}
					}
					// Even though we're just using the boxed value again, the
					// unboxed form is also still available in the manifest for
					// use by subsequent primitives, which might allow the
					// boxing instruction to evaporate.
					callSiteHelper.useAnswer(
						readBoxed(semanticPrimitive),
						// Bit logic can't endanger escaped locals.
						false)
				}
				return true
			}
			finally
			{
				if (fallback.currentlyReachable())
				{
					// The fallback block is reachable, so generate the slow
					// case within it.
					startBlock(fallback)
					callSiteHelper.translator.fallbackBody()
				}
			}
		}
	}

	companion object
	{
		/**
		 * Colculate a type bound for `a mod b`, given the types for a and b.
		 * A must be ≥ 0, and b must be > 0.  Both must be < 2^31.
		 */
		fun modBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractInt
			val aHigh = a.upperBound.extractInt
			val bLow = b.lowerBound.extractInt
			val bHigh = b.upperBound.extractInt
			assert(aLow >= 0)
			assert(bLow > 0)

			val lowQuo = aLow / bHigh
			val highQuo = aHigh / bLow
			return if (lowQuo == highQuo)
			{
				// The modulus doesn't wrap past the denominator, so we can say
				// it only spans the obvious limit cases.
				inclusive(aLow % bHigh, aHigh % bLow)
			}
			else
			{
				// The modulus wraps past the denominator (to 0), so we have to
				// say the modulus is between 0 and one less than the largest
				// denominator.
				inclusive(0, bHigh - 1)
			}
		}

		/**
		 * Calculate a bound for `a ushr b`, given the types for a and b.
		 * Only the bottom 5 bits of b are used.  If the a is negative and b is
		 * zero, the result should be treated as negative.
		 */
		fun ushrBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractLong
			val aHigh = a.upperBound.extractLong
			var bLow = b.lowerBound.extractInt
			var bHigh = b.upperBound.extractInt

			if ((bLow and 31.inv()) != (bHigh and 31.inv()))
			{
				// b wraps past 31 and 0, so we should consider that whole range
				// as possible.
				bLow = 0
				bHigh = 31
			}
			else
			{
				bLow = bLow and 31
				bHigh = bHigh and 31
			}
			val boundaryValues = mutableSetOf<Int>()
			listOf(aLow, -1L, 0L, 1L, aHigh).forEach { aSampleLong ->
				val aSampleInt = aSampleLong.toInt()
				if (aSampleInt !in aLow..aHigh) return@forEach
				boundaryValues.add(aSampleInt ushr bLow)
				boundaryValues.add(aSampleInt ushr bHigh)
			}
			return inclusive(boundaryValues.min(), boundaryValues.max())
		}

		/**
		 * Calculate a bound for `max(a, b)`, given the types for a and b.
		 */
		fun maxBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractInt
			val aHigh = a.upperBound.extractInt
			var bLow = b.lowerBound.extractInt
			var bHigh = b.upperBound.extractInt
			return inclusive(max(aLow, bLow), max(aHigh, bHigh))
		}

		/**
		 * Calculate a bound for `min(a, b)`, given the types for a and b.
		 */
		fun minBound(a: A_Type, b: A_Type): A_Type
		{
			val aLow = a.lowerBound.extractInt
			val aHigh = a.upperBound.extractInt
			var bLow = b.lowerBound.extractInt
			var bHigh = b.upperBound.extractInt

			return inclusive(min(aLow, bLow), min(aHigh, bHigh))
		}
	}
}
class Rule(
	val pattern: Pattern,
	val producer: RuleEvaluationContext.()->Pattern)

sealed class Pattern
{
	open fun subString(): String = toString()

	open val allVariables: List<Variable> get() = emptyList()

	open val allConstants: List<Constant> get() = emptyList()

	abstract class Constant(): Pattern()
	{
		override fun toString(): String = this::class.simpleName!!

		override val allConstants get() = listOf(this)

		object K1 : Constant()
		object K2 : Constant()
		object K3 : Constant()
		object K4 : Constant()
	}

	/** A *specific* constant value. */
	class C(val n: Int): Pattern()
	{
		override fun toString() = n.toString()
	}

	abstract class Variable(): Pattern()
	{
		override fun toString(): String = this::class.simpleName!!

		override val allVariables get() = listOf(this)

		object W : Variable()
		object X : Variable()
		object Y : Variable()
		object Z : Variable()
	}

	class BinaryOp(
		val operation: BitOperation,
		val arg1: Pattern,
		val arg2: Pattern
	): Pattern()
	{
		override fun subString(): String = "(${toString()})"

		override fun toString() =
			"${arg1.subString()} $operation ${arg2.subString()}"

		override val allVariables
			get() = arg1.allVariables + arg2.allVariables

		override val allConstants
			get() = arg1.allConstants + arg2.allConstants
	}
}
