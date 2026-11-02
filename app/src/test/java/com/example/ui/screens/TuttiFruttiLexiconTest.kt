package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuttiFruttiLexiconTest {
  @Test fun acceptsKnownSpanishWordInMatchingCategory() {
    assertTrue(TuttiFruttiLexicon.isValid("Animal", "gato"))
  }

  @Test fun rejectsWordOutsideCategory() {
    assertFalse(TuttiFruttiLexicon.isValid("Animal", "mesa"))
  }

  @Test fun rejectsUnknownWord() {
    assertFalse(TuttiFruttiLexicon.isValid("Animal", "xqzzplm"))
  }

  @Test fun acceptsProvinceWithAccentNormalization() {
    assertTrue(TuttiFruttiLexicon.isValid("País o provincia", "Córdoba"))
  }

  @Test fun preservesEnye() {
    assertTrue(TuttiFruttiLexicon.isValid("Animal", "ñandú"))
  }
  @Test fun acceptsExpandedVocabularyInMatchingCategories() {
    assertTrue(TuttiFruttiLexicon.isValid("Animal", "gecko"))
    assertTrue(TuttiFruttiLexicon.isValid("Comida", "baklava"))
    assertTrue(TuttiFruttiLexicon.isValid("Objeto", "mosquetón"))
    assertTrue(TuttiFruttiLexicon.isValid("Profesión", "oceanógrafo"))
    assertTrue(TuttiFruttiLexicon.isValid("Fruta o verdura", "pitahaya"))
  }

  @Test fun acceptsMultiwordAnswersAndNormalizesAccents() {
    assertTrue(TuttiFruttiLexicon.isValid("Ciudad", "Buenos Aires"))
    assertTrue(TuttiFruttiLexicon.isValid("Comida", "SÁNDWICH DE MIGA"))
    assertTrue(TuttiFruttiLexicon.isValid("País o provincia", "Tierra del Fuego"))
  }

  @Test fun rejectsKnownWordFromWrongCategoryAndUnknownCategory() {
    assertFalse(TuttiFruttiLexicon.isValid("Comida", "gecko"))
    assertFalse(TuttiFruttiLexicon.isValid("Categoría nueva", "gato"))
  }

}
