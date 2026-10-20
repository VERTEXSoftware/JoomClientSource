package org.engine.audio;

import org.engine.utils.ResourceManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;

public class SoundBuffer {
    private final int bufferId;

    public SoundBuffer(String filePath) throws Exception {
        ByteBuffer soundData = ResourceManager.getResourceOrError(filePath,"error.ogg");
        if (soundData == null) {
            throw new Exception("Аудио файл не найден: " + filePath);
        }

        if (filePath.toLowerCase().endsWith(".ogg")) {
            this.bufferId = loadOgg(soundData, filePath);
        } else {
            // Предполагаем, что это WAV
            this.bufferId = loadWav(soundData, filePath);
        }
    }

    /**
     * Декодирование OGG Vorbis с помощью LWJGL STB
     */
    private int loadOgg(ByteBuffer oggData, String filePath) throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);

            // Открываем OGG поток из памяти
            long decoder = STBVorbis.stb_vorbis_open_memory(oggData, error, null);
            if (decoder == 0L) {
                throw new Exception("Ошибка чтения OGG файла: " + filePath + "\nКод ошибки STB: " + error.get(0));
            }

            // Получаем информацию о файле (каналы, частота дискретизации)
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(decoder, info);

            int channels = info.channels();
            int sampleRate = info.sample_rate();

            // Узнаем общее количество сэмплов (учитывая все каналы)
            int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

            // Выделяем память под распакованные PCM данные (16-битные сэмплы)
            // BufferUtils УЖЕ создает буфер с нативным порядком байтов
            ShortBuffer pcm = BufferUtils.createShortBuffer(totalSamples * channels);

            // Декодируем весь файл за раз (interleaved формат)
            STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);

            STBVorbis.stb_vorbis_close(decoder);

            // Формат OpenAL (OGG обычно 16-битный)
            int format = (channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

            int bufId = alGenBuffers();
            alBufferData(bufId, format, pcm, sampleRate);
            return bufId;
        }
    }

    /**
     * Парсинг стандартного WAV файла (из предыдущего шага)
     */
    private int loadWav(ByteBuffer soundData, String filePath) throws Exception {
        soundData.order(ByteOrder.LITTLE_ENDIAN);
        soundData.position(12); // Пропускаем "RIFF", размер, "WAVE"

        int channels = 0, sampleRate = 0, bitsPerSample = 0;
        ByteBuffer pcmData = null;

        while (soundData.remaining() >= 8) {
            int chunkId = soundData.getInt();
            int chunkSize = soundData.getInt();

            if (chunkId == 0x666D7420) { // "fmt "
                channels = soundData.getShort() & 0xFFFF;
                sampleRate = soundData.getInt();
                soundData.getInt(); // byte rate
                soundData.getShort(); // block align
                bitsPerSample = soundData.getShort() & 0xFFFF;

                if (chunkSize > 16) {
                    soundData.position(soundData.position() + (chunkSize - 16));
                }
            } else if (chunkId == 0x64617461) { // "data"
                pcmData = soundData.slice();
                pcmData.limit(chunkSize);
                soundData.position(soundData.position() + chunkSize);
                break;
            } else {
                soundData.position(soundData.position() + chunkSize);
            }
        }

        if (pcmData == null) {
            throw new Exception("Не удалось найти PCM данные в WAV: " + filePath);
        }

        int format = 0;
        if (channels == 1) {
            format = (bitsPerSample == 8) ? AL_FORMAT_MONO8 : AL_FORMAT_MONO16;
        } else if (channels == 2) {
            format = (bitsPerSample == 8) ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16;
        } else {
            throw new Exception("Неподдерживаемое количество каналов WAV: " + channels);
        }

        int bufId = alGenBuffers();
        alBufferData(bufId, format, pcmData, sampleRate);
        return bufId;
    }

    public int getBufferId() {
        return bufferId;
    }

    public void cleanup() {
        alDeleteBuffers(bufferId);
    }
}