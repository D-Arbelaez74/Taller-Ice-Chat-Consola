package chat.client.messaging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import chat.contract.TextMessage;

//Aquí van los comandos de mensajes del cliente y lo que se muestra cuando llega un mensaje
public class MessagingController {

    private final ClientContext ctx;

    // Para mostrar la hora del mensaje en formato corto, por ejemplo 14:05
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public MessagingController(ClientContext ctx) {
        this.ctx = ctx;
    }

    public void registerCommands(CommandShell shell) {
        shell.register("/msg", "/msg <usuario> <texto>", "Envia un mensaje privado", args -> {
            // Separamos solo en dos partes para que el texto pueda llevar espacios
            String[] parts = args.trim().split("\\s+", 2);
            if (parts.length < 2) {
                System.out.println("Uso: /msg <usuario> <texto>");
                return;
            }

            // Si el servidor no lanza ninguna excepción es porque recibió el mensaje
            ctx.messaging.sendPrivateMessage(ctx.nickname(), parts[0], parts[1]);
            System.out.println("Mensaje enviado a " + parts[0]);
        });

        shell.register("/create", "/create <sala>", "Crea una sala y te une a ella", args -> {
            String room = args.trim();
            if (room.isEmpty()) {
                System.out.println("Uso: /create <sala>");
                return;
            }
            ctx.messaging.createRoom(ctx.nickname(), room);
            System.out.println("Sala " + room + " creada. Ya estas adentro.");
        });

        shell.register("/rooms", "/rooms", "Lista las salas disponibles", args -> {
            String[] rooms = ctx.messaging.listRooms();
            if (rooms.length == 0) {
                System.out.println("Todavia no hay salas. Crea una con /create <sala>");
            } else {
                System.out.println("Salas: " + String.join(", ", rooms));
            }
        });

        shell.register("/join", "/join <sala>", "Te une a una sala", args -> {
            String room = args.trim();
            if (room.isEmpty()) {
                System.out.println("Uso: /join <sala>");
                return;
            }
            ctx.messaging.joinRoom(ctx.nickname(), room);
            System.out.println("Te uniste a la sala " + room);
        });

        shell.register("/leave", "/leave <sala>", "Sales de una sala", args -> {
            String room = args.trim();
            if (room.isEmpty()) {
                System.out.println("Uso: /leave <sala>");
                return;
            }
            ctx.messaging.leaveRoom(ctx.nickname(), room);
            System.out.println("Saliste de la sala " + room);
        });

        shell.register("/room", "/room <sala> <texto>", "Envia un mensaje a una sala", args -> {
            // Igual que en /msg, la primera palabra es la sala y el resto es el mensaje
            String[] parts = args.trim().split("\\s+", 2);
            if (parts.length < 2) {
                System.out.println("Uso: /room <sala> <texto>");
                return;
            }
            ctx.messaging.sendRoomMessage(ctx.nickname(), parts[0], parts[1]);
            System.out.println("Mensaje enviado a la sala " + parts[0]);
        });
    }

    public void onPrivateMessage(TextMessage msg) {
        System.out.println("[" + formatTime(msg.timestamp) + "] [privado] " + msg.sender + ": " + msg.text);
    }

    public void onRoomMessage(TextMessage msg) {
        System.out.println("[" + formatTime(msg.timestamp) + "] [#" + msg.room + "] " + msg.sender + ": " + msg.text);
    }

    // El servidor manda la hora en milisegundos, aquí la pasamos a algo que se pueda leer
    private String formatTime(long timestamp) {
        return timeFormat.format(Instant.ofEpochMilli(timestamp));
    }
}
