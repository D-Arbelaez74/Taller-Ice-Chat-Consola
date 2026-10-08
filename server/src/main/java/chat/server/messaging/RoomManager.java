package chat.server.messaging;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//Aquí guardamos las salas de chat y quiénes están en cada una
public class RoomManager {

    // Nombre de la sala -> nicknames de sus miembros.
    // Usamos colecciones concurrentes porque varios clientes pueden entrar y salir al mismo tiempo
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();

    // Devuelve false si ya había una sala con ese nombre. El que la crea queda adentro de una vez
    public boolean create(String room, String creator) {
        Set<String> members = ConcurrentHashMap.newKeySet();
        members.add(creator);
        return rooms.putIfAbsent(room, members) == null;
    }

    public boolean exists(String room) {
        return rooms.containsKey(room);
    }

    // Devuelve false si la sala no existe o si el usuario ya estaba adentro
    public boolean join(String room, String nickname) {
        Set<String> members = rooms.get(room);
        return members != null && members.add(nickname);
    }

    // Devuelve false si la sala no existe o si el usuario no estaba adentro
    public boolean leave(String room, String nickname) {
        Set<String> members = rooms.get(room);
        return members != null && members.remove(nickname);
    }

    public boolean isMember(String room, String nickname) {
        Set<String> members = rooms.get(room);
        return members != null && members.contains(nickname);
    }

    // Entregamos una copia para que quien la recorra no se vea afectado si alguien entra o sale en ese momento
    public Set<String> members(String room) {
        Set<String> members = rooms.get(room);
        return members == null ? Set.of() : Set.copyOf(members);
    }

    public String[] list() {
        return rooms.keySet().toArray(new String[0]);
    }

    // Se usa cuando alguien cierra sesión o se le cae la conexión, para que no le sigan llegando mensajes
    public void removeUserFromAllRooms(String nickname) {
        for (Set<String> members : rooms.values()) {
            members.remove(nickname);
        }
    }
}
