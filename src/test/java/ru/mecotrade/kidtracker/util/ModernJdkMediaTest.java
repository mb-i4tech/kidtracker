package ru.mecotrade.kidtracker.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class ModernJdkMediaTest {
    @TempDir Path temporary;

    @Test void thumbnailDataUrlStillRoundTripsOnJava21() throws Exception {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        String resized = ThumbUtils.resize("data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()), 10);
        assertTrue(resized.startsWith("data:image/png;base64,"));
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(resized.split(",", 2)[1])));
        assertEquals(10, result.getWidth()); assertEquals(10, result.getHeight());
    }

    @Test void upgradedJaveNativeEncoderConvertsSyntheticAudio() throws Exception {
        Path wav = temporary.resolve("synthetic.wav");
        Path mp3 = temporary.resolve("synthetic.mp3");
        AudioFormat pcm = new AudioFormat(22050, 16, 1, true, false);
        try (AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(new byte[44100]), pcm, 22050)) {
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, wav.toFile());
        }
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("libmp3lame"); audio.setBitRate(16000); audio.setChannels(1); audio.setSamplingRate(22050);
        EncodingAttributes encoding = new EncodingAttributes();
        encoding.setOutputFormat("mp3"); encoding.setAudioAttributes(audio);
        new Encoder().encode(new MultimediaObject(wav.toFile()), mp3.toFile(), encoding);
        assertTrue(Files.size(mp3) > 0);
        assertEquals(1, new MultimediaObject(mp3.toFile()).getInfo().getAudio().getChannels());
    }
}
