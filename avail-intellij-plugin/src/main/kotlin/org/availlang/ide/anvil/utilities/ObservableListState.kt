package org.availlang.ide.anvil.utilities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * `ObservableListState` wraps a mutable list that, every time a new value is
 * added, it notifies its observers of the new value.
 *
 * TODO Add some form of cleanup functionality that allows for forcing a
 *  remove notifier call for all handlers and to "remove" them from? Could this
 *  be abused to create memory leaks by abusing this?
 *
 * @author Richard Arriaga
 *
 * @param T
 *   The type of the list element.
 * @property scope
 *   The coroutine scope to use to run updates.
 *
 * @constructor
 * Create a new [ObservableListState].
 *
 * @param scope
 *   The coroutine scope to use to run updates.
 */
class ObservableListState<T> constructor(
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
): MutableList<T>
{
	/**
	 * The wrapped List state that, when added to, notifies all interested
	 * observers.
	 */
	val state = mutableListOf<T>()

	/**
	 * Answer the number of elements in [state].
	 */
	override val size: Int get() = state.size

	override operator fun get (index: Int): T = state[index]

	override fun isEmpty (): Boolean = state.isEmpty()

	/**
	 * @return
	 *   `true` if the list is not empty; `false` otherwise.
	 */
	fun isNotEmpty (): Boolean = state.isNotEmpty()

	/**
	 * The map from [Handle.id] to notifier suspend functions for the observers.
	 */
	private val observers = ConcurrentHashMap<
		Int, Pair<Handle<T>, suspend (ListStateChange<T>) -> Unit>>()

	/**
	 * The generator for [Handle] ids.
	 */
	private val idGenerator = AtomicInteger(0)

	/**
	 * Observe this [ObservableListState].
	 *
	 * @param notifier
	 *  The suspend function that accepts the wrapped value that has just been
	 *  updated.
	 */
	fun observe (
		notifier: suspend (ListStateChange<T>) -> Unit
	): Handle<T>
	{
		val handle = Handle(this)
		observers[handle.id] = Pair(handle, notifier)
		return handle
	}

	/**
	 * Answer a new [Handle] for this [ObservableListState].
	 */
	fun acquireHandle (): Handle<T> = Handle(this)

	/**
	 * Notify all the observers of the provided [ListStateChange].
	 *
	 * @param change
	 *   The `ListStateChange` to report.
	 */
	private fun notify (change: ListStateChange<T>)
	{
		observers.values.forEach {
			scope.launch {
				it.second(change)
			}
		}
	}

	/**
	 * [Remove the handler notifiers][Handle.removeNotifier] for all [Handle]s
	 * observing this [ObservableListState].
	 */
	fun cleanUpObservers ()
	{
		observers.clear()
	}

	override fun clear ()
	{
		state.clear()
		notify(ListClear(state))
	}

	override fun add (element: T): Boolean
	{
		val result = state.add(element)
		notify(ListAdd(state, state.size - 1, element))
		return result
	}

	override fun add (index: Int, element: T)
	{
		state.add(index, element)
		notify(ListAdd(state, state.size - 1, element))
	}

	override fun set (index: Int, element: T): T
	{
		val e = state.set(index, element)
		notify(ListReplace(state, index, element))
		return e
	}

	override fun removeAt (index: Int): T
	{
		val removed = state.removeAt(index)
		notify(ListRemove(state, index, removed))
		return removed
	}

	override fun contains(element: T): Boolean = state.contains(element)

	override fun containsAll(elements: Collection<T>): Boolean =
		state.containsAll(elements)

	override fun indexOf(element: T): Int = state.indexOf(element)

	override fun iterator(): MutableIterator<T> = state.iterator()

	override fun lastIndexOf(element: T): Int = state.lastIndexOf(element)

	override fun addAll(index: Int, elements: Collection<T>): Boolean
	{
		TODO("Not yet implemented")
	}

	override fun addAll(elements: Collection<T>): Boolean
	{
		TODO("Not yet implemented")
	}

	override fun listIterator(): MutableListIterator<T> = state.listIterator()

	override fun listIterator(index: Int): MutableListIterator<T> =
		state.listIterator(index)

	override fun remove(element: T): Boolean
	{
		TODO("Not yet implemented")
	}

	override fun removeAll(elements: Collection<T>): Boolean
	{
		TODO("Not yet implemented")
	}

	override fun retainAll(elements: Collection<T>): Boolean
	{
		TODO("Not yet implemented")
	}

	override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> =
		state.subList(fromIndex, toIndex)

	/**
	 * `Handle` links to the owning [ObservableListState] providing a reference
	 * back to the `ObservableState`.
	 */
	class Handle<T> constructor(
		private var observableState: ObservableListState<T>?)
	{
		/**
		 * The integer that uniquely identifies this [Handle].
		 */
		val id: Int = observableState!!.idGenerator.getAndIncrement()

		/**
		 * Remove this interest from the [ObservableListState].
		 */
		fun removeNotifier ()
		{
			observableState?.observers?.remove(id)
		}

		/**
		 * Resume observation with the provided notifier function.
		 *
		 * @param notifier
		 *  The suspend function that accepts the wrapped value that has just
		 *  been updated.
		 */
		fun reObserve (notifier: suspend (ListStateChange<T>) -> Unit)
		{
			observableState?.let {
				it.observers[id] = Pair(this, notifier)
			}
		}
	}
}
