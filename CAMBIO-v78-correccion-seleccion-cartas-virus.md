# v78 — Corrección de selección de cartas en VIRUS!

- La selección visual de la mano ahora se identifica por posición, no solamente por el valor codificado de la carta.
- Esto evita que dos copias iguales (por ejemplo, dos medicinas del mismo tipo) aparezcan seleccionadas al mismo tiempo.
- Se limpia la selección local y los objetivos al cambiar el turno, el resultado o la cantidad de cartas en mano, evitando selecciones obsoletas tras actualizaciones del estado compartido.
- Se conserva la baraja y los recursos gráficos existentes de esta base; no se recuperan archivos de versiones antiguas.

Pendiente de validar mediante una compilación Android completa en el entorno de CI.
