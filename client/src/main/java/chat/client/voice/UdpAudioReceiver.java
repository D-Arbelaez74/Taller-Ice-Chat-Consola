package chat.client.voice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import javax.sound.sampled.*;

public class UdpAudioReceiver implements Runnable {

    private final DatagramSocket socket;
    private volatile boolean running = true;

    // Inicializa el receptor escuchando en el socket UDP proporcionado
    public UdpAudioReceiver(DatagramSocket socket) {
        this.socket = socket;
    }

    /**
     * Recibe los datagramas UDP con audio codificado PCM
     * y los reproduce en tiempo real a través de los altavoces usando SourceDataLine.
     */
    @Override
    public void run() {
        AudioFormat format = AudioConfig.getAudioFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try (SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info)) {
            // Abre y activa la línea de salida de audio (altavoces)
            speakers.open(format);
            speakers.start();

            byte[] buffer = new byte[AudioConfig.BUFFER_SIZE];

            // Bucle continuo de recepción y reproducción de voz
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                // Bloquea la ejecución hasta recibir un paquete UDP
                socket.receive(packet);
                // Escribe el contenido del paquete directamente a los altavoces
                speakers.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("Error en receptor UDP de voz: " + e.getMessage());
            }
        }
    }

    /**
     * Detiene el bucle de recepción de datos de audio.
     */
    public void stop() {
        this.running = false;
    }
}