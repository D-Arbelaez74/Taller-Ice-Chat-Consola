package chat.client.voice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.*;

public class UdpAudioReceiver implements Runnable {

    private final DatagramSocket socket;
    private volatile boolean running = true;

    // En la llamada grupal es nuestro numero de participante, para no reproducir nuestra propia voz.
    // En la llamada 1 a 1 vale 0 y los paquetes llegan sin cabecera
    private final int ownId;

    public UdpAudioReceiver(DatagramSocket socket) {
        this(socket, 0);
    }

    public UdpAudioReceiver(DatagramSocket socket, int ownId) {
        this.socket = socket;
        this.ownId = ownId;
    }

    @Override
    public void run() {
        if (ownId != 0) {
            runGroup();
            return;
        }

        AudioFormat format = AudioConfig.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try (SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info)) {
            speakers.open(format);
            speakers.start();

            byte[] buffer = new byte[AudioConfig.BUFFER_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                speakers.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("Error en receptor UDP de voz: " + e.getMessage());
            }
        }
    }

    // Llamada grupal: por el broadcast llegan paquetes de todos los participantes, incluidos los nuestros
    private void runGroup() {
        // Una linea de altavoz por cada persona, asi cuando dos hablan a la vez el sistema mezcla
        // las voces en vez de reproducirlas una detras de otra
        Map<Integer, SourceDataLine> speakersBySender = new HashMap<>();
        byte[] buffer = new byte[AudioConfig.HEADER_SIZE + AudioConfig.BUFFER_SIZE];

        try {
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                if (packet.getLength() <= AudioConfig.HEADER_SIZE) {
                    continue;
                }

                // Los primeros 4 bytes dicen quien envio el paquete. Si somos nosotros lo ignoramos
                int senderId = ByteBuffer.wrap(buffer).getInt();
                if (senderId == ownId) {
                    continue;
                }

                SourceDataLine speakers = speakersBySender.get(senderId);
                if (speakers == null) {
                    speakers = openSpeakers();
                    speakersBySender.put(senderId, speakers);
                }
                speakers.write(buffer, AudioConfig.HEADER_SIZE, packet.getLength() - AudioConfig.HEADER_SIZE);
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("Error en receptor UDP de voz: " + e.getMessage());
            }
        } finally {
            for (SourceDataLine speakers : speakersBySender.values()) {
                speakers.close();
            }
        }
    }

    private SourceDataLine openSpeakers() throws LineUnavailableException {
        AudioFormat format = AudioConfig.getFormat();
        SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
        speakers.open(format);
        speakers.start();
        return speakers;
    }

    public void stop() {
        this.running = false;
    }
}
