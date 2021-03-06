package org.johnnei.javatorrent.internal.utp;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.johnnei.javatorrent.internal.utils.SlidingTimedValue;
import org.johnnei.javatorrent.internal.utp.protocol.PacketType;
import org.johnnei.javatorrent.internal.utp.protocol.packet.UtpPacket;

/**
 * Responsible for calculating the delay on the socket and adjust the window size to reach additional delay of {@link #CCONTROL_TARGET} on top of the normal
 * socket delay.
 */
public class SocketWindowHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocketWindowHandler.class);

	private static final Duration CCONTROL_TARGET = Duration.ofMillis(100);

	private static final int MAX_WINDOW_CHANGE_PER_PACKET = 500;

	private int maxWindow;

	private SlidingTimedValue<Integer> measuredDelays;

	private Map<Short, UtpPacket> packetsInFlight;

	public SocketWindowHandler() {
		maxWindow = 150;
		measuredDelays = new SlidingTimedValue<>();
		packetsInFlight = new HashMap<>();
	}

	/**
	 * Updates the Socket Window based on the received packet.
	 *
	 * @param packet The received packet.
	 * @return The acked packet or {@link Optional#empty()} if no new packet was acked.
	 */
	public Optional<UtpPacket> onReceivedPacket(UtpPacket packet) {
		int measuredDelay = packet.getHeader().getTimestampDifference();
		measuredDelays.addValue(measuredDelay);

		Duration ourDelay = Duration.of((long) measuredDelay - measuredDelays.getMinimum(), ChronoUnit.MICROS);

		UtpPacket ackedPacket;
		synchronized (this) {
			ackedPacket = packetsInFlight.remove(packet.getHeader().getAcknowledgeNumber());
		}

		if (!ourDelay.isZero() && ackedPacket != null) {
			Duration offTarget = CCONTROL_TARGET.minus(ourDelay);
			double delayFactor = offTarget.toNanos() / (double) CCONTROL_TARGET.toNanos();

			// Due to window violations the window factor may exceed 1.0d which shouldn't be allow as we shouldn't exceed the max window.
			int ackedBytes = ackedPacket.getSize();

			double windowFactor = Math.min(ackedBytes, (double) maxWindow) / Math.max(ackedBytes, (double) maxWindow);
			int scaledGain = (int) (MAX_WINDOW_CHANGE_PER_PACKET * delayFactor * windowFactor);

			maxWindow = Math.max(0, maxWindow + scaledGain);

			synchronized (this) {
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace(
						"our_delay: [{}] us, off_target: [{}] us, delayFactor [{}], windowFactor [{}], scaledGain [{}] bytes, maxWindow [{}] bytes, packets in flight: {}",
						ourDelay,
						offTarget,
						delayFactor,
						windowFactor,
						scaledGain,
						maxWindow,
						packetsInFlight.values()
							.stream()
							.map(p -> Short.toUnsignedInt(p.getHeader().getSequenceNumber()) + "-" + p.getSize())
							.collect(Collectors.toList())
					);
				}
			}
		}

		return Optional.ofNullable(ackedPacket);
	}

	public void onSentPacket(UtpPacket packet) {
		if (packet.getHeader().getType() != PacketType.DATA.getTypeField()) {
			return;
		}

		synchronized (this) {
			packetsInFlight.putIfAbsent(packet.getHeader().getSequenceNumber(), packet);
		}
	}

	public int getBytesInFlight() {
		synchronized (this) {
			return packetsInFlight.values()
				.stream()
				.map(UtpPacket::getSize)
				.reduce((a, b) -> a + b)
				.orElse(0);
		}
	}

	public int getMaxWindow() {
		return maxWindow;
	}

	public void onTimeout() {
		maxWindow = 150;
		LOGGER.trace("Timeout occurred. max_window=[{}]", maxWindow);
	}

	public void onPacketLoss(UtpPacket packet) {
		maxWindow /= 2;
		LOGGER.trace("Packet [{}] was lost. max_window=[{}]", Short.toUnsignedInt(packet.getHeader().getSequenceNumber()), maxWindow);
	}
}
