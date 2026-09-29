package chat.server;

import chat.server.calls.CallServiceI;
import chat.server.files.FileServiceI;
import chat.server.messaging.MessagingServiceI;
import chat.server.messaging.RoomManager;
import chat.server.session.SessionManager;
import chat.server.session.SessionServiceI;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

/**
 * Punto de entrada del servidor. Registra un servant por modulo:
 * Session (P1), Messaging (P2), Files (P3), Calls (P4).
 */
public class ServerMain {

    public static void main(String[] args) {
        int exitCode = 0;
        try (Communicator communicator = Util.initialize(args, "config.server")) {
            ObjectAdapter adapter = communicator.createObjectAdapter("ChatAdapter");

            // Estado compartido entre servants
            SessionManager sessions = new SessionManager();
            RoomManager rooms = new RoomManager();

            adapter.add(new SessionServiceI(sessions, rooms), Util.stringToIdentity("Session"));
            adapter.add(new MessagingServiceI(sessions, rooms), Util.stringToIdentity("Messaging"));
            adapter.add(new FileServiceI(sessions, rooms), Util.stringToIdentity("Files"));
            adapter.add(new CallServiceI(sessions), Util.stringToIdentity("Calls"));

            adapter.activate();
            System.out.println("[SERVER] Servidor Ice iniciado: "
                    + communicator.getProperties().getProperty("ChatAdapter.Endpoints"));
            System.out.println("[SERVER] Identidades: Session, Messaging, Files, Calls");

            communicator.waitForShutdown();
        } catch (Exception e) {
            System.err.println("[SERVER] Error fatal: " + e.getMessage());
            e.printStackTrace();
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
