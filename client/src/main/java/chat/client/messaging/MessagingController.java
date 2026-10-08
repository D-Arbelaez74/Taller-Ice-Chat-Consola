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

        // TODO (P2): registrar /create <sala>, /rooms, /join <sala>, /leave <sala>, /room <sala> <texto>
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
