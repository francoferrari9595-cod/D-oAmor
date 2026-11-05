SINI&JOSS v85 — primera etapa de mejoras

Base: SINI-JOSS-v84-MULTICOLOR-DESCARTE-YOUTUBE.zip recibido en esta conversación.
No se recuperaron archivos de versiones anteriores ni se revirtieron cambios de v84.

Cambios incluidos:
1. Truco argentino: corregido el índice de palos usado en la jerarquía. El mazo interno está codificado en el orden oros, copas, espadas, bastos, pero la fuerza de los anchos y sietes se estaba calculando como si el índice 0 fuera espada. Ahora el ancho de espadas, ancho de bastos, siete de espadas y siete de oros tienen su jerarquía correcta. También se corrigió el símbolo mostrado para cada palo.
2. Tests del Truco: actualizadas las pruebas de jerarquía para usar los identificadores de carta correctos.
3. Adivina el dibujo: ampliado el banco local de consignas mediante nuevos sujetos y combinaciones con situaciones. El banco generado supera las 5.009 consignas únicas; son consignas textuales para dibujar, no 5.009 imágenes prediseñadas.
4. Adivina el dibujo: agregado un sonido WAV corto para cada uno de los últimos diez segundos del cronómetro. El sonido se reproduce localmente en los dispositivos que ven la cuenta regresiva.
5. Chat: al tocar una foto enviada se abre un visor ampliado y se puede cerrar tocando fuera o con el botón de cerrar. La foto de perfil abre el visor si hay una imagen real disponible; los avatares emoji no abren una vista vacía.

Validación disponible:
- El ZIP pasó la comprobación de integridad.
- La revisión sintáctica básica no detectó errores de parseo en los tres archivos Kotlin modificados; la compilación completa no se pudo ejecutar.
- No se pudo ejecutar compilación Android en este entorno porque el ZIP no trae gradlew y no hay Gradle instalado.
- No se afirma que los cambios estén validados en dispositivos reales.

Pendiente de próximas etapas:
- Diccionario español completo y validación de categoría para Tutti Frutti.
- Revisión integral de todos los cantos, respuestas, reglas y pruebas del Truco.
- Implementación de lotería mexicana, bingo de 1 a 6 jugadores, juego de propiedades y trivia con 5.000 preguntas.
- Pruebas de sincronización, reconexión y partidas entre dispositivos.
