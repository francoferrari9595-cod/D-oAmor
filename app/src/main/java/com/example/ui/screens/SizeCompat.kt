package com.example.ui.screens

import androidx.compose.ui.unit.IntSize

/**
 * Dentro de `pointerInput { }` el `size` es un IntSize (en píxeles enteros), que NO tiene `minDimension`
 * (esa propiedad solo existe en `Size`, el tamaño de un DrawScope). Esta extensión permite escribir
 * `size.minDimension` en ambos lugares sin errores de compilación.
 */
internal val IntSize.minDimension: Float get() = minOf(width, height).toFloat()
