package org.availlang.artifact.jar

import org.availlang.artifact.AvailArtifactException
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter

/**
 * Indicates whether an Avail artifact has JVM components and lists the main
 * function classes if it does.
 *
 * @author Richard Arriaga
 *
 * @property hasJVMComponents
 *   `true` indicates the artifact has JVM components; `false` otherwise.
 * @property description
 *   The description of the [JvmComponent] if [hasJVMComponents] is `true`; a
 *   blank String otherwise.
 * @property mains
 *   The map from the fully qualified class that contains a main function to a
 *   short description of what the main function does.
 */
class JvmComponent constructor(
	val hasJVMComponents: Boolean,
	val description: String = "",
	val mains: Map<String, String> = mapOf()
): JSONFriendly
{
	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::hasJVMComponents.name) { write(hasJVMComponents) }
			at(::description.name) { write(description) }
			at(::mains.name) {
				writeArray {
					mains.forEach { (k,v) ->
						writeObject {
							at(MAIN) { write(k) }
							at(DESCRIPTION) { write(v) }
						}
					}
				}
			}
		}
	}

	companion object
	{
		private const val MAIN = "main"
		private const val DESCRIPTION = "description"

		/**
		 * The canonical no JVM [JvmComponent].
		 */
		val NONE = JvmComponent(false)

		/**
		 * Extract an [JvmComponent] from the given [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the data from.
		 * @return
		 *   The extracted [JvmComponent].
		 * @throws AvailArtifactException
		 *   If there is an issue with extracting the [JvmComponent].
		 */
		fun from (obj: JSONObject): JvmComponent
		{
			val hasJVMComponents =
				try
				{
					obj.getBoolean(JvmComponent::hasJVMComponents.name)
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem extracting Avail Manifest JvmComponent " +
							"hasJVMComponents.",
						e)
				}
			val description =
				try
				{
					obj.getString(JvmComponent::description.name)
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem extracting Avail Manifest JvmComponent " +
							"description.",
						e)
				}
			val mainMap = mutableMapOf<String, String>()
			try
			{
				obj.getArray(JvmComponent::mains.name).forEach {
					it as JSONObject
					mainMap[it.getString(MAIN)] = it.getString(DESCRIPTION)
				}
			}
			catch (e: Throwable)
			{
				throw AvailArtifactException(
					"Problem extracting Avail Manifest JvmComponent mains map.",
					e)
			}
			return JvmComponent(hasJVMComponents, description, mainMap)
		}
	}
}
