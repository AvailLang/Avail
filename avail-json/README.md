Avail JSON
===============================================================================

This is a utility for creating arbitrary JSON. It provides a JSON builder that 
is like a `StringBuilder` that contains a JSON state machine. It produces JSON 
documents that adhere to [ECMA 404: "The JSON Data Interchange Format"](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf).

## JSONFriendly
`JSONFriendly` is an interface that indicates an object has capability to be 
written to a [JSONWriter](#JSONWriter) through the function:

```kotlin
/**
	 * Emit a JSON representation of the [receiver][JSONFriendly] onto the
	 * specified [writer][JSONWriter].
	 *
	 * @param writer
	 *   A [JSONWriter].
	 */
	fun writeTo(writer: JSONWriter)
```

## JSONWriter
The `JSONWriter` is the writer used to build JSON. It uses a `java.io.Writer` to
build the JSON. By default, the `JSONWriter` uses a `StringWriter`. It uses a 
state machine, `JSONState`, which represents the `JSONWriter`'s view of 
what operations are legal based on what operations have become before.


## JSONReader
The `JSONReader` is the reader used to parse and read JSON.

## Example
The following is an example of a `JSONFriendly`. The example shows usage of both
reading and writing JSON.

```kotlin
import java.io.StringReader
import java.util.UUID

/**
 * Sample [JSONFriendly].
 */
class Baz: JSONFriendly
{
	/** Sample String */
	val name: String
	
	/** Sample Int */
	val id: Int

	override fun writeTo(writer: JSONWriter)
	{
		// Indicate starting a new JSON Object
		writer.startObject()
		
		// Write a String value
		writer.at("name") { write(name) }
		
		// Write an int value
		writer.at("id") { write(id) }
		
		// Indicate ending the JSON Object opened at the start of this function
		writer.endObject()
	}

	/**
	 * Basic constructor
	 */
	constructor(name: String, id: Int)
	{
		this.name = name
		this.id = id
	}

	/**
	 * Constructor using a raw JSON payload as a String. Use a [JSONReader] to 
	 * construct a [JSONObject] from which the JSON-serialized data can be read
	 * from.
	 */
	constructor(rawJSONString: String): this(
		JSONReader(StringReader(rawJSONString)).read() as JSONObject?
			?: error("Not a proper JSON Object!"))

	/**
	 * Constructor using a [JSONObject]. It expects the JSON object takes the
	 * form that was created in the [writeTo] implementation.
	 */
	constructor(jsonObj: JSONObject)
	{
		this.name = jsonObj.getString("name")
		this.id = jsonObj.getNumber("id").int
	}
}

/**
 * A `Foo` is an example of [JSONFriendly].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class Foo: JSONFriendly
{
	/** A Sample Int */
	val myInt: Int

	/** A Sample Double */
	val myDouble: Double

	/** A Sample String */
	val myString: String?

	/** A Sample UUID */
	val myUUID: UUID

	/** A Sample Boolean */
	val myBoolean: Boolean

	/** A Sample [JSONFriendly] [Baz] */
	val myBaz: Baz

	/** A Sample List of Longs */
	val myLongs: List<Long>
	
	override fun writeTo(writer: JSONWriter)
	{
		writer.apply {
			// Indicate starting a new JSON Object
			startObject()
			at("myString") {
				if (myString == null) writeNull()
				else write(myString)
			}
			at("myDouble") { write(myDouble) }
			at("myBoolean") { write(myBoolean) }
			at("myUUID") { write(myUUID.toString()) }
			at("myBaz") { myBaz.writeTo(this) }
			at("myLongs") {
				// Indicate an array is starting
				startArray()
				myLongs.forEach { write(it) }
				// Indicate the array is ending
				endArray()
			}
			// Indicate ending the JSON Object opened at the start of this 
			// function
			endObject()
		}
	}

	/**
	 * Basic constructor
	 */
	constructor(
		myInt: Int, 
		myDouble: Double,
		myString: String?,
		myBoolean: Boolean,
		myUUID: UUID,
		myBaz: Baz,
		myLongs: List<Long>)
	{
		this.myInt = myInt
		this.myString = myString
		this.myUUID = myUUID
		this.myBoolean = myBoolean
		this.myDouble = myDouble
		this.myLongs = myLongs
		this.myBaz = myBaz
	}

	/**
	 * Constructor using a raw JSON payload as a String. Use a [JSONReader] to
	 * construct a [JSONObject] from which the JSON-serialized data can be read
	 * from.
	 */
	constructor(rawJSONString: String): this(
		JSONReader(StringReader(rawJSONString)).read() as JSONObject?
			?: error("Not a proper JSON Object!"))

	/**
	 * Constructor using a [JSONObject]. It expects the JSON object takes the
	 * form that was created in the [writeTo] implementation.
	 */
	constructor(jsonObj: JSONObject)
	{
		this.myString = 
			jsonObj["myString"].let { if (it.isNull) null else it.toString() }
		this.myInt =  jsonObj.getNumber("myInt").int
		this.myUUID = UUID.fromString(jsonObj.getString("myUUID"))
		this.myBoolean = jsonObj.getBoolean("myBoolean")
		this.myDouble = jsonObj.getNumber("myDouble").double
		this.myBaz = Baz(jsonObj)
		this.myLongs = 
			jsonObj.getArray("myLongs").map { (it as JSONNumber).long }
		
	}
}
```
