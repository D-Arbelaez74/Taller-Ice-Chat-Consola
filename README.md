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
