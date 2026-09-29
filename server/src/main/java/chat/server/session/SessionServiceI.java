package chat.server.session;

import chat.contract.ChatException;
import chat.contract.ClientCallbackPrx;
import chat.contract.SessionService;
import chat.server.messaging.RoomManager;
import com.zeroc.Ice.Current;

/**
 * RF-01: login con nickname unico, registro de callback, presencia y logout.
 * Responsable: P1
 */
public class SessionServiceI implements SessionService {

    private final SessionManager sessions;
    private final RoomManager rooms;

    public SessionServiceI(SessionManager sessions, RoomManager rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public String[] login(String nickname, ClientCallbackPrx callback, Current current) throws ChatException {
        // TODO (P1): validar nickname, lanzar NicknameInUseException, registrar callback, notificar presencia
        throw new ChatException("login: no implementado");
    }

    @Override
    public void logout(String nickname, Current current) {
        // TODO (P1): remover de sesiones y salas, notificar desconexion
    }

    @Override
    public String[] getOnlineUsers(Current current) {
        return sessions.onlineUsers();
    }
}
