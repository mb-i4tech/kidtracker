package ru.mecotrade.kidtracker.device;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import ru.mecotrade.kidtracker.exception.KidTrackerParseException;
import ru.mecotrade.kidtracker.dao.service.MessageService;
import ru.mecotrade.kidtracker.processor.MediaProcessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageConnectorBoundsTest {
    DeviceManager manager = mock(DeviceManager.class);
    MessageConnector connector() {return new MessageConnector(new Socket(),manager,mock(MessageService.class),mock(MediaProcessor.class));}
    @Test void handlesEveryByteFragmentAndCoalescedFrames() throws Exception {
        MessageConnector c = connector();
        for(byte b : "[SG*123*0002*LK]".getBytes(StandardCharsets.US_ASCII)) c.process(new byte[]{b});
        c.process("[SG*123*0002*LK][SG*123*0002*LK]".getBytes());
        verify(manager,times(3)).onMessage(any(),eq(c));
    }
    @Test void missingTrailerWaitsRatherThanIndexingPastBuffer() throws Exception {
        MessageConnector c = connector(); c.process("[SG*123*0002*LK".getBytes());
        verifyNoInteractions(manager); c.process(new byte[]{']'});verify(manager).onMessage(any(),eq(c));
    }
    @Test void rejectsInvalidLengthsAndOversizeHeadersAndBuffers() {
        for(String frame : new String[]{"[SG*123*-001*", "[SG*123*GGGG*", "[SG*123*10000*", "[SG*123*0002*LK!", "["+"x".repeat(128)})
            assertThrows(KidTrackerParseException.class,()->connector().process(frame.getBytes()));
        assertThrows(KidTrackerParseException.class,()->connector().process(new byte[MessageConnector.MAX_BUFFER+1]));
    }
    @Test void maximumPayloadAndBinarySeparatorsRemainValid() throws Exception {
        MessageConnector c = connector();
        byte[] frame = new byte["[SG*123*FFFF*".length()+65535+1];
        byte[] header = "[SG*123*FFFF*".getBytes();System.arraycopy(header,0,frame,0,header.length);
        java.util.Arrays.fill(frame,header.length,frame.length,(byte)']');
        frame[header.length]='T';frame[header.length+1]='K';frame[header.length+2]=',';
        for(int i=0;i<frame.length;i+=1024)c.process(java.util.Arrays.copyOfRange(frame,i,Math.min(i+1024,frame.length)));
        verify(manager).onMessage(any(),eq(c));
    }
    @Test void partialFrameHasAbsoluteDeadline() throws Exception {
        MessageConnector c=connector();c.process("[SG".getBytes());
        org.springframework.test.util.ReflectionTestUtils.setField(c,"partialStarted",System.nanoTime()-java.util.concurrent.TimeUnit.SECONDS.toNanos(31));
        assertThrows(KidTrackerParseException.class,()->c.process(new byte[]{'*'}));
    }
    @Test void runtimeFailureStillClosesSocket() throws Exception {
        Socket socket=mock(Socket.class);
        when(socket.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[]{1}));
        when(socket.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
        DeviceConnector c=new DeviceConnector(socket){void process(byte[] data){throw new IllegalStateException("test");}};
        assertThrows(IllegalStateException.class,c::run);verify(socket).close();verify(socket).setSoTimeout(300000);
    }
    @Test void debugLinesAreBounded() {
        assertThrows(KidTrackerParseException.class,()->new DebugConnector(new Socket()).process(new byte[8193]));
    }
    @Test void listenerExecutorHasHardCapacityAndNoQueue() {
        java.util.concurrent.ThreadPoolExecutor pool = (java.util.concurrent.ThreadPoolExecutor)new ru.mecotrade.kidtracker.config.KidTrackerConfig().deviceListenerExecutor();
        assertEquals(128,pool.getMaximumPoolSize());assertEquals(0,pool.getQueue().remainingCapacity());pool.shutdownNow();
        pool=(java.util.concurrent.ThreadPoolExecutor)new ru.mecotrade.kidtracker.config.KidTrackerConfig().notificationExecutor();
        assertEquals(8,pool.getMaximumPoolSize());assertEquals(128,pool.getQueue().remainingCapacity());
        assertInstanceOf(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy.class,pool.getRejectedExecutionHandler());pool.shutdownNow();
    }
}
