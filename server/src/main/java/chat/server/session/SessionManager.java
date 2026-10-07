package chat.server.session;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import chat.contract.ClientCallbackPrx;

//La idea es hacer una conexión segura de usuarios y los proxies para callback
//Se va a usar por todos los servants para localizar la ubicación de destino de un evento.
public class SessionManager {

    private final Map<String, ClientCallbackPrx> clients = new ConcurrentHashMap<>();

    //Aquí lanza un false si es que el nickname ya estaba en uso por alguien más
    public boolean register(String nickname, ClientCallbackPrx callback) {
        return clients.putIfAbsent(nickname, callback) == null;
    }

    public boolean unregister(String nickname) {
        return clients.remove(nickname) != null;
    }

    public Optional<ClientCallbackPrx> callbackOf(String nickname) {
        return Optional.ofNullable(clients.get(nickname));
    }

    public boolean isOnline(String nickname) {
        return clients.containsKey(nickname);
    }

    public String[] onlineUsers() {
        return clients.keySet().toArray(new String[0]);
    }

    public Map<String, ClientCallbackPrx> snapshot() {
        return Map.copyOf(clients);
    }

    // Notifica a todos los demás usuarios que alguien nuevo entró
    public void notifyUserConnected(String newNickname) {
        for (Map.Entry<String, ClientCallbackPrx> entry : clients.entrySet()) { //Recorremos el Map para conocer los clientes que tenemos y notificar si es que hay alguno nuevo que entre
            String peerNickname = entry.getKey(); //LLave del que este entrando
            ClientCallbackPrx callback = entry.getValue();

            try {
                // Usamos Async para enviar el mensaje por la red sin bloquear el ciclo
                callback.onUserConnectedAsync(newNickname);
            } catch (com.zeroc.Ice.LocalException e) { //Aquí utilizamos esa excepción por si la consola se cierra sola por algun error y se avisa que el usuario se salio en vez de que falle todo el programa.
                System.out.println("Se ha desconectado el cliente -> " + peerNickname);
                unregister(peerNickname);
                // También se notifica de forma recursiva si es el caso de que se desconecte el cliente, entonces se llama al mismo método
                notifyUserDisconnected(peerNickname); 
            }
        }
    }

    // Casi la misma logica del de conexión pero ya para cuando un cliente se desconecta
    public void notifyUserDisconnected(String leftNickname) {
        for (Map.Entry<String, ClientCallbackPrx> entry : clients.entrySet()) { //Mismo recorrido para ver los clientes que tengo
            String peerNickname = entry.getKey();
            ClientCallbackPrx callback = entry.getValue();

            try {
                callback.onUserDisconnectedAsync(leftNickname);
            } catch (com.zeroc.Ice.LocalException e) { //También se implementa la excepción porque puede que el cliente se desconecte no por orden sino porque se cayo el sistema o su llamada
                //Por eso también incluimos nuevamente el catch.
                System.out.println("Cliente desconectado abruptamente -> " + peerNickname);
                unregister(peerNickname);
            }
        }
    }
}
