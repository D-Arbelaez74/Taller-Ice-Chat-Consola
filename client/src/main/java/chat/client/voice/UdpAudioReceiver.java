package chat.client.voice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import javax.sound.sampled.*;

public class UdpAudioReceiver implements Runnable {

    private final DatagramSocket socket;
    private volatile boolean running = true;

    public UdpAudioReceiver(DatagramSocket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
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

    public void stop() {
        this.running = false;
    }
}