package chat.server.calls;

import com.zeroc.Ice.Current;
import chat.contract.*;

public class CallServiceI implements CallService {

    private final SessionManager sessionManager;

    // Constructor que recibe el administrador de sesiones para resolver usuarios y callbacks
    public CallServiceI(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Procesa la solicitud de llamada de un usuario hacia un destinatario.
     * Busca al destinatario en el SessionManager y le notifica la llamada entrante mediante su observer.
     */
    @Override
    public void initiateCall(String targetUser, VoiceEndpoint callerEndpoint, Current current)
            throws UserNotFoundException, CallException {
        // Identifica quién está realizando la llamada a partir del objeto Current de Ice
        String caller = sessionManager.getUserByCurrent(current);

        // Obtiene el proxy del callback (observer) del usuario destino
        CallObserverPrx targetObserver = sessionManager.getCallObserver(targetUser);

        if (targetObserver == null) {
            throw new UserNotFoundException("El usuario " + targetUser + " no está disponible.");
        }

        // Notifica al destinatario enviando el nombre del llamante y su endpoint de red
        targetObserver.incomingCall(caller, callerEndpoint);
    }

    /**
     * Procesa la aceptación de una llamada por parte del usuario destinatario.
     * Devuelve al llamante original la dirección IP y puerto UDP del receptor.
     */
    @Override
    public void acceptCall(String targetUser, VoiceEndpoint calleeEndpoint, Current current)
            throws UserNotFoundException, CallException {
        // Identifica quién está aceptando la llamada
        String callee = sessionManager.getUserByCurrent(current);

        // Obtiene el observer de la persona que inició la llamada
        CallObserverPrx callerObserver = sessionManager.getCallObserver(targetUser);

        if (callerObserver != null) {
            // Notifica al creador de la llamada que fue aceptada y le comparte el endpoint del callee
            callerObserver.callAccepted(callee, calleeEndpoint);
        }
    }

    /**
     * Notifica al originador que la llamada fue rechazada.
     */
    @Override
    public void rejectCall(String targetUser, String reason, Current current) throws UserNotFoundException {
        CallObserverPrx observer = sessionManager.getCallObserver(targetUser);
        if (observer != null) {
            String callee = sessionManager.getUserByCurrent(current);
            observer.callRejected(callee, reason);
        }
    }

    /**
     * Notifica a la otra parte que la llamada activa ha sido colgada.
     */
    @Override
    public void endCall(String targetUser, Current current) throws UserNotFoundException {
        CallObserverPrx observer = sessionManager.getCallObserver(targetUser);
        if (observer != null) {
            String user = sessionManager.getUserByCurrent(current);
            observer.callEnded(user);
        }
    }
}