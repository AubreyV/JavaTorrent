package org.johnnei.javatorrent.module;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.johnnei.javatorrent.bittorrent.encoding.Bencoder;
import org.johnnei.javatorrent.bittorrent.protocol.messages.IMessage;
import org.johnnei.javatorrent.network.InStream;
import org.johnnei.javatorrent.torrent.MetadataFileSet;
import org.johnnei.javatorrent.torrent.Torrent;
import org.johnnei.javatorrent.torrent.peer.Peer;
import org.johnnei.javatorrent.ut_metadata.protocol.messages.MessageData;
import org.johnnei.javatorrent.ut_metadata.protocol.messages.MessageReject;
import org.johnnei.javatorrent.ut_metadata.protocol.messages.MessageRequest;
import org.johnnei.javatorrent.ut_metadata.protocol.messages.MessageUnknown;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link UTMetadataExtension}
 */
public class UTMetadataExtensionTest extends EasyMockSupport {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private static final Charset UTF8 = Charset.forName("UTF-8");

	@Test
	public void testGetExtensionName() throws IOException {
		assertEquals("ut_metadata", new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder()).getExtensionName());
	}

	@Test
	public void testProcessHandshakeMetadataNoMetadataSize() throws IOException {
		Peer peerMock = createMock(Peer.class);

		Map<String, Object> handshakeDictionary = new HashMap<>();
		Map<String, Object> emptyDictionary = Collections.emptyMap();
		handshakeDictionary.put("m", emptyDictionary);

		replayAll();

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		cut.processHandshakeMetadata(peerMock, handshakeDictionary, emptyDictionary);

		verifyAll();
	}

	@Test
	public void testProcessHandshakeMetadata() throws IOException {
		Peer peerMock = createMock(Peer.class);

		Map<String, Object> handshakeDictionary = new HashMap<>();
		Map<String, Object> emptyDictionary = Collections.emptyMap();
		handshakeDictionary.put("metadata_size", 512);
		handshakeDictionary.put("m", emptyDictionary);

		Capture<MetadataInformation> informationCapture = EasyMock.newCapture();
		peerMock.addModuleInfo(capture(informationCapture));

		replayAll();

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		cut.processHandshakeMetadata(peerMock, handshakeDictionary, emptyDictionary);

		verifyAll();

		MetadataInformation metaInfo = informationCapture.getValue();
		assertEquals("Incorrect metadata size", 512, metaInfo.getMetadataSize());
	}

	@Test
	public void testAddHandshakeMetadataDownloadingMetadata() throws IOException {
		Torrent torrent = createNiceMock(Torrent.class);
		Peer peerMock = createNiceMock(Peer.class);
		Bencoder bencoderMock = createMock(Bencoder.class);

		expect(peerMock.getTorrent()).andStubReturn(torrent);
		expect(torrent.isDownloadingMetadata()).andReturn(true);

		replayAll();

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		cut.addHandshakeMetadata(peerMock, bencoderMock);

		verifyAll();
	}

	@Test
	public void testAddHandshakeMetadata() throws IOException {
		Torrent torrent = createNiceMock(Torrent.class);
		Peer peerMock = createNiceMock(Peer.class);
		Bencoder bencoderMock = createMock(Bencoder.class);
		MetadataFileSet metadataMock = createNiceMock(MetadataFileSet.class);

		expect(peerMock.getTorrent()).andStubReturn(torrent);
		expect(torrent.isDownloadingMetadata()).andReturn(false);
		expect(torrent.getMetadata()).andReturn(Optional.of(metadataMock));
		expect(metadataMock.getTotalFileSize()).andReturn(512L);
		bencoderMock.string("metadata_size");
		bencoderMock.integer(512);

		replayAll();

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		cut.addHandshakeMetadata(peerMock, bencoderMock);

		verifyAll();
	}

	@Test
	public void testGetMessageReject() throws IOException {
		InStream inStream = new InStream("d8:msg_typei2e5:piecei5ee".getBytes(UTF8));

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		IMessage message = cut.getMessage(inStream);

		assertNotNull("A message should have been returned", message);
		assertTrue("Incorrect message type returned. Expected: MessageReject.", message instanceof MessageReject);
	}

	@Test
	public void testGetMessageRequest()  throws IOException {
		InStream inStream = new InStream("d8:msg_typei0e5:piecei5ee".getBytes(UTF8));

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		IMessage message = cut.getMessage(inStream);

		assertNotNull("A message should have been returned", message);
		assertTrue("Incorrect message type returned. Expected: MessageRequest.", message instanceof MessageRequest);
	}

	@Test
	public void testGetMessageUnknown() throws IOException {
		InStream inStream = new InStream("d8:msg_typei151e5:piecei42ee".getBytes(UTF8));

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		IMessage message = cut.getMessage(inStream);

		assertNotNull("A message should have been returned", message);
		assertTrue("Incorrect message type returned. Expected: MessageUnknown.", message instanceof MessageUnknown);
	}

	@Test
	public void testGetMessageData() throws IOException {
		InStream inStream = new InStream("d8:msg_typei1e5:piecei42ee".getBytes(UTF8));

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), temporaryFolder.newFolder());
		IMessage message = cut.getMessage(inStream);

		assertNotNull("A message should have been returned", message);
		assertTrue("Incorrect message type returned. Expected: MessageData.", message instanceof MessageData);
	}

	@Test
	public void testGetDownloadFolder() throws IOException {
		File tempFolder = temporaryFolder.newFolder();

		UTMetadataExtension cut = new UTMetadataExtension(temporaryFolder.newFolder(), tempFolder);

		assertEquals("Incorrect location", tempFolder, cut.getDownloadFolder());
	}

	@Test
	public void testGetTorrentFile() throws IOException {
		Torrent torrentMock = createMock(Torrent.class);
		expect(torrentMock.getHash()).andReturn("c8369f0ba4bf6cd87fb13b3437782e2c7820bb38");
		replayAll();

		File tempFolder = temporaryFolder.newFolder();

		UTMetadataExtension cut = new UTMetadataExtension(tempFolder, temporaryFolder.newFolder());
		File torrentFile = cut.getTorrentFile(torrentMock);

		verifyAll();

		assertEquals("Incorrect location", new File(tempFolder, "c8369f0ba4bf6cd87fb13b3437782e2c7820bb38.torrent"), torrentFile);
	}

}