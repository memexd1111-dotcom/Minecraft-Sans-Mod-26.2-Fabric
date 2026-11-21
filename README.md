# Sans Abilities (Fabric, Minecraft 26.2, cliente + servidor)

Un amuleto que da habilidades inspiradas en Sans. Necesita estar instalado en el cliente **y** en el servidor
(en un mundo de un jugador ya cumple las dos cosas). Requiere Fabric API.

## Conseguir el amuleto
- Comando: `/give @s sansmod:sans_charm` (y `/give @s sansmod:hot_dog`).
- Creativo: pestaña Combate (amuleto) y Comida (perrito caliente).
- Supervivencia (balance): receta cara en `data/sansmod/recipe/sans_charm.json`:
  4 bloques de diamante, 2 estrellas del Nether, 2 bloques de hueso y 1 calavera de esqueleto.
  Si lo quieres solo por comando, borra ese archivo del proyecto antes de compilar.
  El perrito caliente se craftea con 1 chuleta cocinada + 1 pan (da 2).

## Controles (se cambian en Opciones > Controles)
- **R**: lanzar la habilidad elegida
- **V**: menú para elegir habilidad (clic en el icono)
- **X / Z**: siguiente / anterior habilidad
El amuleto debe estar en cualquier lugar del inventario.

## Habilidades
Esquive (automático, con energía), Karma, Huesos, Muro de huesos, Blaster, Lluvia de blasters,
Telequinesis, Atajo (teletransporte), Ojo brillante, Mal momento. Los perritos calientes curan.

## Configuración
`config/sansmod.properties` (se crea sola): cooldowns, daños, rangos, energía de esquive, costo de hambre, etc.

## Simplificaciones
- Los huesos, calaveras y el muro son armor stands invisibles con un objeto en la cabeza (sprites planos);
  los rayos son partículas. No hay renderers propios.
- Sonidos y partículas de vanilla. Texturas de pixel art generadas por código (reemplázalas en
  `assets/sansmod/textures` por las tuyas).
- El estado (cooldowns, energía) se pierde al cerrar el mundo.
