# SINI-JOSS v76 — Diseño acumulativo de apuestas virtuales

## Decisiones confirmadas
- Los créditos son virtuales y no se retiran ni canjean por dinero.
- Las recargas y recompensas actuales (incluido el envío de audios) se conservan sin cambios.
- Cada mesa puede usar apuesta fija o límites definidos por el anfitrión; el modo se guarda en la configuración de esa mesa.
- La versión base es `SINI-JOSS-v75-youtube-truco-profesional.zip`. No se recuperan archivos de versiones anteriores.

## Flujo de cada partida
1. El anfitrión crea/abre una mesa y selecciona `FIXED` o `HOST_LIMITS`, con mínimo, máximo e intervalo. En modo fijo, todos aportan el importe fijado; en modo anfitrión, cada participante elige dentro de los límites.
2. Cada participante confirma la entrada. El servidor valida saldo y reserva los créditos; no se inicia la partida hasta que todas las entradas estén reservadas.
3. El estado de apuesta queda ligado a `roomId + gameId + roundId`. Las acciones de juego existentes siguen siendo responsables de cartas/turnos/resultados.
4. Al finalizar, el juego entrega un resultado validado. El servidor liquida una sola vez y registra movimientos de débito, premio o devolución.
5. Si hay empate/cancelación, se devuelve el importe reservado según la regla de la mesa. Desconexión no declara automáticamente ganador: aplica el estado de abandono definido por el juego.

## Modelo añadido
`app/src/main/java/com/example/model/Betting.kt` define políticas, participantes, estados, validación de importes, reserva lógica y cálculo puro de liquidación. No escribe directamente los saldos.

## Persistencia remota necesaria antes de habilitarlo en producción
El saldo existente está en `wallet.<uid>` y se actualiza mediante `FieldValue.increment` desde `CoupleViewModel`. No debe usarse ese incremento aislado como reserva de apuesta: dos dispositivos podrían gastar simultáneamente el mismo saldo. La integración debe realizarse con una transacción Firestore (o callable Cloud Function) que lea y valide todos los saldos, escriba las reservas y un `roundId` idempotente en una única operación. Si las reglas de Firestore no permiten una transacción segura desde el cliente, usar Cloud Function autenticada.

Estructura sugerida:
- `couples/{coupleId}/betRooms/{roomId}`: anfitrión, modo, límites, estado.
- `couples/{coupleId}/betRounds/{roundId}`: juego, participantes, apuestas reservadas, estado, ganador y `settledAt`.
- `couples/{coupleId}/walletLedger/{operationId}`: uid, delta, tipo (`STAKE`, `PRIZE`, `REFUND`), roundId, timestamp. `operationId` único e idempotente.

## Integración por juego
- Truco: reservar antes de repartir; liquidar al resultado final validado de la partida, no en cada carta ni cada baza salvo que se diseñe explícitamente una apuesta de baza.
- Póker: conservar la lógica de rondas/all-in existente y reemplazar los movimientos de saldo directos por operaciones del ledger; evitar doble cobro por re-render/reconexión.
- Ruleta, tragamonedas y demás minijuegos: cada juego tendrá un adaptador que valide la apuesta y mande el resultado a un único servicio. Los juegos que hoy tienen premios propios no deben sumar premios duplicados.
- Ajedrez y otros juegos competitivos: entrada por partida y liquidación al resultado final.

## Compatibilidad y límites de esta entrega
Esta versión añade el modelo de dominio y el diseño, pero NO activa apuestas en todas las pantallas ni cambia el saldo actual todavía. Es intencional: conectar solo la interfaz sin una reserva remota atómica permitiría saldos negativos o premios duplicados. La próxima fase es implementar el servicio transaccional y después conectar cada juego sobre la base v76, con pruebas de concurrencia, empate, cancelación, reconexión y liquidación repetida.
