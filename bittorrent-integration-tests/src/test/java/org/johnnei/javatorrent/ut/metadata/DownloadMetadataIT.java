package org.johnnei.javatorrent.ut.metadata;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.johnnei.javatorrent.TorrentClient;
import org.johnnei.javatorrent.bittorrent.encoding.SHA1;
import org.johnnei.javatorrent.magnetlink.MagnetLink;
import org.johnnei.javatorrent.module.UTMetadataExtension;
import org.johnnei.javatorrent.network.ConnectionDegradation;
import org.johnnei.javatorrent.network.PeerConnectInfo;
import org.johnnei.javatorrent.network.socket.TcpSocket;
import org.johnnei.javatorrent.phases.PhaseData;
import org.johnnei.javatorrent.phases.PhaseMetadata;
import org.johnnei.javatorrent.phases.PhasePreMetadata;
import org.johnnei.javatorrent.phases.PhaseRegulator;
import org.johnnei.javatorrent.protocol.extension.ExtensionModule;
import org.johnnei.javatorrent.test.DummyEntity;
import org.johnnei.javatorrent.torrent.Metadata;
import org.johnnei.javatorrent.torrent.Torrent;
import org.johnnei.javatorrent.torrent.algos.requests.RateBasedLimiter;
import org.johnnei.javatorrent.tracker.PeerConnector;
import org.johnnei.javatorrent.tracker.UncappedDistributor;
import org.johnnei.javatorrent.utils.StringUtils;
import org.johnnei.junit.jupiter.Folder;
import org.johnnei.junit.jupiter.TempFolderExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the integration between all ut_metadata components by downloading a torrent metadata file.
 */
@ExtendWith(TempFolderExtension.class)
public class DownloadMetadataIT {

	private static final Logger LOGGER = LoggerFactory.getLogger(DownloadMetadataIT.class);

	private static final String SINGLE_FILE_TORRENT = "/torrent-files/gimp-2.8.16-setup-1.exe.torrent";

	private static final byte[] TORRENT_FILE_HASH = new byte[] {
			(byte) 0xc8,        0x36, (byte) 0x9f,        0x0b, (byte) 0xa4,
			(byte) 0xbf,        0x6c, (byte) 0xd8,        0x7f, (byte) 0xb1,
			       0x3b,        0x34,        0x37,        0x78,        0x2e,
			       0x2c,        0x78,        0x20, (byte) 0xbb,        0x38
	};

	private static final String METADATA_LINK = "magnet:?dn=GIMP+2.8.16-setup-1.exe&xt=urn:btih:c8369f0ba4bf6cd87fb13b3437782e2c7820bb38";

	private void copy(File from, File to) throws IOException {
		Path pathFrom = from.toPath();
		Path pathTo = to.toPath();
		try {
			Files.copy(pathFrom, pathTo, StandardCopyOption.REPLACE_EXISTING);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	@Test
	public void downloadMetadata(@Folder Path tmp) throws Exception {
		LOGGER.info("Verifying expected torrent files to exist.");
		File torrentFile = new File(DownloadMetadataIT.class.getResource(SINGLE_FILE_TORRENT).toURI());

		assertPreconditions(torrentFile);

		LOGGER.info("Setting up test environment.");
		copy(torrentFile, new File(torrentFile.getParentFile(), StringUtils.byteArrayToString(TORRENT_FILE_HASH).toLowerCase() + ".torrent"));
		File downloadFolderOne = tmp.resolve("client-one").toFile();
		File downloadFolderTwo = tmp.resolve("client-two").toFile();

		assertTrue(downloadFolderOne.mkdirs());
		assertTrue(downloadFolderTwo.mkdirs());

		LOGGER.info("Preparing torrent client to download with magnetlink.");
		CountDownLatch linkCompleteLatch = new CountDownLatch(1);
		// Pass an incorrect folder so that the metadata file can't be found
		TorrentClient clientWithLink = prepareTorrentClient(downloadFolderOne, downloadFolderOne)
				.setPeerDistributor(UncappedDistributor::new)
				.setPhaseRegulator(new PhaseRegulator.Builder()
						.registerInitialPhase(PhasePreMetadata.class, PhasePreMetadata::new, PhaseMetadata.class)
						.registerPhase(PhaseMetadata.class, PhaseMetadata::new, PhaseDataCountDown.class)
						.registerPhase(PhaseDataCountDown.class, (client, torrent) -> new PhaseDataCountDown(linkCompleteLatch, client, torrent))
						.build())
				.build();

		CountDownLatch metadataInitalizedLatch = new CountDownLatch(1);
		LOGGER.info("Preparing torrent client to download with file.");
		TorrentClient clientWithTorrent = prepareTorrentClient(torrentFile.getParentFile(), downloadFolderTwo)
				.setPeerDistributor(UncappedDistributor::new)
				.setPhaseRegulator(new PhaseRegulator.Builder()
						.registerInitialPhase(PhasePreMetadata.class, PhasePreMetadata::new, PhaseMetadata.class)
						.registerPhase(PhaseMetadata.class, PhaseMetadata::new, PhaseDataCountDown.class)
						.registerPhase(PhaseDataCountDown.class, (client, torrent) -> new PhaseDataCountDown(metadataInitalizedLatch, client, torrent))
						.build())
				.build();

		Torrent torrentFromFile = createTorrentFromFile(clientWithTorrent, torrentFile, downloadFolderTwo);
		Torrent torrentFromLink = new MagnetLink(METADATA_LINK, clientWithLink).getTorrent();

		LOGGER.info("Starting downloading");
		LOGGER.debug("[MAGNET ] Directory: {}, Port: {}", downloadFolderOne.getAbsolutePath(), clientWithLink.getDownloadPort());
		LOGGER.debug("[TORRENT] Directory: {}, Port: {}", downloadFolderTwo.getAbsolutePath(), clientWithTorrent.getDownloadPort());

		clientWithTorrent.download(torrentFromFile);
		clientWithLink.download(torrentFromLink);

		LOGGER.info("Waiting for client with torrent metadata to initialize the metadata structures.");
		assertTrue(metadataInitalizedLatch.await(5, TimeUnit.SECONDS), "Torrent failed to initialize metadata structure.");

		LOGGER.info("Adding peer connect request to client.");
		clientWithTorrent.getPeerConnector().enqueuePeer(
				new PeerConnectInfo(torrentFromFile, new InetSocketAddress("localhost", clientWithLink.getDownloadPort())));

		assertTimeoutPreemptively(Duration.of(1, ChronoUnit.MINUTES), () -> {
				do {
					linkCompleteLatch.await(1, TimeUnit.SECONDS);
					torrentFromFile.pollRates();
					torrentFromLink.pollRates();
					LOGGER.debug("[MAGNET ] Download: {}kb/s, Upload: {}kb/s", torrentFromLink.getDownloadRate() / 1024, torrentFromLink.getUploadRate() / 1024);
					LOGGER.debug("[TORRENT] Download: {}kb/s, Upload: {}kb/s", torrentFromFile.getDownloadRate() / 1024, torrentFromFile.getUploadRate() / 1024);
				} while (linkCompleteLatch.getCount() > 0);
			});

		clientWithLink.shutdown();
		clientWithTorrent.shutdown();
	}

	private Torrent createTorrentFromFile(TorrentClient torrentClient, File torrentFile, File downloadFolder) throws IOException {
		return new Torrent.Builder()
				.setTorrentClient(torrentClient)
				.setName("GIMP")
				.setDownloadFolder(downloadFolder)
				.setMetadata(new Metadata.Builder().readFromFile(torrentFile).build())
				.build();
	}

	private TorrentClient.Builder prepareTorrentClient(File torrentFileFolder, File downloadFolder) throws Exception {
		return new TorrentClient.Builder()
				.acceptIncomingConnections(true)
				.setConnectionDegradation(new ConnectionDegradation.Builder()
						.registerDefaultConnectionType(TcpSocket.class, TcpSocket::new)
						.build())
				.setDownloadPort(DummyEntity.findAvailableTcpPort())
				.setExecutorService(Executors.newScheduledThreadPool(2))
				.setRequestLimiter(new RateBasedLimiter())
				.setPeerConnector(PeerConnector::new)
				.registerModule(new ExtensionModule.Builder()
						.registerExtension(new UTMetadataExtension(torrentFileFolder, downloadFolder))
						.build())
				.registerTrackerProtocol("stub", (s, torrentClient) -> null);
	}

	private void assertPreconditions(File torrentFile) throws IOException {
		byte[] bytes = new byte[(int) torrentFile.length()];
		try (DataInputStream inputStream = new DataInputStream(new FileInputStream(torrentFile))) {
			inputStream.readFully(bytes);
		}

		assertArrayEquals(TORRENT_FILE_HASH, SHA1.hash(bytes), "The torrent file used to setup the test has a mismatching hash.");
	}

	private static class PhaseDataCountDown extends PhaseData {

		private final CountDownLatch latch;

		PhaseDataCountDown(CountDownLatch latch, TorrentClient torrentClient, Torrent torrent) {
			super(torrentClient, torrent);
			this.latch = latch;
		}

		@Override
		public void onPhaseEnter() {
			latch.countDown();
			super.onPhaseEnter();
		}
	}
}
