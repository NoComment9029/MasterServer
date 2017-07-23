package com.aceattorneyonline.master;

import java.time.Duration;
import java.time.Instant;

import io.vertx.core.net.SocketAddress;

/** Represents a server being advertised, perhaps by an advertiser. */
public class AdvertisedServer {
	private final SocketAddress address;
	private final int port;
	private final String name;
	private final String description;
	private final String version;
	private final Instant timeAdded;

	public AdvertisedServer(SocketAddress address, int port, String name, String description, String version) {
		this.address = address;
		this.port = port;
		this.name = name;
		this.description = description;
		this.version = version;
		this.timeAdded = Instant.now();
	}

	public SocketAddress address() {
		return address;
	}
	
	public int port() {
		return port;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public String version() {
		return version;
	}

	public Duration uptime() {
		return Duration.between(timeAdded, Instant.now());
	}

	public String toString() {
		return String.format("Server (%8s) - %30s - %s:%d", version(), name(), address().host(), port());
	}

}
