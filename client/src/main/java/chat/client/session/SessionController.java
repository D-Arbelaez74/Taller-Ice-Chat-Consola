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
                args -> System.out.println("Conectados: " + String.join(", ", ctx.session.getOnlineUsers())));
        shell.register("/exit", "/exit", "Cierra la sesion y sale", args -> {
            // Llamar al logout del servidor para cerrar la sesión de forma ordenada
            try {
                if (ctx.nickname() != null) {
                    ctx.session.logout(ctx.nickname());
                }
            } catch (Exception ignored) {
                // Si el servidor se cayó justo cuando íbamos a salir, no importa, ignoramos el error
            }
            System.out.println("Desconectado. Saliendo...");
            shell.stop();
        });
    }

    public void onUserConnected(String nickname) {
        System.out.println("*** " + nickname + " se conecto");
    }

    public void onUserDisconnected(String nickname) {
        System.out.println("*** " + nickname + " se desconecto");
    }
}
