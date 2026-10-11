package chat.client.voice;

import javax.sound.sampled.AudioFormat;

public class AudioConfig {

    public static final int BUFFER_SIZE = 1024;

    // Bytes al inicio de cada paquete de la llamada grupal para guardar el numero del emisor
    public static final int HEADER_SIZE = 4;

    public static AudioFormat getFormat() {
        float sampleRate = 8000.0f; // 8kHz es suficiente para voz humana
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;

        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}

