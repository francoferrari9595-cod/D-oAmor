# SINI&JOSS v84 — cambios sobre la base v83

- Se mantiene v83 como base; no se recuperaron ni revirtieron archivos de versiones antiguas.
- Virus: se incorpora un modo explícito para seleccionar cartas de la mano y descartar exactamente 1 o 3 cartas en un turno. La selección se limita a tres y se limpia al cambiar el turno.
- Virus: se conserva la regla de coincidencia multicolor implementada por `colorMatch` (el color 4 coincide con todos los colores para virus y medicinas).
- Cine/YouTube: se cambia el reproductor predeterminado de navegación móvil a reproductor embebido, para reducir los casos de captcha y mantener la reproducción bajo control compartido. Los comandos Play/Pausa publican primero el estado sincronizado y cada reproductor converge al estado recibido.

## Validación

No se pudo ejecutar `assembleDebug` en este entorno porque el ZIP de entrada no incluye `gradlew` y no hay instalación de Gradle disponible. Es necesario compilar el proyecto en Android Studio/AI Studio antes de distribuir el APK. YouTube puede seguir restringiendo determinados videos o exigir verificación por decisiones de la plataforma; no es posible garantizar que nunca aparezca un captcha.
