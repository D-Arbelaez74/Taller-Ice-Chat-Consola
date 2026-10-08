package chat.server.calls;

import com.zeroc.Ice.Current;
import chat.contract.*;
import chat.server.session.SessionManager;
import java.util.Optional;

public class CallServiceI implements CallService {

    private final SessionManager sessionManager;

    public CallServiceI(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void startCall(String from, String to, VoiceEndpoint endpoint, Current current) throws ChatException {
        Optional<ClientCallbackPrx> targetObserver = sessionManager.callbackOf(to);

        if (!targetObserver.isPresent()) {
            UserNotFoundException ex = new UserNotFoundException();
            ex.reason = "El usuario " + to + " no esta disponible.";
            throw ex;
        }

        targetObserver.get().onIncomingCall(from, endpoint);
    }

    @Override
    public void answerCall(String callee, String caller, boolean accepted, VoiceEndpoint endpoint, Current current) throws ChatException {
        Optional<ClientCallbackPrx> callerObserver = sessionManager.callbackOf(caller);

        if (callerObserver.isPresent()) {
            if (accepted) {
                callerObserver.get().onCallAccepted(callee, endpoint);
            } else {
                callerObserver.get().onCallRejected(callee);
            }
        }
    }

    @Override
    public void hangup(String from, String peer, Current current) throws ChatException {
        Optional<ClientCallbackPrx> observer = sessionManager.callbackOf(peer);
        if (observer.isPresent()) {
            observer.get().onCallEnded(from);
        }
    }
}

