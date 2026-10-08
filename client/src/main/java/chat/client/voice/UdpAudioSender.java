package chat.client.voice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.sampled.*;
import chat.contract.VoiceEndpoint;

/**
 * Emisor UDP que captura el audio del microfono y lo envia por Datagramas
 * a uno o multiples destinos (Peer-to-Peer Mesh).
 */
public class UdpAudioSender implements Runnable {

    // Lista de endpoints destino a los cuales se enviaran los datagramas
    private final List<VoiceEndpoint> targets = new CopyOnWriteArrayList<>();
    private final DatagramSocket socket;
    
    // Banderas de control de hilo y mute
    private volatile boolean running = true;
    private volatile boolean muted = false;

    public UdpAudioSender(DatagramSocket socket) {
        this.socket = socket;
    }

    public void addTarget(VoiceEndpoint target) {
        targets.add(target);
    }
    
    public void setTargets(VoiceEndpoint[] newTargets) {
        targets.clear();
        for (VoiceEndpoint t : newTargets) {
            targets.add(t);
        }
    }

    @Override
    public void run() {
        AudioFormat format = AudioConfig.getFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        try (TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info)) {
            // Se abre y se inicia la linea de microfono
            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[AudioConfig.BUFFER_SIZE];

            while (running) {
                // Se lee el audio del microfono
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && !muted) {
                    // Por cada endpoint destino (Mesh P2P), se envia una replica del paquete
                    for (VoiceEndpoint target : targets) {
                        try {
                            InetAddress address = InetAddress.getByName(target.host);
                            DatagramPacket packet = new DatagramPacket(buffer, bytesRead, address, target.port);
                            socket.send(packet);
                        } catch (Exception ignored) {
                            // Si un destino falla, continuamos con los demas
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error en emisor UDP de voz: " + e.getMessage());
        }
    }

    public void stop() {
        this.running = false;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }
    
    public boolean isMuted() {
        return this.muted;
    }
}

