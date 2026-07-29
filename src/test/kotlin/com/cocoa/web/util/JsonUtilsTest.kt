package com.cocoa.web.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonUtilsTest {
    data class Sample(val name: String, val count: Int)

    @BeforeAll
    fun setUp() {
        JsonUtils.initialize(ObjectMapper().registerKotlinModule())
    }

    @Test
    fun `write serializes to JSON string`() {
        val json = JsonUtils.write(Sample(name = "alice", count = 3))

        assertTrue(json.contains("\"name\":\"alice\""))
        assertTrue(json.contains("\"count\":3"))
    }

    @Test
    fun `read deserializes JSON string into JsonNode`() {
        val node: JsonNode = JsonUtils.read("{\"k\":\"v\"}")

        assertEquals("v", node.get("k").asText())
    }

    @Test
    fun `readInto deserializes JSON string into typed object`() {
        val sample = JsonUtils.readInto("{\"name\":\"bob\",\"count\":7}", Sample::class)

        assertEquals(Sample(name = "bob", count = 7), sample)
    }

    @Test
    fun `toJsonNodeOrNull succeeds for valid object`() {
        val node = Sample("c", 1).toJsonNodeOrNull()

        assertNotNull(node)
        assertEquals("c", node!!.get("name").asText())
    }

    @Test
    fun `createEmptyArrayNode returns empty array`() {
        val arr = JsonUtils.createEmptyArrayNode()

        assertTrue(arr.isArray)
        assertEquals(0, arr.size())
    }

    @Test
    fun `toDataClassList converts array to list`() {
        val array =
            JsonUtils.objectMapper.createArrayNode()
                .add(JsonUtils.objectMapper.valueToTree<JsonNode>(Sample("a", 1)))
                .add(JsonUtils.objectMapper.valueToTree<JsonNode>(Sample("b", 2)))

        val list = array.toDataClassList(Sample::class)

        assertEquals(2, list!!.size)
        assertEquals("a", list[0].name)
    }

    @Test
    fun `toDataClassList returns null for null node`() {
        val nullNode: JsonNode = JsonUtils.objectMapper.nullNode()

        val list = nullNode.toDataClassList(Sample::class)

        assertNull(list)
    }
}
