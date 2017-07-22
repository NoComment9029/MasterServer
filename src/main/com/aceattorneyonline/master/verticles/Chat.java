package com.aceattorneyonline.master.verticles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aceattorneyonline.master.ChatCommand;
import com.aceattorneyonline.master.ChatCommandSyntax;
import com.aceattorneyonline.master.Client;
import com.aceattorneyonline.master.Player;
import com.aceattorneyonline.master.events.EventErrorReason;
import com.aceattorneyonline.master.events.Events;
import com.aceattorneyonline.master.events.PlayerEventProtos.GetChatCommandHelp;
import com.aceattorneyonline.master.events.PlayerEventProtos.GetChatCommandList;
import com.aceattorneyonline.master.events.PlayerEventProtos.SendChat;
import com.aceattorneyonline.master.events.UuidProto.Uuid;
import com.google.protobuf.InvalidProtocolBufferException;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class Chat extends ClientListVerticle {

	private static final Logger logger = LoggerFactory.getLogger(Chat.class);

	private final Map<String, Events> commands = Events.getAllChatCommands();

	public Chat(Map<UUID, Client> clientList) {
		super(clientList);
	}

	@Override
	public void start() {
		logger.info("Chat verticle starting");
		EventBus eventBus = getVertx().eventBus();
		eventBus.consumer(Events.SEND_CHAT.getEventName(), this::handleSendChat);
		eventBus.consumer(Events.CHAT_COMMAND_LIST.getEventName(), this::handleCommandList);
		eventBus.consumer(Events.CHAT_COMMAND_HELP.getEventName(), this::handleCommandHelp);
	}

	@Override
	public void stop() {
		logger.info("Chat verticle stopping");
	}

	public void handleSendChat(Message<String> event) {
		try {
			SendChat chat = SendChat.parseFrom(event.body().getBytes());
			Uuid senderProtoId = chat.getId();
			UUID senderId = UUID.fromString(senderProtoId.getId());
			Player sender = getPlayerById(senderId);
			String senderName = chat.getUsername();
			String message = chat.getMessage().trim();

			if (sender == null) {
				event.fail(EventErrorReason.SECURITY_ERROR, "Requester is not a player.");
			} else if (sender.name() != null && !senderName.equals(sender.name())) {
				logger.warn("{} tried to send a message with a different name ({})!", sender, senderName);
				event.fail(EventErrorReason.SECURITY_ERROR, "You cannot send chat messags with a name other than \""
						+ sender.name() + "\" unless you change it.");
				// TODO make !name command to change one's name
			} else if (message.isEmpty()) {
				logger.warn("{} tried to send an empty message!", sender);
			} else if (message.charAt(0) == '!') {
				logger.info("{} ran a command: {}", senderId.toString(), message);
				List<String> tokens = Chat.parseChatCommand(message);
				// Remove the command token and strip the prefix.
				Events commandEvent = commands.get(tokens.remove(0).substring(1));
				if (commandEvent != null) {
					ChatCommand command = commandEvent.getChatCommand();
					try {
						com.google.protobuf.Message msg = command.serializeCommand(senderProtoId, tokens);
						// Pass the reply directly to the chat command sender, success or fail.
						// It's not our responsibility anymore.
						getVertx().eventBus().send(commandEvent.getEventName(), msg, event::reply);
					} catch (IllegalArgumentException e) {
						ChatCommandSyntax syntax = command.getSyntax();
						event.fail(EventErrorReason.USER_ERROR,
								"Syntax error.\nCommand usage: " + syntax.name() + " " + syntax.arguments());
					}
				} else {
					logger.info("Parsed command was not found in commands list");
					event.fail(EventErrorReason.USER_ERROR, "Command not found.");
				}
			} else {
				if (sender.name() == null) {
					sender.setName(senderName);
				}
				getVertx().eventBus().publish(Events.BROADCAST_CHAT.toString(), message);
				logger.info("{}: {}", senderId.toString(), message);
			}
			// TODO: anti-flood
		} catch (InvalidProtocolBufferException e) {
			event.fail(EventErrorReason.INTERNAL_ERROR, "Could not parse SendChat protobuf");
		}
	}

	public void handleCommandList(Message<String> event) {
		StringBuilder listBuilder = new StringBuilder();
		listBuilder.append("Commands list:\n");
		for (Events commandEvent : commands.values()) {
			ChatCommandSyntax command = commandEvent.getChatCommand().getSyntax();
			listBuilder.append(command.name() + " - " + command.description() + "\n");
		}
		event.reply(listBuilder.toString());
	}

	@ChatCommandSyntax(name = "list", description = "Lists all chat commands.", arguments = "")
	public static com.google.protobuf.Message parseCommandList(Uuid invoker, List<String> args)
			throws IllegalArgumentException {
		return GetChatCommandList.newBuilder().build();
	}

	public void handleCommandHelp(Message<String> event) {
		try {
			GetChatCommandHelp help = GetChatCommandHelp.parseFrom(event.body().getBytes());
			Events commandEvent = commands.get(help.getCommand());
			if (commandEvent != null) {
				ChatCommandSyntax syntax = commandEvent.getChatCommand().getSyntax();
				String description = syntax.description();
				if (description == null) {
					description = "No description provided.";
				}
				event.reply(syntax.name() + " " + syntax.arguments() + "\n" + description);
			} else {
				event.fail(EventErrorReason.USER_ERROR, "Command not found.");
			}
		} catch (InvalidProtocolBufferException e) {
			event.fail(EventErrorReason.INTERNAL_ERROR, "Could not parse GetCommandHelp protobuf");
		}
	}

	@ChatCommandSyntax(name = "help", description = "Gets help on one chat command or lists all chat commands.", arguments = "[command]")
	public static com.google.protobuf.Message parseCommandHelp(Uuid invoker, List<String> args)
			throws IllegalArgumentException {
		if (args.isEmpty()) {
			return GetChatCommandHelp.newBuilder().setCommand("").build();
		} else if (args.size() == 1) {
			String query = args.remove(0);
			if (query.charAt(0) == '!' || query.charAt(0) == '/') {
				query = query.substring(1);
			}
			return GetChatCommandHelp.newBuilder().setCommand(query).build();
		} else {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Tokenizes a chat message as if it were a command.
	 * 
	 * This parser can interpret quotation marks (for arguments containing spaces)
	 * as well as escaped quotation marks.
	 * 
	 * @param message
	 *            the message to be parsed
	 * @return a list of tokens, including the chat command sent with the prefix
	 *         ('!', '/', ...)
	 */
	public static List<String> parseChatCommand(String message) {
		List<String> tokens = new ArrayList<String>();

		// Trim the chat command itself out
		int cmdEnd = message.indexOf(' ');
		tokens.add(message.substring(0, cmdEnd));
		message = message.substring(cmdEnd + 1).trim(); // Trim again as there might be multiple separating spaces

		// https://stackoverflow.com/a/366239
		// Group 2: quoted strings
		// Group 3: unquoted individual words (but won't be inside a Group 2 match)
		final Pattern regex = Pattern.compile("(?:(['\"])(.*?)(?<!\\\\)(?>\\\\\\\\)*\\1|([^\\s]+))");
		Matcher matcher = regex.matcher(message);
		while (matcher.find()) {
			if (matcher.group(2) != null) {
				tokens.add(matcher.group(2));
			} else if (matcher.group(3) != null) {
				tokens.add(matcher.group(3));
			}
		}
		return tokens;
	}

}
