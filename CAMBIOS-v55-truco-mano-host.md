# SINI-JOSS v55 — Truco: anfitrión único y parda triple

Base exclusiva: v54.

## Cambios
- Solo el anfitrión determinista puede iniciar/reiniciar una partida de Truco y seleccionar la cantidad inicial de bots. El otro dispositivo muestra una espera en lugar de publicar una segunda baraja que sobrescriba la primera.
- Si las tres bazas terminan empatadas, ahora se otorgan los puntos al equipo del jugador que fue mano al inicio de la ronda (`mano % 2`), en lugar de adjudicarlos siempre al equipo 1.

## Validación
- Revisión estática de inicio, revancha y desempate de tres pardas.
- ZIP comprobado íntegro.
- Sin compilación ni prueba de dos dispositivos; el entorno no tiene el Gradle Wrapper ni dependencias Android/Compose.
