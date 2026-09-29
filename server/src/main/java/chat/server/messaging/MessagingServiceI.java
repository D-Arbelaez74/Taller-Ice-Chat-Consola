package chat.server.messaging;

import chat.contract.ChatException;
import chat.contract.MessagingService;
import chat.server.session.SessionManager;
import com.zeroc.Ice.Current;

/**
 * RF-02 mensajes privados y RF-03 salas con broadcast aislado.
 * Responsable: P2
 */
public class MessagingServiceI implements MessagingService {

    private final SessionManager sessions;
    private final RoomManager rooms;

    public MessagingServiceI(SessionManager sessions, RoomManager rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public void sendPrivateMessage(String from, String to, String text, Current current) throws ChatException {
        // TODO (P2): localizar callback del destino y despachar (UserNotFoundException si no existe)
        throw new ChatException("sendPrivateMessage: no implementado");
    }

    @Override
    public void createRoom(String nickname, String room, Current current) throws ChatException {
        throw new ChatException("createRoom: no implementado");
    }

    @Override
    public String[] listRooms(Current current) {
        return new String[0];
    }

    @Override
    public void joinRoom(String nickname, String room, Current current) throws ChatException {
        throw new ChatException("joinRoom: no implementado");
    }

    @Override
    public void leaveRoom(String nickname, String room, Current current) throws ChatException {
        throw new ChatException("leaveRoom: no implementado");
    }

    @Override
    public void sendRoomMessage(String from, String room, String text, Current current) throws ChatException {
        // TODO (P2): broadcast a los miembros de la sala excepto el emisor
        throw new ChatException("sendRoomMessage: no implementado");
    }
}
