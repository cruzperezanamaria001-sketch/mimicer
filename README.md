# THE MIMIC 1.0.0

Mod de terror para Minecraft Java 1.20.1 (Forge 47.4.10, Java 17).

## Instalación
1. Descarga el `.jar` desde la pestaña **Actions** → última ejecución → artefacto `mimicer-jar`.
2. Ponlo en la carpeta `mods` de Minecraft Forge 1.20.1.

## Qué incluye
- **Mímico**: de día es un aldeano normal (usa la textura del aldeano de vainilla); de noche se transforma
  con una animación en un monstruo con ojos rojos. Persigue jugadores, aldeanos y animales, abre y rompe puertas,
  cristales y tablas, investiga ruidos, revisa camas, se asoma a las ventanas y rodea la casa antes de atacar.
  Los monstruos hostiles no lo atacan. Aparece en un 5 % de los aldeanos de las aldeas nuevas, y con su huevo generador.
- **5 apariencias con habilidad propia** (se elige una al azar al aparecer; la forma de aldeano es igual en todas):
  - *Clásico* (verde, ojos rojos): **embestida** en línea recta a media distancia y golpe con empuje.
  - *Ceniza* (carbonizado, ojos naranja): **inmune al fuego** y prende a sus víctimas.
  - *Hueso* (pálido, ojos azul hielo): **aura de frío** que ralentiza y congela a jugadores y aldeanos cercanos.
  - *Musgo* (podrido, ojos amarillos): **veneno** al golpear y se regenera con el tiempo.
  - *Desollado* (músculo expuesto, ojos blancos): **frenesí**: cada golpe lo acelera (hasta 5 niveles) y le cura.
- **Daño localizado**: puede arrancar brazos y piernas (la víctima sobrevive, sangra y queda debilitada o lenta),
  sangre con partículas, cuerpos que conservan las partes perdidas (y la skin de los jugadores) y que el Mímico
  puede levantar y lanzar.
- **Aldeanos**: al ver algo raro alertan a los demás, corren a su cama, cierran puertas y colocan Tablas; la huida
  tiene tiempo y distancia máximos respecto a la aldea.
- **Tabla**: bloque fino disponible en el inventario creativo (pestaña *The Mimic*) y por receta (3 tablones → 6 tablas).
- **Esconderse**: agacharse + clic derecho en una cama. Saltar para salir. Solo te busca el Mímico que te vio
  esconderte (si no te vio, puede revisar la casa pero nunca te descubre). Te descubre si está
  pegado a la cama (con línea directa, no a través de paredes) o si se acerca, sospecha y la rompe.

## Estado
Compila en GitHub Actions. **No se ha probado dentro del juego**, así que el comportamiento de las IA, las
animaciones y los modelos puede necesitar ajustes. Pendiente / limitaciones conocidas:
- En los cuerpos de animales no se ocultan las patas perdidas (solo en jugadores y aldeanos).
- Los aldeanos usan su cerebro de vainilla; su huida y refugio se corrigen sobrescribiendo su objetivo de movimiento.
- La cámara bajo la cama se simula reduciendo la hitbox y el campo de visión del jugador dentro de la cama.
