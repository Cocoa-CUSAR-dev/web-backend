package com.cocoa.web.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.slf4j.LoggerFactory
import java.io.InputStream
import kotlin.reflect.KClass

object JsonUtils {
    private val logger = LoggerFactory.getLogger(this::class.java)
    lateinit var objectMapper: ObjectMapper
        private set

    /** Initialize on application startup using Spring's configured [ObjectMapper]. */
    fun initialize(objectMapper: ObjectMapper) {
        JsonUtils.objectMapper = objectMapper
    }

    /** Deserialize a string into a [JsonNode] object. */
    fun read(content: String): JsonNode = objectMapper.readTree(content)

    /** Deserialize a string into [T]. */
    fun <T : Any> readInto(content: String, type: KClass<T>): T = objectMapper.readValue(content, type.java)

    /** Deserialize an InputStream into [T]. */
    fun <T : Any> readInto(inputStream: InputStream, type: KClass<T>): T = objectMapper.readValue(inputStream, type.java)

    /** Serialize any object into JSON string. */
    fun write(value: Any): String = objectMapper.writeValueAsString(value)

    fun createEmptyArrayNode(): ArrayNode = objectMapper.createArrayNode()

    private fun convertToJsonNodeOrNull(obj: Any): JsonNode? {
        return try {
            objectMapper.valueToTree(obj)
        } catch (e: Exception) {
            logger.error("Error converting object to JsonNode", e)
            logger.error("Object: {}", obj)
            null
        }
    }

    private fun <T : Any> convertToDataClassOrNull(jsonNode: JsonNode, type: KClass<T>): T? {
        if (jsonNode.isNull) return null

        return try {
            objectMapper.treeToValue(jsonNode, type.java)
        } catch (e: Exception) {
            logger.error("Error converting JsonNode to DataClass", e)
            logger.error("JsonNode: {}", jsonNode)
            logger.error("DataClassType: {}", type)
            null
        }
    }

    private fun <T : Any> convertToDataClassList(jsonArrayNode: JsonNode, itemType: KClass<T>): List<T>? {
        if (jsonArrayNode.isNull) return null

        require(jsonArrayNode.isArray) { "JsonNode must be an Array: $jsonArrayNode" }
        return jsonArrayNode.map { itemNode -> objectMapper.treeToValue(itemNode, itemType.java) }
    }

    /** Converts this object to a [JsonNode] using [objectMapper], or `null` if failed.
     *  Exceptions raised during conversion will not be thrown.
     * */
    fun Any.toJsonNodeOrNull(): JsonNode? = convertToJsonNodeOrNull(this)

    /** Converts a JSON object node to [T], or `null` if failed.
     *  Exceptions raised during conversion will not be thrown.
     * */
    fun <T : Any> JsonNode.toDataClassOrNull(type: KClass<T>): T? = convertToDataClassOrNull(this, type)

    /** Converts a JSON array node to a list of [T].
     *  - If this is a null node ([JsonNode.isNull]) then the result is `null`.
     *  - Any exceptions raised during conversion will be thrown.
     * */
    fun <T : Any> JsonNode.toDataClassList(itemType: KClass<T>): List<T>? = convertToDataClassList(this, itemType)
}