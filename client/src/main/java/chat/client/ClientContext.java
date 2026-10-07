package chat.client;


import chat.contract.CallServicePrx;
import chat.contract.FileServicePrx;
import chat.contract.MessagingServicePrx;
import chat.contract.SessionServicePrx;
import com.zeroc.Ice.Communicator;

/**
 * Estado compartido del cliente: proxies a los servicios remotos, consola y usuario actual.
 */
public class ClientContext {

    public final Communicator communicator;
    public final SessionServicePrx session;
    public final MessagingServicePrx messaging;
    public final FileServicePrx files;
    public final CallServicePrx calls;

    private volatile String nickname;

    public ClientContext(Communicator communicator, SessionServicePrx session, MessagingServicePrx messaging,
                         FileServicePrx files, CallServicePrx calls) {
        this.communicator = communicator;
        this.session = session;
        this.messaging = messaging;
        this.files = files;
        this.calls = calls;
    }

    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
