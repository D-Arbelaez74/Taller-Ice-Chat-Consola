package chat.client.voice;

import javax.sound.sampled.AudioFormat;

/**
 * Formato de audio comun a captura y reproduccion (ambos extremos deben usar el mismo).
 * Responsable: P4
 */
public final class AudioConfig {

    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    /** Bytes por datagrama: 20 ms de audio a 16 kHz, 16 bits, mono. */
    public static final int PACKET_BYTES = 640;

    private AudioConfig() {
    }
}
