/*
 * ObjectTracer.kt
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
package avail.utility

import java.lang.ref.Reference
import java.lang.reflect.Modifier.isStatic
import java.util.IdentityHashMap

/**
 * A utility for determining the shortest path through fields from a starting
 * object to a target object.  The search is performed breadth-first to ensure
 * the shortest path is found.
 *
 * @author Mark van Gulik <mark@availlang.org>
 *
 * @constructor
 * Create the instance, but don't start the search.
 *
 * @property start
 *   The initial object from which to begin the path search.
 * @property target
 *   The object to which to find a path, starting at [start].
 */
@Suppress("unused")
class ObjectTracer
constructor (
	val start: Any,
	val target: Any)
{
	/**
	 * A map from reached objects to the object that preceded it in the graph.
	 * There may be multiple predecessors, but this link indicates the parent
	 * object that led to the value first being reached.  Since the traversal
	 * is breadth-first, this parent is along a shortest path back to the start.
	 */
	private val backLinks = IdentityHashMap<Any, Any>()

	/**
	 * The elements on this queue are unique, and represent objects that have
	 * been added to [backLinks] but not yet scanned.
	 */
	private val workQueue = ArrayDeque<Any>()

	/**
	 * A cache from each encountered class to its list of instance fields that
	 * hold objects.
	 */
	private val cacheByClass = mutableMapOf<Class<*>, List<(Any)->Any?>>()

	/**
	 * Answer the shortest chain of objects starting with [start] and ending
	 * with [target].  If no such chain was found, answer `null`.
	 */
	fun scan(): List<Any>?
	{
		// In theory, the tracer is reusable.
		backLinks.clear()
		workQueue.clear()
		workQueue.add(start)
		backLinks[start] = start  // Terminates the  fiction.
		if (start === target) return listOf(start)
		while (workQueue.isNotEmpty())
		{
			if (scanObject(workQueue.removeFirst()))
			{
				// We just completed the chain.
				var o = target
				val chain = mutableListOf(o)
				while (o !== start)
				{
					o = backLinks[o]!!
					chain.add(o)
				}
				return chain.reversed()
			}
		}
		return null
	}

	/**
	 * Scan [obj], which is already in [backLinks], and has just been removed
	 * (for the only time) from [workQueue].
	 */
	private fun scanObject(obj: Any): Boolean
	{
		val cls = obj.javaClass
		if (cls.isArray)
		{
			if (!cls.componentType.isPrimitive)
			{
				(obj as Array<*>).forEach { child ->
					if (child !== null && child !in backLinks)
					{
						backLinks[child] = obj
						workQueue.add(child)
						if (child === target) return true
					}
				}
			}
		}
		else if (Reference::class.java.isAssignableFrom(cls))
		{
			// Don't trace through References.
			return false
		}
		else
		{
			gettersForClass(obj.javaClass).forEach { getter ->
				getter(obj)?.let { child ->
					if (child !in backLinks)
					{
						backLinks[child] = obj
						workQueue.add(child)
						if (child === target) return true
					}
				}
			}
		}
		return false
	}

	private fun gettersForClass(cls: Class<*>): List<(Any)->Any?> =
		cacheByClass.computeIfAbsent(cls) { theClass ->
			val getters = mutableListOf<(Any)->Any?>()
			var eachCls: Class<*>? = theClass
			while (eachCls !== null)
			{
				eachCls.declaredFields.forEach { field ->
					if (!isStatic(field.modifiers)
						&& !field.type.isPrimitive
						&& field.trySetAccessible())
					{
						getters.add(field::get)
					}
				}
				eachCls = eachCls.superclass
			}
			getters
		}
}