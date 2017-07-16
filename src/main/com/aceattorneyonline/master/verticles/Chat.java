package com.aceattorneyonline.master.verticles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aceattorneyonline.master.events.Events;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class Chat extends AbstractVerticle {

	private static final Logger logger = LoggerFactory.getLogger(Chat.class);

	@Override
	public void start() {
		logger.info("Chat verticle starting");
		EventBus eventBus = getVertx().eventBus();
		eventBus.consumer(Events.SEND_CHAT.toString(), this::handleSendChat);
	}

	@Override
	public void stop() {
		logger.info("Chat verticle stopping");
	}

	public void handleSendChat(Message<String> event) {
		event.fail(0, "not implemented"); // TODO
	}

}