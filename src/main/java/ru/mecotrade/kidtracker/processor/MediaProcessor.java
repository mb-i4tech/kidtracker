/*
 * Copyright 2020 Sergey Shadchin (sergei.shadchin@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.mecotrade.kidtracker.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.mecotrade.kidtracker.dao.service.MediaService;
import ru.mecotrade.kidtracker.dao.model.Media;
import ru.mecotrade.kidtracker.dao.model.Message;
import ru.mecotrade.kidtracker.model.ChatMessage;
import ru.mecotrade.kidtracker.util.MessageUtils;

import java.util.HexFormat;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MediaProcessor {

    // skip 15 bytes of prefix "x,y," where x=5 if image is captured by RCAPTURE command
    // and x=6 if image is uploaded manually from device, y is capture date and time as YYmmddHHMMSS format
    private static final int IMG_SKIP_BYTES = 15;

    private static final byte MEDIA_ESCAPE = 0x7d;

    private static final Map<Byte, byte[]> MEDIA_MAPPING = new HashMap<>();

    static {

        MEDIA_MAPPING.put((byte) 0x01, new byte[]{0x7d});
        MEDIA_MAPPING.put((byte) 0x02, new byte[]{0x5b});
        MEDIA_MAPPING.put((byte) 0x03, new byte[]{0x5d});
        MEDIA_MAPPING.put((byte) 0x04, new byte[]{0x2c});
        MEDIA_MAPPING.put((byte) 0x05, new byte[]{0x2a});
    }

    @Autowired
    private MediaService mediaService;

    @Value("${kidtracker.chat.scrollUp.count}")
    private int scrollUpCount;

    private final File workspace;

    private final String codec, format;
    private final int bitrate, samplingRate;
    private final String audioContentType;
    static final int MAX_MEDIA_BYTES = 0xffff;

    public static byte[] toMediaBytes(byte[] payload) {
        if (payload.length > MAX_MEDIA_BYTES) throw new IllegalArgumentException("Media too large");
        java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream(payload.length);
        for (int i = 0; i < payload.length; i++) {
            if (payload[i] == MEDIA_ESCAPE && i + 1 < payload.length) {
                byte[] replacement = MEDIA_MAPPING.get(payload[i + 1]);
                if (replacement != null) { result.write(replacement[0]); i++; continue; }
                // Preserve unknown escape pairs exactly, as the original watch decoder did.
                result.write(payload[i++]);
            }
            result.write(payload[i]);
        }
        return result.toByteArray();
    }

    public static String toContentType(String magic) {
        switch (magic) {
            case "89504e47":
                return "image/png";
            case "47494638":
                return "image/gif";
            case "ffd8ffe0":
            case "ffd8ffe1":
            case "ffd8ffdb":
            case "ffd8ffe2":
                return "image/jpeg";
            default:
                return null;
        }
    }

    public MediaProcessor(@Value("${kidtracker.media.workspace}") String workspace,
                          @Value("${kidtracker.media.audio.codec}") String codec,
                          @Value("${kidtracker.media.audio.bitrate}") int bitrate,
                          @Value("${kidtracker.media.audio.samplingRate}") int samplingRate,
                          @Value("${kidtracker.media.audio.format}") String format,
                          @Value("${kidtracker.media.audio.contentType}") String audioContentType) {

        this.workspace = new File(workspace);
        if (this.workspace.mkdirs()) {
            log.info("Media workspace folder {} was created", workspace);
        }

        this.codec = codec;
        this.bitrate = bitrate;
        this.samplingRate = samplingRate;
        this.format = format;

        this.audioContentType = audioContentType;
    }

    public Media process(Message message) {

        Media media = null;

        if (message.getPayload() != null) {
            if (MessageUtils.MEDIA_TYPES.contains(message.getType())
                    && message.getPayload().length() > ((MAX_MEDIA_BYTES + 2) / 3) * 4) return null;

            if (MessageUtils.AUDIO_TYPES.contains(message.getType())) {

                java.nio.file.Path source = null, target = null;
                try {
                    byte[] audio = toMediaBytes(Base64.getDecoder().decode(message.getPayload()));
                    source = Files.createTempFile(workspace.toPath(), "audio-", ".input");
                    target = Files.createTempFile(workspace.toPath(), "audio-", ".output");
                    Files.write(source, audio);
                    BoundedAudioEncoder.encode(source, target, codec, bitrate, samplingRate, format);
                    long size = Files.size(target);
                    if (size == 0 || size >= BoundedAudioEncoder.MAX_OUTPUT) {
                        throw new java.io.IOException("Audio output empty or exceeds capacity");
                    }
                    media = mediaService.save(Media.builder().message(message).type(Media.Type.AUDIO)
                            .contentType(audioContentType).content(Files.readAllBytes(target)).build());
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    log.warn("Unable to create audio media record: {}", ex.getClass().getSimpleName());
                } finally {
                    for (java.nio.file.Path path : new java.nio.file.Path[]{source, target}) {
                        if (path != null) try { Files.deleteIfExists(path); }
                        catch (java.io.IOException ex) { log.warn("Unable to clean temporary audio file"); }
                    }
                }

            } else if (MessageUtils.IMAGE_TYPES.contains(message.getType())) {

                byte[] payload;
                try { payload = Base64.getDecoder().decode(message.getPayload()); }
                catch (IllegalArgumentException ex) { return null; }
                if (payload.length < IMG_SKIP_BYTES + 4 || payload.length > MAX_MEDIA_BYTES) return null;
                byte[] image = toMediaBytes(Arrays.copyOfRange(payload, IMG_SKIP_BYTES, payload.length));

                String magic = HexFormat.of().withUpperCase().formatHex(Arrays.copyOfRange(image, 0, 4)).toLowerCase();
                String contentType = toContentType(magic);

                if (contentType != null) {
                    media = mediaService.save(Media.builder()
                            .message(message)
                            .type(Media.Type.IMAGE)
                            .contentType(contentType)
                            .content(image)
                            .build());
                    log.debug("Image content is saved as {}", media);

                } else {
                    log.warn("Unrecognized media type for magic {} message {}", magic, message);
                }
            } else if (MessageUtils.TEXT_TYPES.contains(message.getType())) {

                media = mediaService.save(Media.builder()
                        .message(message)
                        .type(Media.Type.TEXT)
                        .contentType("text/plain;charset=utf-8")
                        .content(message.getPayload().getBytes(StandardCharsets.UTF_8))
                        .build());
                log.debug("Text message saved as {}", media);
            }
        }

        return media;
    }

    public Optional<Media> media(String deviceId, Long mediaId) {
        return mediaService.getBetween(mediaId).filter(m -> m.getMessage().getDeviceId().equals(deviceId));
    }

    public Collection<ChatMessage> chat(String deviceId, Long start, Long end) {
        return mediaService.getBetween(deviceId, new Date(start), new Date(end)).stream()
                .map(ChatMessage::of)
                .collect(Collectors.toList());
    }

    public Collection<ChatMessage> chatAfter(String deviceId, Long mediaId) {
        return mediaService.getAfter(deviceId, mediaId).stream()
                .map(ChatMessage::of)
                .collect(Collectors.toList());
    }

    public Collection<ChatMessage> chatBefore(String deviceId, Long mediaId) {
        return mediaService.getBefore(deviceId, mediaId, scrollUpCount).stream()
                .map(ChatMessage::of)
                .collect(Collectors.toList());
    }

    public Collection<ChatMessage> chatLast(String deviceId) {
        return mediaService.getLast(deviceId, scrollUpCount).stream()
                .map(ChatMessage::of).collect(Collectors.toList());
    }
}
