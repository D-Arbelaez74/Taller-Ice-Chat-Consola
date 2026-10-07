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