package chat.server.messaging;

import com.zeroc.Ice.Current;

import chat.contract.ChatException;
import chat.contract.ClientCallbackPrx;
import chat.contract.MessagingService;
import chat.contract.NotLoggedInException;
import chat.contract.TextMessage;
import chat.contract.UserNotFoundException;
import chat.server.session.SessionManager;

//Aquí se manejan los mensajes privados entre dos usuarios y los mensajes de las salas
public class MessagingServiceI implements MessagingService {

    private final SessionManager sessions;
    private final RoomManager rooms;

    public MessagingServiceI(SessionManager sessions, RoomManager rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public void sendPrivateMessage(String from, String to, String text, Current current) throws ChatException {
        // Solo puede enviar mensajes alguien que ya hizo login
        if (!sessions.isOnline(from)) {
            throw new NotLoggedInException("Debes iniciar sesion antes de enviar mensajes");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new ChatException("El mensaje no puede estar vacio");
        }
        if (from.equals(to)) {
            throw new ChatException("No puedes enviarte un mensaje a ti mismo");
        }

        // Buscamos el callback del destinatario. Si no aparece es porque no existe o ya se desconecto
        ClientCallbackPrx target = sessions.callbackOf(to)
                .orElseThrow(() -> new UserNotFoundException("El usuario " + to + " no existe o no esta conectado"));

        // El campo de la sala va vacio porque es un mensaje privado
        TextMessage msg = new TextMessage(from, "", text.trim(), System.currentTimeMillis());

        // Usamos Async para no dejar bloqueado al servidor mientras el mensaje viaja al destinatario
        target.onPrivateMessageAsync(msg);
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
