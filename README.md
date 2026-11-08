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
- **Aldeanos escondidos**: tras atrincherarse, un aldeano alarmado se mete bajo su cama (invisible y quieto). El
  Mímico lo descubre con las mismas reglas que a ti: solo si lo vio esconderse, y estando pegado a la cama o
  rompiéndola. Sale solo cuando pasa el peligro, si le hieren o si rompen la cama. Una cama solo aloja a uno.
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

## The Void (nuevo monstruo)

Alto y negro, como un Enderman, con ojos rojos. Se teletransporta a árboles y paredes de casas cerca de ti y se asoma tras el tronco o la pared. Si te acercas dice **"No te acerques"** (texto rojo + voz). Si te acercas más (o te quedas mirándolo) te persigue, te agarra, te levanta y te estampa contra el suelo (muerte instantánea, con sangre y posible pérdida de extremidades). Solo ataca a jugadores y aldeanos; los monstruos no le hacen nada. Huevo de aparición en la pestaña "The Mimic".

Limitaciones (sin probar en el juego): los golems de hierro sí pueden atacarlo; al medir 2,9 no cabe por puertas normales, así que si no encuentra camino se teletransporta junto a su presa.

## The Glitch (tercera entidad, requiere GeckoLib 4.8.4)

**Requisito:** instala GeckoLib 4.8.4 para Forge 1.20.1 junto al mod.

- **Observa:** aparece en aldeas (de forma natural solo cerca de aldeanos), tras ventanas o junto a paredes de casas, inmóvil y mirándote. Cambia de sitio cada 15-40 s sin ponerse casi nunca justo delante de ti.
- **Jumpscare:** si lo miras directamente se abalanza delante de ti, con distorsión de pantalla, náusea breve y sonido; luego se aparta. Enfriamiento configurable (por jugador y propio).
- **Corrupción:** si algo lo persigue (jugador, aldeano, animal o monstruo que lo ataque o se le acerque rápido) huye caminando y convierte el suelo en **Bloques Glitch** (textura animada, partículas), poco a poco. Límites en `mimicer-common.toml`: bloques máximos por entidad, radio, bloques por segundo; no toca bloques con inventario, irrompibles ni demasiado duros.
- **Rotura:** si se atasca rompe obstáculos blandos (dureza máxima y tiempo configurables), con grietas, partículas y sonido.
- **Rayo morado:** se detiene, carga energía y dispara un rayo que viaja hacia el objetivo, se detiene contra bloques y golpea una sola vez a cada entidad (2-3 corazones, configurable).
- **Sonidos:** sintetizados (aparición, jumpscare, corrupción, carga, rayo, pasos).
- Animaciones GeckoLib: aparición, idle, observación, giro de cabeza, jumpscare, caminata, persecución, corrupción, rotura, carga, disparo y muerte.

Limitaciones: compila en Actions, pero no se ha probado en el juego; el modelo y las animaciones están escritos a mano y pueden necesitar ajustes.

## Dynamic Blood System

- **Manchas = bloques finos** (`blood_stain`): sin entidades, sincronizados con todos los jugadores, sin colisión ni selección. Se pegan a suelos, paredes, techos, puertas, trampillas, vallas, barrotes y cristales. Se oscurecen en 3 fases y desaparecen solas.
- **Salpicaduras direccionales:** cada golpe lanza rayos desde el impacto en la dirección del ataque (proyectil, atacante o explosión) con dispersión aleatoria; la cantidad depende del daño. Hay 6 texturas (charco, mancha, chorro, gotas, arrastre) con rotación aleatoria. Las grandes se extienden o gotean por la pared.
- **Heridas:** los golpes y las extremidades perdidas generan una herida que decae sola; los heridos sueltan gotas, y huyendo dejan un rastro más denso cuanto menos vida y más grave la herida.
- **Muerte y cadáveres:** charco y salpicaduras horizontales que manchan paredes cercanas; los cadáveres con extremidades perdidas siguen manando un rato.
- **Mutilaciones:** cabeza (chorro hacia arriba), brazos (abanico lateral), piernas (bajo y gran charco). El mismo sistema se aplica al Mímico y a The Void. `BloodSystem.heavyHit(...)` queda preparado para The Reaper, que aún no existe.
- **No sangran:** The Glitch, The Void, golems, esqueletos, slimes ni blazes; tampoco hay manchas por fuego, ahogamiento o hambre.
- **Configuración** (`mimicer-blood.toml`): duración, intensidad, manchas máximas por minuto y por golpe, partículas por tick, rastros, frecuencia, extensión, charcos y cadáveres.

No se usa GeckoLib aquí: las manchas son bloques y las salpicaduras, partículas estándar. Compila en Actions, no probado en el juego.

## Alimentación y extracción de camas (The Mimic y The Void)

- **Alimentación**: tras matar a un jugador, aldeano o animal, el Mímico y The Void se acercan al cadáver, se agachan y lo devoran por tandas (brazos, piernas y al final la cabeza). Cada mordisco hace desaparecer una parte del cadáver con la máscara de mutilación existente y salpica sangre. Si les hacen daño, interrumpen la comida. The Glitch queda excluido.
- **Extracción**: si ven a alguien escondido bajo una cama, caminan hasta un lado de la cama, se agachan, rebuscan con el brazo, lo agarran y lo arrastran sin teletransporte; después empieza el ataque. Cada monstruo tiene su propia animación. Una víctima solo la puede reservar un monstruo a la vez.
- Mímico y Void siguen con sus modelos propios (animación procedimental); GeckoLib 4.8.4 se usa en The Glitch.
- Compila en Actions; no probado en el juego.
