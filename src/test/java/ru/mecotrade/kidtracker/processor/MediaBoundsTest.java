package ru.mecotrade.kidtracker.processor;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import ru.mecotrade.kidtracker.dao.model.*;
import ru.mecotrade.kidtracker.dao.service.MediaService;
import javax.sound.sampled.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaBoundsTest {
    @TempDir Path dir;
    @Test void escapingIsLinearAndPreservesUnknownPairs() {
        assertArrayEquals(new byte[]{'}','[',']',',','*','}',9,'}'},MediaProcessor.toMediaBytes(new byte[]{'}',1,'}',2,'}',3,'}',4,'}',5,'}',9,'}'}));
        assertThrows(IllegalArgumentException.class,()->MediaProcessor.toMediaBytes(new byte[65536]));
    }
    @Test void childProcessTimeoutIsEnforced() {
        long start=System.nanoTime();
        assertThrows(IOException.class,()->BoundedAudioEncoder.run(List.of("/bin/sleep","30"),100,TimeUnit.MILLISECONDS));
        assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(6));
    }
    @Test void validAudioConvertsAndInvalidOrOversizeMediaLeavesNoTemporaryFiles() throws Exception {
        MediaProcessor p = new MediaProcessor(dir.toString(),"libmp3lame",16000,22050,"mp3","audio/mpeg");
        MediaService service=mock(MediaService.class);ReflectionTestUtils.setField(p,"mediaService",service);
        when(service.save(any())).thenAnswer(i->i.getArgument(0));
        ByteArrayOutputStream wav=new ByteArrayOutputStream();
        try(AudioInputStream in=new AudioInputStream(new ByteArrayInputStream(new byte[22050]),new AudioFormat(22050,16,1,true,false),11025)) {
            AudioSystem.write(in,AudioFileFormat.Type.WAVE,wav);
        }
        // Encode watch escaping so arbitrary WAV bytes round-trip through the media decoder.
        ByteArrayOutputStream escaped=new ByteArrayOutputStream();
        for(byte b:wav.toByteArray()) {
            int code=switch(b){case 0x7d->1;case 0x5b->2;case 0x5d->3;case 0x2c->4;case 0x2a->5;default->0;};
            if(code>0){escaped.write(0x7d);escaped.write(code);}else escaped.write(b);
        }
        Media media=p.process(Message.device("SG","123","TK",Base64.getEncoder().encodeToString(escaped.toByteArray())));
        assertNotNull(media);assertTrue(media.getContent().length>0);
        assertNull(p.process(Message.device("SG","123","TK","invalid!")));
        assertNull(p.process(Message.device("SG","123","TK",Base64.getEncoder().encodeToString(new byte[]{1,2,3}))));
        assertNull(p.process(Message.device("SG","123","TK","A".repeat(90000))));
        assertNull(p.process(Message.device("SG","123","IMG","AA==")));
        try(var files=Files.list(dir)){assertEquals(0,files.count());}
    }
}
