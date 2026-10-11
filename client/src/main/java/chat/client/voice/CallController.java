package chat.client.voice;

import chat.contract.*;
import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;

/**
 * Controlador encargado de manejar la logica de las llamadas de voz.
 * Las llamadas 1 a 1 envian el audio directo al otro usuario; las grupales lo envian por broadcast UDP.
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
    private String pendingRoom; // sala que nos esta timbrando y todavia no contestamos

    public CallController(ClientContext ctx) {
        this.ctx = ctx;
        this.callService = ctx.calls;
    }

    /**
     * Registra los comandos interactivos en el interprete (CLI).
     */
    public void registerCommands(CommandShell shell) {
        shell.register("/call", "/call <user>", "Llamar a un usuario (1 a 1)", args -> startCall(args));
        shell.register("/accept", "/accept", "Aceptar llamada entrante (1 a 1 o grupal)", args -> acceptCall());
        shell.register("/reject", "/reject", "Rechazar llamada entrante (1 a 1 o grupal)", args -> rejectCall());
        shell.register("/hangup", "/hangup", "Colgar la llamada actual o salir de la grupal", args -> hangUp());
        shell.register("/mute", "/mute", "Silenciar/Desilenciar el microfono", args -> toggleMute());

        // Llamada grupal (RF-06): timbra a los miembros de la sala y cada uno contesta con /accept
        shell.register("/callroom", "/callroom <sala>", "Iniciar o entrar a la llamada grupal de una sala", args -> joinGroupCall(args));
    }

    // ==========================================
    // LLAMADAS 1 A 1 (RF-05)
    // ==========================================

    public void startCall(String targetUser) {
        if (targetUser == null || targetUser.trim().isEmpty()) {
            System.out.println("Debes especificar un usuario para llamar.");
            return;
        }
        if (activePeer != null || activeRoom != null) {
            System.out.println("Ya estas en una llamada. Usa /hangup antes de iniciar otra.");
            return;
        }
        try {
            this.udpSocket = new DatagramSocket();
            // Localizamos nuestro puerto para pasarselo al destino y que sepa donde enviarnos el audio
            VoiceEndpoint myEndpoint = new VoiceEndpoint(localIp(), udpSocket.getLocalPort());
            this.activePeer = targetUser;

            // Enviamos la senal a traves de Ice (TCP)
            callService.startCall(ctx.nickname(), targetUser, myEndpoint);
            System.out.println("Llamando a " + targetUser + "...");
        } catch (Exception e) {
            System.err.println("Error al iniciar la llamada: " + describe(e));
            closeSocket();
            this.activePeer = null;
        }
    }

    public void acceptCall() {
        // Si lo que esta timbrando es una llamada grupal, contestar es entrar a ella
        if (pendingCaller == null && pendingRoom != null) {
            joinGroupCall(pendingRoom);
            return;
        }
        if (pendingCaller == null) {
            System.out.println("No hay llamadas entrantes pendientes.");
            return;
        }
        if (activePeer != null || activeRoom != null) {
            System.out.println("Ya estas en una llamada. Usa /hangup antes de contestar otra.");
            return;
        }

        try {
            this.udpSocket = new DatagramSocket();
            VoiceEndpoint myEndpoint = new VoiceEndpoint(localIp(), udpSocket.getLocalPort());
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
            System.err.println("Error al aceptar la llamada: " + describe(e));
        }
    }

    public void rejectCall() {
        // Rechazar una llamada grupal es simplemente no entrar, los demas siguen hablando
        if (pendingCaller == null && pendingRoom != null) {
            System.out.println("Llamada grupal de la sala " + pendingRoom + " rechazada.");
            pendingRoom = null;
            return;
        }
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
            System.err.println("Error al rechazar la llamada: " + describe(e));
        }
    }

    public void hangUp() {
        // En una llamada grupal colgar es salir, sin cortarle la llamada a los demas
        if (activeRoom != null) {
            leaveGroupCall();
            return;
        }
        if (activePeer == null) {
            System.out.println("No hay una llamada activa.");
            return;
        }
        try {
            // Notificamos cierre via Ice
            callService.hangup(ctx.nickname(), activePeer);
        } catch (Exception ignored) {}
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
        // Si ya colgamos o no estabamos llamando a esa persona, no abrimos el audio
        if (activePeer == null || !activePeer.equals(callee)) {
            return;
        }
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

        // Si el que colgo es el que nos estaba llamando, la llamada pendiente ya no se puede aceptar
        if (peer.equals(pendingCaller)) {
            pendingCaller = null;
            pendingIncomingEndpoint = null;
        }

        // Solo cerramos el audio si la llamada que termino es la que teniamos activa
        if (activePeer != null && !activePeer.equals(peer)) {
            System.out.print("> ");
            return;
        }
        stopAudioStream();
        System.out.print("> ");
    }

    // ==========================================
    // LLAMADAS GRUPALES POR BROADCAST (RF-06)
    // ==========================================

    // Inicia la llamada de una sala o entra a la que ya esta en curso
    public void joinGroupCall(String room) {
        if (room == null || room.trim().isEmpty()) {
            System.out.println("Debes especificar una sala.");
            return;
        }
        if (activePeer != null || activeRoom != null) {
            System.out.println("Ya estas en una llamada. Usa /hangup antes de entrar a otra.");
            return;
        }
        room = room.trim();
        try {
            // El servidor nos dice el puerto de la llamada. Solo lo reciben los que entran,
            // por eso los demas equipos de la red no escuchan nada aunque el paquete sea broadcast
            GroupCallInfo info = callService.joinGroupCall(ctx.nickname(), room);

            // Todos los participantes abren el mismo puerto. Con setReuseAddress el sistema permite
            // que varios programas lo usen a la vez, y con setBroadcast nos deja enviar a toda la red
            this.udpSocket = new DatagramSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.setBroadcast(true);
            udpSocket.bind(new InetSocketAddress(info.port));

            this.activeRoom = room;
            this.pendingRoom = null;

            startAudioStream(info.senderId);
            // Un solo destino: la direccion de broadcast. La red se encarga de repartir el paquete
            audioSender.addTarget(new VoiceEndpoint(broadcastAddress(), info.port));

            System.out.println("Estas en la llamada grupal de la sala " + room + ". Usa /hangup para salir.");
        } catch (Exception e) {
            System.err.println("Error al entrar a la llamada grupal: " + describe(e));
            leaveQuietly(room);
            stopAudioStream();
        }
    }

    public void leaveGroupCall() {
        String room = activeRoom;
        leaveQuietly(room);
        stopAudioStream();
        System.out.println("Saliste de la llamada grupal de la sala " + room + ".");
    }

    // Le avisa al servidor que salimos. Si falla no importa, igual cerramos el audio de nuestro lado
    private void leaveQuietly(String room) {
        try {
            callService.leaveGroupCall(ctx.nickname(), room);
        } catch (Exception ignored) {}
    }

    // Callbacks Invocados por el servidor via Ice

    public void onGroupCallInvite(String room, String caller) {
        // Si ya estamos en esa llamada no hay nada que contestar
        if (room.equals(activeRoom)) {
            return;
        }
        this.pendingRoom = room;
        System.out.println("\n[LLAMADA GRUPAL] " + caller + " inicio una llamada en la sala " + room
                + ". Usa '/accept' o '/reject'");
        System.out.print("> ");
    }

    public void onGroupCallJoined(String room, String nickname) {
        if (room.equals(activeRoom)) {
            System.out.println("\n" + nickname + " entro a la llamada de la sala " + room);
            System.out.print("> ");
        }
    }

    public void onGroupCallLeft(String room, String nickname) {
        if (room.equals(activeRoom)) {
            System.out.println("\n" + nickname + " salio de la llamada de la sala " + room);
            System.out.print("> ");
        }
    }

    // Busca la direccion de broadcast de la red por la que estamos conectados al servidor.
    // El equipo puede tener varias redes (wifi, virtuales...) y cada una tiene su propia direccion
    private String broadcastAddress() throws Exception {
        InetAddress local = InetAddress.getByName(localIp());
        NetworkInterface network = NetworkInterface.getByInetAddress(local);
        if (network != null) {
            for (InterfaceAddress address : network.getInterfaceAddresses()) {
                if (address.getBroadcast() != null && address.getAddress().equals(local)) {
                    return address.getBroadcast().getHostAddress();
                }
            }
        }
        // Si no la encontramos (por ejemplo conectados por 127.0.0.1) usamos el broadcast general
        return "255.255.255.255";
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
        startAudioStream(0); // 0 = llamada 1 a 1, los paquetes no llevan numero de emisor
    }

    public void startAudioStream(int senderId) {
        if (audioSender != null || audioReceiver != null) {
            return; // Ya esta corriendo
        }

        audioReceiver = new UdpAudioReceiver(udpSocket, senderId);
        audioSender = new UdpAudioSender(udpSocket, senderId);

        receiverThread = new Thread(audioReceiver);
        senderThread = new Thread(audioSender);

        // Hilos daemon para que el programa pueda cerrarse con /exit aunque haya una llamada abierta
        receiverThread.setDaemon(true);
        senderThread.setDaemon(true);

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

    // Publicamos la misma IP con la que este equipo se conecto al servidor, porque con 127.0.0.1
    // el audio solo llegaria si los dos usuarios estuvieran en la misma maquina
    private String localIp() {
        if (ctx.session.ice_getConnection().getInfo() instanceof com.zeroc.Ice.IPConnectionInfo ipInfo) {
            return ipInfo.localAddress;
        }
        return "127.0.0.1";
    }

    // Las excepciones de nuestro contrato traen el motivo en "reason" y no en getMessage()
    private String describe(Exception e) {
        if (e instanceof ChatException chatError) {
            return chatError.reason;
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private void closeSocket() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }
}

