# Advent Calendar — LEEME

Mod de Minecraft Java (Forge 1.20.1): calendario de adviento de 31 días para diciembre.
Cada día de diciembre se desbloquea una recompensa, con un NPC personalizable,
efectos y sonido al reclamar.

## Cómo compilar

Necesitas JDK 17. En la raíz del proyecto:

```
gradlew build
```

El jar queda en `build/libs/adventcalendar-1.0.0.jar`.

Para probar en cliente de desarrollo:

```
gradlew runClient
```

Para servidor de desarrollo:

```
gradlew runServer
```

> Nota: si `gradlew` no arranca (falta `gradle-wrapper.jar`), genera el wrapper con un
> Gradle 8 instalado: `gradle wrapper --gradle-version 8.1.1`.

## Comandos (requieren permiso 2 / OP)

- `/summon adventcalendar:advent_npc` — invoca al NPC del calendario.
- `/advent skin <url|usuario>` — pone la skin al NPC más cercano (10 bloques).
  - URL directa a un PNG: `/advent skin https://ejemplo.com/mi-skin.png`
  - Nombre de usuario de Minecraft: `/advent skin Notch`
  - La imagen se descarga y se guarda en `config/adventcalendar/skins/`. Si falla,
    el NPC usa la skin de Steve.
- `/advent setrecompensa <día 1-31>` — guarda una copia del ítem que tengas en la
  mano como recompensa de ese día. Ejemplo: con una manzana dorada en la mano,
  `/advent setrecompensa 25`.

## Cómo funciona

1. Invoca al NPC donde quieras (`/summon`) y ponle skin con `/advent skin`.
2. Configura las 31 recompensas con `/advent setrecompensa <día>` (una por día).
3. En diciembre, los jugadores hacen clic derecho al NPC: se abre el calendario.
   - Gris = día futuro o sin recompensa configurada.
   - Dorado = disponible (clic para reclamar).
   - Verde = ya reclamado.
4. Al reclamar: el ítem va al inventario (o al suelo si está lleno), salen
   partículas y suena el reto completado.

Detalles técnicos:

- La fecha es la **real del servidor** (zona horaria de la máquina).
- Los días reclamados se guardan **por jugador** (bitmask en su NBT persistente).
- Las recompensas se guardan en el SavedData del overworld
  (`world/data/adventcalendar_rewards.dat`): sobreviven reinicios.
- La skin del NPC se sincroniza a los clientes y se guarda en el NBT de la
  entidad, así que también sobrevive reinicios.

## Qué falta para v2 (no incluido en este MVP)

- GUI personalizada para configurar las recompensas (hoy es por comando).
- Sonidos propios del mod (hoy se usa `UI_TOAST_CHALLENGE_COMPLETE` de vanilla).
- VFX más elaborados al reclamar (hoy son partículas `TOTEM_OF_UNDYING`).
- Soporte para skins "slim" (brazos de 3px): el renderer usa el modelo clásico;
  una skin slim se ve con los brazos ligeramente anchos.
