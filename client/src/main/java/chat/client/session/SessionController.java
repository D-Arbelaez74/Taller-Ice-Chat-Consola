package chat.client.session;

import chat.client.ClientContext;
import chat.client.cli.CommandShell;

/**
 * RF-01 lado cliente: login, /users, /exit y eventos de presencia.
 * Responsable: P1
 */
public class SessionController {

    private final ClientContext ctx;

    public SessionController(ClientContext ctx) {
        this.ctx = ctx;
    }

    public void registerCommands(CommandShell shell) {
        shell.register("/users", "/users", "Lista los usuarios conectados",
                args -> ctx.printer.event("Conectados: " + String.join(", ", ctx.session.getOnlineUsers())));
        shell.register("/exit", "/exit", "Cierra la sesion y sale", args -> {
            // TODO (P1): ctx.session.logout(ctx.nickname()) y liberar recursos (llamadas, archivos)
            shell.stop();
        });
    }

    public void onUserConnected(String nickname) {
        ctx.printer.event("*** " + nickname + " se conecto");
    }

    public void onUserDisconnected(String nickname) {
        ctx.printer.event("*** " + nickname + " se desconecto");
    }
}
