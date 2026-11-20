# v65 — Inicio de póker desde la sala compartida

Base: SINI-JOSS v64.

## Cambios
- Al presionar «Comenzar partida» en la sala compartida, el anfitrión también reparte automáticamente la primera mano de póker. Se elimina el segundo paso manual «Sentarse y repartir» en el flujo normal.
- Se mantiene el control de anfitrión único para evitar dos repartos simultáneos.

## Validación
- Revisión estática del efecto y del control `isTableHost`.
- ZIP verificado con `unzip -t`.
- No se ha podido ejecutar compilación Android ni probar dos dispositivos reales.
