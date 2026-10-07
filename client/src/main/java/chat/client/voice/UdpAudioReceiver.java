package chat.client.voice;

import javax.sound.sampled.*;
import java.net.*;

public class UdpAudioReceiver implements Runnable {
    private int listenPort;
    private boolean running = false;
    private DatagramSocket socket;

    public UdpAudioReceiver(int listenPort) {
        this.listenPort = listenPort;
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(listenPort);
            AudioFormat format = AudioConfig.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

            SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(format);
            speakers.start();

            running = true;
            byte[] buffer = new byte[1024];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                speakers.write(packet.getData(), 0, packet.getLength());
            }

            speakers.stop();
            speakers.close();
        } catch (Exception e) {
            if (running) System.err.println("Error en receptor de audio: " + e.getMessage());
        }
    }
}