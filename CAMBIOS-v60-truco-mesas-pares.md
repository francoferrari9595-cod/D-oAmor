# v60 — Truco siempre en equipos pares

Base: SINI-JOSS v59.

## Corrección
El cálculo anterior podía dejar una mesa de tres asientos si había dos humanos y se pedía un bot, o si el recuento de bots generaba una cantidad impar. Ahora se agregan primero los bots solicitados y después se completa automáticamente con un bot compañero cuando el total queda impar. La mesa queda entre 2 y 4 asientos y siempre tiene un número par de jugadores.

## Validación
- Revisión estática de los casos de 1, 2, 3 y 4 humanos y del límite de cuatro asientos.
- ZIP verificado con `unzip -t`.
- Falta ejecutar pruebas Android y partidas reales.
