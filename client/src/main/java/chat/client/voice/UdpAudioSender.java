package chat.client.voice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import javax.sound.sampled.*;

public class UdpAudioSender implements Runnable {

    private final String targetIp;
    private final int targetPort;
    private final DatagramSocket socket;
    private volatile boolean running = true;
    private volatile boolean muted = false;

    public UdpAudioSender(String targetIp, int targetPort, DatagramSocket socket) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.socket = socket;
    }

    @Override
    public void run() {
        AudioFormat format = AudioConfig.getFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        try (TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info)) {
            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[AudioConfig.BUFFER_SIZE];
            InetAddress address = InetAddress.getByName(targetIp);

            while (running) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && !muted) {
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, address, targetPort);
                    socket.send(packet);
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

