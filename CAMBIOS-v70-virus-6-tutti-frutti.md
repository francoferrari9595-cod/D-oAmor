# SINI-JOSS v70 — Virus 2–6 y Tutti Frutti sincronizado

- Base: SINI-JOSS v69 únicamente; se conservan las correcciones anteriores.
- Añadido Tutti Frutti con letra aleatoria, categorías aleatorias, cronómetro sincronizado, botón ¡BASTA!, respuestas compartidas y puntuación de respuestas únicas/repetidas.
- El lobby admite hasta seis participantes humanos para Virus y Tutti Frutti.
- Virus admite hasta seis asientos y el tamaño visual de las cartas de órganos se adapta al ancho disponible.
- La carta de medicina permite curar un órgano de otro jugador, además del propio, como variante de ayuda solicitada.

## Pendiente de validar
- Compilación Android completa en GitHub Actions.
- Prueba real en varios teléfonos y comprobación de escrituras concurrentes en Firestore.
- El PDF de reglas de Virus todavía no estaba adjunto al momento de esta modificación; revisar reglas y nombres/ilustraciones cuando se aporte.
- Las apuestas con coins de todos los juegos necesitan liquidación atómica/idempotente en servidor; no se simula una seguridad financiera que la implementación actual de `trySpend` todavía no garantiza.
