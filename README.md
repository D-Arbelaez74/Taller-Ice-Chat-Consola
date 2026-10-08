# Taller U2 — Chat Multimedia y Voz UDP con ZeroC Ice

> Manual técnico en construcción (P3 lo consolida al final): diagrama de arquitectura,
> manual de comandos, respuestas al cuestionario y bitácora de IA.

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
  chat/client/cli/              CommandShell (lectura de comandos) y ConsolePrinter      (P2)
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



2. Manejo de Buffer y Chunking en Ice: Si un usuario intenta transferir un video de 80 MB mediante una única llamada RPC pasando toda la secuencia de bytes en un parámetro  sequence<byte>, ¿que excepción o comportamiento manifestar a ZeroC Ice por defecto? Explique cómo la técnica de fragmentación en bloques resuelve este problema y qué parámetros de configuración de Ice intervienen.



3. Escalabilidad en Llamadas Grupales (Relay vs. Mesh): Analice comparativamente la topología de Audio Relay centralizado frente a la topología Mesh Peer-to-Peer elegida por su equipo. Si una sala cuenta con N participantes activos hablando simultáneamente, ¿cuál es el número total de paquetes UDP que debe emitir cada cliente y cuál es la carga total que soporta la red del servidor en ambas alternativas?

R/=
En nuestro proyecto no implementamos las llamadas grupales porque ese requerimiento era opcional, entonces solo tenemos la llamada de uno a uno. Aun así, si las hubiéramos hecho habríamos escogido Mesh, porque es la misma idea que ya usamos en la llamada de uno a uno: el servidor solo ayuda a que los clientes se pasen su IP y su puerto por Ice, y después el audio viaja directo entre ellos sin pasar por el servidor.

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