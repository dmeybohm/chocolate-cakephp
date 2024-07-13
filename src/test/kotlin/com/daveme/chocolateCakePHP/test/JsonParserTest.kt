package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.JsonParser
import com.daveme.chocolateCakePHP.jsonParse

class JsonParserTest : BaseTestCase() {

    fun `test parsing empty object`() {
        val empty = JsonParser("{}").parse()
        val asMap = empty as? Map<*, *>
        assertNotNull(empty)
        assertEquals(asMap!!.size, 0)
    }

    fun `test parsing empty object with whitespace`() {
        val emptyWithWhitespace = JsonParser("   {    }  ").parse()
        val asMapWithWhitespace = emptyWithWhitespace as? Map<*, *>
        assertNotNull(emptyWithWhitespace)
        assertEquals(asMapWithWhitespace!!.size, 0)
    }

    fun `test parsing number`() {
        val numberParsed = JsonParser("   120490 ").parse()
        val number = numberParsed as? Number
        assertNotNull(number)
        assertEquals(number!!, 120490.0)
    }

    fun `test parsing string`() {
        val stringParsed = JsonParser("   \"Hello\" ").parse()
        val string = stringParsed as? String
        assertNotNull(string)
        assertEquals(string!!, "Hello")
    }

    fun `test empty object parses`() {
        val jsonString = "{}"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertTrue((result as Map<*, *>).isEmpty())
    }

    fun `test simple object parses`() {
        val jsonString = """{"key": "value"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value", (result as Map<*, *>)["key"])
    }

    fun `test array parses`() {
        val jsonString = """[1, 2, 3]"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        assertEquals(listOf(1.0, 2.0, 3.0), result)
    }

    fun `test mixed array parses`() {
        val jsonString = """[1, "two", {"key": "value"}, [3, 4]]"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        assertEquals(1.0, (result as List<*>)[0])
        assertEquals("two", result[1])
        assertTrue(result[2] is Map<*, *>)
        assertEquals("value", (result[2] as Map<*, *>)["key"])
        assertTrue(result[3] is List<*>)
        assertEquals(listOf(3.0, 4.0), result[3])
    }

    fun `test string parses`() {
        val jsonString = """"Hello, world!""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("Hello, world!", result)
    }

    fun `test number parses`() {
        val jsonString = "42"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(42.0, result)
    }

    fun `test true parses`() {
        val jsonString = "true"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Boolean)
        assertEquals(true, result)
    }

    fun `test false parses`() {
        val jsonString = "false"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Boolean)
        assertEquals(false, result)
    }

    fun `test null parses`() {
        val jsonString = "null"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertNull(result)
    }

    fun `test integer parses`() {
        val jsonString = "42"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(42.0, result)
    }

    fun `test negative integer parses`() {
        val jsonString = "-42"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(-42.0, result)
    }

    fun `test floating point number parses`() {
        val jsonString = "3.14159"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(3.14159, result)
    }

    fun `test negative floating point number parses`() {
        val jsonString = "-3.14159"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(-3.14159, result)
    }

    fun `test exponential number parses`() {
        val jsonString = "123e4"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(1230000.0, result)
    }

    fun `test negative exponential number parses`() {
        val jsonString = "-123e4"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(-1230000.0, result)
    }

    fun `test exponential number with positive exponent parses`() {
        val jsonString = "123E+4"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(1230000.0, result)
    }

    fun `test exponential number with negative exponent parses`() {
        val jsonString = "123e-4"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(0.0123, result)
    }

    fun `test floating point exponential number parses`() {
        val jsonString = "1.23e4"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(12300.0, result)
    }

    fun `test negative floating point exponential number parses`() {
        val jsonString = "-1.23e4"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(-12300.0, result)
    }

    fun `test floating point exponential number with negative exponent parses`() {
        val jsonString = "1.23e-4"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Number)
        assertEquals(0.000123, result)
    }

    fun `test string with double quote escape parses`() {
        val jsonString = """"He said, \"Hello!\"""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("He said, \"Hello!\"", result)
    }

    fun `test string with backslash escape parses`() {
        val jsonString = """
            "C:\\Path\\To\\File"
        """.trimIndent()
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("C:\\Path\\To\\File", result)
    }

    fun `test string with forward slash escape parses`() {
        val jsonString = """"\/path\/to\/resource""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("/path/to/resource", result)
    }

    fun `test string with backspace escape parses`() {
        val jsonString = """"Hello\bWorld""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("Hello\bWorld", result)
    }

    fun `test string with form feed escape parses`() {
        val jsonString = """"Hello\fWorld""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("Hello\u000CWorld", result)
    }

    fun `test string with newline escape parses`() {
        val jsonString = """"Hello\nWorld""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("Hello\nWorld", result)
    }

    fun `test string with carriage return escape parses`() {
        val jsonString = """"Hello\rWorld""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("Hello\rWorld", result)
    }

    fun `test string with tab escape parses`() {
        val jsonString = """"Hello\tWorld""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("Hello\tWorld", result)
    }

    fun `test string with unicode escape parses`() {
        val jsonString = """"Unicode test: \u263A""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is String)
        assertEquals("Unicode test: \u263A", result)
    }

    fun `test nested object parses`() {
        val jsonString = """{"key": {"nestedKey": "nestedValue"}}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val nested = (result as Map<*, *>)["key"]
        assertTrue(nested is Map<*, *>)
        assertEquals("nestedValue", (nested as Map<*, *>)["nestedKey"])
    }

    fun `test object with array parses`() {
        val jsonString = """{"key": [1, 2, 3]}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val array = (result as Map<*, *>)["key"]
        assertTrue(array is List<*>)
        assertEquals(listOf(1.0, 2.0, 3.0), array)
    }

    fun `test complex object parses`() {
        val jsonString = """
            {
                "name": "John",
                "age": 30,
                "isStudent": false,
                "address": {
                    "city": "New York",
                    "zipcode": "10001"
                },
                "hobbies": ["reading", "traveling", "coding"]
            }
        """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals("John", mapResult["name"])
        assertEquals(30.0, mapResult["age"])
        assertEquals(false, mapResult["isStudent"])
        val address = mapResult["address"]
        assertTrue(address is Map<*, *>)
        assertEquals("New York", (address as Map<*, *>)["city"])
        assertEquals("10001", address["zipcode"])
        val hobbies = mapResult["hobbies"]
        assertTrue(hobbies is List<*>)
        assertEquals(listOf("reading", "traveling", "coding"), hobbies)
    }

    fun `test object with null value parses`() {
        val jsonString = """{"key": null}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertNull((result as Map<*, *>)["key"])
    }

    fun `test object with boolean values parses`() {
        val jsonString = """{"trueKey": true, "falseKey": false}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals(true, (result as Map<*, *>)["trueKey"])
        assertEquals(false, result["falseKey"])
    }

    fun `test object with number values parses`() {
        val jsonString = """{"integer": 42, "float": 3.14, "negative": -7, "exponential": 1.23e4}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals(42.0, mapResult["integer"])
        assertEquals(3.14, mapResult["float"])
        assertEquals(-7.0, mapResult["negative"])
        assertEquals(12300.0, mapResult["exponential"])
    }

    fun `test object with nested arrays parses`() {
        val jsonString = """{"key": [[1, 2], [3, 4]]}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val array = (result as Map<*, *>)["key"]
        assertTrue(array is List<*>)
        assertEquals(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)), array)
    }

    fun `test complex nested object parses`() {
        val jsonString = """
            {
                "level1": {
                    "level2": {
                        "level3": {
                            "name": "deepNested",
                            "value": 42
                        },
                        "array": [1, 2, 3]
                    },
                    "boolean": true
                },
                "rootValue": "test"
            }
        """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val level1 = (result as Map<*, *>)["level1"]
        assertTrue(level1 is Map<*, *>)
        val level2 = (level1 as Map<*, *>)["level2"]
        assertTrue(level2 is Map<*, *>)
        val level3 = (level2 as Map<*, *>)["level3"]
        assertTrue(level3 is Map<*, *>)
        assertEquals("deepNested", (level3 as Map<*, *>)["name"])
        assertEquals(42.0, level3["value"])
        val array = level2["array"]
        assertTrue(array is List<*>)
        assertEquals(listOf(1.0, 2.0, 3.0), array)
        assertEquals(true, level1["boolean"])
        assertEquals("test", result["rootValue"])
    }

    fun `test complex mixed object parses`() {
        val jsonString = """
            {
                "string": "test",
                "number": 123,
                "boolean": true,
                "nullValue": null,
                "array": [1, "two", false, null, {"nested": "object"}],
                "nestedObject": {
                    "key1": "value1",
                    "key2": 2,
                    "key3": [3, 4, 5]
                }
            }
        """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals("test", mapResult["string"])
        assertEquals(123.0, mapResult["number"])
        assertEquals(true, mapResult["boolean"])
        assertNull(mapResult["nullValue"])
        val array = mapResult["array"]
        assertTrue(array is List<*>)
        val arrayList = array as List<*>
        assertEquals(1.0, arrayList[0])
        assertEquals("two", arrayList[1])
        assertEquals(false, arrayList[2])
        assertNull(arrayList[3])
        val nestedInArray = arrayList[4]
        assertTrue(nestedInArray is Map<*, *>)
        assertEquals("object", (nestedInArray as Map<*, *>)["nested"])
        val nestedObject = mapResult["nestedObject"]
        assertTrue(nestedObject is Map<*, *>)
        assertEquals("value1", (nestedObject as Map<*, *>)["key1"])
        assertEquals(2.0, nestedObject["key2"])
        val nestedArray = nestedObject["key3"]
        assertTrue(nestedArray is List<*>)
        assertEquals(listOf(3.0, 4.0, 5.0), nestedArray)
    }

    fun `test empty string parses`() {
        val jsonString = ""
        val parser = JsonParser(jsonString)
        try {
            parser.parse()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected exception
        }
    }

    fun `test whitespace only string parses`() {
        val jsonString = "   "
        val parser = JsonParser(jsonString)
        try {
            parser.parse()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected exception
        }
    }

    fun `test string with escaped backslash parses`() {
        val jsonString = """"This is a backslash: \\""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("This is a backslash: \\", result)
    }

    fun `test string with escaped forward slash parses`() {
        val jsonString = """"This is a forward slash: \/""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("This is a forward slash: /", result)
    }

    fun `test string with escaped double quote parses`() {
        val jsonString = """"He said, \"Hello!\"""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("He said, \"Hello!\"", result)
    }

    fun `test string with escaped unicode character parses`() {
        val jsonString = """"Unicode test: \u263A""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("Unicode test: \u263A", result)
    }

    fun `test string with mixed escapes parses`() {
        val jsonString = """"Mixed: \\ \/ \" \b \f \n \r \t \u263A""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("Mixed: \\ / \" \b \u000C \n \r \t \u263A", result)
    }

    fun `test string with consecutive escape sequences parses`() {
        val jsonString = """"Consecutive: \\\\ \"\" \u263A\u263A""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("Consecutive: \\\\ \"\" \u263A\u263A", result)
    }

    fun `test empty array parses`() {
        val jsonString = "[]"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        assertTrue((result as List<*>).isEmpty())
    }

    fun `test array with mixed types parses`() {
        val jsonString = "[1, \"two\", false, null, {\"key\": \"value\"}]"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        val array = result as List<*>
        assertEquals(1.0, array[0])
        assertEquals("two", array[1])
        assertEquals(false, array[2])
        assertNull(array[3])
        val nested = array[4]
        assertTrue(nested is Map<*, *>)
        assertEquals("value", (nested as Map<*, *>)["key"])
    }

    fun `test array with nested arrays parses`() {
        val jsonString = "[[1, 2], [3, 4]]"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        val array = result as List<*>
        assertTrue(array[0] is List<*>)
        assertTrue(array[1] is List<*>)
        assertEquals(listOf(1.0, 2.0), array[0])
        assertEquals(listOf(3.0, 4.0), array[1])
    }

    fun `test array with nested objects parses`() {
        val jsonString = "[{\"key1\": \"value1\"}, {\"key2\": \"value2\"}]"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        val array = result as List<*>
        assertTrue(array[0] is Map<*, *>)
        assertTrue(array[1] is Map<*, *>)
        assertEquals("value1", (array[0] as Map<*, *>)["key1"])
        assertEquals("value2", (array[1] as Map<*, *>)["key2"])
    }

    fun `test object with empty key parses`() {
        val jsonString = """{"": "empty key"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("empty key", (result as Map<*, *>)[""])
    }

    fun `test object with empty string value parses`() {
        val jsonString = """{"key": ""}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("", (result as Map<*, *>)["key"])
    }

    fun `test object with nested empty object parses`() {
        val jsonString = """{"key": {}}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val nested = (result as Map<*, *>)["key"]
        assertTrue(nested is Map<*, *>)
        assertTrue((nested as Map<*, *>).isEmpty())
    }

    fun `test object with boolean keys parses`() {
        val jsonString = """{"true": "trueKey", "false": "falseKey"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals("trueKey", mapResult["true"])
        assertEquals("falseKey", mapResult["false"])
    }

    fun `test object with numeric keys parses`() {
        val jsonString = """{"1": "one", "2": "two"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals("one", mapResult["1"])
        assertEquals("two", mapResult["2"])
    }

    fun `test object with special character keys parses`() {
        val jsonString = """{"!@#$%^&*()": "special"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("special", (result as Map<*, *>)["!@#$%^&*()"])
    }

    fun `test deeply nested arrays and objects parses`() {
        val jsonString = """{"key": [[[{"nestedKey": [1, 2, 3]}]]]}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val nestedArray = (result as Map<*, *>)["key"]
        assertTrue(nestedArray is List<*>)
        val level1 = nestedArray as List<*>
        val level2 = level1[0] as List<*>
        val level3 = level2[0] as List<*>
        val level4 = level3[0] as Map<*, *>
        val nestedKey = level4["nestedKey"]
        assertEquals(listOf(1.0, 2.0, 3.0), nestedKey)
    }

    fun `test object with duplicate keys parses`() {
        val jsonString = """{"key": "value1", "key": "value2"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value2", (result as Map<*, *>)["key"])
    }

    fun `test array with null elements parses`() {
        val jsonString = "[null, null, null]"
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        val array = result as List<*>
        assertNull(array[0])
        assertNull(array[1])
        assertNull(array[2])
    }

    fun `test object with null values parses`() {
        val jsonString = """{"key1": null, "key2": null}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertNull((result as Map<*, *>)["key1"])
        assertNull(result["key2"])
    }

    fun `test string with control characters parses`() {
        val jsonString = """"Control characters: \b \f \n \r \t""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("Control characters: \b \u000C \n \r \t", result)
    }

    fun `test object with floating point numbers parses`() {
        val jsonString = """{"float1": 0.1, "float2": -0.1, "float3": 3.14159}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals(0.1, mapResult["float1"])
        assertEquals(-0.1, mapResult["float2"])
        assertEquals(3.14159, mapResult["float3"])
    }

    fun `test object with very large integers parses`() {
        val jsonString = """{"largeInt": 9223372036854775807}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals(9223372036854775807.0, (result as Map<*, *>)["largeInt"])
    }

    fun `test object with very small integers parses`() {
        val jsonString = """{"smallInt": -9223372036854775808}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals(-9223372036854775808.0, (result as Map<*, *>)["smallInt"])
    }

    fun `test object with floating point numbers in exponential notation parses`() {
        val jsonString = """{"exp1": 1.23e4, "exp2": -1.23e-4}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals(12300.0, mapResult["exp1"])
        assertEquals(-0.000123, mapResult["exp2"])
    }

    fun `test object with mixed case true false null parses`() {
        val jsonString = """{"True": true, "False": false, "Null": null}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals(true, mapResult["True"])
        assertEquals(false, mapResult["False"])
        assertNull(mapResult["Null"])
    }

    fun `test object with leading and trailing whitespace parses`() {
        val jsonString = """   {"key": "value"}   """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value", (result as Map<*, *>)["key"])
    }

    fun `test array with leading and trailing whitespace parses`() {
        val jsonString = """   [1, 2, 3]   """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        assertEquals(listOf(1.0, 2.0, 3.0), result)
    }

    fun `test complex object with mixed whitespace parses`() {
        val jsonString = """
            {
                "key1" : "value1",
                "key2":    "value2"   ,
                "key3" :{ "nestedKey" : [ 1 ,  2 , 3  ] } 
            }
        """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals("value1", mapResult["key1"])
        assertEquals("value2", mapResult["key2"])
        val nested = mapResult["key3"]
        assertTrue(nested is Map<*, *>)
        assertEquals(listOf(1.0, 2.0, 3.0), (nested as Map<*, *>)["nestedKey"])
    }

    fun `test object with special unicode characters parses`() {
        val jsonString = """{"key": "value\u263A"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value\u263A", (result as Map<*, *>)["key"])
    }

    fun `test string with only unicode characters parses`() {
        val jsonString = """"\u263A\u263B\u263C""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("\u263A\u263B\u263C", result)
    }

    fun `test object with multiple nested levels and mixed content parses`() {
        val jsonString = """
            {
                "level1": {
                    "level2": {
                        "level3": {
                            "name": "deepNested",
                            "value": 42
                        },
                        "array": [1, 2, 3]
                    },
                    "boolean": true
                },
                "rootValue": "test"
            }
        """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val level1 = (result as Map<*, *>)["level1"]
        assertTrue(level1 is Map<*, *>)
        val level2 = (level1 as Map<*, *>)["level2"]
        assertTrue(level2 is Map<*, *>)
        val level3 = (level2 as Map<*, *>)["level3"]
        assertTrue(level3 is Map<*, *>)
        assertEquals("deepNested", (level3 as Map<*, *>)["name"])
        assertEquals(42.0, level3["value"])
        val array = level2["array"]
        assertTrue(array is List<*>)
        assertEquals(listOf(1.0, 2.0, 3.0), array)
        assertEquals(true, level1["boolean"])
        assertEquals("test", result["rootValue"])
    }

    fun `test object with single-character keys and values parses`() {
        val jsonString = """{"a": "b", "c": "d"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val mapResult = result as Map<*, *>
        assertEquals("b", mapResult["a"])
        assertEquals("d", mapResult["c"])
    }

    fun `test string with escaped single quote parses`() {
        val jsonString = """"This is a single quote: \'""""
        val parser = JsonParser(jsonString)
        try {
            parser.parse()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected exception because single quote escape is not valid in JSON
        }
    }

    fun `test string with multiple unicode escape sequences parses`() {
        val jsonString = """"Unicode: \u263A \u263B \u263C""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("Unicode: \u263A \u263B \u263C", result)
    }

    fun `test object with deeply nested mixed content and large array parses`() {
        val jsonString = """
            {
                "level1": {
                    "level2": {
                        "level3": {
                            "name": "deepNested",
                            "value": 42,
                            "array": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
                        },
                        "boolean": true
                    },
                    "rootValue": "test"
                }
            }
        """
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        val level1 = (result as Map<*, *>)["level1"]
        assertTrue(level1 is Map<*, *>)
        val level2 = (level1 as Map<*, *>)["level2"]
        assertTrue(level2 is Map<*, *>)
        val level3 = (level2 as Map<*, *>)["level3"]
        assertTrue(level3 is Map<*, *>)
        assertEquals("deepNested", (level3 as Map<*, *>)["name"])
        assertEquals(42.0, level3["value"])
        val array = level3["array"]
        assertTrue(array is List<*>)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0), array)
        assertEquals(true, level2["boolean"])
        assertEquals("test", level1["rootValue"])
    }

    fun `test missing comma between elements throws exception`() {
        val jsonString = """["value1" "value2"]"""
        val parser = JsonParser(jsonString)
        try {
            parser.parse()
            fail("Expected IllegalArgumentException for missing comma between elements")
        } catch (e: IllegalArgumentException) {
            // Expected exception
        }
    }

    fun `test missing colon throws exception`() {
        val jsonString = """{"key" "value"}"""
        val parser = JsonParser(jsonString)
        try {
            parser.parse()
            fail("Expected IllegalArgumentException for missing colon between key and value")
        } catch (e: IllegalArgumentException) {
            // Expected exception
        }
    }

    fun `test trailing comma in object is allowed`() {
        val jsonString = """{"key": "value",}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse() as? Map<*, *>
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("value", result["key"])
    }

    fun `test trailing comma in array is allowed`() {
        val jsonString = """["value1", "value2",]"""
        val parser = JsonParser(jsonString)
        val result = parser.parse() as? List<*>
        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals(listOf("value1", "value2"), result)
    }

    fun `test missing value in array throws exception`() {
        val jsonString = """["value1", , "value2"]"""
        val parser = JsonParser(jsonString)
        try {
            parser.parse()
            fail("Expected IllegalArgumentException for missing value in array")
        } catch (e: IllegalArgumentException) {
            // Expected exception
        }
    }

    fun `test missing value in object throws exception`() {
        val jsonString = """{"key1": "value1", "key2": }"""
        val parser = JsonParser(jsonString)
        try {
            parser.parse()
            fail("Expected IllegalArgumentException for missing value in object")
        } catch (e: IllegalArgumentException) {
            // Expected exception
        }
    }

    fun `test missing closing brace returns partial object`() {
        val jsonString = """{"string": "value""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value", (result as Map<*, *>)["string"])
    }

    fun `test missing closing bracket returns partial array`() {
        val jsonString = """["value1", "value2""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        val array = result as List<*>
        assertEquals(2, array.size)
        assertEquals("value1", array[0])
        assertEquals("value2", array[1])
    }

    fun `test unterminated string returns partial object`() {
        val jsonString = """{"key": "unterminated}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("unterminated}", (result as Map<*, *>)["key"])
    }

    fun `test trailing comma in object returns valid part`() {
        val jsonString = """{"key": "value",}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value", (result as Map<*, *>)["key"])
    }

    fun `test trailing comma in array returns valid part`() {
        val jsonString = """["value1", "value2",]"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        val array = result as List<*>
        assertEquals(2, array.size)
        assertEquals("value1", array[0])
        assertEquals("value2", array[1])
    }

    fun `test extra closing brace returns valid part`() {
        val jsonString = """{"key": "value"}}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value", (result as Map<*, *>)["key"])
    }

    fun `test extra closing bracket returns valid part`() {
        val jsonString = """["value1", "value2"]]"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is List<*>)
        val array = result as List<*>
        assertEquals(2, array.size)
        assertEquals("value1", array[0])
        assertEquals("value2", array[1])
    }

    fun `test unescaped control character returns partial object`() {
        val jsonString = """{"key": "value with unescaped \u0001 control character"}"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value with unescaped \u0001 control character", (result as Map<*, *>)["key"])
    }

    fun `test invalid unicode escape sequence returns valid part`() {
        val jsonString = """"Invalid unicode: \u263""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("Invalid unicode: \u0263", result)
    }

    fun `test invalid character outside of JSON structure returns valid object`() {
        val jsonString = """{"key": "value"} invalid"""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertTrue(result is Map<*, *>)
        assertEquals("value", (result as Map<*, *>)["key"])
    }

    fun `test single value without container returns value`() {
        val jsonString = """"value""""
        val parser = JsonParser(jsonString)
        val result = parser.parse()
        assertEquals("value", result)
    }

    fun `test composer json`() {
        val jsonString = """
            {
                "name": "cakephp/app",
                "description": "CakePHP skeleton app",
                "homepage": "https://cakephp.org",
                "type": "project",
                "license": "MIT",
                "require": {
                    "php": ">=7.4",
                    "cakephp/cakephp": "4.4.*",
                    "cakephp/migrations": "^3.2",
                    "cakephp/plugin-installer": "^1.3",
                    "mobiledetect/mobiledetectlib": "^2.8"
                },
                "require-dev": {
                    "cakephp/bake": "^2.6",
                    "cakephp/cakephp-codesniffer": "^4.5",
                    "cakephp/debug_kit": "^4.5",
                    "josegonzalez/dotenv": "^3.2",
                    "phpunit/phpunit": "~8.5.0 || ^9.3"
                },
                "suggest": {
                    "markstory/asset_compress": "An asset compression plugin which provides file concatenation and a flexible filter system for preprocessing and minification.",
                    "dereuromark/cakephp-ide-helper": "After baking your code, this keeps your annotations in sync with the code evolving from there on for maximum IDE and PHPStan/Psalm compatibility.",
                    "phpstan/phpstan": "PHPStan focuses on finding errors in your code without actually running it. It catches whole classes of bugs even before you write tests for the code.",
                    "cakephp/repl": "Console tools for a REPL interface for CakePHP applications."
                },
                "autoload": {
                    "psr-4": {
                        "App\\": "src/"
                    }
                },
                "autoload-dev": {
                    "psr-4": {
                        "App\\Test\\": "tests/",
                        "Cake\\Test\\": "vendor/cakephp/cakephp/tests/"
                    }
                },
                "scripts": {
                    "post-install-cmd": "App\\Console\\Installer::postInstall",
                    "post-create-project-cmd": "App\\Console\\Installer::postInstall",
                    "check": [
                        "@test",
                        "@cs-check"
                    ],
                    "cs-check": "phpcs --colors -p  src/ tests/",
                    "cs-fix": "phpcbf --colors -p src/ tests/",
                    "stan": "phpstan analyse",
                    "test": "phpunit --colors=always"
                },
                "prefer-stable": true,
                "config": {
                    "sort-packages": true,
                    "allow-plugins": {
                        "cakephp/plugin-installer": true,
                        "dealerdirect/phpcodesniffer-composer-installer": true
                    }
                }
            }
        """.trimIndent()
        val parsed = jsonParse(jsonString) as? Map<*, *>
        assertNotNull(parsed)
        val autoloadObj = parsed!!["autoload"] as? Map<*, *>
        assertNotNull(autoloadObj)
        val psr4 = autoloadObj!!["psr-4"] as? Map<*, *>
        assertNotNull(psr4)
        assertEquals(setOf("App\\"), psr4!!.keys)
        assertEquals(setOf("src/"), psr4.values.toSet())
    }

}
