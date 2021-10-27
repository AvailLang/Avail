package avail.anvil.utilities

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * `ObservableMutableState` is wraps a [MutableState], linking it to
 *
 * @author Richard Arriaga
 *
 * @param T
 *   The type of the wrapped object.
 * @property observableState
 *   The backing [ObservableState] to observe.
 * @property changeAction
 *   An action that accepts the updated value from [observableState].
 *
 * @constructor
 * Construct a [ObservableLiveData].
 *
 * @param observableState
 *   The backing [ObservableState] to observe.
 * @param changeAction
 *   An action that accepts the updated value from `observableState`.
 */
class ObservableMutableState<T> constructor(
	private val observableState: ObservableState<T>,
	private val changeAction: (T) -> Unit = {})
{
	/**
	 * The wrapped [MutableState].
	 */
	val state: MutableState<T> = mutableStateOf(observableState.state)

	/**
	 * The [ObservableState.Handle] that links to the [ObservableState].
	 */
	private var handle: ObservableState.Handle<T> =
		observableState.acquireHandle()

	init
	{
		handle.reObserve {
			state.value = it
			changeAction(it)
		}
	}

	/**
	 * Remove the notifier using the [handle].
	 */
	fun removeNotifier () { handle.removeNotifier() }
}

/**
 * `ObservableSnapshotStateList` is wraps a [MutableState], linking it to
 *
 * @author Richard Arriaga
 *
 * @param T
 *   The type of the wrapped object.
 * @property observableListState
 *   The backing [ObservableState] to observe.
 * @property changeAction
 *   An action that accepts the a newly added value to [observableListState].
 *
 * @constructor
 * Construct a [ObservableLiveData].
 *
 * @param observableListState
 *   The backing [ObservableState] to observe.
 * @param changeAction
 *   An action that accepts the a newly added value to `observableState`.
 */
class ObservableSnapshotStateList<T> constructor(
	private val observableListState: ObservableListState<T>,
	private val changeAction: (ListStateChange<T>) -> Unit = {})
{
	/**
	 * The wrapped [MutableState].
	 */
	val state: SnapshotStateList<T> = mutableStateListOf()

	/**
	 * Answer the number of elements in [state].
	 */
	val size: Int get() = state.size

	/**
	 * The [ObservableState.Handle] that links to the [ObservableState].
	 */
	private var handle: ObservableListState.Handle<T> =
		observableListState.acquireHandle()

	init
	{
		state.addAll(observableListState.state)
		handle.reObserve {
			when (it)
			{
				is ListAdd -> state.add(it.key, it.value)
				is ListClear -> state.clear()
				is ListRemove -> state.removeAt(it.key)
				is ListReplace -> state[it.key] = it.value
			}
			changeAction(it)
		}
	}

	/**
	 * Remove the notifier using the [handle].
	 */
	fun removeNotifier () { handle.removeNotifier() }
}
