package com.djmetry.ui

import com.djmetry.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

/** Контуры стран для карты аналитики зашиты в приложение (на JVM Android ресурсы Compose без контекста не читаются). */
class CountriesResourceTest {
    @Test
    fun countriesShapesBundled() = runTest {
        val json = Json.parseToJsonElement(Res.readBytes("files/countries.geojson").decodeToString()).jsonObject
        val isos = json["features"]!!.jsonArray.map { it.jsonObject["properties"]!!.jsonObject["iso"]!!.jsonPrimitive.content }.toSet()
        assertTrue(isos.size > 150)
        assertTrue(listOf("DE", "US", "FR", "NO", "BR", "UA").all { it in isos }, "Франция и Норвегия — по ISO_A2_EH")
    }
}
