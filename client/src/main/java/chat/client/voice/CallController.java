package chat.client.voice;

import chat.contract.*;
import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import java.net.DatagramSocket;

public class CallController {

    private final CallServicePrx callService;
    private final ClientContext ctx;
    private DatagramSocket udpSocket;
    private Thread senderThread;
    private Thread receiverThread;
    private UdpAudioSender audioSender;
    private UdpAudioReceiver audioReceiver;

    private String activePeer;
    private VoiceEndpoint pendingIncomingEndpoint;
    private String pendingCaller;

    // El taller espera recibir ClientContext en el constructor
    public CallController(ClientContext ctx) {
        this.ctx = ctx;
        this.callService = ctx.calls; // O ctx.getCallServicePrx() según tu ClientContext
    }

    // Registra los comandos en el CLI del cliente
    public void registerCommands(CommandShell shell) {
        shell.register("/call", "/call <user>", "Llamar a un usuario", args -> startCall(args));
        shell.register("/accept", "/accept", "Aceptar llamada entrante", args -> acceptCall());
        shell.register("/reject", "/reject", "Rechazar llamada entrante", args -> rejectCall());
        shell.register("/hangup", "/hangup", "Finalizar llamada actual", args -> hangUp());
        shell.register("/mute", "/mute", "Silenciar/Desilenciar el microfono", args -> toggleMute());
    }

    public void startCall(String targetUser) {
        if (targetUser == null || targetUser.trim().isEmpty()) {
            System.out.println("Debes especificar un usuario para llamar.");
            return;
        }
        try {
            this.udpSocket = new DatagramSocket();
            VoiceEndpoint myEndpoint = new VoiceEndpoint("127.0.0.1", udpSocket.getLocalPort());
            this.activePeer = targetUser;

            callService.startCall(ctx.nickname(), targetUser, myEndpoint);
            System.out.println("Llamando a " + targetUser + "...");
        } catch (Exception e) {
            System.err.println("Error al iniciar la llamada: " + e.getMessage());
            closeSocket();
            this.activePeer = null;
        }
    }

    // Métodos invocados por ClientCallbackI
    public void onIncomingCall(String caller, VoiceEndpoint endpoint) {
        this.pendingCaller = caller;
        this.pendingIncomingEndpoint = endpoint;
        System.out.println("\n[LLAMADA ENTRANTE] De: " + caller + ". Usa '/accept' o '/reject'");
        System.out.print("> ");
    }

    public void onCallAccepted(String callee, VoiceEndpoint endpoint) {
        System.out.println("\nLlamada aceptada por " + callee);
        startAudioStream(endpoint.host, endpoint.port);
        System.out.print("> ");
    }

    public void onCallRejected(String callee) {
        System.out.println("\nLlamada rechazada por " + callee);
        stopAudioStream();
        System.out.print("> ");
    }

    public void onCallEnded(String peer) {
        System.out.println("\nLlamada finalizada por " + peer);
        stopAudioStream();
        System.out.print("> ");
    }

    public void acceptCall() {
        if (pendingCaller == null) {
            System.out.println("No hay llamadas entrantes pendientes.");
            return;
        }

        try {
            this.udpSocket = new DatagramSocket();
            VoiceEndpoint myEndpoint = new VoiceEndpoint("127.0.0.1", udpSocket.getLocalPort());
            this.activePeer = pendingCaller;

            callService.answerCall(ctx.nickname(), pendingCaller, true, myEndpoint);
            startAudioStream(pendingIncomingEndpoint.host, pendingIncomingEndpoint.port);
            System.out.println("Llamada aceptada. En comunicación con " + activePeer);

            pendingCaller = null;
            pendingIncomingEndpoint = null;
        } catch (Exception e) {
            System.err.println("Error al aceptar la llamada: " + e.getMessage());
        }
    }

    public void rejectCall() {
        if (pendingCaller == null) {
            System.out.println("No hay llamadas entrantes pendientes.");
            return;
        }

        try {
            callService.answerCall(ctx.nickname(), pendingCaller, false, new VoiceEndpoint("", 0));
            System.out.println("Llamada rechazada.");
            pendingCaller = null;
            pendingIncomingEndpoint = null;
        } catch (Exception e) {
            System.err.println("Error al rechazar la llamada: " + e.getMessage());
        }
    }

    public void hangUp() {
        if (activePeer != null) {
            try {
                callService.hangup(ctx.nickname(), activePeer);
            } catch (Exception ignored) {}
        }
        stopAudioStream();
        System.out.println("Llamada finalizada.");
    }
    
    public void toggleMute() {
        if (audioSender != null) {
            boolean isMuted = !audioSender.isMuted();
            audioSender.setMuted(isMuted);
            System.out.println(isMuted ? "Microfono silenciado." : "Microfono encendido.");
        } else {
            System.out.println("No hay una llamada activa.");
        }
    }

    public void startAudioStream(String targetIp, int targetPort) {
        audioReceiver = new UdpAudioReceiver(udpSocket);
        audioSender = new UdpAudioSender(targetIp, targetPort, udpSocket);

        receiverThread = new Thread(audioReceiver);
        senderThread = new Thread(audioSender);

        receiverThread.start();
        senderThread.start();
    }

    public void stopAudioStream() {
        if (audioSender != null) audioSender.stop();
        if (audioReceiver != null) audioReceiver.stop();
        closeSocket();
        activePeer = null;
    }

    private void closeSocket() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }
}

