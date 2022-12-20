package com.example.animebot.service;

import com.example.animebot.model.Animechan;
import com.example.animebot.client.JSONLoader;
import com.example.animebot.config.BotConfig;
import com.google.gson.Gson;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramWebhookBot {

    final BotConfig config;
    final String URL_ANIMECHAN = "https://animechan.vercel.app/api/random";
    final String URL_WAIFU = "https://api.waifu.pics/sfw/%s";
    final String URL_SET_WEBHOOK = "https://api.telegram.org/bot%s/setWebhook?url=%s";
    final List<String> categoryWaifu = List.of("waifu", "megumin", "dance", "kick", "cry", "blush", "kiss", "cuddle", "hug", "pat", "bonk", "smile", "nom", "happy", "wink");

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Стартуем!"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

        try {
            JSONLoader.getJSON(String.format(URL_SET_WEBHOOK, config.getToken(), config.getWebHookPath()));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

//        try {
//            setWebhook(SetWebhook.builder().url().build());
//        } catch (TelegramApiException e) {
//            throw new RuntimeException(e);
//        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public String getBotPath() {
        return config.getWebHookPath();
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (update.getMessage() != null && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            switch (messageText) {
                case "/check":
                    break;
                case "/start":
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "Получить цитату из аниме":
                    sendQuote(chatId);
                    break;
                case "Получить вайфу (image)":
                    sendCategory(chatId);
                default:
                    if (categoryWaifu.contains(messageText))
                        sendImage(chatId, messageText);
            }
        }
        return null;
    }

    private void sendImage(long chatId, String messageText) {
        String jsonStr;
        try {
            jsonStr = JSONLoader.getJSON(String.format(URL_WAIFU, messageText));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        JSONObject jsonObject = new JSONObject(jsonStr);
        InputFile file = new InputFile();
        String url = jsonObject.getString("url");
        file.setMedia(url);

        if (url.matches("\\S+.gif")) {
            SendAnimation sendAnimation = new SendAnimation();
            sendAnimation.setChatId(chatId);
            sendAnimation.setAnimation(file);
            executeMessage(sendAnimation);
        } else {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId);
            sendPhoto.setPhoto(file);
            executeMessage(sendPhoto);
        }
    }

    private void sendCategory(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "Выбери категорию");
        message.setReplyMarkup(getKeyboardCategoryWaifu());
        executeMessage(message);
    }

    private ReplyKeyboard getKeyboardCategoryWaifu() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        for (String category : categoryWaifu) {
            if (row.size() > 4) {
                keyboardRows.add(row);
                row = new KeyboardRow();
            }
            row.add(category);
        }
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private void sendQuote(long chatId) {
        Gson gson = new Gson();
        Animechan animechan;
        try {
            animechan = gson.fromJson(JSONLoader.getJSON(URL_ANIMECHAN), Animechan.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        executeMessage(new SendMessage(String.valueOf(chatId), animechan.toString()));
    }

    private ReplyKeyboardMarkup getKeyboardStart() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        row.add("Получить цитату из аниме");
        keyboardRows.add(row);

        row = new KeyboardRow();
        row.add("Получить вайфу (image)");
        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private void startCommandReceived(long chatId, String name) {
        String greeting = EmojiParser.parseToUnicode("Приветствую, " + name + " :smile:" +
                "\nВоспользуйся кнопками ниже)");
        SendMessage message = new SendMessage(String.valueOf(chatId), greeting);
        message.setReplyMarkup(getKeyboardStart());
        executeMessage(message);
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeMessage(SendPhoto photo) {
        try {
            execute(photo);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeMessage(SendAnimation animation) {
        try {
            execute(animation);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}










