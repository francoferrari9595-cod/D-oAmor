# SINI&JOSS v86 — Tutti Frutti: primera fase de diccionario

Base de trabajo: paquete v85 recibido en esta conversación. No se restauraron archivos de versiones anteriores.

## Cambios
- Nuevo `TuttiFruttiLexicon.kt` con vocabulario español local inicial y listas de palabras por categoría.
- La puntuación automática solo considera respuestas reconocidas por la lista de su categoría y que comiencen con la letra de la ronda.
- Se conserva la regla de 10 puntos para respuestas válidas únicas y 5 puntos por respuesta válida repetida.
- Normalización de mayúsculas, tildes, espacios y tratamiento especial de la letra ñ.
- Añadidas pruebas unitarias de aceptación, rechazo, tildes y ñ.

## Limitación pendiente
Este vocabulario es una primera base local curada, no un diccionario exhaustivo de español ni una verificación directa con la RAE. Puede rechazar palabras válidas que todavía no estén en sus listas. El siguiente incremento debe ampliar el léxico con una fuente compatible con su licencia y extender los vocabularios por categoría.

## Validación
No se declara compilación exitosa: el paquete de base no contiene `gradlew` y el entorno actual no dispone de Gradle para ejecutar el build.
