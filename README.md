# Luz Viva — NeoForge 1.21.1

Todos los objetos que emiten luz se dibujan en 3D y tienen animacion.
El Farol (y el Farol de almas) cuelga de una cadena sostenida en tu mano y se balancea con tu movimiento.

## Obtener el .jar
Opcion A (sin instalar nada): sube esta carpeta a un repositorio de GitHub -> pestana Actions -> "Compilar jar" -> Run workflow.
Cuando termine, descarga el artefacto `luzviva-jar` (contiene el .jar).
Opcion B (local): instala JDK 21 + Gradle 8.10 y ejecuta `build.bat` (Windows) o `./build.sh`.
El .jar queda en `build/libs/luzviva-1.0.0.jar`; copialo a la carpeta `mods` de NeoForge 1.21.1.

## Compilar (detalle)
Requisitos: JDK 21.

1. Genera el wrapper de Gradle (una sola vez):  `gradle wrapper --gradle-version 8.10`
   (o copia `gradlew`, `gradlew.bat` y la carpeta `gradle/` del MDK oficial de NeoForge 1.21.1).
2. `./gradlew runClient`  -> abre Minecraft para probar.
3. `./gradlew build`      -> el .jar queda en `build/libs/`.

Si Gradle no encuentra el plugin `net.neoforged.moddev` en esa version, usa la que traiga el MDK oficial.
Cualquier `neo_version` 21.1.x sirve (en `gradle.properties`).

## Como funciona
- `LightItems`       decide que objetos emiten luz (BlockItem con luz > 0, tambien de otros mods).
- `LuzVivaClient`    les asigna el renderer y envuelve su modelo (sin JSON por objeto).
- `LightItemRenderer` dibuja el modelo 3D real del bloque y aplica las animaciones.
- `SwingTracker`     pendulo amortiguado que reacciona a caminar / correr / frenar / strafe.

## Novedades v1.1
- Primera persona: se dibuja TU brazo (con tu skin, Steve/Alex, mano izquierda o derecha) agarrando la cadena.
  `HandMesh` + constantes `ARM_PX` y la direccion del brazo en `LightItemRenderer`.
- El farol tambien reacciona al giro de la camara y a saltos/caidas (la cadena se estira y rebota).
- Tercera persona: ya se ve tu mano real sosteniendo la cadena.

## Ajustes rapidos
- Fisica del balanceo: constantes al inicio de `SwingTracker`.
- Posicion/escala/largo de cadena en la mano: `renderHangingLantern` (bloques `isFirst` / `isThird`).
- Que animacion usa cada objeto: `Kind.of(...)` en `LightItemRenderer`.
