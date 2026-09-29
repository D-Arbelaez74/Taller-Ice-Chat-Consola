package chat.client.messaging;

import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import chat.contract.TextMessage;

/**
 * RF-02 / RF-03 lado cliente: /msg, /create, /rooms, /join, /leave, /room.
 * Responsable: P2
 */
public class MessagingController {

    private final ClientContext ctx;

    public MessagingController(ClientContext ctx) {
        this.ctx = ctx;
    }

    public void registerCommands(CommandShell shell) {
        // TODO (P2): registrar /msg <user> <texto>, /create <sala>, /rooms, /join <sala>,
        //            /leave <sala>, /room <sala> <texto>
    }

    public void onPrivateMessage(TextMessage msg) {
        // TODO (P2): formato con hora
        ctx.printer.event("[privado] " + msg.sender + ": " + msg.text);
    }

    public void onRoomMessage(TextMessage msg) {
        ctx.printer.event("[#" + msg.room + "] " + msg.sender + ": " + msg.text);
    }
}
