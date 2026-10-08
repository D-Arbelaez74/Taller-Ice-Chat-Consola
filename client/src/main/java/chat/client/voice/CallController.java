package chat.client.voice;

import chat.contract.*;
import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import java.net.DatagramSocket;

/**
 * Controlador encargado de manejar la logica de las llamadas de voz.
 * Implementa tanto llamadas 1 a 1 como llamadas grupales (Mesh UDP).
 */
public class CallController {

    private final CallServicePrx callService;
    private final ClientContext ctx;
    private DatagramSocket udpSocket;
    private Thread senderThread;
    private Thread receiverThread;
    private UdpAudioSender audioSender;
    private UdpAudioReceiver audioReceiver;

    // Estado para llamadas 1 a 1
    private String activePeer;
    private VoiceEndpoint pendingIncomingEndpoint;
    private String pendingCaller;

    // Estado para llamadas grupales
    private String activeRoom;

    public CallController(ClientContext ctx) {
        this.ctx = ctx;
        this.callService = ctx.calls;
    }

    /**
     * Registra los comandos interactivos en el interprete (CLI).
     */
    public void registerCommands(CommandShell shell) {
        shell.register("/call", "/call <user>", "Llamar a un usuario (1 a 1)", args -> startCall(args));
        shell.register("/accept", "/accept", "Aceptar llamada entrante", args -> acceptCall());
        shell.register("/reject", "/reject", "Rechazar llamada entrante", args -> rejectCall());
        shell.register("/hangup", "/hangup", "Finalizar llamada actual (1 a 1)", args -> hangUp());
        shell.register("/mute", "/mute", "Silenciar/Desilenciar el microfono", args -> toggleMute());
        
        // Comandos de llamadas grupales (RF-06)
        shell.register("/joinvoice", "/joinvoice <room>", "Unirse a llamada grupal en una sala", args -> joinGroupCall(args));
        shell.register("/leavevoice", "/leavevoice", "Salir de la llamada grupal", args -> leaveGroupCall());
    }

    // ==========================================
    // LLAMADAS 1 A 1 (RF-05)
    // ==========================================

    public void startCall(String targetUser) {
        if (targetUser == null || targetUser.trim().isEmpty()) {
            System.out.println("Debes especificar un usuario para llamar.");
            return;
        }
        try {
            this.udpSocket = new DatagramSocket();
            // Localizamos nuestro puerto para pasarselo al destino y que sepa donde enviarnos el audio
            VoiceEndpoint myEndpoint = new VoiceEndpoint("127.0.0.1", udpSocket.getLocalPort());
            this.activePeer = targetUser;

            // Enviamos la senal a traves de Ice (TCP)
            callService.startCall(ctx.nickname(), targetUser, myEndpoint);
            System.out.println("Llamando a " + targetUser + "...");
        } catch (Exception e) {
            System.err.println("Error al iniciar la llamada: " + e.getMessage());
            closeSocket();
            this.activePeer = null;
        }
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

            // Confirmamos aceptacion via Ice
            callService.answerCall(ctx.nickname(), pendingCaller, true, myEndpoint);
            
            // Iniciamos hilos de captura y reproduccion de audio
            startAudioStream();
            audioSender.addTarget(pendingIncomingEndpoint);
            
            System.out.println("Llamada aceptada. En comunicacion con " + activePeer);

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
            // Confirmamos rechazo via Ice
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
                // Notificamos cierre via Ice
                callService.hangup(ctx.nickname(), activePeer);
            } catch (Exception ignored) {}
        }
        stopAudioStream();
        System.out.println("Llamada finalizada.");
    }

    // Callbacks Invocados por el servidor via Ice
    
    public void onIncomingCall(String caller, VoiceEndpoint endpoint) {
        this.pendingCaller = caller;
        this.pendingIncomingEndpoint = endpoint;
        System.out.println("\n[LLAMADA ENTRANTE] De: " + caller + ". Usa '/accept' o '/reject'");
        System.out.print("> ");
    }

    public void onCallAccepted(String callee, VoiceEndpoint endpoint) {
        System.out.println("\nLlamada aceptada por " + callee);
        startAudioStream();
        audioSender.addTarget(endpoint);
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

    // ==========================================
    // LLAMADAS GRUPALES MESH (RF-06)
    // ==========================================

    public void joinGroupCall(String room) {
        if (room == null || room.trim().isEmpty()) {
            System.out.println("Debes especificar una sala.");
            return;
        }
        try {
            this.udpSocket = new DatagramSocket();
            VoiceEndpoint myEndpoint = new VoiceEndpoint("127.0.0.1", udpSocket.getLocalPort());
            this.activeRoom = room;

            // Inicia hilos de audio inmediatamente
            startAudioStream();
            
            // Avisa al servidor (Ice) que entramos a la llamada grupal
            callService.joinGroupCall(ctx.nickname(), room, myEndpoint);
            System.out.println("Unido a la llamada de voz de la sala " + room);
        } catch (Exception e) {
            System.err.println("Error al unirse a la llamada grupal: " + e.getMessage());
            closeSocket();
            this.activeRoom = null;
        }
    }

    public void leaveGroupCall() {
        if (activeRoom != null) {
            try {
                callService.leaveGroupCall(ctx.nickname(), activeRoom);
            } catch (Exception ignored) {}
        }
        stopAudioStream();
        System.out.println("Saliste de la llamada grupal.");
    }

    // Callback Invocado por el servidor via Ice
    
    public void onGroupCallSync(String room, VoiceEndpoint[] endpoints) {
        if (room.equals(activeRoom) && audioSender != null) {
            // Actualiza la malla de destinos P2P (Mesh)
            // Cuando alguien entra o sale, el servidor nos manda la lista nueva.
            audioSender.setTargets(endpoints);
        }
    }

    // ==========================================
    // FUNCIONES COMUNES
    // ==========================================

    public void toggleMute() {
        if (audioSender != null) {
            boolean isMuted = !audioSender.isMuted();
            audioSender.setMuted(isMuted);
            System.out.println(isMuted ? "Microfono silenciado." : "Microfono encendido.");
        } else {
            System.out.println("No hay una llamada activa.");
        }
    }

    /**
     * Inicia los hilos receptores y emisores de audio (Datagramas UDP)
     */
    public void startAudioStream() {
        if (audioSender != null || audioReceiver != null) {
            return; // Ya esta corriendo
        }
        
        audioReceiver = new UdpAudioReceiver(udpSocket);
        audioSender = new UdpAudioSender(udpSocket);

        receiverThread = new Thread(audioReceiver);
        senderThread = new Thread(audioSender);

        receiverThread.start();
        senderThread.start();
    }

    /**
     * Detiene los hilos y libera los recursos de audio (mic/speakers) y red.
     */
    public void stopAudioStream() {
        if (audioSender != null) audioSender.stop();
        if (audioReceiver != null) audioReceiver.stop();
        closeSocket();
        activePeer = null;
        activeRoom = null;
        audioSender = null;
        audioReceiver = null;
    }

    private void closeSocket() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }
}

