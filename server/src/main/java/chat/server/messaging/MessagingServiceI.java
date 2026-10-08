package chat.server.messaging;

import com.zeroc.Ice.Current;

import chat.contract.ChatException;
import chat.contract.ClientCallbackPrx;
import chat.contract.MessagingService;
import chat.contract.NotLoggedInException;
import chat.contract.RoomException;
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
        requireLogin(nickname);

        // El nombre no puede llevar espacios porque en la consola el nombre de la sala es una sola palabra
        if (room == null || room.trim().isEmpty() || room.trim().contains(" ")) {
            throw new RoomException("El nombre de la sala no puede estar vacio ni tener espacios");
        }

        // create devuelve false si ya había una sala con ese nombre
        if (!rooms.create(room.trim(), nickname)) {
            throw new RoomException("La sala " + room.trim() + " ya existe");
        }
        System.out.println("Sala creada: " + room.trim() + " por " + nickname);
    }

    @Override
    public String[] listRooms(Current current) {
        return rooms.list();
    }

    @Override
    public void joinRoom(String nickname, String room, Current current) throws ChatException {
        requireLogin(nickname);
        requireRoom(room);

        if (!rooms.join(room, nickname)) {
            throw new RoomException("Ya estas en la sala " + room);
        }
    }

    @Override
    public void leaveRoom(String nickname, String room, Current current) throws ChatException {
        requireLogin(nickname);
        requireRoom(room);

        if (!rooms.leave(room, nickname)) {
            throw new RoomException("No estas en la sala " + room);
        }
    }

    @Override
    public void sendRoomMessage(String from, String room, String text, Current current) throws ChatException {
        requireLogin(from);
        requireRoom(room);

        // Solo los miembros pueden escribir en la sala, así los mensajes no salen ni entran de otros grupos
        if (!rooms.isMember(room, from)) {
            throw new RoomException("Debes unirte a la sala " + room + " antes de escribir en ella");
        }
        if (text == null || text.trim().isEmpty()) {
            throw new ChatException("El mensaje no puede estar vacio");
        }

        TextMessage msg = new TextMessage(from, room, text.trim(), System.currentTimeMillis());

        // Recorremos solo los miembros de esta sala y nos saltamos al que envía para que no le llegue repetido
        for (String member : rooms.members(room)) {
            if (member.equals(from)) {
                continue;
            }
            sessions.callbackOf(member).ifPresent(callback -> callback.onRoomMessageAsync(msg));
        }
    }

    // Validaciones que se repiten en varios métodos
    private void requireLogin(String nickname) throws NotLoggedInException {
        if (!sessions.isOnline(nickname)) {
            throw new NotLoggedInException("Debes iniciar sesion antes de usar el chat");
        }
    }

    private void requireRoom(String room) throws RoomException {
        if (!rooms.exists(room)) {
            throw new RoomException("La sala " + room + " no existe");
        }
    }
}
