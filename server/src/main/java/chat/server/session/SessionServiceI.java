package chat.server.session;

import com.zeroc.Ice.Current;

import chat.contract.ChatException;
import chat.contract.ClientCallbackPrx;
import chat.contract.SessionService;
import chat.server.messaging.RoomManager;

//Se hace el registro por nickname unico, registro de callback, presencia y logout
public class SessionServiceI implements SessionService {

    private final SessionManager sessions;
    private final RoomManager rooms;

    public SessionServiceI(SessionManager sessions, RoomManager rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public String[] login(String nickname, ClientCallbackPrx callback, Current current) throws ChatException {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new ChatException("El nickname no puede estar vacio");
        }

        // Intenta registrar el usuario y devuelve false si ya existía.
        boolean success = sessions.register(nickname, callback);
        if (success == false) {
            throw new chat.contract.NicknameInUseException("El nickname " + nickname + " ya esta en uso");
        }

        System.out.println("Usuario conectado: " + nickname);

        // Si la conexión de este cliente se cierra sin que haya hecho logout (se cayó o cerró la consola)
        // Ice nos avisa aquí, entonces lo sacamos de la lista y le avisamos a los demás
        current.con.setCloseCallback(con -> {
            if (sessions.unregister(nickname, callback)) {
                System.out.println("Cliente desconectado abruptamente -> " + nickname);
                sessions.notifyUserDisconnected(nickname);
            }
        });

        // Se notifica que se logueo el nuevo usuario
        sessions.notifyUserConnected(nickname);

        // Retornamos el arreglo de usuarios conectados para que el cliente que acaba de entrar vea quién está
        return sessions.onlineUsers();
    }

    @Override
    public void logout(String nickname, Current current) {
        // Lo sacamos de la lista de conectados
        boolean wasOnline = sessions.unregister(nickname);
        if (wasOnline == true) {
            System.out.println("Usuario desconectado correctamente: " + nickname);
            
            // Nota para P2: Aquí se debería llamar a rooms.removeUserFromAllRooms(nickname) después
            
            // Avisamos a los demás
            sessions.notifyUserDisconnected(nickname);
        }
    }

    @Override
    public String[] getOnlineUsers(Current current) {
        return sessions.onlineUsers();
    }
}
