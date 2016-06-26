package org.johnnei.javatorrent.torrent.algos.pieceselector;

import java.util.Arrays;
import java.util.Optional;

import org.johnnei.javatorrent.torrent.AbstractFileSet;
import org.johnnei.javatorrent.torrent.Torrent;
import org.johnnei.javatorrent.torrent.files.BlockStatus;
import org.johnnei.javatorrent.torrent.files.Piece;
import org.johnnei.javatorrent.torrent.peer.Peer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link FullPieceSelect}
 */
public class FullPieceSelectTest {

	@Test
	public void testSelectStartedPiecesOverUnstarted() {
		Piece pieceOne = new Piece(null, new byte[20], 0, 10, 5);
		pieceOne.setBlockStatus(1, BlockStatus.Requested);
		Piece pieceTwo = new Piece(null, new byte[20], 1, 10, 5);

		AbstractFileSet filesMock = mock(AbstractFileSet.class);
		when(filesMock.getNeededPieces()).thenReturn(Arrays.asList(pieceOne, pieceTwo).stream());

		Peer peerMock = mock(Peer.class);

		when(peerMock.hasPiece(anyInt())).thenReturn(true);

		Torrent torrentMock = mock(Torrent.class);
		when(torrentMock.getFileSet()).thenReturn(filesMock);

		FullPieceSelect cut = new FullPieceSelect(torrentMock);
		Optional<Piece> chosenPiece = cut.getPieceForPeer(peerMock);

		assertEquals("Incorrect piece has been selected", pieceOne, chosenPiece.get());
	}

	@Test
	public void testSelectStartedPiecesOverUnstartedExcludingPiecesWithoutAnyNeededBlock() {
		Piece pieceOne = new Piece(null, new byte[20], 0, 10, 5);
		pieceOne.setBlockStatus(0, BlockStatus.Requested);
		pieceOne.setBlockStatus(1, BlockStatus.Requested);
		Piece pieceTwo = new Piece(null, new byte[20], 1, 10, 5);

		AbstractFileSet filesMock = mock(AbstractFileSet.class);
		when(filesMock.getNeededPieces()).thenReturn(Arrays.asList(pieceOne, pieceTwo).stream());

		Peer peerMock = mock(Peer.class);

		when(peerMock.hasPiece(anyInt())).thenReturn(true);

		Torrent torrentMock = mock(Torrent.class);
		when(torrentMock.getFileSet()).thenReturn(filesMock);

		FullPieceSelect cut = new FullPieceSelect(torrentMock);
		Optional<Piece> chosenPiece = cut.getPieceForPeer(peerMock);

		assertEquals("Incorrect piece has been selected", pieceTwo, chosenPiece.get());
	}

	@Test
	public void testSelectStartedPiecesOverUnstartedNonFirstElement() {
		Piece pieceOne = new Piece(null, new byte[20], 0, 10, 5);
		Piece pieceTwo = new Piece(null, new byte[20], 1, 10, 5);
		pieceTwo.setBlockStatus(1, BlockStatus.Requested);

		AbstractFileSet filesMock = mock(AbstractFileSet.class);
		when(filesMock.getNeededPieces()).thenReturn(Arrays.asList(pieceOne, pieceTwo).stream());

		Peer peerMock = mock(Peer.class);

		when(peerMock.hasPiece(anyInt())).thenReturn(true);

		Torrent torrentMock = mock(Torrent.class);
		when(torrentMock.getFileSet()).thenReturn(filesMock);

		FullPieceSelect cut = new FullPieceSelect(torrentMock);
		Optional<Piece> chosenPiece = cut.getPieceForPeer(peerMock);

		assertEquals("Incorrect piece has been selected", pieceTwo, chosenPiece.get());
	}

	@Test
	public void testPickRarerPieces() {
		Piece pieceOne = new Piece(null, new byte[20], 0, 10, 5);
		Piece pieceTwo = new Piece(null, new byte[20], 1, 10, 5);

		AbstractFileSet filesMock = mock(AbstractFileSet.class);
		when(filesMock.getNeededPieces()).thenReturn(Arrays.asList(pieceOne, pieceTwo).stream());

		Peer peerMock = mock(Peer.class);
		Peer peerTwoMock = mock(Peer.class);

		when(peerMock.hasPiece(anyInt())).thenReturn(true);
		when(peerTwoMock.hasPiece(eq(0))).thenReturn(true);
		when(peerTwoMock.hasPiece(eq(1))).thenReturn(false);

		Torrent torrentMock = mock(Torrent.class);
		when(torrentMock.getFileSet()).thenReturn(filesMock);
		when(torrentMock.getPeers()).thenReturn(Arrays.asList(peerMock, peerTwoMock));

		FullPieceSelect cut = new FullPieceSelect(torrentMock);
		Optional<Piece> chosenPiece = cut.getPieceForPeer(peerMock);

		assertEquals("Incorrect piece has been selected", pieceTwo, chosenPiece.get());
	}
}