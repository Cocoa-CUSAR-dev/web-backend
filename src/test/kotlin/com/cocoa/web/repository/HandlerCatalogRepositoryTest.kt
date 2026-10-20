package com.cocoa.web.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// US2-13: column type decides which question input types the builder offers
// and Kotlin accepts. The first entry is the builder's default.
class HandlerCatalogRepositoryTest {
    private fun allowed(
        dataType: String,
        fk: Boolean = false,
    ) = HandlerCatalogRepository.allowedInputTypes(dataType, fk)

    @Test
    fun `uuid columns are choices only`() {
        assertThat(allowed("uuid", fk = true)).containsExactly("OPTION")
    }

    @Test
    fun `numbers`() {
        assertThat(allowed("integer")).containsExactly("INT")
        assertThat(allowed("numeric")).containsExactly("FLOAT", "INT")
    }

    @Test
    fun `booleans, dates and timestamps`() {
        assertThat(allowed("boolean")).containsExactly("BOOLEAN")
        assertThat(allowed("date")).containsExactly("DATE")
        assertThat(allowed("timestamp without time zone")).containsExactly("DATETIME", "DATE")
    }

    @Test
    fun `varchar is free text unless it is a foreign key like grade_code`() {
        assertThat(allowed("character varying")).containsExactly("VARCHAR")
        assertThat(allowed("character varying", fk = true)).containsExactly("OPTION", "VARCHAR")
    }

    @Test
    fun `an unknown column type allows nothing rather than guessing`() {
        assertThat(allowed("geometry")).isEmpty()
    }

    @Test
    fun `registry identifiers are checked before they reach dynamic SQL`() {
        assertThat(HandlerRegistryRepository.isIdentifier("farm_activity")).isTrue()
        assertThat(HandlerRegistryRepository.isIdentifier("farm_activity; DROP TABLE x")).isFalse()
        assertThat(HandlerRegistryRepository.isIdentifier("Farm")).isFalse()
        assertThat(HandlerRegistryRepository.isIdentifier("1farm")).isFalse()
    }
}
