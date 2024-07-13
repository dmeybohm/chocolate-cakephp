package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.JsonParser

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
        val parsed = JsonParser(jsonString).parse() as? Map<*, *>
        assertNotNull(parsed)
        val autoloadObj = parsed!!["autoload"] as? Map<*, *>
        assertNotNull(autoloadObj)
        val psr4 = autoloadObj!!["psr-4"] as? Map<*, *>
        assertNotNull(psr4)
        assertEquals(setOf("App\\"), psr4!!.keys)
        assertEquals(setOf("src/"), psr4.values.toSet())
    }

}
