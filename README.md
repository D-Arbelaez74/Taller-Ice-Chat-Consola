# Taller U2 — Chat Multimedia y Voz UDP con ZeroC Ice

## Requisitos

| Herramienta | Versión | Nota |
|---|---|---|
| JDK | 17 | Gradle lo usa por *toolchain*; no importa qué `java` haya en el PATH, pero para ejecutar `gradlew` define `JAVA_HOME` a un JDK ≥ 8. |
| ZeroC Ice | 3.7.11 | Necesario por `slice2java`. En Windows: instalar `Ice-3.7.11.msi` desde zeroc.com (queda en `C:\Program Files\ZeroC\Ice-3.7.11`). Si se instala en otra ruta, definir la variable `ICE_HOME`. |
| Gradle | 8.6 | Viene con el wrapper; no hay que instalarlo. |

## Ejecución

```bash
./gradlew build          # compila los .ice (build/generated-src) y los 3 módulos
./gradlew runServer      # servidor en tcp -p 10000 (server/config.server)
./gradlew runClient      # cliente contra 127.0.0.1 (client/config.client)
./gradlew runClient -PserverHost=192.168.1.20   # cliente contra otra máquina de la LAN
```

## Arquitectura

El sistema es **híbrido**: todo lo que necesita fiabilidad (sesión, presencia, mensajes, salas, archivos y la
señalización de llamadas) viaja por **ZeroC Ice sobre TCP**; el audio de las llamadas viaja por **UDP directo
entre clientes**, sin pasar por el servidor ni por Ice.

```
                         ┌────────────────────────────────────────────┐
                         │          SERVIDOR  (tcp -h * -p 10000)     │
                         │  ObjectAdapter "ChatAdapter"               │
                         │   ├─ Session   → SessionServiceI           │
                         │   ├─ Messaging → MessagingServiceI         │
                         │   ├─ Files     → FileServiceI  (relevo)    │
                         │   └─ Calls     → CallServiceI              │
                         │  Estado: SessionManager, RoomManager       │
                         │  (ConcurrentHashMap, thread-safe)          │
                         └───────▲───────────────┬────────────────────┘
        Ice / TCP  (cliente→servidor)            │ Ice / TCP (servidor→cliente, callbacks)
        login, /msg, /room, /sendfile,           │ onUserConnected, onPrivateMessage,
        /call, /accept, /hangup ...              │ onRoomMessage, onFileStart/Chunk/End,
                         │                       │ onIncomingCall, onCallAccepted ...
              ┌──────────┴───────────┐   ┌───────▼──────────────┐
              │      CLIENTE A       │   │      CLIENTE B       │
              │ ClientCallbackI      │   │ ClientCallbackI      │
              │ (ObjectAdapter propio│   │ (ObjectAdapter propio│
              │  tcp, puerto libre)  │   │  tcp, puerto libre)  │
              │ CommandShell (CLI)   │   │ CommandShell (CLI)   │
              └──────────┬───────────┘   └───────────▲──────────┘
                         │      Voz: datagramas UDP  │
                         └───────────────────────────┘
                     (javax.sound.sampled → DatagramSocket → altavoz)
                     El servidor solo intercambia IP:puerto UDP por Ice.
```

### Flujos

| Flujo | Transporte | Cómo funciona |
|---|---|---|
| **Sesión y presencia** (RF-01) | Ice / TCP | `login(nickname, callbackPrx)` registra el nickname único y el proxy de callback del cliente; el servidor avisa a todos con `onUserConnected` / `onUserDisconnected`. Si la conexión se cae sin `/exit`, Ice avisa por `setCloseCallback` y el servidor limpia al usuario. |
| **Mensajes privados y salas** (RF-02, RF-03) | Ice / TCP | El cliente llama a `MessagingService`; el servidor localiza el callback del destinatario (o de los miembros de la sala, sin incluir al emisor) y lo invoca con `onPrivateMessage` / `onRoomMessage`. |
| **Archivos** (RF-04) | Ice / TCP | El emisor parte el archivo en bloques de 64 KB (`sequence<byte>`) y llama a `beginTransfer` → `sendChunk` × N → `endTransfer(sha256)`. El servidor (`FileServiceI`) solo reenvía cada bloque con `onFileStart` / `onFileChunk` / `onFileEnd`. El receptor reconstruye el archivo en `downloads/` y verifica su SHA-256. |
| **Señalización de llamadas** (RF-05) | Ice / TCP | `startCall` / `answerCall` / `hangup` intercambian entre los dos clientes su IP y puerto UDP de audio (`VoiceEndpoint`). |
| **Audio de la llamada** (RF-05) | **UDP** | Cada cliente captura el micrófono (`javax.sound.sampled`, 8 kHz, 16 bits, mono), lo empaqueta en `DatagramPacket` y lo envía al `VoiceEndpoint` del otro; un hilo dedicado recibe y reproduce. |

## Manual de usuario (comandos)

La interfaz es una **CLI**: al arrancar pide el nickname (no existe un comando `/login`; si el nickname ya
está en uso, el servidor lo rechaza con `NicknameInUseException`) y luego muestra el prompt `>`. Los mensajes,
archivos y llamadas entrantes se imprimen de forma asíncrona sin bloquear la lectura de comandos.

| Comando | Descripción | Ejemplo |
|---|---|---|
| `/help` | Lista todos los comandos disponibles. | `/help` |
| `/users` | Lista los usuarios conectados. | `/users` |
| `/msg <usuario> <texto>` | Mensaje privado 1 a 1. Confirma el envío o muestra el error (usuario inexistente o desconectado). | `/msg ana hola` |
| `/create <sala>` | Crea una sala (nombre sin espacios) y te une a ella. | `/create redes` |
| `/rooms` | Lista las salas existentes. | `/rooms` |
| `/join <sala>` | Te une a una sala. | `/join redes` |
| `/leave <sala>` | Sales de una sala. | `/leave redes` |
| `/room <sala> <texto>` | Mensaje a todos los miembros de la sala (debes pertenecer a ella). | `/room redes buenas` |
| `/sendfile <usuario> <ruta>` | Envía un archivo a un usuario. | `/sendfile ana C:\fotos\gato.png` |
| `/sendfile #<sala> <ruta>` | Envía un archivo a todos los miembros de una sala. | `/sendfile #redes informe.pdf` |
| `/call <usuario>` | Inicia una llamada de voz 1 a 1. | `/call ana` |
| `/accept` | Acepta la llamada entrante. | `/accept` |
| `/reject` | Rechaza la llamada entrante. | `/reject` |
| `/hangup` | Cuelga la llamada actual. | `/hangup` |
| `/mute` | Silencia / reactiva el micrófono durante la llamada. | `/mute` |
| `/joinvoice <sala>` | Te une a la llamada de voz grupal de una sala. | `/joinvoice redes` |
| `/leavevoice` | Sales de la llamada grupal sin cortar la de los demás. | `/leavevoice` |
| `/exit` | Cierra la sesión (logout ordenado) y sale. | `/exit` |

**Notas sobre `/sendfile`**

- La ruta puede llevar espacios y estar entre comillas: `/sendfile ana "C:\mis fotos\gato 1.png"`.
- El envío corre en un hilo aparte: puedes seguir escribiendo mientras sube un archivo grande y verás el progreso en consola.
- Los archivos recibidos se guardan en `client/downloads/` (propiedad `Chat.Downloads.Dir` de `client/config.client`).
  El receptor imprime `[SHA-256 verificado]` cuando el archivo llegó íntegro.
- Si el destino no existe, la sala no existe, el emisor no pertenece a la sala o el archivo no se encuentra,
  se imprime el motivo y no se envía nada.

**Prueba rápida con varios clientes** (misma máquina o LAN): arrancar `./gradlew runServer`, luego un
`./gradlew runClient` por terminal (`-PserverHost=<ip>` para otra máquina), y probar `/msg`, una sala con
`/create` + `/join` + `/room`, `/sendfile` con un archivo mayor a 10 MB y una llamada con `/call`.

## Estructura

```
common/src/main/slice/        Contratos Slice → paquete Java chat.contract
    Types.ice                   tipos y excepciones base              (P1)
    Session.ice                 SessionService + ClientCallback       (P1)
    Messaging.ice               MessagingService + MessageObserver    (P2)
    Files.ice                   FileService + FileObserver            (P3)
    Calls.ice                   CallService + CallObserver            (P4)

server/                       config.server
  chat/server/ServerMain.java   un servant por servicio: Session, Messaging, Files, Calls
  chat/server/session/          SessionManager (usuarios → callbacks), SessionServiceI   (P1)
  chat/server/messaging/        RoomManager, MessagingServiceI                           (P2)
  chat/server/files/            FileServiceI                                             (P3)
  chat/server/calls/            CallServiceI                                             (P4)

client/                       config.client, downloads/ (archivos recibidos, ignorado por git)
  chat/client/ClientMain.java   conexión, proxies y arranque de la consola
  chat/client/ClientContext     proxies + consola + nickname compartidos
  chat/client/callback/         ClientCallbackI: delega cada evento al controller del módulo
  chat/client/cli/              CommandShell (lectura de comandos, registro de comandos) (P2)
  chat/client/session/          SessionController: login, /users, /exit, presencia       (P1)
  chat/client/messaging/        MessagingController: /msg, /create, /join, /room ...     (P2)
  chat/client/files/            FileController, FileSender, FileAssembler                (P3)
  chat/client/voice/            CallController, UdpAudioSender/Receiver, AudioConfig     (P4)
```

Cada módulo registra sus comandos en `CommandShell` desde su propio controller, así que
nadie necesita editar `ClientMain` ni `ClientCallbackI` para agregar funcionalidad.

## Reglas del equipo

- El contrato `.ice` se cambia solo con acuerdo del equipo (rompe la compilación de los demás).
- Una rama por funcionalidad (`feat/session`, `feat/rooms`, `feat/files`, `feat/voice`) y PR a `main`.
- El código generado por Slice **no** se versiona (vive en `build/`).

## Preguntas

1. Arquitectura H´ıbrida TCP/UDP: ¿Cuales son las razones técnicas fundamentales para diseñar una arquitectura híbrida donde el control, el chat de texto y la transferencia de archivos corren sobre ZeroC Ice (TCP), mientras que el flujo de voz corre directamente sobre UDP? ¿Qué consecuencias acústicas y de rendimiento ocurrirían si la voz se transmite sobre Ice/TCP bajo condiciones de fluctuación de red (jitter) o pérdida de paquetes?

R/=
La razón es que no todo lo que se envía en el chat necesita lo mismo de la red.

Los mensajes de texto, las salas, los archivos y la señalización de las llamadas necesitan llegar completos y en orden. Si se pierde un pedazo de un archivo, el archivo queda dañado, y si se pierde un mensaje, la conversación queda incompleta. Para eso sirve TCP, que es sobre lo que trabaja Ice: confirma que cada dato llegó y si algo se pierde lo vuelve a enviar. En estos casos no importa que tarde un poco más, lo importante es que llegue bien. Además Ice nos da los contratos Slice, las excepciones y los callbacks, entonces no tenemos que armar los mensajes a mano.

La voz es al revés: lo importante es que llegue rápido, aunque no llegue perfecta. Un pedazo de audio que llega tarde ya no sirve, porque la conversación ya siguió. Por eso usamos UDP, que manda cada paquete y no espera confirmación ni reenvía nada. Si se pierde un paquete solo se escucha un corte muy pequeño (unos milisegundos) que casi no se nota, y la llamada sigue en tiempo real.

Si la voz fuera por Ice/TCP pasaría lo siguiente:

- Cuando se pierde un paquete, TCP detiene todo lo que viene detrás hasta que logra reenviarlo. Entonces el audio se queda en silencio un momento y después llega todo junto. Se escucharía entrecortado, con pausas y con pedazos que suenan acelerados o atrasados.
- Ese retraso se va acumulando. Cada reenvío deja la conversación un poco más atrás del tiempo real, hasta que las dos personas terminan hablando una encima de la otra porque cada una escucha a la otra con varios segundos de diferencia.
- Con jitter (cuando los paquetes llegan con tiempos irregulares) el efecto es parecido: TCP los entrega en orden pero a destiempo, y el audio suena a tirones.
- También se gasta más red y más procesador, por las confirmaciones de TCP y porque cada pedazo de audio tendría que viajar como una llamada remota de Ice, con su cabecera y esperando respuesta.

En resumen, TCP prefiere que el dato llegue completo aunque llegue tarde, y para la voz es mejor que llegue a tiempo aunque se pierda un poco. Por eso en el proyecto Ice solo se usa para avisar de la llamada e intercambiar la IP y el puerto, y el audio viaja directo entre los clientes por UDP.



2. Manejo de Buffer y Chunking en Ice: Si un usuario intenta transferir un video de 80 MB mediante una única llamada RPC pasando toda la secuencia de bytes en un parámetro  sequence<byte>, ¿que excepción o comportamiento manifestar a ZeroC Ice por defecto? Explique cómo la técnica de fragmentación en bloques resuelve este problema y qué parámetros de configuración de Ice intervienen.

R/ Ice rechaza la llamada ya que supera nuestro Ice.MessageSizeMax el cual se encuentra configurado en 2048KB en el archivo config.server, el servidor lanza un MemoryLimitException cuando lee la cabezera y observa que supera el numero maximo de bytes permitido. SIn embargo el cliente no recibe ese error si no un SocketException con "Conection reset by peer", ya que el servidor cierra la conexion.
Los parametros que intervienen en esta situacion son el Ice.MessageSizeMax, que deben coincidir en el cliente y el servidor.

Este problema corresponde no solo a la configuracion si no tambien al diseño ya que si subimos el limite maximo digamos a 100 MB si funcionaria pero haria que tuviera que tener el archivo marshalado en memoria en el cliente y en el servidor.

La forma de resolver este problema es a traves de la particion del bloque entero en bloques de 64KB lo que permite tener un archivo de uno 65 KB que estas significativamente debajo del limite, ademas solo hay un bloque en memoria a la vez, se puede mostrar progreso y  cancelar, por ultimo el índice y el SHA-256 permiten detectar pérdida o corrupción.

La evidencia de la solucion es una prueba ejecutada, donde se enviaron archivos de prueba temporales de 12 MB y 25 MB, con SHA-256 verificado, lo que indica que se envio el archivo sin corrupciones. 

3. Escalabilidad en Llamadas Grupales (Relay vs. Mesh): Analice comparativamente la topología de Audio Relay centralizado frente a la topología Mesh Peer-to-Peer elegida por su equipo. Si una sala cuenta con N participantes activos hablando simultáneamente, ¿cuál es el número total de paquetes UDP que debe emitir cada cliente y cuál es la carga total que soporta la red del servidor en ambas alternativas?

R/=
En nuestro proyecto las llamadas grupales eran opcionales, pero las implementamos y escogimos Mesh (comandos `/joinvoice` y `/leavevoice`). La escogimos porque es la misma idea que ya usamos en la llamada de uno a uno: el servidor solo ayuda a que los clientes se pasen su IP y su puerto por Ice, y después el audio viaja directo entre ellos sin pasar por el servidor. Cuando alguien entra o sale de la llamada, el servidor le manda a cada participante la lista actualizada de los demás.

La diferencia entre las dos opciones es quién hace el trabajo de repartir el audio:

- Mesh (Peer-to-Peer): Cada participante le manda su voz directamente a todos los demás. Entonces si en la sala hay N personas, cada cliente tiene que enviar N-1 paquetes cada vez que manda un pedazo de audio, uno para cada compañero. Sumando a todos, por la red viajan N x (N-1) paquetes. El servidor no recibe ni un solo paquete de audio, su carga es cero, porque solo se usa para la señalización.

- Relay centralizado: Cada participante le manda su voz una sola vez al servidor, y el servidor es el que la reenvía a los demás. Entonces cada cliente solo envía 1 paquete sin importar cuántos haya en la sala. Pero el servidor recibe N paquetes (uno de cada persona) y tiene que reenviar cada uno a los otros N-1, o sea que envía N x (N-1) paquetes. Toda la carga queda en la red del servidor.

Por ejemplo, con 5 personas hablando al mismo tiempo:

| | Paquetes que envía cada cliente | Paquetes que recibe el servidor | Paquetes que envía el servidor |
|---|---|---|---|
| Mesh | 4 | 0 | 0 |
| Relay | 1 | 5 | 20 |

En las dos opciones cada cliente termina recibiendo lo mismo, N-1 paquetes, uno por cada compañero que habla.

Lo que cambia es a quién le pesa cuando la sala crece. En Mesh el esfuerzo de subida de cada cliente crece con cada persona que entra, entonces funciona bien para grupos pequeños como los de una clase, pero con muchas personas los computadores o redes más débiles se empiezan a quedar cortos. En Relay el cliente siempre hace el mismo esfuerzo y por eso aguanta salas más grandes, pero el servidor se vuelve el punto crítico: su carga crece muy rápido (casi al cuadrado de N), si se cae se cae la llamada de todos, y además el audio da un salto de más, lo que agrega un poco de retraso.



4. Transparencia y Callbacks en Ice: Describa el ciclo de vida y la configuración de adaptadores de objetos requerida en el cliente para permitir que el servidor Ice invoque métodos de retorno (callbacks) sobre él. ¿Qué papel juegan los proxies directos o indirectos en este flujo?

Para que funcione la comunicación entre el cliente y el servidor, lo que se hace es que primero se debe conectar un cliente y establecer un callback con el sevidor. De esta forma el servidor no se tiene que estar preguntando quién le escribio, sino que directamente usando el callback da una respuesta, por ejemplo conexión establecida. En nuestro caso:
- En ClientMain iniciamos un Object Adapter que hace que podamos usarlo como un servidor pequeño.
- Instanciamos la ClientCallbackI y este lo guardamos en el adaptador.
- Hacemos adapter.activate() para que el cliente pueda escuchar las peticiones por red.
- Por último le pasamos al servidor principal nuestor proxy, el cual es como decir el control remoto, por parametro al método login(nickname, callback). Ya ahí el servidor guarda ese proxy en su mapa y lo usa para llamar de vuelta al cliente cuando ocurre unn evento. Como que un cliente nuevo se conecto o desconecto.

Diferencias de Proxies:

- Proxy Directo: Un proxy directo es aquel que trae la dirección IP y el puerto de red explicitamente en él. Entonces este proxy dentro de su composición tiene estos dos atributos.

Este es el que se utilizo en el proyecto y basicamente lo que hace es que cuando un cliente se conecta, Ice genera un proxy que guardaba información interna diciendo que para comunicarse con ese cliente especifico es necesario a través de una IP especifica y su respectivo puerto. Entonces ahí ya queda la conexión hecha y el servidor siempre va a estar mandando respuestas a esa dirección IP. 

- Proxy Indirecto: Este proxy solo tiene un nombre logico, por lo cual no almacena ni una dirección IP ni un puerto. El nombre logico sería algo como Cliente2.

En este proyecto no se utilizaron proxies indirectos debido a que con los directos era la forma adecuada de poder realizar la comunicación con los clientes mandando mensajes desde el servidor a estos clientes inscritos.

## Bitácora de uso ético de IAG (Nivel 3)

Política del curso: **Nivel 3 — Colaboración asistida**. El equipo usó IA generativa (asistente de código,
Claude Code) como apoyo para entender, proponer, depurar y documentar; las decisiones de diseño y la
revisión final fueron del equipo, que es responsable de poder defender todo el código en la sustentación.

### Cómo se usó la IA

| Etapa | Prompts principales (resumidos) | Para qué se usó |
|---|---|---|
| Comprensión | "Explica qué hace este contrato Slice / esta clase y por qué." | Entender el contrato `.ice`, los callbacks de Ice y la arquitectura antes de escribir código. |
| Implementación | "Escribe esta clase siguiendo el esqueleto del repo y compila." | Generar la primera versión de cada módulo (sesión, mensajería y salas, archivos, llamadas), pidiendo avanzar por pasos y compilando después de cada uno. |
| Depuración | "¿Por qué falla / se comporta así?" con el error o la salida real. | Encontrar la causa de errores de compilación, de configuración de Ice y de concurrencia. |
| Pruebas | "Prueba con varios clientes y compara el resultado." | Verificar el comportamiento real (múltiples clientes, archivos grandes, errores esperados) en lugar de confiar en que compile. |
| Documentación | "¿Cómo organizo el README y el manual de comandos?" | Solo la estructura y el formato del README (secciones, tabla de comandos, diagrama). Las respuestas del cuestionario las redactó el equipo. |

### Código generado que se adaptó críticamente

El código sugerido por la IA no se aceptó sin revisión. Ejemplos del proyecto:

- **Transferencia de archivos:** el esqueleto proponía escribir cada fragmento en `index * CHUNK_SIZE`, pero el
  receptor no conoce el tamaño de fragmento del emisor; se cambió a escritura secuencial verificando el índice.
- **Compatibilidad con Windows:** el renombrado final con `ATOMIC_MOVE` sobrescribe archivos existentes en
  Windows; se reemplazó por `Files.move` sin opciones y un sufijo ` (1)` para no pisar archivos.
- **Seguridad y robustez:** se añadieron validaciones que la IA no incluía por defecto: nombres de archivo sin
  rutas (`../`), pertenencia a la sala antes de enviar, orden y tamaño de fragmentos, y limpieza de
  transferencias abandonadas.
- **Contrato compartido:** los cambios al `.ice` se limitaron a lo acordado por el equipo, aunque la IA pudiera
  proponer otros, para no romper la compilación de los demás módulos.
- **Estructura del README:** se corrigió una mención a una clase inexistente (`ConsolePrinter`) y los comandos de
  llamadas se contrastaron con la rama de voz antes de ponerlos en el manual.

### Lecciones aprendidas durante la depuración

- **Medir antes de afirmar.** Para la pregunta 2 se esperaba que el cliente recibiera `MemoryLimitException` al
  enviar 80 MB en una sola llamada. La prueba real mostró que la excepción salta en el **servidor** al leer la
  cabecera del mensaje (`requested 83886135 bytes, maximum allowed is 2097152`) y el cliente solo ve un
  `SocketException` ("Connection reset by peer"). El equipo redactó su respuesta con ese resultado medido.
- **Verificar la integridad contra el original.** Los archivos recibidos se compararon byte a byte con los
  originales, no solo con el mensaje `[SHA-256 verificado]` del programa.
- **Avanzar por pasos pequeños y compilar en cada uno** evita arrastrar errores y facilita entender el código.
- **Revisar los detalles de plataforma y de seguridad:** la IA acelera el trabajo, pero no los resuelve sola.
- **Dividir el trabajo por contratos Slice** permitió avanzar en paralelo, a costa de coordinar cualquier cambio
  del `.ice`.
