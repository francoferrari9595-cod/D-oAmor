# Cambios v76 — diseño de apuestas virtuales
- Base acumulativa: SINI-JOSS v75 YouTube/Truco profesional con piezas de ajedrez transparentes.
- Añadido `Betting.kt`: modelo puro para políticas de apuesta fija o límites del anfitrión, validación de saldo, reserva lógica y liquidación/refund sin doble procesamiento por estado.
- Añadido `DISEÑO-APUESTAS-V76.md`: arquitectura de integración para todos los juegos, ledger, idempotencia y persistencia transaccional.
- No se modificaron los flujos de recarga, envío de audio ni los juegos existentes en esta fase.
- No se declara terminada la integración completa: falta conectar una operación transaccional remota y los adaptadores de cada juego antes de activar apuestas reales dentro de la app.
