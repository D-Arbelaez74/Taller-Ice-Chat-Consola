package chat.client.voice;

import javax.sound.sampled.*;
import java.net.*;

public class UdpAudioSender implements Runnable {
    private String targetIp;
    private int targetPort;
    private boolean running = false;
    private DatagramSocket socket;

    public UdpAudioSender(String targetIp, int targetPort) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
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
            socket = new DatagramSocket();
            AudioFormat format = AudioConfig.getFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            running = true;
            byte[] buffer = new byte[1024];
            InetAddress address = InetAddress.getByName(targetIp);

            while (running) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, address, targetPort);
                    socket.send(packet);
                }
            }

            microphone.stop();
            microphone.close();
        } catch (Exception e) {
            if (running) System.err.println("Error en emisor de audio: " + e.getMessage());
        }
    }
}