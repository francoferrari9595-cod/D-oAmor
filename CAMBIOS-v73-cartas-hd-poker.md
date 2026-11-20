# v73 — Cartas HD en póker

- Base: SINI-JOSS-v72-truco-cartas-HD.
- Se integraron 52 imágenes de cartas recortadas desde la cuadrícula aportada, en `app/src/main/res/drawable-nodpi/poker_card_00.png` a `poker_card_51.png`.
- La pantalla de póker ahora muestra imágenes de las cartas propias, comunitarias y del rival cuando se revelan; las cartas ocultas conservan el dorso visual.
- Se mantuvo el código de Truco y el resto del proyecto de v72 sin cambios intencionales.
- Pendiente de verificación externa: ejecutar `./gradlew :app:assembleDebug` y revisar visualmente la correspondencia de las figuras/ases en dispositivo.
