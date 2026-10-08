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

    // Inicializa el emisor con la IP y el puerto UDP del destino, utilizando el socket local
    public UdpAudioSender(String targetIp, int targetPort, DatagramSocket socket) {
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.socket = socket;
    }

    /**
     * Captura el audio desde el micrófono local usando TargetDataLine
     * y lo transmite continuamente mediante paquetes UDP.
     */
    @Override
    public void run() {
        AudioFormat format = AudioConfig.getAudioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        try (TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info)) {
            // Abre y activa la línea de entrada de audio (micrófono)
            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[AudioConfig.BUFFER_SIZE];
            InetAddress address = InetAddress.getByName(targetIp);

            // Bucle continuo de captura y envío de voz
            while (running) {
                // Lee los bytes de audio capturados por el micrófono
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    // Empaqueta los datos PCM y los envía vía UDP
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, address, targetPort);
                    socket.send(packet);
                }
            }
        } catch (Exception e) {
            System.err.println("Error en emisor UDP de voz: " + e.getMessage());
        }
    }

    /**
     * Cambia el estado de la variable de control para detener el hilo de captura.
     */
    public void stop() {
        this.running = false;
    }
}