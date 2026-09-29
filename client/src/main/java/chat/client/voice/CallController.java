package chat.client.voice;

import chat.client.ClientContext;
import chat.client.cli.CommandShell;
import chat.contract.VoiceEndpoint;

/**
 * RF-05 lado cliente: senalizacion (/call, /accept, /reject, /hangup, /mute) y
 * control de los hilos de audio UDP.
 * Responsable: P4
 */
public class CallController {

    private final ClientContext ctx;

    public CallController(ClientContext ctx) {
        this.ctx = ctx;
    }

    public void registerCommands(CommandShell shell) {
        // TODO (P4): /call <user>, /accept, /reject, /hangup, /mute
    }

    public void onIncomingCall(String caller, VoiceEndpoint callerEndpoint) {
        ctx.printer.event("Llamada entrante de " + caller + " (/accept o /reject)");
    }

    public void onCallAccepted(String callee, VoiceEndpoint calleeEndpoint) {
        // TODO (P4): iniciar UdpAudioSender hacia calleeEndpoint
    }

    public void onCallRejected(String callee) {
        ctx.printer.event(callee + " rechazo la llamada");
    }

    public void onCallEnded(String peer) {
        // TODO (P4): detener sender/receiver y liberar lineas de audio
    }
}
