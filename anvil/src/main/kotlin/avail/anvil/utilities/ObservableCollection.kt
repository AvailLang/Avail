package avail.anvil.utilities

/**
 * Interface for holding an immutable value.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 */
interface ValueHolder<V>
{
	/**
	 * The wrapped immutable value
	 */
	val value: V
}

/**
 * Interface for holding an immutable key-value pair.
 *
 * @author Richard Arriaga
 *
 * @param K
 *   The type of the key.
 * @param V
 *   The type of the wrapped value.
 */
interface KeyValueHolder<K, V>: ValueHolder<V>
{
	/**
	 * The wrapped immutable value
	 */
	val key: K
}

/**
 * `ListValueHolder` is the interface for holding a value from a list, providing
 * a [value] from the list and the [index][key] where that value is stored in
 * the list.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 */
interface ListValueHolder<V>: KeyValueHolder<Int, V>

/**
 * Represents a change to a [List].
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 */
sealed class ListStateChange<V>
{
	/**
	 * The list that has changed.
	 */
	abstract val list: List<V>
}

/**
 * `ListClear` is a [ListStateChange] that represents clearing the entire list
 * resulting in list size of zero.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListClear].
 *
 * @param list
 *   The list that has changed.
 */
data class ListClear<V> constructor(override val list: List<V>)
	: ListStateChange<V>()

/**
 * `ListAdd` is a [ListStateChange] that represents adding the contained [value]
 * at the contained [index][key]. This always results in the [list] increasing
 * in size by one element.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListAdd].
 *
 * @param list
 *   The list that has changed.
 * @param key
 *   The index where the [value] was added.
 * @param value
 *   The added value.
 */
data class ListAdd<V> constructor(
	override val list: List<V>,
	override val key: Int,
	override val value: V): ListValueHolder<V>, ListStateChange<V>()

/**
 * `ListReplace` is a [ListStateChange] that represents replacing the contained
 * [value] at the contained [index][key]. This always results in the [list]
 * remaining the same size.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListReplace].
 *
 * @param list
 *   The list that has changed.
 * @param key
 *   The index where the [value] was replaced.
 * @param value
 *   The added value.
 */
data class ListReplace<V> constructor(
	override val list: List<V>,
	override val key: Int,
	override val value: V): ListValueHolder<V>, ListStateChange<V>()

/**
 * `ListRemove` is a [ListStateChange] that represents removing the contained
 * [value] at the contained [index][key]. This always results in the [list]
 * decreasing in size by one element.
 *
 * @author Richard Arriaga
 *
 * @param V
 *   The type of the wrapped value.
 *
 * @constructor
 * Construct a new [ListReplace].
 *
 * @param list
 *   The list that has changed.
 * @param key
 *   The index where the [value] was replaced.
 * @param value
 *   The added value.
 */
data class ListRemove<V> constructor(
	override val list: List<V>,
	override val key: Int,
	override val value: V): ListValueHolder<V>, ListStateChange<V>()
