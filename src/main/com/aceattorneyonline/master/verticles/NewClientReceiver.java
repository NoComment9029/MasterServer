package com.aceattorneyonline.master.verticles;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aceattorneyonline.master.Advertiser;
import com.aceattorneyonline.master.Player;
import com.aceattorneyonline.master.events.AdvertiserEventProtos.NewAdvertiser;
import com.aceattorneyonline.master.events.EventErrorReason;
import com.aceattorneyonline.master.events.Events;
import com.aceattorneyonline.master.events.PlayerEventProtos.NewPlayer;
import com.aceattorneyonline.master.events.SharedEventProtos.GetMotd;
import com.google.protobuf.InvalidProtocolBufferException;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class NewClientReceiver extends ClientListVerticle {

	private static final Logger logger = LoggerFactory.getLogger(NewClientReceiver.class);

	@Override
	public void start() {
		logger.info("New client receiver verticle starting");
		EventBus eventBus = getVertx().eventBus();
		eventBus.consumer(Events.NEW_PLAYER.toString(), this::handleNewPlayer);
		eventBus.consumer(Events.NEW_ADVERTISER.toString(), this::handleNewAdvertiser);
	}

	@Override
	public void stop() {
		logger.info("New client receiver verticle stopping (no new clients will be accepted)");
	}

	public void handleNewPlayer(Message<byte[]> event) {
		try {
			NewPlayer newPlayer = NewPlayer.parseFrom(event.body());
			UUID clientId = UUID.fromString(newPlayer.getId().getId());
			Player player = getPlayerById(clientId);
			if (player != null) {
				player.socket().endHandler(nil -> {
					logger.info("Dropped {} from client list", player);
					removePlayer(clientId, player);
				});
				getVertx().eventBus().send(Events.GET_MOTD.getEventName(), GetMotd.newBuilder().build().toByteArray(), reply -> {
					event.reply(reply.result().body());
				});
			} else {
				event.fail(EventErrorReason.INTERNAL_ERROR, "Player does not exist on player table");
			}
		} catch (InvalidProtocolBufferException e) {
			event.fail(EventErrorReason.INTERNAL_ERROR, "Could not parse NewPlayer protobuf");
		}
	}

	public void handleNewAdvertiser(Message<byte[]> event) {
		try {
			NewAdvertiser newAdvertiser = NewAdvertiser.parseFrom(event.body());
			UUID clientId = UUID.fromString(newAdvertiser.getId().getId());
			Advertiser advertiser = getAdvertiserById(clientId);
			if (advertiser != null) {
				advertiser.socket().endHandler(nil -> {
					logger.info("Dropped {} from client list", advertiser);
					removeAdvertiser(clientId, advertiser);
				});
				event.reply(null);
			} else {
				event.fail(EventErrorReason.SECURITY_ERROR, "Advertiser does not exist on advertiser table");
			}
		} catch (InvalidProtocolBufferException e) {
			event.fail(EventErrorReason.INTERNAL_ERROR, "Could not parse NewAdvertiser protobuf");
		}
	}

}
