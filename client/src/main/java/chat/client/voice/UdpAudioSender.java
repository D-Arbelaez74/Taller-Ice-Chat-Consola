package chat.client.voice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
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

    // En la llamada grupal cada paquete lleva al inicio el numero de quien lo envia.
    // En la llamada 1 a 1 vale 0 y el paquete lleva solo el audio
    private final int senderId;

    public UdpAudioSender(DatagramSocket socket) {
        this(socket, 0);
    }

    public UdpAudioSender(DatagramSocket socket, int senderId) {
        this.socket = socket;
        this.senderId = senderId;
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

            // Si es llamada grupal dejamos 4 bytes al inicio para el numero del emisor
            int header = (senderId != 0) ? AudioConfig.HEADER_SIZE : 0;
            byte[] buffer = new byte[header + AudioConfig.BUFFER_SIZE];
            if (header > 0) {
                ByteBuffer.wrap(buffer).putInt(senderId);
            }

            while (running) {
                // Se lee el audio del microfono y se guarda despues del numero del emisor, si lo hay
                int bytesRead = microphone.read(buffer, header, AudioConfig.BUFFER_SIZE);
                if (bytesRead > 0 && !muted) {
                    // En 1 a 1 el destino es el otro usuario; en grupal es la direccion de broadcast
                    for (VoiceEndpoint target : targets) {
                        try {
                            InetAddress address = InetAddress.getByName(target.host);
                            DatagramPacket packet = new DatagramPacket(buffer, header + bytesRead, address, target.port);
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

